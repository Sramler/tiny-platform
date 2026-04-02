package com.tiny.platform.infrastructure.tenant.service;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.domain.ApiEndpointEntry;
import com.tiny.platform.infrastructure.auth.resource.domain.UiActionEntry;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.repository.CarrierProjectionRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.CarrierTemplateResourceSnapshotView;
import com.tiny.platform.infrastructure.auth.resource.repository.RoleResourcePermissionBindingView;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.service.ResourcePermissionBindingService;
import com.tiny.platform.infrastructure.auth.resource.support.PlatformControlPlaneResourcePolicy;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleResourceRelationProjection;
import com.tiny.platform.infrastructure.menu.domain.MenuEntry;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.infrastructure.tenant.config.PlatformTenantProperties;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 新租户从平台模板复制角色与资源，并在副本上写入 {@code role_permission}（不再写入 {@code role_resource}）。
 *
 * <p>运行时统一以 {@code tenant_id IS NULL} 的 role/resource 作为平台模板来源。
 * 若历史环境尚未回填平台模板，则仅在首次 bootstrap 时从配置的平台租户复制一次模板，
 * 之后所有新租户都只从平台模板派生，避免继续直接依赖“default 租户即模板”的隐式语义。</p>
 *
 * <p><b>非「租户副本重建」入口</b>：本类的 {@link #bootstrapFromPlatformTemplate} <strong>不是</strong>对已存在租户做模板重置；
 * {@link #assertTargetTenantHasNoDerivedTemplates} 在目标租户已有角色或 carrier 行时拒绝执行。产品层不提供一键重建/回退 API 的正式理由与替代手段见
 * {@code TINY_PLATFORM_TENANT_GOVERNANCE.md} §3.2。</p>
 */
@Service
public class TenantBootstrapServiceImpl implements TenantBootstrapService {

    private static final String ROLE_LEVEL_PLATFORM = "PLATFORM";
    private static final String RESOURCE_LEVEL_PLATFORM = "PLATFORM";

    /** 与 {@link com.tiny.platform.infrastructure.auth.role.domain.Role#name} 列长度一致 */
    private static final int ROLE_NAME_COLUMN_MAX_LEN = 50;

    private static final String PLATFORM_ROLE_NAME_SUFFIX_SEP = " · ";

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    private final TenantRepository tenantRepository;
    private final CarrierProjectionRepository carrierProjectionRepository;
    private final MenuEntryRepository menuEntryRepository;
    private final UiActionEntryRepository uiActionEntryRepository;
    private final ApiEndpointEntryRepository apiEndpointEntryRepository;
    private final RoleRepository roleRepository;
    private final PlatformTenantProperties platformTenantProperties;
    private final ResourcePermissionBindingService resourcePermissionBindingService;

    public TenantBootstrapServiceImpl(
        TenantRepository tenantRepository,
        CarrierProjectionRepository carrierProjectionRepository,
        MenuEntryRepository menuEntryRepository,
        UiActionEntryRepository uiActionEntryRepository,
        ApiEndpointEntryRepository apiEndpointEntryRepository,
        RoleRepository roleRepository,
        PlatformTenantProperties platformTenantProperties,
        ResourcePermissionBindingService resourcePermissionBindingService
    ) {
        this.tenantRepository = tenantRepository;
        this.carrierProjectionRepository = carrierProjectionRepository;
        this.menuEntryRepository = menuEntryRepository;
        this.uiActionEntryRepository = uiActionEntryRepository;
        this.apiEndpointEntryRepository = apiEndpointEntryRepository;
        this.roleRepository = roleRepository;
        this.platformTenantProperties = platformTenantProperties;
        this.resourcePermissionBindingService = resourcePermissionBindingService;
    }

    @Override
    @Transactional
    public void bootstrapFromPlatformTemplate(Tenant targetTenant) {
        Long targetTenantId = requireTargetTenantId(targetTenant);
        assertTargetTenantHasNoDerivedTemplates(targetTenantId);
        PlatformTemplateSnapshot snapshot = resolvePlatformTemplateSnapshot(targetTenantId);
        ResourceCloneResult resourceCloneResult = cloneResourcesFromSourceList(snapshot.resources(), targetTenantId, "TENANT");
        backfillPermissionBindings(targetTenantId);
        assertPermissionBindingsReady(targetTenantId, "目标租户");
        cloneRolesAndRelationsFromLists(snapshot.roles(), snapshot.relations(), resourceCloneResult, targetTenantId, "TENANT");
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformTemplateDiffResult diffPlatformTemplateForTenant(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        // Ensure platform template is initialized and legal before diffing.
        ensurePlatformTemplatesInitialized(tenantId);
        List<Resource> platformResources = loadCarrierTemplateSnapshot(null, RESOURCE_LEVEL_PLATFORM);
        List<Role> platformRoles = roleRepository.findByTenantIdIsNullOrderByIdAsc();
        assertPlatformTemplateSnapshot(platformResources, platformRoles);

        List<Resource> tenantResources = loadCarrierTemplateSnapshot(tenantId, "TENANT");
        Map<String, Resource> platformByKey = platformResources.stream()
            .collect(Collectors.toMap(
                this::stableCarrierKey,
                r -> r,
                (a, b) -> a,
                LinkedHashMap::new
            ));
        Map<String, Resource> tenantByKey = tenantResources.stream()
            .collect(Collectors.toMap(
                this::stableCarrierKey,
                r -> r,
                (a, b) -> a,
                LinkedHashMap::new
            ));

        List<PlatformTemplateDiffResult.EntryDiff> diffs = new ArrayList<>();

        for (Map.Entry<String, Resource> entry : platformByKey.entrySet()) {
            String key = entry.getKey();
            Resource platform = entry.getValue();
            Resource tenant = tenantByKey.get(key);
            if (tenant == null) {
                diffs.add(new PlatformTemplateDiffResult.EntryDiff(
                    carrierType(platform),
                    key,
                    platform.getId(),
                    null,
                    PlatformTemplateDiffResult.DiffType.MISSING_IN_TENANT,
                    Map.of()
                ));
                continue;
            }
            Map<String, PlatformTemplateDiffResult.FieldDiff> fieldDiffs = diffFields(platform, tenant);
            if (!fieldDiffs.isEmpty()) {
                diffs.add(new PlatformTemplateDiffResult.EntryDiff(
                    carrierType(platform),
                    key,
                    platform.getId(),
                    tenant.getId(),
                    PlatformTemplateDiffResult.DiffType.CHANGED,
                    fieldDiffs
                ));
            }
        }

        for (Map.Entry<String, Resource> entry : tenantByKey.entrySet()) {
            String key = entry.getKey();
            if (platformByKey.containsKey(key)) {
                continue;
            }
            Resource tenant = entry.getValue();
            diffs.add(new PlatformTemplateDiffResult.EntryDiff(
                carrierType(tenant),
                key,
                null,
                tenant.getId(),
                PlatformTemplateDiffResult.DiffType.EXTRA_IN_TENANT,
                Map.of()
            ));
        }

        diffs.sort(Comparator
            .comparing(PlatformTemplateDiffResult.EntryDiff::carrierType, Comparator.nullsLast(String::compareTo))
            .thenComparing(PlatformTemplateDiffResult.EntryDiff::diffType)
            .thenComparing(PlatformTemplateDiffResult.EntryDiff::key));

        long missing = diffs.stream().filter(d -> d.diffType() == PlatformTemplateDiffResult.DiffType.MISSING_IN_TENANT).count();
        long extra = diffs.stream().filter(d -> d.diffType() == PlatformTemplateDiffResult.DiffType.EXTRA_IN_TENANT).count();
        long changed = diffs.stream().filter(d -> d.diffType() == PlatformTemplateDiffResult.DiffType.CHANGED).count();

        return new PlatformTemplateDiffResult(
            tenantId,
            new PlatformTemplateDiffResult.Summary(
                platformResources.size(),
                tenantResources.size(),
                (int) missing,
                (int) extra,
                (int) changed
            ),
            diffs
        );
    }

    private void backfillPermissionBindings(Long targetTenantId) {
        resourcePermissionBindingService.backfillPermissionCatalogFromResources(targetTenantId);
        resourcePermissionBindingService.bindRequiredPermissionIdsForResources(targetTenantId);
    }

    @Override
    @Transactional
    public boolean ensurePlatformTemplatesInitialized() {
        return ensurePlatformTemplatesInitialized(null);
    }

    private PlatformTemplateSnapshot resolvePlatformTemplateSnapshot(Long targetTenantId) {
        ensurePlatformTemplatesInitialized(targetTenantId);
        backfillPermissionBindings(null);
        List<Resource> platformResources = loadCarrierTemplateSnapshot(null, RESOURCE_LEVEL_PLATFORM);
        List<Role> platformRoles = roleRepository.findByTenantIdIsNullOrderByIdAsc();
        assertPlatformTemplateSnapshot(platformResources, platformRoles);
        return new PlatformTemplateSnapshot(
            platformResources,
            platformRoles,
            roleRepository.findGrantedRoleCarrierPairsForPlatformTemplate()
        );
    }

    private boolean ensurePlatformTemplatesInitialized(Long targetTenantId) {
        List<Resource> platformResources = loadCarrierTemplateSnapshot(null, RESOURCE_LEVEL_PLATFORM);
        List<Role> platformRoles = roleRepository.findByTenantIdIsNullOrderByIdAsc();
        assertPlatformTemplateSnapshot(platformResources, platformRoles);
        if (!platformResources.isEmpty() && !platformRoles.isEmpty()) {
            backfillPermissionBindings(null);
            return false;
        }
        if (!platformResources.isEmpty() || !platformRoles.isEmpty()) {
            throw new IllegalStateException("平台模板数据不完整，请先修复 tenant_id IS NULL 的角色/资源模板后再创建新租户");
        }
        backfillPlatformTemplatesFromConfiguredTenant(targetTenantId);
        return true;
    }

    private void backfillPlatformTemplatesFromConfiguredTenant(Long targetTenantId) {
        String sourceCode = platformTenantProperties.getPlatformTenantCode();
        Tenant sourceTenant = tenantRepository.findByCode(sourceCode)
            .orElseThrow(() -> new IllegalStateException("平台模板缺失，且平台/模板租户不存在（code=" + sourceCode + "），无法初始化新租户权限模型"));

        if (targetTenantId != null && Objects.equals(sourceTenant.getId(), targetTenantId)) {
            throw new IllegalStateException("平台模板缺失，且目标租户与平台/模板租户相同，无法回填平台模板");
        }

        List<Resource> sourceResources = loadCarrierTemplateSnapshot(sourceTenant.getId(), "TENANT");
        List<Role> sourceRoles = roleRepository.findByTenantIdOrderByIdAsc(sourceTenant.getId());
        List<RoleResourceRelationProjection> sourceRelations =
            roleRepository.findGrantedRoleCarrierPairsByTenantId(sourceTenant.getId());

        if (sourceResources.isEmpty() || sourceRoles.isEmpty()) {
            throw new IllegalStateException("平台模板缺失，且平台/模板租户未提供可复制的角色与资源");
        }

        ResourceCloneResult resourceCloneResult = cloneResourcesFromSourceList(sourceResources, null, "PLATFORM");
        backfillPermissionBindings(null);
        assertPermissionBindingsReady(null, "平台模板");
        cloneRolesAndRelationsFromLists(sourceRoles, sourceRelations, resourceCloneResult, null, "PLATFORM");
    }

    private void assertTargetTenantHasNoDerivedTemplates(Long targetTenantId) {
        if (existsCarrierRowsByTenantId(targetTenantId) || roleRepository.existsByTenantId(targetTenantId)) {
            throw new IllegalStateException("目标租户已存在角色或资源副本，不允许重复从平台模板派生");
        }
    }

    private boolean existsCarrierRowsByTenantId(Long tenantId) {
        if (tenantId == null) {
            return false;
        }
        long menuCount = menuEntryRepository.count((root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("tenantId"), tenantId));
        if (menuCount > 0) {
            return true;
        }
        long actionCount = uiActionEntryRepository.count((root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("tenantId"), tenantId));
        if (actionCount > 0) {
            return true;
        }
        long apiCount = apiEndpointEntryRepository.count((root, query, criteriaBuilder) ->
            criteriaBuilder.equal(root.get("tenantId"), tenantId));
        return apiCount > 0;
    }

    private void assertPlatformTemplateSnapshot(List<Resource> platformResources, List<Role> platformRoles) {
        boolean invalidResource = platformResources.stream().anyMatch(resource ->
            resource.getTenantId() != null || !RESOURCE_LEVEL_PLATFORM.equalsIgnoreCase(resource.getResourceLevel()));
        if (invalidResource) {
            throw new IllegalStateException("平台模板资源快照非法：tenant_id IS NULL 的资源必须全部标记为 PLATFORM");
        }
        boolean invalidRole = platformRoles.stream().anyMatch(role ->
            role.getTenantId() != null || !ROLE_LEVEL_PLATFORM.equalsIgnoreCase(role.getRoleLevel()));
        if (invalidRole) {
            throw new IllegalStateException("平台模板角色快照非法：tenant_id IS NULL 的角色必须全部标记为 PLATFORM");
        }
    }

    private ResourceCloneResult cloneResourcesFromSourceList(List<Resource> allSourceResources, Long targetTenantId, String targetResourceLevel) {
        Set<Long> skippedSourceResourceIds = allSourceResources.stream()
            .filter(PlatformControlPlaneResourcePolicy::isPlatformOnlyResource)
            .map(Resource::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        List<Resource> sourceResources = allSourceResources.stream()
            .filter(resource -> !PlatformControlPlaneResourcePolicy.isPlatformOnlyResource(resource))
            .toList();
        if (sourceResources.isEmpty()) {
            return new ResourceCloneResult(Map.of(), skippedSourceResourceIds);
        }

        Map<Long, MenuEntry> clonedMenuBySourceId = new LinkedHashMap<>();
        Map<Long, UiActionEntry> clonedUiActionBySourceId = new LinkedHashMap<>();
        Map<Long, ApiEndpointEntry> clonedApiEndpointBySourceId = new LinkedHashMap<>();
        List<MenuEntry> clonedMenus = new ArrayList<>();
        List<UiActionEntry> clonedUiActions = new ArrayList<>();
        List<ApiEndpointEntry> clonedApiEndpoints = new ArrayList<>();
        for (Resource source : sourceResources) {
            if (source == null || source.getId() == null || source.getType() == null) {
                continue;
            }
            if (source.getType() == ResourceType.MENU || source.getType() == ResourceType.DIRECTORY) {
                MenuEntry clone = new MenuEntry();
                clone.setTenantId(targetTenantId);
                clone.setResourceLevel(targetResourceLevel);
                clone.setName(source.getName());
                clone.setTitle(source.getTitle());
                clone.setPath(source.getUrl());
                clone.setIcon(source.getIcon());
                clone.setShowIcon(source.getShowIcon());
                clone.setSort(source.getSort());
                clone.setComponent(source.getComponent());
                clone.setRedirect(source.getRedirect());
                clone.setHidden(source.getHidden());
                clone.setKeepAlive(source.getKeepAlive());
                clone.setPermission(source.getPermission());
                clone.setRequiredPermissionId(null);
                clone.setType(source.getType().getCode());
                clone.setEnabled(source.getEnabled());
                clone.setParentId(null);
                clonedMenuBySourceId.put(source.getId(), clone);
                clonedMenus.add(clone);
                continue;
            }
            if (source.getType() == ResourceType.BUTTON) {
                UiActionEntry clone = new UiActionEntry();
                clone.setTenantId(targetTenantId);
                clone.setResourceLevel(targetResourceLevel);
                clone.setName(source.getName());
                clone.setTitle(source.getTitle());
                // ui_action.action_key is required by schema; snapshot does not currently include it.
                // Use a stable derived value to keep bootstrap deterministic.
                clone.setActionKey(source.getName());
                clone.setPagePath(source.getUrl());
                clone.setPermission(source.getPermission());
                clone.setRequiredPermissionId(null);
                clone.setParentMenuId(null);
                clone.setSort(source.getSort());
                clone.setEnabled(source.getEnabled());
                clonedUiActionBySourceId.put(source.getId(), clone);
                clonedUiActions.add(clone);
                continue;
            }
            if (source.getType() == ResourceType.API) {
                ApiEndpointEntry clone = new ApiEndpointEntry();
                clone.setTenantId(targetTenantId);
                clone.setResourceLevel(targetResourceLevel);
                clone.setName(source.getName());
                clone.setTitle(source.getTitle());
                clone.setUri(source.getUri());
                clone.setMethod(source.getMethod());
                clone.setPermission(source.getPermission());
                clone.setRequiredPermissionId(null);
                clone.setEnabled(source.getEnabled());
                clonedApiEndpointBySourceId.put(source.getId(), clone);
                clonedApiEndpoints.add(clone);
            }
        }

        if (!clonedMenus.isEmpty()) {
            menuEntryRepository.saveAll(clonedMenus);
        }
        if (!clonedUiActions.isEmpty()) {
            uiActionEntryRepository.saveAll(clonedUiActions);
        }
        if (!clonedApiEndpoints.isEmpty()) {
            apiEndpointEntryRepository.saveAll(clonedApiEndpoints);
        }

        boolean hasParentReference = false;
        for (Resource source : sourceResources) {
            if (source.getParentId() == null) {
                continue;
            }
            if (source.getId() == null || source.getType() == null) {
                continue;
            }
            if (source.getType() == ResourceType.MENU || source.getType() == ResourceType.DIRECTORY) {
                MenuEntry clonedParent = clonedMenuBySourceId.get(source.getParentId());
                MenuEntry cloned = clonedMenuBySourceId.get(source.getId());
                if (clonedParent == null || clonedParent.getId() == null || cloned == null) {
                    throw new IllegalStateException("平台模板菜单父节点映射缺失，无法复制菜单树");
                }
                cloned.setParentId(clonedParent.getId());
                hasParentReference = true;
                continue;
            }
            if (source.getType() == ResourceType.BUTTON) {
                MenuEntry clonedParent = clonedMenuBySourceId.get(source.getParentId());
                UiActionEntry cloned = clonedUiActionBySourceId.get(source.getId());
                if (clonedParent == null || clonedParent.getId() == null || cloned == null) {
                    throw new IllegalStateException("平台模板按钮父节点映射缺失，无法复制按钮树");
                }
                cloned.setParentMenuId(clonedParent.getId());
                hasParentReference = true;
            }
        }
        if (hasParentReference) {
            if (!clonedMenus.isEmpty()) {
                menuEntryRepository.saveAll(clonedMenus);
            }
            if (!clonedUiActions.isEmpty()) {
                uiActionEntryRepository.saveAll(clonedUiActions);
            }
        }

        Map<Long, Long> resourceIdMapping = new LinkedHashMap<>();
        for (Resource source : sourceResources) {
            if (source == null || source.getId() == null || source.getType() == null) {
                continue;
            }
            Long clonedId = null;
            if (source.getType() == ResourceType.MENU || source.getType() == ResourceType.DIRECTORY) {
                MenuEntry cloned = clonedMenuBySourceId.get(source.getId());
                clonedId = cloned != null ? cloned.getId() : null;
            } else if (source.getType() == ResourceType.BUTTON) {
                UiActionEntry cloned = clonedUiActionBySourceId.get(source.getId());
                clonedId = cloned != null ? cloned.getId() : null;
            } else if (source.getType() == ResourceType.API) {
                ApiEndpointEntry cloned = clonedApiEndpointBySourceId.get(source.getId());
                clonedId = cloned != null ? cloned.getId() : null;
            }
            if (clonedId == null) {
                throw new IllegalStateException("平台模板资源复制失败，缺少目标载体ID: sourceId=" + source.getId());
            }
            resourceIdMapping.put(source.getId(), clonedId);
        }
        return new ResourceCloneResult(resourceIdMapping, skippedSourceResourceIds);
    }

    private void cloneRolesAndRelationsFromLists(List<Role> sourceRoles, List<RoleResourceRelationProjection> relations,
                                                 ResourceCloneResult resourceCloneResult, Long targetTenantId,
                                                 String targetRoleLevel) {
        if (sourceRoles.isEmpty()) {
            return;
        }

        Map<Long, Role> clonedBySourceRoleId = new LinkedHashMap<>();
        List<Role> clonedRoles = new ArrayList<>();
        for (Role source : sourceRoles) {
            Role clone = new Role();
            clone.setTenantId(targetTenantId);
            clone.setRoleLevel(targetRoleLevel);
            clone.setCode(source.getCode());
            clone.setName(resolveClonedRoleDisplayName(source, targetTenantId, targetRoleLevel));
            clone.setEnabled(source.isEnabled());
            clone.setBuiltin(source.isBuiltin());
            clone.setDescription(source.getDescription());
            clonedBySourceRoleId.put(source.getId(), clone);
            clonedRoles.add(clone);
        }

        roleRepository.saveAll(clonedRoles);
        Map<Long, Long> permissionIdByTargetResourceId = resolvePermissionIdByTargetResourceId(
            resourceCloneResult.resourceIdMapping(),
            targetTenantId,
            targetRoleLevel
        );

        for (RoleResourceRelationProjection relation : relations) {
            Role targetRole = clonedBySourceRoleId.get(relation.getRoleId());
            Long sourceResourceId = relation.getResourceId();
            Long targetResourceId = resourceCloneResult.resourceIdMapping().get(sourceResourceId);
            if (targetRole == null || targetRole.getId() == null) {
                throw new IllegalStateException("平台模板角色复制失败，缺少目标角色ID");
            }
            if (targetResourceId == null) {
                if (resourceCloneResult.skippedSourceResourceIds().contains(sourceResourceId)) {
                    continue;
                }
                throw new IllegalStateException("平台模板角色资源关联复制失败，缺少目标资源ID");
            }
            Long permissionId = permissionIdByTargetResourceId.get(targetResourceId);
            if (permissionId == null) {
                throw new IllegalStateException("平台模板角色资源关联复制失败，缺少目标权限绑定: resourceId=" + targetResourceId);
            }
            roleRepository.addRolePermissionRelationByPermissionId(targetTenantId, targetRole.getId(), permissionId);
        }
    }

    private Map<Long, Long> resolvePermissionIdByTargetResourceId(Map<Long, Long> resourceIdMapping,
                                                                   Long targetTenantId,
                                                                   String targetRoleLevel) {
        List<Long> targetResourceIds = resourceIdMapping.values().stream()
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (targetResourceIds.isEmpty()) {
            return Map.of();
        }
        List<RoleResourcePermissionBindingView> carrierBindings = carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(
            targetResourceIds,
            targetTenantId,
            targetRoleLevel
        );
        Map<Long, Long> permissionByResourceId = toPermissionIdMap(carrierBindings);
        boolean carrierSnapshotComplete = permissionByResourceId.keySet().containsAll(targetResourceIds);
        boolean carrierBindingsReady = targetResourceIds.stream()
            .allMatch(id -> permissionByResourceId.get(id) != null);
        if (carrierSnapshotComplete && carrierBindingsReady) {
            return permissionByResourceId;
        }
        throw new IllegalStateException("平台模板权限绑定快照不完整：载体行存在但缺少 required_permission_id，无法继续 bootstrap");
    }

    private Map<Long, Long> toPermissionIdMap(List<RoleResourcePermissionBindingView> bindings) {
        List<RoleResourcePermissionBindingView> safeBindings = bindings == null ? List.of() : bindings;
        Map<Long, Long> permissionByResourceId = new LinkedHashMap<>();
        for (RoleResourcePermissionBindingView binding : safeBindings) {
            if (binding == null || binding.getId() == null) {
                continue;
            }
            Long existing = permissionByResourceId.putIfAbsent(binding.getId(), binding.getRequiredPermissionId());
            if (existing != null && !Objects.equals(existing, binding.getRequiredPermissionId())) {
                throw new IllegalStateException("平台模板权限快照冲突：同一 resourceId 出现多个 required_permission_id: " + binding.getId());
            }
        }
        return permissionByResourceId;
    }

    private Long requireTargetTenantId(Tenant targetTenant) {
        if (targetTenant == null || targetTenant.getId() == null) {
            throw new IllegalArgumentException("目标租户不存在，无法初始化权限模型");
        }
        return targetTenant.getId();
    }

    /**
     * 克隆角色展示名：去掉首尾空白与异常换行，避免脏数据触发 uk_role_tenant_name；
     * 平台模板（tenant_id IS NULL）在 name 后附加 {@code · roleCode}，保证多角色不与同名字段冲突。
     *
     * <p>注意：数据库约束要求 {@link com.tiny.platform.infrastructure.auth.role.domain.Role#name} 全局唯一。
     * 因此当从平台模板克隆到租户（tenant_id != NULL）时，也必须为租户侧 role.name 引入租户区分后缀。</p>
     * 结果长度不超过 50 字符（与 {@code Role.name} 列一致），避免 Data truncation。
     */
    private static String resolveClonedRoleDisplayName(Role source, Long targetTenantId, String targetRoleLevel) {
        String raw = source.getName() != null ? source.getName() : "";
        String normalized = WHITESPACE_RUN.matcher(raw.trim()).replaceAll(" ");
        String code = source.getCode() != null ? source.getCode() : "";
        String base = normalized.isEmpty() ? code : normalized;
        if (targetTenantId == null && ROLE_LEVEL_PLATFORM.equalsIgnoreCase(targetRoleLevel)) {
            String suffix = PLATFORM_ROLE_NAME_SUFFIX_SEP + code;
            String combined = base + suffix;
            if (combined.length() <= ROLE_NAME_COLUMN_MAX_LEN) {
                return combined;
            }
            if (suffix.length() <= ROLE_NAME_COLUMN_MAX_LEN) {
                int maxBase = ROLE_NAME_COLUMN_MAX_LEN - suffix.length();
                String head = maxBase > 0 ? truncateUtf16PreservingLength(base, maxBase) : "";
                return head + suffix;
            }
            return truncateUtf16PreservingLength(suffix, ROLE_NAME_COLUMN_MAX_LEN);
        }
        if (targetTenantId != null && !ROLE_LEVEL_PLATFORM.equalsIgnoreCase(targetRoleLevel)) {
            // Tenant clone: ensure global uniqueness of Role.name.
            String suffix = PLATFORM_ROLE_NAME_SUFFIX_SEP + "tenant:" + targetTenantId;
            String combined = base + suffix;
            return truncateUtf16PreservingLength(combined, ROLE_NAME_COLUMN_MAX_LEN);
        }
        return truncateUtf16PreservingLength(base, ROLE_NAME_COLUMN_MAX_LEN);
    }

    private static String truncateUtf16PreservingLength(String s, int maxChars) {
        if (s == null || s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars);
    }

    private void assertPermissionBindingsReady(Long tenantId, String label) {
        List<Resource> resources = loadCarrierTemplateSnapshot(
            tenantId,
            tenantId == null ? RESOURCE_LEVEL_PLATFORM : "TENANT"
        );
        List<Long> invalidResourceIds = resources.stream()
            .filter(resource -> StringUtils.hasText(resource.getPermission()))
            .filter(resource -> resource.getRequiredPermissionId() == null)
            .map(Resource::getId)
            .filter(Objects::nonNull)
            .limit(10)
            .toList();
        if (!invalidResourceIds.isEmpty()) {
            throw new IllegalStateException(label + "存在未完成权限绑定的资源，无法继续 bootstrap: " + invalidResourceIds);
        }
    }

    private List<Resource> loadCarrierTemplateSnapshot(Long tenantId, String resourceLevel) {
        List<CarrierTemplateResourceSnapshotView> snapshotViews =
            carrierProjectionRepository.findTemplateSnapshotViewsByScope(tenantId, resourceLevel);
        List<CarrierTemplateResourceSnapshotView> safeViews = snapshotViews == null ? List.of() : snapshotViews;
        return safeViews.stream()
            .map(this::toSnapshotResource)
            .toList();
    }

    private String stableCarrierKey(Resource resource) {
        if (resource == null) {
            return "";
        }
        String type = resource.getType() != null ? String.valueOf(resource.getType().getCode()) : "unknown";
        String permission = normalizeBlank(resource.getPermission());
        if (StringUtils.hasText(permission)) {
            return type + ":perm:" + permission;
        }
        // Fallback: try to keep deterministic for assets without permission.
        String name = normalizeBlank(resource.getName());
        if (resource.getType() == ResourceType.API) {
            return type + ":api:" + normalizeBlank(resource.getMethod()).toUpperCase(Locale.ROOT) + ":" + normalizeBlank(resource.getUri());
        }
        String url = normalizeBlank(resource.getUrl());
        return type + ":" + name + ":" + url;
    }

    private String normalizeBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private String carrierType(Resource resource) {
        if (resource == null || resource.getType() == null) {
            return "unknown";
        }
        return switch (resource.getType()) {
            case MENU, DIRECTORY -> "menu";
            case BUTTON -> "ui_action";
            case API -> "api_endpoint";
            default -> "unknown";
        };
    }

    private Map<String, PlatformTemplateDiffResult.FieldDiff> diffFields(Resource platform, Resource tenant) {
        Map<String, PlatformTemplateDiffResult.FieldDiff> diffs = new LinkedHashMap<>();
        if (!Objects.equals(platform.getRequiredPermissionId(), tenant.getRequiredPermissionId())) {
            diffs.put("requiredPermissionId", new PlatformTemplateDiffResult.FieldDiff(
                String.valueOf(platform.getRequiredPermissionId()),
                String.valueOf(tenant.getRequiredPermissionId())
            ));
        }
        // common fields
        addIfDifferent(diffs, "enabled", boolToString(platform.getEnabled(), true), boolToString(tenant.getEnabled(), true));
        addIfDifferent(diffs, "title", platform.getTitle(), tenant.getTitle());

        if (platform.getType() == ResourceType.MENU || platform.getType() == ResourceType.DIRECTORY) {
            addIfDifferent(diffs, "path", platform.getUrl(), tenant.getUrl());
            addIfDifferent(diffs, "component", platform.getComponent(), tenant.getComponent());
            addIfDifferent(diffs, "redirect", platform.getRedirect(), tenant.getRedirect());
            addIfDifferent(diffs, "hidden", boolToString(platform.getHidden(), false), boolToString(tenant.getHidden(), false));
            addIfDifferent(diffs, "keepAlive", boolToString(platform.getKeepAlive(), false), boolToString(tenant.getKeepAlive(), false));
            addIfDifferent(diffs, "sort", String.valueOf(platform.getSort()), String.valueOf(tenant.getSort()));
        } else if (platform.getType() == ResourceType.BUTTON) {
            addIfDifferent(diffs, "pagePath", platform.getUrl(), tenant.getUrl());
            addIfDifferent(diffs, "sort", String.valueOf(platform.getSort()), String.valueOf(tenant.getSort()));
        } else if (platform.getType() == ResourceType.API) {
            addIfDifferent(diffs, "method", normalizeBlank(platform.getMethod()).toUpperCase(Locale.ROOT), normalizeBlank(tenant.getMethod()).toUpperCase(Locale.ROOT));
            addIfDifferent(diffs, "uri", platform.getUri(), tenant.getUri());
        }
        return diffs;
    }

    private void addIfDifferent(Map<String, PlatformTemplateDiffResult.FieldDiff> diffs,
                                String field,
                                String platformValue,
                                String tenantValue) {
        String p = platformValue == null ? "" : platformValue;
        String t = tenantValue == null ? "" : tenantValue;
        if (!Objects.equals(p, t)) {
            diffs.put(field, new PlatformTemplateDiffResult.FieldDiff(p, t));
        }
    }

    private String boolToString(Boolean value, boolean defaultValue) {
        boolean v = value != null ? value : defaultValue;
        return v ? "true" : "false";
    }

    private Resource toSnapshotResource(CarrierTemplateResourceSnapshotView view) {
        Resource resource = new Resource();
        resource.setId(view.getId());
        resource.setTenantId(view.getTenantId());
        resource.setResourceLevel(view.getResourceLevel());
        resource.setName(str(view.getName()));
        resource.setUrl(str(view.getUrl()));
        resource.setUri(str(view.getUri()));
        resource.setMethod(str(view.getMethod()));
        resource.setIcon(str(view.getIcon()));
        resource.setShowIcon(bool(view.getShowIcon()));
        resource.setSort(intVal(view.getSort()));
        resource.setComponent(str(view.getComponent()));
        resource.setRedirect(str(view.getRedirect()));
        resource.setHidden(bool(view.getHidden()));
        resource.setKeepAlive(bool(view.getKeepAlive()));
        resource.setTitle(str(view.getTitle()));
        resource.setPermission(str(view.getPermission()));
        resource.setRequiredPermissionId(view.getRequiredPermissionId());
        resource.setType(ResourceType.fromCode(view.getTypeCode()));
        resource.setParentId(view.getParentId());
        resource.setEnabled(bool(view.getEnabled(), true));
        return resource;
    }

    private String str(String value) {
        return value != null ? value : "";
    }

    private boolean bool(Long value) {
        return bool(value, false);
    }

    private boolean bool(Long value, boolean defaultValue) {
        return value != null ? value != 0 : defaultValue;
    }

    private int intVal(Integer value) {
        return value != null ? value : 0;
    }

    private record ResourceCloneResult(Map<Long, Long> resourceIdMapping, Set<Long> skippedSourceResourceIds) {
    }

    private record PlatformTemplateSnapshot(List<Resource> resources,
                                            List<Role> roles,
                                            List<RoleResourceRelationProjection> relations) {
    }
}
