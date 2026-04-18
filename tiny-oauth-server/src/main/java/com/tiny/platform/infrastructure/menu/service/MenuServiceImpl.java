package com.tiny.platform.infrastructure.menu.service;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScope;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.exception.NotFoundException;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceRequestDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.repository.ApiEndpointEntryRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.UiActionEntryRepository;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.domain.RequirementAwareAuditDetail;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.resource.service.CarrierPermissionReferenceSafetyService;
import com.tiny.platform.infrastructure.auth.resource.service.CarrierPermissionRequirementEvaluator;
import com.tiny.platform.infrastructure.auth.resource.service.ResourcePermissionBindingService;
import com.tiny.platform.infrastructure.auth.resource.support.PlatformControlPlaneResourcePolicy;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.menu.domain.MenuEntry;
import com.tiny.platform.infrastructure.menu.repository.MenuEntryRepository;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.criteria.Predicate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.ArrayList;


/**
 * 菜单业务实现类，专注于 type=0/1 的菜单和目录。
 * <p>租户范围以 {@link TenantContext#getActiveTenantId()} 为准，不以 user.tenant_id 为依据。
 * PLATFORM 作用域的读侧直接使用 {@code tenant_id IS NULL} 的平台正式载体；
 * 写侧要求显式 activeTenantId，不再回退到 default/platformTenantCode 兼容壳。
 */
@Service
public class MenuServiceImpl implements MenuService {
    private static final String ACTIVE = "ACTIVE";
    private static final String LEGACY_PLATFORM_ROLE_CONSTRAINT_PATH = "/system/role/constraint";
    private static final Logger logger = LoggerFactory.getLogger(MenuServiceImpl.class);
    private final MenuEntryRepository menuEntryRepository;
    private final UiActionEntryRepository uiActionEntryRepository;
    private final ApiEndpointEntryRepository apiEndpointEntryRepository;
    private final TenantUserRepository tenantUserRepository;
    private final UserUnitRepository userUnitRepository;
    private final ResourcePermissionBindingService resourcePermissionBindingService;
    private final CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService;
    private final CarrierPermissionRequirementEvaluator carrierPermissionRequirementEvaluator;
    private final AuthorizationAuditService authorizationAuditService;
    private final RoleRepository roleRepository;

    @Autowired
    public MenuServiceImpl(MenuEntryRepository menuEntryRepository,
                           UiActionEntryRepository uiActionEntryRepository,
                           ApiEndpointEntryRepository apiEndpointEntryRepository,
                           TenantUserRepository tenantUserRepository,
                           UserUnitRepository userUnitRepository,
                           ResourcePermissionBindingService resourcePermissionBindingService,
                           CarrierPermissionReferenceSafetyService carrierPermissionReferenceSafetyService,
                           CarrierPermissionRequirementEvaluator carrierPermissionRequirementEvaluator,
                           AuthorizationAuditService authorizationAuditService,
                           RoleRepository roleRepository) {
        this.menuEntryRepository = menuEntryRepository;
        this.uiActionEntryRepository = uiActionEntryRepository;
        this.apiEndpointEntryRepository = apiEndpointEntryRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.userUnitRepository = userUnitRepository;
        this.resourcePermissionBindingService = resourcePermissionBindingService;
        this.carrierPermissionReferenceSafetyService = carrierPermissionReferenceSafetyService;
        this.carrierPermissionRequirementEvaluator = carrierPermissionRequirementEvaluator;
        this.authorizationAuditService = authorizationAuditService;
        this.roleRepository = roleRepository;
    }

    private Long normalizeParentId(Long parentId) {
        if (parentId == null || parentId == 0) {
            return null;
        }
        return parentId;
    }

