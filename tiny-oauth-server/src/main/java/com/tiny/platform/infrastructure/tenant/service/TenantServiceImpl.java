package com.tiny.platform.infrastructure.tenant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.RoleAssignmentSyncService;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.auth.user.service.UserAuthenticationBridgeWriter;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationCredential;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationMethodProfiles;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationScopePolicy;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.core.exception.exception.NotFoundException;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantRequestDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantServiceImpl implements TenantService {
    private static final Pattern TENANT_CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,31}$");
    private static final String ACTIVE = "ACTIVE";
    private static final String FROZEN = "FROZEN";
    private static final String DECOMMISSIONED = "DECOMMISSIONED";
    private static final String MODULE_TENANT = "tenant";
    private static final String SCOPE_TYPE_PLATFORM = "PLATFORM";
    private static final String SCOPE_TYPE_TENANT = "TENANT";
    private static final String DEFAULT_INITIAL_ADMIN_NICKNAME = "租户管理员";
    private static final String ROLE_TENANT_ADMIN_CODE = "ROLE_TENANT_ADMIN";
    private static final String AUTH_PROVIDER_LOCAL = "LOCAL";
    private static final String AUTH_TYPE_PASSWORD = "PASSWORD";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(1[3-9]\\d{9})?$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TenantRepository tenantRepository;
    private final TenantBootstrapService tenantBootstrapService;
    private final AuthorizationAuditService auditService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final RoleAssignmentSyncService roleAssignmentSyncService;
    private final TenantQuotaService tenantQuotaService;
    private final UserAuthenticationBridgeWriter authenticationBridgeWriter;

    @Autowired
    public TenantServiceImpl(TenantRepository tenantRepository,
                             TenantBootstrapService tenantBootstrapService,
                             AuthorizationAuditService auditService,
                             UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             RoleRepository roleRepository,
                             RoleAssignmentSyncService roleAssignmentSyncService,
                             TenantQuotaService tenantQuotaService,
                             UserAuthenticationBridgeWriter authenticationBridgeWriter) {
        this.tenantRepository = tenantRepository;
        this.tenantBootstrapService = tenantBootstrapService;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.roleAssignmentSyncService = roleAssignmentSyncService;
        this.tenantQuotaService = tenantQuotaService;
        this.authenticationBridgeWriter = authenticationBridgeWriter;
    }

    @Override
    public Page<TenantResponseDto> list(TenantRequestDto query, Pageable pageable) {
        Specification<Tenant> spec = (root, cq, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();

            if (query.getCode() != null && !query.getCode().trim().isEmpty()) {
                predicates.add(cb.like(root.get("code"), "%" + query.getCode().trim() + "%"));
            }

            if (query.getName() != null && !query.getName().trim().isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + query.getName().trim() + "%"));
            }

            if (query.getEnabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), query.getEnabled()));
            }

            if (query.getDomain() != null && !query.getDomain().trim().isEmpty()) {
                predicates.add(cb.like(root.get("domain"), "%" + query.getDomain().trim() + "%"));
            }

            if (query.getLifecycleStatus() != null && !query.getLifecycleStatus().trim().isEmpty()) {
                predicates.add(cb.equal(root.get("lifecycleStatus"), normalizeLifecycleStatus(query.getLifecycleStatus())));
            }

            if (query.getIncludeDeleted() == null || !query.getIncludeDeleted()) {
                predicates.add(cb.isNull(root.get("deletedAt")));
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return tenantRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Override
    public Optional<Tenant> findById(Long id) {
        return tenantRepository.findById(id).filter(t -> t.getDeletedAt() == null);
    }

    @Override
    @Transactional
    public TenantResponseDto create(TenantCreateUpdateDto dto) {
        InitialAdminDraft initialAdmin = validateInitialAdmin(dto);
        String normalizedCode = normalizeTenantCode(dto.getCode());
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "租户名称不能为空");
        }
        tenantQuotaService.validateQuotaSettingsForCreate(dto.getMaxUsers(), dto.getMaxStorageGb());
        if (dto.getLifecycleStatus() != null && !dto.getLifecycleStatus().isBlank()) {
            String requestedStatus = normalizeLifecycleStatus(dto.getLifecycleStatus());
            if (!ACTIVE.equals(requestedStatus)) {
                throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "新建租户的初始生命周期状态只能为 ACTIVE");
            }
        }
        if (tenantRepository.existsByCode(normalizedCode)) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "租户编码已存在");
        }
        String domain = normalizeNullable(dto.getDomain());
        if (domain != null && tenantRepository.existsByDomain(domain)) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "租户域名已存在");
        }
        Tenant tenant = new Tenant();
        tenant.setCode(normalizedCode);
        tenant.setName(dto.getName().trim());
        tenant.setDomain(domain);
        tenant.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        tenant.setPlanCode(normalizeNullable(dto.getPlanCode()));
        tenant.setExpiresAt(parseDateTime(dto.getExpiresAt()));
        tenant.setMaxUsers(dto.getMaxUsers());
        tenant.setMaxStorageGb(dto.getMaxStorageGb());
        tenant.setContactName(normalizeNullable(dto.getContactName()));
        tenant.setContactEmail(normalizeNullable(dto.getContactEmail()));
        tenant.setContactPhone(normalizeNullable(dto.getContactPhone()));
        tenant.setRemark(dto.getRemark());
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenant.setLifecycleStatus(ACTIVE);
        Tenant saved = tenantRepository.save(tenant);
        tenantBootstrapService.bootstrapFromPlatformTemplate(saved);
        User initialAdminUser = createInitialAdmin(saved, initialAdmin);
        logTenantSuccess(AuthorizationAuditEventType.TENANT_CREATE, saved,
            "system:tenant:create", buildTenantCreateDetail(saved, initialAdminUser));
        return toDto(saved);
    }

    @Override
    @Transactional
    public TenantResponseDto update(Long id, TenantCreateUpdateDto dto) {
        Tenant tenant = requireTenant(id);
        if (DECOMMISSIONED.equalsIgnoreCase(tenant.getLifecycleStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "租户已下线，不允许再修改基础信息");
        }
        if (dto.getLifecycleStatus() != null && !dto.getLifecycleStatus().isBlank()) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "请使用冻结、解冻或下线专用接口修改租户生命周期状态");
        }
        tenantQuotaService.validateQuotaSettingsForUpdate(dto.getMaxUsers(), dto.getMaxStorageGb());
        Map<String, Object> before = snapshotTenantState(tenant);

        if (dto.getCode() != null && !dto.getCode().trim().isEmpty()) {
            String normalizedCode = normalizeTenantCode(dto.getCode());
            if (!normalizedCode.equals(tenant.getCode()) && tenantRepository.existsByCode(normalizedCode)) {
                throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "租户编码已存在");
            }
            tenant.setCode(normalizedCode);
        }

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            tenant.setName(dto.getName().trim());
        }

        if (dto.getDomain() != null) {
            String domain = normalizeNullable(dto.getDomain());
            if (domain != null && !domain.equals(tenant.getDomain()) && tenantRepository.existsByDomain(domain)) {
                throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "租户域名已存在");
            }
            tenant.setDomain(domain);
        }

        if (dto.getEnabled() != null) {
            tenant.setEnabled(dto.getEnabled());
        }

        if (dto.getPlanCode() != null) {
            tenant.setPlanCode(normalizeNullable(dto.getPlanCode()));
        }

        if (dto.getExpiresAt() != null) {
            tenant.setExpiresAt(parseDateTime(dto.getExpiresAt()));
        }

        if (dto.getMaxUsers() != null) {
            tenantQuotaService.assertMaxUsersNotBelowCurrentUsage(tenant.getId(), dto.getMaxUsers());
            tenant.setMaxUsers(dto.getMaxUsers());
        }

        if (dto.getMaxStorageGb() != null) {
            tenantQuotaService.assertMaxStorageNotBelowCurrentUsage(tenant.getId(), dto.getMaxStorageGb());
            tenant.setMaxStorageGb(dto.getMaxStorageGb());
        }

        if (dto.getContactName() != null) {
            tenant.setContactName(normalizeNullable(dto.getContactName()));
        }

        if (dto.getContactEmail() != null) {
            tenant.setContactEmail(normalizeNullable(dto.getContactEmail()));
        }

        if (dto.getContactPhone() != null) {
            tenant.setContactPhone(normalizeNullable(dto.getContactPhone()));
        }

        if (dto.getRemark() != null) {
            tenant.setRemark(dto.getRemark());
        }

        tenant.setUpdatedAt(LocalDateTime.now());
        Tenant saved = tenantRepository.save(tenant);
        logTenantSuccess(AuthorizationAuditEventType.TENANT_UPDATE, saved,
            "system:tenant:edit", buildTenantUpdateDetail(saved, before));
        return toDto(saved);
    }

    @Override
    @Transactional
    public TenantResponseDto freeze(Long id) {
        return transitionLifecycle(id, FROZEN,
            AuthorizationAuditEventType.TENANT_FREEZE, "system:tenant:freeze");
    }

    @Override
    @Transactional
    public TenantResponseDto unfreeze(Long id) {
        return transitionLifecycle(id, ACTIVE,
            AuthorizationAuditEventType.TENANT_UNFREEZE, "system:tenant:unfreeze");
    }

    @Override
    @Transactional
    public TenantResponseDto decommission(Long id) {
        return transitionLifecycle(id, DECOMMISSIONED,
            AuthorizationAuditEventType.TENANT_DECOMMISSION, "system:tenant:decommission");
    }

    @Override
    @Transactional
    public boolean initializePlatformTemplates() {
        boolean initialized = tenantBootstrapService.ensurePlatformTemplatesInitialized();
        auditService.log(
            AuthorizationAuditEventType.PLATFORM_TEMPLATE_INITIALIZE,
            null,
            null,
            null,
            SCOPE_TYPE_PLATFORM,
            null,
            MODULE_TENANT,
            "system:tenant:template:initialize",
            buildPlatformTemplateInitializationDetail(initialized),
            "SUCCESS",
            null
        );
        return initialized;
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformTemplateDiffResult diffPlatformTemplate(Long tenantId) {
        Tenant tenant = requireTenant(tenantId);
        PlatformTemplateDiffResult result = tenantBootstrapService.diffPlatformTemplateForTenant(tenant.getId());
        auditService.log(
            AuthorizationAuditEventType.PLATFORM_TEMPLATE_DIFF,
            tenant.getId(),
            null,
            null,
            SCOPE_TYPE_PLATFORM,
            tenant.getId(),
            MODULE_TENANT,
            "system:tenant:view",
            buildPlatformTemplateDiffDetail(result),
            "SUCCESS",
            null
        );
        return result;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        tenantRepository.findById(id).ifPresent(tenant -> {
            if (tenant.getDeletedAt() == null) {
                Map<String, Object> before = snapshotTenantState(tenant);
                String previousStatus = tenant.getLifecycleStatus();
                tenant.setEnabled(false);
                tenant.setLifecycleStatus(DECOMMISSIONED);
                tenant.setDeletedAt(LocalDateTime.now());
                tenant.setUpdatedAt(LocalDateTime.now());
                Tenant saved = tenantRepository.save(tenant);
                logTenantSuccess(AuthorizationAuditEventType.TENANT_DELETE, saved,
                    "system:tenant:delete", buildTenantDeleteDetail(saved, previousStatus, before));
            }
        });
    }

    private TenantResponseDto toDto(Tenant tenant) {
        TenantResponseDto dto = new TenantResponseDto();
        BeanUtils.copyProperties(tenant, dto);
        if (tenant.getExpiresAt() != null) {
            dto.setExpiresAt(tenant.getExpiresAt().toString());
        }
        return dto;
    }

    private String buildPlatformTemplateDiffDetail(PlatformTemplateDiffResult result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", "PLATFORM_TEMPLATE_DIFF");
        detail.put("tenantId", result.tenantId());
        detail.put("summary", Map.of(
            "totalPlatformEntries", result.summary().totalPlatformEntries(),
            "totalTenantEntries", result.summary().totalTenantEntries(),
            "missingInTenant", result.summary().missingInTenant(),
            "extraInTenant", result.summary().extraInTenant(),
            "changed", result.summary().changed()
        ));
        // Keep audit detail bounded: include at most 50 diff entries.
        detail.put("diffSample", result.diffs().stream().limit(50).toList());
        try {
            return OBJECT_MAPPER.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "序列化平台模板差异审计失败", e);
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "时间格式不正确: " + value, ex);
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private InitialAdminDraft validateInitialAdmin(TenantCreateUpdateDto dto) {
        String username = normalizeNullable(dto.getInitialAdminUsername());
        if (username == null) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "初始管理员用户名不能为空");
        }
        if (username.length() < 3 || username.length() > 20 || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "初始管理员用户名格式不正确，需为 3-20 位字母、数字或下划线");
        }
        if (userRepository.findUserIdByUsername(username).isPresent()) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "初始管理员用户名已存在");
        }

        String password = dto.getInitialAdminPassword();
        if (password == null || password.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "初始管理员密码不能为空");
        }
        if (password.length() < 6 || password.length() > 20) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "初始管理员密码长度需为 6-20 位");
        }
        if (!password.equals(dto.getInitialAdminConfirmPassword())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "初始管理员两次输入的密码不一致");
        }

        String email = normalizeNullable(dto.getInitialAdminEmail());
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "初始管理员邮箱格式不正确");
        }

        String phone = normalizeNullable(dto.getInitialAdminPhone());
        if (phone != null && !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "初始管理员手机号格式不正确");
        }

        String nickname = normalizeNullable(dto.getInitialAdminNickname());
        if (nickname == null) {
            nickname = DEFAULT_INITIAL_ADMIN_NICKNAME;
        }
        return new InitialAdminDraft(username, nickname, email, phone, password);
    }

    private User createInitialAdmin(Tenant tenant, InitialAdminDraft initialAdmin) {
        tenantQuotaService.assertCanCreateUsers(tenant.getId(), 1, "创建初始管理员");
        Role adminRole = roleRepository.findByCodeAndTenantId(ROLE_TENANT_ADMIN_CODE, tenant.getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "新租户缺少初始化管理员角色 ROLE_TENANT_ADMIN"));

        User user = new User();
        user.setUsername(initialAdmin.username());
        user.setNickname(initialAdmin.nickname());
        user.setEmail(initialAdmin.email());
        user.setPhone(initialAdmin.phone());
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        User savedUser = userRepository.save(user);

        roleAssignmentSyncService.ensureTenantMembership(savedUser.getId(), tenant.getId(), true);
        roleAssignmentSyncService.replaceUserTenantRoleAssignments(savedUser.getId(), tenant.getId(), java.util.List.of(adminRole.getId()));
        createPasswordAuthenticationMethod(savedUser.getId(), tenant.getId(), initialAdmin.password());
        return savedUser;
    }

    private void createPasswordAuthenticationMethod(Long userId, Long tenantId, String plainPassword) {
        String encodedPassword = passwordEncoder.encode(plainPassword);
        Map<String, Object> config = new HashMap<>();
        config.put("password", encodedPassword);

        UserAuthenticationMethod method = UserAuthenticationMethodProfiles.create(
            new UserAuthenticationCredential(
                userId,
                AUTH_PROVIDER_LOCAL,
                AUTH_TYPE_PASSWORD,
                config,
                null,
                null,
                null
            ),
            new UserAuthenticationScopePolicy(
                userId,
                tenantId,
                AUTH_PROVIDER_LOCAL,
                AUTH_TYPE_PASSWORD,
                true,
                true,
                0
            )
        );
        method.setCreatedAt(LocalDateTime.now());
        method.setUpdatedAt(LocalDateTime.now());
        authenticationBridgeWriter.upsert(
                userId,
                AUTH_PROVIDER_LOCAL,
                AUTH_TYPE_PASSWORD,
                method.getAuthenticationConfiguration(),
                method.getLastVerifiedAt(),
                method.getLastVerifiedIp(),
                method.getExpiresAt(),
                SCOPE_TYPE_TENANT,
                tenantId,
                method.getIsPrimaryMethod(),
                method.getIsMethodEnabled(),
                method.getAuthenticationPriority()
        );
    }

    private Tenant requireTenant(Long id) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("租户", id));
        if (tenant.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "租户已删除");
        }
        return tenant;
    }

    private TenantResponseDto transitionLifecycle(Long id, String targetStatus,
                                                  String eventType, String resourcePermission) {
        Tenant tenant = requireTenant(id);
        String currentStatus = normalizeLifecycleStatus(tenant.getLifecycleStatus());
        String normalizedTargetStatus = normalizeLifecycleStatus(targetStatus);
        if (normalizedTargetStatus.equals(currentStatus)) {
            return toDto(tenant);
        }

        assertLifecycleTransition(currentStatus, normalizedTargetStatus);
        Map<String, Object> before = snapshotTenantState(tenant);

        if (DECOMMISSIONED.equals(normalizedTargetStatus)) {
            tenant.setEnabled(false);
        }

        tenant.setLifecycleStatus(normalizedTargetStatus);
        tenant.setUpdatedAt(LocalDateTime.now());
        Tenant saved = tenantRepository.save(tenant);
        logTenantSuccess(eventType, saved, resourcePermission,
            buildLifecycleTransitionDetail(saved, currentStatus, normalizedTargetStatus, before));
        return toDto(saved);
    }

    private void assertLifecycleTransition(String currentStatus, String targetStatus) {
        boolean allowed = switch (currentStatus) {
            case ACTIVE -> FROZEN.equals(targetStatus) || DECOMMISSIONED.equals(targetStatus);
            case FROZEN -> ACTIVE.equals(targetStatus) || DECOMMISSIONED.equals(targetStatus);
            case DECOMMISSIONED -> false;
            default -> false;
        };
        if (!allowed) {
            throw new BusinessException(
                ErrorCode.RESOURCE_STATE_INVALID,
                "不允许的租户生命周期流转: " + currentStatus + " -> " + targetStatus
            );
        }
    }

    private String normalizeLifecycleStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "租户生命周期状态不能为空");
        }
        String normalized = rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!ACTIVE.equals(normalized) && !FROZEN.equals(normalized) && !DECOMMISSIONED.equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "无效的租户生命周期状态");
        }
        return normalized;
    }

    private String normalizeTenantCode(String rawCode) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "租户编码不能为空");
        }
        String normalizedCode = rawCode.trim().toLowerCase(Locale.ROOT);
        if (!TENANT_CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "租户编码格式不正确，仅支持小写字母、数字和中划线，长度 2-32");
        }
        return normalizedCode;
    }

    private void logTenantSuccess(String eventType, Tenant tenant,
                                  String resourcePermission, String eventDetail) {
        auditService.log(eventType, tenant.getId(), null, null,
            SCOPE_TYPE_TENANT, tenant.getId(), MODULE_TENANT,
            resourcePermission, eventDetail, "SUCCESS", null);
    }

    private String buildTenantUpdateDetail(Tenant tenant, Map<String, Object> before) {
        Map<String, Object> after = snapshotTenantState(tenant);
        return writeAuditDetail(buildTenantAuditDetail(
            AuthorizationAuditEventType.TENANT_UPDATE,
            tenant,
            null,
            before,
            after,
            buildDiff(before, after),
            Map.of()
        ));
    }

    private String buildTenantCreateDetail(Tenant tenant, User initialAdmin) {
        Map<String, Object> after = snapshotTenantState(tenant);
        return writeAuditDetail(buildTenantAuditDetail(
            AuthorizationAuditEventType.TENANT_CREATE,
            tenant,
            null,
            null,
            after,
            buildDiff(null, after),
            Map.of(
                "initialAdmin",
                Map.of(
                    "userId", initialAdmin.getId(),
                    "username", initialAdmin.getUsername()
                )
            )
        ));
    }

    private String buildLifecycleTransitionDetail(Tenant tenant,
                                                  String previousStatus,
                                                  String currentStatus,
                                                  Map<String, Object> before) {
        Map<String, Object> after = snapshotTenantState(tenant);
        Map<String, Object> diff = buildDiff(before, after);
        diff.put("lifecycleStatus", Map.of("before", previousStatus, "after", currentStatus));
        return writeAuditDetail(buildTenantAuditDetail(
            tenantLifecycleAction(currentStatus),
            tenant,
            null,
            before,
            after,
            diff,
            Map.of()
        ));
    }

    private String buildTenantDeleteDetail(Tenant tenant, String previousStatus, Map<String, Object> before) {
        Map<String, Object> after = snapshotTenantState(tenant);
        Map<String, Object> diff = buildDiff(before, after);
        diff.put("deleted", Map.of("before", Boolean.FALSE, "after", Boolean.TRUE));
        diff.put("lifecycleStatus", Map.of("before", previousStatus, "after", tenant.getLifecycleStatus()));
        return writeAuditDetail(buildTenantAuditDetail(
            AuthorizationAuditEventType.TENANT_DELETE,
            tenant,
            null,
            before,
            after,
            diff,
            Map.of("deleted", Boolean.TRUE)
        ));
    }

    private String buildPlatformTemplateInitializationDetail(boolean initialized) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", AuthorizationAuditEventType.PLATFORM_TEMPLATE_INITIALIZE);
        detail.put("operator", currentOperator());
        detail.put("scopeType", TenantContext.getActiveScopeType());
        detail.put("result", initialized ? "INITIALIZED" : "NOOP");
        detail.put("message", initialized
            ? "平台模板缺失，已按配置的平台租户回填完成"
            : "平台模板已存在，无需重复初始化");
        return writeAuditDetail(detail);
    }

    private String tenantLifecycleAction(String currentStatus) {
        return switch (currentStatus) {
            case FROZEN -> AuthorizationAuditEventType.TENANT_FREEZE;
            case ACTIVE -> AuthorizationAuditEventType.TENANT_UNFREEZE;
            case DECOMMISSIONED -> AuthorizationAuditEventType.TENANT_DECOMMISSION;
            default -> AuthorizationAuditEventType.TENANT_UPDATE;
        };
    }

    private Map<String, Object> buildTenantAuditDetail(String action,
                                                       Tenant tenant,
                                                       String reason,
                                                       Map<String, Object> before,
                                                       Map<String, Object> after,
                                                       Map<String, Object> diff,
                                                       Map<String, Object> extras) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", action);
        detail.put("tenant", Map.of(
            "id", tenant.getId(),
            "code", tenant.getCode(),
            "name", tenant.getName()
        ));
        detail.put("operator", currentOperator());
        detail.put("reason", reason);
        detail.put("before", before);
        detail.put("after", after);
        detail.put("diff", diff);
        detail.putAll(extras);
        return detail;
    }

    private Map<String, Object> snapshotTenantState(Tenant tenant) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("code", tenant.getCode());
        snapshot.put("name", tenant.getName());
        snapshot.put("domain", tenant.getDomain());
        snapshot.put("enabled", tenant.isEnabled());
        snapshot.put("lifecycleStatus", tenant.getLifecycleStatus());
        snapshot.put("planCode", tenant.getPlanCode());
        snapshot.put("expiresAt", tenant.getExpiresAt() != null ? tenant.getExpiresAt().toString() : null);
        snapshot.put("maxUsers", tenant.getMaxUsers());
        snapshot.put("maxStorageGb", tenant.getMaxStorageGb());
        snapshot.put("contactName", tenant.getContactName());
        snapshot.put("contactEmail", tenant.getContactEmail());
        snapshot.put("contactPhone", tenant.getContactPhone());
        snapshot.put("remark", tenant.getRemark());
        snapshot.put("deletedAt", tenant.getDeletedAt() != null ? tenant.getDeletedAt().toString() : null);
        return snapshot;
    }

    private Map<String, Object> buildDiff(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> diff = new LinkedHashMap<>();
        if (after == null) {
            return diff;
        }
        for (Map.Entry<String, Object> entry : after.entrySet()) {
            String key = entry.getKey();
            Object afterValue = entry.getValue();
            Object beforeValue = before == null ? null : before.get(key);
            if (!java.util.Objects.equals(beforeValue, afterValue)) {
                diff.put(key, beforeAfter(beforeValue, afterValue));
            }
        }
        return diff;
    }

    private Map<String, Object> beforeAfter(Object before, Object after) {
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("before", before);
        pair.put("after", after);
        return pair;
    }

    private Map<String, Object> currentOperator() {
        Map<String, Object> operator = new LinkedHashMap<>();
        operator.put("scopeType", TenantContext.getActiveScopeType());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser securityUser) {
            operator.put("userId", securityUser.getUserId());
            operator.put("username", securityUser.getUsername());
        } else {
            operator.put("userId", null);
            operator.put("username", null);
        }
        return operator;
    }

    private String writeAuditDetail(Map<String, Object> detail) {
        try {
            return OBJECT_MAPPER.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "租户治理审计序列化失败", ex);
        }
    }

    private record InitialAdminDraft(String username, String nickname, String email, String phone, String password) {
    }
}