    /**
     * 分页查询菜单（支持type、parentId、title、enabled多条件）
     */
    @Override
    @DataScope(module = "menu")
    public Page<ResourceResponseDto> menus(ResourceRequestDto query, Pageable pageable) {
        Long tenantId = resolveReadTenantId();
        LinkedHashSet<Long> visibleCreatorIds = resolveVisibleCreatorIdsForRead(tenantId);
        if (requiresDataScopeFilterForScope(tenantId) && visibleCreatorIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Page<MenuEntry> page = menuEntryRepository.findAll(
            buildMenuEntrySpecification(query, tenantId, visibleCreatorIds, false),
            normalizeMenuPageable(pageable)
        );
        return page.map(menu -> toDto(menu, tenantId));
    }
    
    /**
     * 获取菜单树结构（只查 type=0/1）
     */
    @Override
    public List<ResourceResponseDto> menuTree() {
        List<MenuEntry> menus = findTreeMenusForCurrentUser(resolveReadTenantId());
        List<ResourceResponseDto> fullTree = buildDtoTree(menus.stream()
            .map(this::toDto)
            .toList());

        // 1. 获取所有节点的扁平化列表，用于后续查找 redirect 目标
        List<ResourceResponseDto> flatList = new ArrayList<>();
        flattenTree(fullTree, flatList);

        // 2. 从扁平化列表中，筛选出所有可见菜单的 URL，作为有效 redirect 目标集合
        Set<String> visibleUrls = flatList.stream()
                .filter(this::isNodeVisible)
                .map(ResourceResponseDto::getUrl)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        // 3. 返回前进行过滤，并传入可见 URL 集合
        return filterVisibleMenus(fullTree, visibleUrls);
    }

    /**
     * 返回不带任何过滤的完整菜单树
     * 用于角色授权、菜单管理等后台场景
     */
    @Override
    public List<ResourceResponseDto> menuTreeAll() {
        Long tenantId = resolveReadTenantId();
        List<MenuEntry> menus = tenantId == null
            ? menuEntryRepository.findByTenantIdIsNullAndTypeInOrderBySortAsc(
                List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode())
            )
            : menuEntryRepository.findByTenantIdAndTypeInOrderBySortAsc(
                tenantId,
                List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode())
            );
        List<ResourceResponseDto> dtos = menus.stream()
            .map(menu -> toDto(menu, tenantId))
            .filter(this::canAccessPlatformMenu)
            .toList();
        return buildDtoTree(dtos);
    }

    /**
     * 将树形结构扁平化为列表
     * @param nodes 节点列表
     * @param flatList 存储扁平化结果的列表
     */
    private void flattenTree(List<ResourceResponseDto> nodes, List<ResourceResponseDto> flatList) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        for (ResourceResponseDto node : nodes) {
            flatList.add(node);
            if (node.getChildren() != null) {
                flattenTree(node.getChildren(), flatList);
            }
        }
    }

    /**
     * 递归过滤出启用的、未隐藏的菜单项
     * @param nodes 菜单节点列表
     * @param visibleUrls 所有可见菜单的 URL 集合，用于校验 redirect
     * @return 过滤后的菜单节点列表
     */
    private List<ResourceResponseDto> filterVisibleMenus(List<ResourceResponseDto> nodes, Set<String> visibleUrls) {
        if (nodes == null) {
            return new ArrayList<>();
        }

        return nodes.stream()
            .filter(this::isNodeVisible)
            .map(node -> {
                if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                    node.setChildren(filterVisibleMenus(node.getChildren(), visibleUrls));
                }
                if (node.getType() != null && node.getType() == ResourceType.DIRECTORY.getCode()) {
                    node.setRedirect(normalizeDirectoryRedirect(node, visibleUrls));
                }
                return node;
            })
            // 过滤掉没有可访问子菜单的目录，除非它有有效的重定向
            .filter(node -> {
                // 非目录节点直接保留
                if (node.getType() != 0) {
                    return true;
                }

                // 是目录节点，满足以下任一条件则保留：
                // 1. 有可见的子菜单
                boolean hasVisibleChildren = node.getChildren() != null && !node.getChildren().isEmpty();
                // 2. 有一个有效的重定向地址（该地址必须是一个可见菜单的URL）
                boolean hasValidRedirect = StringUtils.hasText(node.getRedirect()) && visibleUrls.contains(node.getRedirect());

                return hasVisibleChildren || hasValidRedirect;
            })
            .collect(Collectors.toList());
    }

    private String normalizeDirectoryRedirect(ResourceResponseDto node, Set<String> visibleUrls) {
        if (node == null) {
            return null;
        }
        if (StringUtils.hasText(node.getRedirect()) && visibleUrls.contains(node.getRedirect())) {
            return node.getRedirect();
        }
        return resolveFirstVisibleNavigableUrl(node.getChildren(), visibleUrls);
    }

    private String resolveFirstVisibleNavigableUrl(List<ResourceResponseDto> nodes, Set<String> visibleUrls) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        for (ResourceResponseDto node : nodes) {
            String target = resolveNavigableUrl(node, visibleUrls);
            if (StringUtils.hasText(target)) {
                return target;
            }
        }
        return null;
    }

    private String resolveNavigableUrl(ResourceResponseDto node, Set<String> visibleUrls) {
        if (node == null || !isNodeVisible(node)) {
            return null;
        }
        if (node.getType() != null && node.getType() == ResourceType.DIRECTORY.getCode()) {
            return normalizeDirectoryRedirect(node, visibleUrls);
        }
        if (StringUtils.hasText(node.getUrl())) {
            return node.getUrl();
        }
        return resolveFirstVisibleNavigableUrl(node.getChildren(), visibleUrls);
    }

    private boolean isNodeVisible(ResourceResponseDto node) {
        boolean enabled = !Boolean.FALSE.equals(node.getEnabled());
        boolean hidden = Boolean.TRUE.equals(node.getHidden());
        return enabled && !hidden;
    }

    /**
     * 根据父级ID查询子菜单（只查 type=0/1）。
     * <p>该入口服务于菜单管理控制面，应返回当前作用域下可管理的完整菜单载体，
     * 不再复用运行态菜单树的 requirement-aware 过滤口径。</p>
     */
    @Override
    @DataScope(module = "menu")
    public List<ResourceResponseDto> getMenusByParentId(Long parentId) {
        Long tenantId = resolveReadTenantId();
        LinkedHashSet<Long> visibleCreatorIds = resolveVisibleCreatorIdsForRead(tenantId);
        if (requiresDataScopeFilterForScope(tenantId) && visibleCreatorIds.isEmpty()) {
            return List.of();
        }

        ResourceRequestDto query = new ResourceRequestDto();
        query.setParentId(normalizeParentId(parentId));

        return menuEntryRepository.findAll(
                buildMenuEntrySpecification(query, tenantId, visibleCreatorIds, false),
                Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
            )
            .stream()
            .map(menu -> toDto(menu, tenantId))
            .filter(this::canAccessPlatformMenu)
            .collect(Collectors.toList());
    }

    /**
     * 创建菜单
     */
    @Override
    public Resource createMenu(ResourceCreateUpdateDto resourceDto) {
        Long tenantId = requireTenantId();
        // 只允许 type=0/1
        if (resourceDto.getType() == null || (resourceDto.getType() != ResourceType.DIRECTORY.getCode() && resourceDto.getType() != ResourceType.MENU.getCode())) {
            resourceDto.setType(ResourceType.MENU.getCode());
        }
        
        // 验证父ID设置（创建时ID为null，所以传入0作为占位符）
        resourceDto.setParentId(normalizeParentId(resourceDto.getParentId()));
        validateParentId(0L, resourceDto.getParentId());
        
        Resource resource = new Resource();
        resource.setTenantId(tenantId);
        resource.setResourceLevel(TenantContext.isPlatformScope() ? "PLATFORM" : "TENANT");
        resource.setCreatedBy(extractCurrentUserId());
        resource.setName(resourceDto.getName());
        resource.setTitle(resourceDto.getTitle());
        resource.setUrl(resourceDto.getUrl());
        resource.setUri(StringUtils.hasText(resourceDto.getUri()) ? resourceDto.getUri() : "");
        resource.setMethod(StringUtils.hasText(resourceDto.getMethod()) ? resourceDto.getMethod() : "");
        resource.setIcon(resourceDto.getIcon());
        resource.setShowIcon(resourceDto.isShowIcon());
        resource.setSort(resourceDto.getSort());
        resource.setComponent(resourceDto.getComponent());
        resource.setRedirect(resourceDto.getRedirect());
        resource.setHidden(resourceDto.isHidden());
        resource.setKeepAlive(resourceDto.isKeepAlive());
        resource.setPermission(resourceDto.getPermission());
        resource.setRequiredPermissionId(resourceDto.getRequiredPermissionId());
        resource.setType(ResourceType.fromCode(resourceDto.getType()));
        resource.setParentId(resourceDto.getParentId());
        resourcePermissionBindingService.bindResource(resource, resource.getCreatedBy());
        failClosedWhenLegacyPermissionInputPersists(resource);
        return toResource(menuEntryRepository.save(toMenuEntry(resource)));
    }

    /**
     * 更新菜单
     */
    @Override
    public Resource updateMenu(ResourceCreateUpdateDto resourceDto) {
        Long tenantId = requireTenantId();
        MenuEntry carrier = menuEntryRepository.findById(resourceDto.getId())
                .filter(r -> Objects.equals(r.getTenantId(), tenantId))
                .orElseThrow(() -> new RuntimeException("菜单不存在"));
        
        // 验证父ID设置
        resourceDto.setParentId(normalizeParentId(resourceDto.getParentId()));
        validateParentId(resourceDto.getId(), resourceDto.getParentId());
        
        carrier.setName(resourceDto.getName());
        carrier.setTitle(resourceDto.getTitle());
        carrier.setPath(resourceDto.getUrl());
        carrier.setIcon(resourceDto.getIcon());
        carrier.setShowIcon(resourceDto.isShowIcon());
        carrier.setSort(resourceDto.getSort());
        carrier.setComponent(resourceDto.getComponent());
        carrier.setRedirect(resourceDto.getRedirect());
        carrier.setHidden(resourceDto.isHidden());
        carrier.setKeepAlive(resourceDto.isKeepAlive());
        carrier.setPermission(resourceDto.getPermission());
        carrier.setRequiredPermissionId(resourceDto.getRequiredPermissionId());
        carrier.setType(resourceDto.getType());
        carrier.setParentId(resourceDto.getParentId());
        Resource marker = toResource(carrier);
        resourcePermissionBindingService.bindResource(marker, extractCurrentUserId());
        failClosedWhenLegacyPermissionInputPersists(marker);
        carrier.setPermission(marker.getPermission());
        carrier.setRequiredPermissionId(marker.getRequiredPermissionId());
        return toResource(menuEntryRepository.save(carrier));
    }

    private void failClosedWhenLegacyPermissionInputPersists(Resource resource) {
        if (resource == null) {
            return;
        }
        if (StringUtils.hasText(resource.getPermission()) && resource.getRequiredPermissionId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Missing required_permission_id for provided permission");
        }
    }
    
    /**
     * 验证父ID设置是否有效
     */
    private void validateParentId(Long menuId, Long parentId) {
        Long tenantId = requireTenantId();
        Long normalizedParentId = normalizeParentId(parentId);
        // 如果父ID为空，表示设置为顶级菜单，这是允许的
        if (normalizedParentId == null) {
            return;
        }
        
        // 不能将自己设置为自己的父菜单
        if (menuId != null && menuId.equals(normalizedParentId)) {
            throw new BusinessException(
                ErrorCode.RESOURCE_CONFLICT,
                "不能将自己设置为父菜单"
            );
        }
        
        // 检查父菜单是否存在
        MenuEntry parentMenu = menuEntryRepository.findById(normalizedParentId)
                .filter(m -> Objects.equals(m.getTenantId(), tenantId))
                .orElseThrow(() -> new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "父菜单不存在，ID: " + normalizedParentId
                ));
        
        // 父菜单必须是目录类型
        if (!Objects.equals(parentMenu.getType(), ResourceType.DIRECTORY.getCode())) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "父菜单必须是目录类型，当前类型: " + parentMenu.getType()
            );
        }
        
        // 检查是否会造成循环引用
        if (wouldCreateCircularReference(menuId, normalizedParentId)) {
            throw new BusinessException(
                ErrorCode.RESOURCE_CONFLICT,
                "设置此父菜单会造成循环引用"
            );
        }
    }
    
    /**
     * 检查是否会造成循环引用
     */
    private boolean wouldCreateCircularReference(Long menuId, Long parentId) {
        Long tenantId = requireTenantId();
        Long currentParentId = parentId;
        Set<Long> visitedIds = new HashSet<>();
        
        while (currentParentId != null && currentParentId != 0) {
            // 如果已经访问过这个ID，说明存在循环
            if (visitedIds.contains(currentParentId)) {
                return true;
            }
            
            // 如果找到了要修改的菜单ID，说明会造成循环
            if (currentParentId.equals(menuId)) {
                return true;
            }
            
            visitedIds.add(currentParentId);
            
            // 获取当前父菜单的父ID
            MenuEntry currentParent = menuEntryRepository.findById(currentParentId)
                    .filter(m -> Objects.equals(m.getTenantId(), tenantId))
                    .orElse(null);
            if (currentParent == null) {
                break;
            }
            currentParentId = currentParent.getParentId();
        }
        
        return false;
    }

    /**
     * 删除菜单
     * 如果菜单有子菜单，会先删除所有子菜单（级联删除）
     * 若该菜单是某权限在当前租户下的最后一个载体，则同步清理对应的 role_permission 关系
     */
    @Override
    @Transactional
    public void deleteMenu(Long id) {
        Long tenantId = requireTenantId();
        // 检查菜单是否存在（优先使用 carrier 读链，resource 仅作兼容删除载体）
        MenuEntry menu = menuEntryRepository.findById(id)
                .filter(m -> Objects.equals(m.getTenantId(), tenantId))
                .orElseThrow(() -> new NotFoundException(
                        "菜单不存在: " + id));
        if (!Objects.equals(menu.getType(), ResourceType.DIRECTORY.getCode())
            && !Objects.equals(menu.getType(), ResourceType.MENU.getCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅支持删除目录/菜单载体");
        }
        
        // 递归删除所有子菜单
        deleteMenuRecursive(id);
        
        // 删除当前菜单（会先删除角色关联）
        deleteResourceWithRoleAssociations(id, menu.getRequiredPermissionId());
    }
    
    /**
     * 递归删除菜单及其所有子菜单
     */
    private void deleteMenuRecursive(Long parentId) {
        Long tenantId = requireTenantId();
        // 查找所有子菜单（包括目录和菜单类型），优先 menu 载体读链
        List<MenuEntry> children = menuEntryRepository.findByTenantIdAndTypeInAndParentIdOrderBySortAsc(
            tenantId,
            List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()),
            parentId
        );
        
        // 递归删除每个子菜单
        for (MenuEntry child : children) {
            deleteMenuRecursive(child.getId());
            deleteResourceWithRoleAssociations(child.getId(), child.getRequiredPermissionId());
        }
    }
    
    /**
     * 删除资源及其与角色的关联关系。
     * <p>当前权限真相源已经是 {@code permission/role_permission}，因此删除载体资源时不应直接把角色授权全部撤掉。
     * 仅当该资源是当前租户下某个 permission 的最后一个载体时，才顺带清理对应的 role_permission。</p>
     */
    private void deleteResourceWithRoleAssociations(Long resourceId, Long requiredPermissionId) {
        Long tenantId = requireTenantId();
        // Resource compatibility rows are no longer maintained in runtime.
        // Deletion only targets carrier tables; legacy resource cleanup (if needed) is handled out-of-band.
        deleteCarrierEntriesById(resourceId);
        if (requiredPermissionId == null) {
            return;
        }
        boolean permissionStillReferenced = carrierPermissionReferenceSafetyService.existsPermissionReference(requiredPermissionId, tenantId);
        if (permissionStillReferenced) {
            return;
        }
        roleRepository.deleteRolePermissionRelationsByPermissionIdAndTenantId(requiredPermissionId, tenantId);
    }

    /**
     * 批量删除菜单
     * 如果菜单有子菜单，会先删除所有子菜单（级联删除）
     */
    @Override
    @Transactional
    public void batchDeleteMenus(List<Long> ids) {
        // 对每个菜单执行级联删除
        for (Long id : ids) {
            deleteMenu(id);
        }
    }

    @Override
    @Transactional
    public Resource updateMenuSort(Long id, Integer sort) {
        Long tenantId = requireTenantId();
        MenuEntry carrier = menuEntryRepository.findById(id)
            .filter(existing -> Objects.equals(existing.getTenantId(), tenantId))
            .orElseThrow(() -> new RuntimeException("菜单不存在"));
        carrier.setSort(sort);
        MenuEntry savedCarrier = menuEntryRepository.save(carrier);
        return toResource(savedCarrier);
    }

    /**
     * 构建菜单树结构
     */
    private List<ResourceResponseDto> buildResourceTree(List<Resource> resources) {
        try {
            // 构建ID到资源的映射
            Map<Long, ResourceResponseDto> resourceMap = new HashMap<>();
            List<ResourceResponseDto> rootResources = new ArrayList<>();
            
            // 转换为DTO并建立映射
            for (Resource resource : resources) {
                ResourceResponseDto dto = toDto(resource);
                resourceMap.put(resource.getId(), dto);
            }
            
            // 构建树形结构
            for (Resource resource : resources) {
                ResourceResponseDto dto = resourceMap.get(resource.getId());
                Long parentId = normalizeParentId(resource.getParentId());
                if (parentId == null) {
                    // 顶级资源
                    rootResources.add(dto);
                } else {
                    // 子资源
                    ResourceResponseDto parent = resourceMap.get(parentId);
                    if (parent != null) {
                        if (parent.getChildren() == null) {
                            parent.setChildren(new ArrayList<>());
                        }
                        parent.getChildren().add(dto);
                    }
                }
            }
            
            return rootResources;
        } catch (Exception e) {
            // 如果构建树形结构失败，返回平铺列表
            return resources.stream().map(this::toDto).collect(Collectors.toList());
        }
    }

    private List<ResourceResponseDto> buildDtoTree(List<ResourceResponseDto> resources) {
        try {
            Map<Long, ResourceResponseDto> resourceMap = new HashMap<>();
            List<ResourceResponseDto> rootResources = new ArrayList<>();

            for (ResourceResponseDto resource : resources) {
                resource.setChildren(new ArrayList<>());
                resourceMap.put(resource.getId(), resource);
            }

            for (ResourceResponseDto resource : resources) {
                Long parentId = normalizeParentId(resource.getParentId());
                if (parentId == null) {
                    rootResources.add(resource);
                    continue;
                }
                ResourceResponseDto parent = resourceMap.get(parentId);
                if (parent != null) {
                    parent.getChildren().add(resource);
                }
            }

            refreshLeafFlags(rootResources);
            return rootResources;
        } catch (Exception e) {
            return resources;
        }
    }

    private void refreshLeafFlags(List<ResourceResponseDto> nodes) {
        if (nodes == null) {
            return;
        }
        for (ResourceResponseDto node : nodes) {
            List<ResourceResponseDto> children = node.getChildren();
            node.setLeaf(children == null || children.isEmpty());
            refreshLeafFlags(children);
        }
    }

    /**
     * 实体转DTO
     */
    private ResourceResponseDto toDto(Resource resource) {
        try {
            if (resource == null) {
                return null;
            }
            
            ResourceResponseDto dto = new ResourceResponseDto();
            dto.setRecordTenantId(resource.getTenantId());
            dto.setId(resource.getId());
            dto.setName(resource.getName());
            dto.setTitle(resource.getTitle());
            dto.setUrl(resource.getUrl());
            dto.setUri(resource.getUri());
            dto.setMethod(resource.getMethod());
            dto.setIcon(resource.getIcon());
            dto.setShowIcon(Boolean.TRUE.equals(resource.getShowIcon()));
            dto.setSort(resource.getSort());
            dto.setComponent(resource.getComponent());
            dto.setRedirect(resource.getRedirect());
            dto.setHidden(Boolean.TRUE.equals(resource.getHidden()));
            dto.setKeepAlive(Boolean.TRUE.equals(resource.getKeepAlive()));
            dto.setPermission(resource.getPermission());
            dto.setRequiredPermissionId(resource.getRequiredPermissionId());
            dto.setType(resource.getType() != null ? resource.getType().getCode() : null);
            dto.setTypeName(resource.getType() != null ? resource.getType().getDescription() : null);
            dto.setCarrierKind(resource.getType() != null ? getCarrierKind(resource.getType()) : null);
            dto.setParentId(normalizeParentId(resource.getParentId()));
            dto.setEnabled(Boolean.TRUE.equals(resource.getEnabled()));
            
            // 判断是否为叶子节点（没有子资源）
            Long tenantId = resource.getTenantId();
            Boolean isLeaf = !hasChildMenu(resource.getId(), tenantId)
                && !hasChildUiAction(resource.getId(), tenantId);
            dto.setLeaf(Boolean.TRUE.equals(isLeaf));
            
            // 初始化children为空集合，避免null指针异常
            dto.setChildren(new ArrayList<>());
            
            return dto;
        } catch (Exception e) {
            // 如果转换失败，返回一个基本的DTO，并确保空指针安全
            ResourceResponseDto dto = new ResourceResponseDto();
            if (resource != null) {
                dto.setId(resource.getId());
                dto.setName(resource.getName());
                dto.setTitle(resource.getTitle());
                dto.setType(resource.getType() != null ? resource.getType().getCode() : null);
                dto.setParentId(resource.getParentId());
            }
            dto.setEnabled(false);
            dto.setLeaf(true);
            dto.setChildren(new ArrayList<>());
            return dto;
        }
    }

    private ResourceResponseDto toDto(MenuEntry menu) {
        return toDto(menu, menu != null ? menu.getTenantId() : null);
    }

    private ResourceResponseDto toDto(MenuEntry menu, Long tenantId) {
        if (menu == null) {
            return null;
        }
        ResourceResponseDto dto = new ResourceResponseDto();
        dto.setRecordTenantId(menu.getTenantId());
        dto.setId(menu.getId());
        dto.setName(menu.getName());
        dto.setTitle(menu.getTitle());
        dto.setUrl(menu.getPath());
        dto.setIcon(menu.getIcon());
        dto.setShowIcon(Boolean.TRUE.equals(menu.getShowIcon()));
        dto.setSort(menu.getSort());
        dto.setComponent(menu.getComponent());
        dto.setRedirect(menu.getRedirect());
        dto.setHidden(Boolean.TRUE.equals(menu.getHidden()));
        dto.setKeepAlive(Boolean.TRUE.equals(menu.getKeepAlive()));
        dto.setPermission(menu.getPermission());
        dto.setRequiredPermissionId(menu.getRequiredPermissionId());
        dto.setType(menu.getType());
        if (menu.getType() != null) {
            ResourceType resourceType = ResourceType.fromCode(menu.getType());
            dto.setTypeName(resourceType != null ? resourceType.getDescription() : null);
            dto.setCarrierKind(resourceType != null ? getCarrierKind(resourceType) : null);
        }
        dto.setParentId(normalizeParentId(menu.getParentId()));
        dto.setEnabled(Boolean.TRUE.equals(menu.getEnabled()));
        if (menu.getId() != null) {
            dto.setLeaf(!hasChildMenu(menu.getId(), tenantId));
        }
        dto.setChildren(new ArrayList<>());
        return dto;
    }

    private Resource toResource(MenuEntry menu) {
        Resource resource = new Resource();
        resource.setId(menu.getId());
        resource.setTenantId(menu.getTenantId());
        resource.setResourceLevel(menu.getResourceLevel());
        resource.setName(menu.getName());
        resource.setTitle(menu.getTitle());
        resource.setUrl(menu.getPath());
        resource.setUri("");
        resource.setMethod("");
        resource.setIcon(menu.getIcon());
        resource.setShowIcon(Boolean.TRUE.equals(menu.getShowIcon()));
        resource.setSort(menu.getSort());
        resource.setComponent(menu.getComponent());
        resource.setRedirect(menu.getRedirect());
        resource.setHidden(Boolean.TRUE.equals(menu.getHidden()));
        resource.setKeepAlive(Boolean.TRUE.equals(menu.getKeepAlive()));
        resource.setPermission(menu.getPermission());
        resource.setRequiredPermissionId(menu.getRequiredPermissionId());
        resource.setType(ResourceType.fromCode(menu.getType()));
        resource.setParentId(normalizeParentId(menu.getParentId()));
        resource.setCreatedAt(menu.getCreatedAt());
        resource.setCreatedBy(menu.getCreatedBy());
        resource.setUpdatedAt(menu.getUpdatedAt());
        resource.setEnabled(Boolean.TRUE.equals(menu.getEnabled()));
        return resource;
    }

    private MenuEntry toMenuEntry(Resource resource) {
        MenuEntry menu = new MenuEntry();
        menu.setId(resource.getId());
        menu.setTenantId(resource.getTenantId());
        menu.setResourceLevel(resource.getResourceLevel());
        menu.setName(resource.getName());
        menu.setTitle(resource.getTitle());
        menu.setPath(resource.getUrl());
        menu.setIcon(resource.getIcon());
        menu.setShowIcon(Boolean.TRUE.equals(resource.getShowIcon()));
        menu.setSort(resource.getSort());
        menu.setComponent(resource.getComponent());
        menu.setRedirect(resource.getRedirect());
        menu.setHidden(Boolean.TRUE.equals(resource.getHidden()));
        menu.setKeepAlive(Boolean.TRUE.equals(resource.getKeepAlive()));
        menu.setPermission(resource.getPermission());
        menu.setRequiredPermissionId(resource.getRequiredPermissionId());
        menu.setType(resource.getType().getCode());
        menu.setParentId(normalizeParentId(resource.getParentId()));
        menu.setEnabled(Boolean.TRUE.equals(resource.getEnabled()));
        menu.setCreatedAt(resource.getCreatedAt());
        menu.setCreatedBy(resource.getCreatedBy());
        menu.setUpdatedAt(resource.getUpdatedAt());
        return menu;
    }

    private void deleteCarrierEntriesById(Long resourceId) {
        if (resourceId == null) {
            return;
        }
        List<Long> ids = List.of(resourceId);
        menuEntryRepository.deleteAllByIdInBatch(ids);
        uiActionEntryRepository.deleteAllByIdInBatch(ids);
        apiEndpointEntryRepository.deleteAllByIdInBatch(ids);
    }

    private String getCarrierKind(ResourceType resourceType) {
        return switch (resourceType) {
            case DIRECTORY, MENU -> "menu";
            case BUTTON -> "ui_action";
            case API -> "api_endpoint";
        };
    }

    /**
     * 按条件查询菜单（type=0/1），返回 list 结构。
     * <p>数据范围由 {@code @DataScope} + {@link DataScopeContext} 注入；几何与有效角色随
     * {@link com.tiny.platform.core.oauth.tenant.TenantContext} 的 active scope 经
     * {@link com.tiny.platform.infrastructure.auth.datascope.service.DataScopeResolverService} 解析（Contract B）。</p>
     */
    @Override
    @DataScope(module = "menu")
    public List<ResourceResponseDto> list(ResourceRequestDto query) {
        Long tenantId = resolveReadTenantId();
        LinkedHashSet<Long> visibleCreatorIds = resolveVisibleCreatorIdsForRead(tenantId);
        if (requiresDataScopeFilterForScope(tenantId) && visibleCreatorIds.isEmpty()) {
            return List.of();
        }
        return menuEntryRepository.findAll(
                buildMenuEntrySpecification(query, tenantId, visibleCreatorIds, true),
                Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
            )
            .stream()
            .map(menu -> toDto(menu, tenantId))
            .filter(this::canAccessTreeMenu)
            .collect(Collectors.toList());
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        return menuEntryRepository.existsByNameAndTenantScope(name, excludeId, resolveReadTenantId());
    }

    @Override
    public boolean existsByUrl(String url, Long excludeId) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        return menuEntryRepository.existsByPathAndTenantScope(url, excludeId, resolveReadTenantId());
    }

    private Pageable normalizeMenuPageable(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Order.asc("sort"), Sort.Order.asc("id"))
        );
    }

    private Specification<MenuEntry> buildMenuEntrySpecification(ResourceRequestDto query,
                                                                 Long tenantId,
                                                                 LinkedHashSet<Long> visibleCreatorIds,
                                                                 boolean rootOnlyWhenParentMissing) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantId == null) {
                predicates.add(criteriaBuilder.isNull(root.get("tenantId")));
            } else {
                predicates.add(criteriaBuilder.equal(root.get("tenantId"), tenantId));
            }

            if (query.getType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), query.getType()));
            } else {
                predicates.add(root.get("type").in(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()));
            }

            Long normalizedParentId = normalizeParentId(query.getParentId());
            if (query.getParentId() != null) {
                if (normalizedParentId == null) {
                    predicates.add(criteriaBuilder.isNull(root.get("parentId")));
                } else {
                    predicates.add(criteriaBuilder.equal(root.get("parentId"), normalizedParentId));
                }
            } else if (rootOnlyWhenParentMissing) {
                predicates.add(criteriaBuilder.isNull(root.get("parentId")));
            }

            if (StringUtils.hasText(query.getTitle())) {
                predicates.add(criteriaBuilder.like(root.get("title"), "%" + query.getTitle() + "%"));
            }
            if (StringUtils.hasText(query.getName())) {
                predicates.add(criteriaBuilder.like(root.get("name"), "%" + query.getName() + "%"));
            }
            if (query.getEnabled() != null) {
                predicates.add(criteriaBuilder.equal(root.get("enabled"), query.getEnabled()));
            }
            if (requiresDataScopeFilterForScope(tenantId)) {
                predicates.add(root.get("createdBy").in(visibleCreatorIds));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getActiveTenantId();
        if (tenantId != null && tenantId > 0) {
            return tenantId;
        }
        if (TenantContext.isPlatformScope()) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "平台作用域写操作需要显式 activeTenantId");
        }
        throw new BusinessException(ErrorCode.MISSING_PARAMETER, "缺少租户信息");
    }

    private Long resolveReadTenantId() {
        Long tenantId = TenantContext.getActiveTenantId();
        if (tenantId != null && tenantId > 0) {
            return tenantId;
        }
        if (TenantContext.isPlatformScope()) {
            return null;
        }
        throw new BusinessException(ErrorCode.MISSING_PARAMETER, "缺少租户信息");
    }

    private List<MenuEntry> filterPlatformOnlyMenus(List<MenuEntry> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        return menus.stream()
            .filter(this::canAccessPlatformMenu)
            .toList();
    }

    private List<MenuEntry> filterMenusForCurrentUser(List<MenuEntry> menus, Long tenantIdForAudit) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        Set<String> authorityCodes = resolveCurrentAuthorityCodes();
        Map<Long, RequirementAwareAuditDetail> details =
            carrierPermissionRequirementEvaluator.resolveMenuRequirementDetails(menus, authorityCodes);

        Set<Long> allowedMenuIds = details.entrySet().stream()
            .filter(e -> "ALLOW".equalsIgnoreCase(e.getValue().decision()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        details.values().forEach(detail -> {
            try {
                authorizationAuditService.logRequirementAware(
                    AuthorizationAuditEventType.REQUIREMENT_AWARE_ACCESS,
                    tenantIdForAudit,
                    "menu-runtime-tree",
                    detail
                );
            } catch (Exception ex) {
                logger.warn(
                    "Requirement-aware menu audit failed (tenantId={}, carrierId={}, carrierType={})",
                    tenantIdForAudit,
                    detail == null ? null : detail.carrierId(),
                    detail == null ? null : detail.carrierType(),
                    ex
                );
            }
        });

        return menus.stream()
            .filter(this::canAccessPlatformMenu)
            .filter(menu -> menu != null && allowedMenuIds.contains(menu.getId()))
            .toList();
    }

    private List<MenuEntry> findTreeMenusForCurrentUser(Long tenantId) {
        List<Integer> menuTypeCodes = List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode());
        if (TenantContext.isPlatformScope()) {
            return filterMenusForCurrentUser(
                menuEntryRepository.findByTenantIdIsNullAndTypeInOrderBySortAsc(menuTypeCodes),
                tenantId
            );
        }
        return filterMenusForCurrentUser(
            menuEntryRepository.findByTenantIdAndTypeInOrderBySortAsc(tenantId, menuTypeCodes),
            tenantId
        );
    }

    private List<MenuEntry> findChildMenusForCurrentUser(Long parentId, Long tenantId) {
        List<MenuEntry> menus;
        if (parentId == null) {
            menus = TenantContext.isPlatformScope()
                ? menuEntryRepository.findByTenantIdIsNullAndTypeInAndParentIdIsNullOrderBySortAsc(
                    List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode())
                )
                : menuEntryRepository.findByTenantIdAndTypeInAndParentIdIsNullOrderBySortAsc(
                    tenantId,
                    List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode())
                );
        } else {
            menus = TenantContext.isPlatformScope()
                ? menuEntryRepository.findByTenantIdIsNullAndTypeInAndParentIdOrderBySortAsc(
                    List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()),
                    parentId
                )
                : menuEntryRepository.findByTenantIdAndTypeInAndParentIdOrderBySortAsc(
                    tenantId,
                    List.of(ResourceType.DIRECTORY.getCode(), ResourceType.MENU.getCode()),
                    parentId
                );
        }
        return filterMenusForCurrentUser(menus, tenantId);
    }


    private boolean canAccessPlatformMenu(MenuEntry menu) {
        if (menu == null) {
            return false;
        }
        if (isLegacyPlatformRoleConstraintPath(menu.getPath())) {
            return false;
        }
        Resource marker = new Resource();
        marker.setName(menu.getName());
        marker.setPermission(menu.getPermission());
        marker.setUrl(menu.getPath());
        marker.setUri("");
        if (PlatformControlPlaneResourcePolicy.isTenantManagementResource(marker)) {
            return hasPlatformAdminAccess();
        }
        if (PlatformControlPlaneResourcePolicy.isIdempotentOpsResource(marker)) {
            return hasPlatformAdminAccess() && hasAuthority("idempotent:ops:view");
        }
        return true;
    }

    private boolean canAccessPlatformMenu(Resource resource) {
        if (resource == null) {
            return false;
        }
        if (isLegacyPlatformRoleConstraintPath(resource.getUrl())) {
            return false;
        }
        if (PlatformControlPlaneResourcePolicy.isTenantManagementResource(resource)) {
            return hasPlatformAdminAccess();
        }
        if (PlatformControlPlaneResourcePolicy.isIdempotentOpsResource(resource)) {
            return hasPlatformAdminAccess() && hasAuthority("idempotent:ops:view");
        }
        return true;
    }

    private boolean canAccessPlatformMenu(ResourceResponseDto resource) {
        if (resource == null) {
            return false;
        }
        if (isLegacyPlatformRoleConstraintPath(resource.getUrl())) {
            return false;
        }
        if (PlatformControlPlaneResourcePolicy.isTenantManagementResource(resource)) {
            return hasPlatformAdminAccess();
        }
        if (PlatformControlPlaneResourcePolicy.isIdempotentOpsResource(resource)) {
            return hasPlatformAdminAccess() && hasAuthority("idempotent:ops:view");
        }
        return true;
    }

    /**
     * 平台 RBAC3 已收口到 /platform/role-constraints；历史错误平台菜单
     * /system/role/constraint 指向 tenant-only 页面，平台态必须显式挡掉。
     */
    private boolean isLegacyPlatformRoleConstraintPath(String path) {
        return TenantContext.isPlatformScope() && LEGACY_PLATFORM_ROLE_CONSTRAINT_PATH.equals(path);
    }

    private boolean canAccessTreeMenu(Resource resource) {
        if (!canAccessPlatformMenu(resource)) {
            return false;
        }
        if (resource == null || resource.getType() == null) {
            return false;
        }
        if (resource.getType() == ResourceType.DIRECTORY) {
            return true;
        }
        return hasMenuAuthority(resource.getPermission());
    }

    private boolean canAccessTreeMenu(ResourceResponseDto resource) {
        if (!canAccessPlatformMenu(resource)) {
            return false;
        }
        if (resource == null || resource.getType() == null) {
            return false;
        }
        if (resource.getType() == ResourceType.DIRECTORY.getCode()) {
            return true;
        }
        return hasMenuAuthority(resource.getPermission());
    }

    private boolean canAccessTreeMenu(MenuEntry menu) {
        if (!canAccessPlatformMenu(menu)) {
            return false;
        }
        if (menu == null || menu.getType() == null) {
            return false;
        }
        return carrierPermissionRequirementEvaluator.isMenuAllowed(menu, resolveCurrentAuthorityCodes());
    }

    private boolean hasMenuAuthority(String permission) {
        if (!StringUtils.hasText(permission)) {
            return true;
        }
        return hasAuthority(permission.trim());
    }

    private static final Set<String> PLATFORM_CONTROL_PLANE_AUTHORITIES = Set.of(
        "system:tenant:list", "system:tenant:view",
        "system:tenant:create", "system:tenant:edit", "system:tenant:delete"
    );

    private boolean hasPlatformAdminAccess() {
        return TenantContext.isPlatformScope()
            && hasAnyOfAuthorities(PLATFORM_CONTROL_PLANE_AUTHORITIES);
    }


    private void appendMenuDataScopeFilter(StringBuilder sqlBuilder,
                                           StringBuilder countSqlBuilder,
                                           LinkedHashSet<Long> visibleCreatorIds) {
        if (!requiresDataScopeFilterForScope(resolveReadTenantId())) {
            return;
        }
        sqlBuilder.append(" AND r.created_by IN (:visibleCreatorIds)");
        countSqlBuilder.append(" AND r.created_by IN (:visibleCreatorIds)");
    }

    private LinkedHashSet<Long> resolveVisibleCreatorIdsForRead(Long tenantId) {
        ResolvedDataScope scope = DataScopeContext.get();
        if (scope == null) {
            return new LinkedHashSet<>();
        }
        if (tenantId == null) {
            // 平台菜单读侧不应借 tenant_user / user_unit 解析平台成员范围。
            return new LinkedHashSet<>();
        }

        Set<Long> activeTenantUserIds = new LinkedHashSet<>(tenantUserRepository.findUserIdsByTenantIdAndStatus(tenantId, ACTIVE));
        LinkedHashSet<Long> visibleCreatorIds = new LinkedHashSet<>();

        if (!scope.getVisibleUserIds().isEmpty()) {
            visibleCreatorIds.addAll(
                tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(
                    tenantId,
                    scope.getVisibleUserIds(),
                    ACTIVE
                )
            );
        }

        if (!scope.getVisibleUnitIds().isEmpty()) {
            visibleCreatorIds.addAll(
                userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(
                    tenantId,
                    scope.getVisibleUnitIds(),
                    ACTIVE
                )
            );
        }

        Long currentUserId = extractCurrentUserId();
        if (scope.isSelfOnly() && currentUserId != null && activeTenantUserIds.contains(currentUserId)) {
            visibleCreatorIds.add(currentUserId);
        }

        visibleCreatorIds.retainAll(activeTenantUserIds);
        return visibleCreatorIds;
    }

    private boolean requiresDataScopeFilter() {
        ResolvedDataScope scope = DataScopeContext.get();
        return scope != null && !scope.isUnrestricted();
    }

    private boolean requiresDataScopeFilterForScope(Long tenantId) {
        return tenantId != null && requiresDataScopeFilter();
    }

    private boolean hasChildMenu(Long parentId, Long tenantId) {
        if (parentId == null) {
            return false;
        }
        return tenantId == null
            ? menuEntryRepository.existsByParentIdAndTenantIdIsNull(parentId)
            : menuEntryRepository.existsByParentIdAndTenantId(parentId, tenantId);
    }

    private boolean hasChildUiAction(Long parentMenuId, Long tenantId) {
        if (parentMenuId == null) {
            return false;
        }
        return tenantId == null
            ? uiActionEntryRepository.existsByParentMenuIdAndTenantIdIsNull(parentMenuId)
            : uiActionEntryRepository.existsByParentMenuIdAndTenantId(parentMenuId, tenantId);
    }

    private Long extractCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        String name = authentication.getName();
        if (!StringUtils.hasText(name) || "anonymousUser".equalsIgnoreCase(name)) {
            return null;
        }
        try {
            return Long.valueOf(name.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Set<String> resolveCurrentAuthorityCodes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
            .map(grantedAuthority -> grantedAuthority.getAuthority())
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean hasAuthority(String... authorities) {
        return hasAnyOfAuthorities(Set.of(authorities));
    }

    private boolean hasAnyOfAuthorities(Set<String> expected) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || expected == null || expected.isEmpty()) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .map(grantedAuthority -> grantedAuthority.getAuthority())
            .anyMatch(expected::contains);
    }
} 
