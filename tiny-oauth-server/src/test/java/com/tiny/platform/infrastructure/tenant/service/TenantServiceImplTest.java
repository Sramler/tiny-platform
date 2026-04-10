package com.tiny.platform.infrastructure.tenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.RoleAssignmentSyncService;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.auth.user.service.UserAuthenticationBridgeWriter;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class TenantServiceImplTest {

    private TenantRepository tenantRepository;
    private TenantBootstrapService tenantBootstrapService;
    private AuthorizationAuditService auditService;
    private UserRepository userRepository;
    private UserAuthenticationBridgeWriter authenticationBridgeWriter;
    private PasswordEncoder passwordEncoder;
    private RoleRepository roleRepository;
    private RoleAssignmentSyncService roleAssignmentSyncService;
    private TenantQuotaService tenantQuotaService;
    private TenantServiceImpl service;

    @BeforeEach
    void setUp() {
        tenantRepository = org.mockito.Mockito.mock(TenantRepository.class);
        tenantBootstrapService = org.mockito.Mockito.mock(TenantBootstrapService.class);
        auditService = org.mockito.Mockito.mock(AuthorizationAuditService.class);
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        authenticationBridgeWriter = org.mockito.Mockito.mock(UserAuthenticationBridgeWriter.class);
        passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        roleRepository = org.mockito.Mockito.mock(RoleRepository.class);
        roleAssignmentSyncService = org.mockito.Mockito.mock(RoleAssignmentSyncService.class);
        tenantQuotaService = org.mockito.Mockito.mock(TenantQuotaService.class);
        service = new TenantServiceImpl(
            tenantRepository,
            tenantBootstrapService,
            auditService,
            userRepository,
            passwordEncoder,
            roleRepository,
            roleAssignmentSyncService,
            tenantQuotaService,
            authenticationBridgeWriter
        );
    }

    @Test
    void create_shouldNormalizeCodeBootstrapAndProvisionInitialAdmin() {
        TenantCreateUpdateDto dto = createDto();

        when(tenantRepository.existsByCode("acme-01")).thenReturn(false);
        when(tenantRepository.existsByDomain("acme.example.com")).thenReturn(false);
        when(userRepository.findUserIdByUsername("acme_admin")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(42L);
            return tenant;
        });
        Role adminRole = new Role();
        adminRole.setId(7L);
        adminRole.setCode("ROLE_TENANT_ADMIN");
        when(roleRepository.findByCodeAndTenantId("ROLE_TENANT_ADMIN", 42L)).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(101L);
            return user;
        });
        when(passwordEncoder.encode("Secret123")).thenReturn("{bcrypt}hashed");

        TenantResponseDto response = service.create(dto);

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> passwordConfigCaptor = ArgumentCaptor.forClass(Map.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        verify(tenantBootstrapService).bootstrapFromPlatformTemplate(tenantCaptor.getValue());
        verify(userRepository).save(userCaptor.capture());
        verify(authenticationBridgeWriter).upsert(
            eq(101L),
            eq("LOCAL"),
            eq("PASSWORD"),
            passwordConfigCaptor.capture(),
            any(),
            any(),
            any(),
            eq("TENANT"),
            eq(42L),
            any(),
            any(),
            any());
        verify(roleAssignmentSyncService).ensureTenantMembership(101L, 42L, true);
        verify(roleAssignmentSyncService).replaceUserTenantRoleAssignments(101L, 42L, java.util.List.of(7L));
        verify(auditService).log(
            eq(AuthorizationAuditEventType.TENANT_CREATE),
            eq(42L),
            isNull(),
            isNull(),
            eq("TENANT"),
            eq(42L),
            eq("tenant"),
            eq("system:tenant:create"),
            argThat(detail -> detail.contains("\"initialAdmin\"")
                && detail.contains("\"userId\":101")
                && detail.contains("\"username\":\"acme_admin\"")),
            eq("SUCCESS"),
            isNull());
        assertThat(tenantCaptor.getValue().getCode()).isEqualTo("acme-01");
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("acme_admin");
        assertThat(userCaptor.getValue().getNickname()).isEqualTo("Acme Admin");
        assertThat(passwordConfigCaptor.getValue()).containsEntry("password", "{bcrypt}hashed");
        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getCode()).isEqualTo("acme-01");
    }

    @Test
    void create_whenBootstrapFails_shouldPropagateAndNotMaskError() {
        TenantCreateUpdateDto dto = createDto();
        dto.setCode("tenant-x");
        dto.setName("Tenant X");

        when(userRepository.findUserIdByUsername("acme_admin")).thenReturn(Optional.empty());
        when(tenantRepository.existsByCode("tenant-x")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(99L);
            return tenant;
        });
        org.mockito.Mockito.doThrow(new IllegalStateException("bootstrap failed"))
            .when(tenantBootstrapService)
            .bootstrapFromPlatformTemplate(any(Tenant.class));

        assertThatThrownBy(() -> service.create(dto))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("bootstrap failed");

        verify(tenantRepository).save(any(Tenant.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void create_whenTenantCodeAlreadyExists_shouldNotBootstrap() {
        TenantCreateUpdateDto dto = createDto();
        dto.setCode("tenant-x");
        dto.setName("Tenant X");
        when(userRepository.findUserIdByUsername("acme_admin")).thenReturn(Optional.empty());
        when(tenantRepository.existsByCode("tenant-x")).thenReturn(true);

        assertThatThrownBy(() -> service.create(dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("租户编码已存在");

        verify(tenantBootstrapService, never()).bootstrapFromPlatformTemplate(any(Tenant.class));
    }

    @Test
    void create_whenInitialAdminIsMissing_shouldRejectBeforePersistingTenant() {
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        dto.setCode("tenant-x");
        dto.setName("Tenant X");

        assertThatThrownBy(() -> service.create(dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("初始管理员用户名不能为空");

        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    void update_whenLifecycleStatusProvided_shouldRejectDedicatedActionRequirement() {
        Tenant existing = tenant(7L, "ACTIVE", true);
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        dto.setLifecycleStatus("FROZEN");

        when(tenantRepository.findById(7L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(7L, dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("专用接口");
    }

    @Test
    void update_shouldLogAuthorizationAudit() {
        Tenant existing = tenant(7L, "ACTIVE", true);
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        dto.setName("Tenant 7 Updated");
        dto.setEnabled(false);

        when(tenantRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantResponseDto response = service.update(7L, dto);

        assertThat(response.getName()).isEqualTo("Tenant 7 Updated");
        assertThat(response.isEnabled()).isFalse();
        verify(auditService).log(
            eq(AuthorizationAuditEventType.TENANT_UPDATE),
            eq(7L),
            isNull(),
            isNull(),
            eq("TENANT"),
            eq(7L),
            eq("tenant"),
            eq("system:tenant:edit"),
            contains("\"name\":\"Tenant 7 Updated\""),
            eq("SUCCESS"),
            isNull());
    }

    @Test
    void freeze_shouldTransitionActiveTenantToFrozen() {
        Tenant existing = tenant(8L, "ACTIVE", true);
        when(tenantRepository.findById(8L)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantResponseDto response = service.freeze(8L);

        assertThat(response.getLifecycleStatus()).isEqualTo("FROZEN");
        assertThat(existing.getLifecycleStatus()).isEqualTo("FROZEN");
        assertThat(existing.isEnabled()).isTrue();
        verify(tenantRepository).save(existing);
        verify(auditService).log(
            eq(AuthorizationAuditEventType.TENANT_FREEZE),
            eq(8L),
            isNull(),
            isNull(),
            eq("TENANT"),
            eq(8L),
            eq("tenant"),
            eq("system:tenant:freeze"),
            argThat(detail -> detail.contains("\"diff\":{\"lifecycleStatus\"")
                && detail.contains("\"before\":\"ACTIVE\"")
                && detail.contains("\"after\":\"FROZEN\"")),
            eq("SUCCESS"),
            isNull());
    }

    @Test
    void unfreeze_shouldTransitionFrozenTenantToActive() {
        Tenant existing = tenant(9L, "FROZEN", true);
        when(tenantRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantResponseDto response = service.unfreeze(9L);

        assertThat(response.getLifecycleStatus()).isEqualTo("ACTIVE");
        assertThat(existing.getLifecycleStatus()).isEqualTo("ACTIVE");
        verify(tenantRepository).save(existing);
        verify(auditService).log(
            eq(AuthorizationAuditEventType.TENANT_UNFREEZE),
            eq(9L),
            isNull(),
            isNull(),
            eq("TENANT"),
            eq(9L),
            eq("tenant"),
            eq("system:tenant:unfreeze"),
            argThat(detail -> detail.contains("\"diff\":{\"lifecycleStatus\"")
                && detail.contains("\"before\":\"FROZEN\"")
                && detail.contains("\"after\":\"ACTIVE\"")),
            eq("SUCCESS"),
            isNull());
    }

    @Test
    void decommission_shouldDisableTenant() {
        Tenant existing = tenant(10L, "ACTIVE", true);
        when(tenantRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TenantResponseDto response = service.decommission(10L);

        assertThat(response.getLifecycleStatus()).isEqualTo("DECOMMISSIONED");
        assertThat(existing.getLifecycleStatus()).isEqualTo("DECOMMISSIONED");
        assertThat(existing.isEnabled()).isFalse();
        verify(tenantRepository).save(existing);
        verify(auditService).log(
            eq(AuthorizationAuditEventType.TENANT_DECOMMISSION),
            eq(10L),
            isNull(),
            isNull(),
            eq("TENANT"),
            eq(10L),
            eq("tenant"),
            eq("system:tenant:decommission"),
            contains("\"lifecycleStatus\":\"DECOMMISSIONED\""),
            eq("SUCCESS"),
            isNull());
    }

    @Test
    void initializePlatformTemplates_shouldReturnTrueAndLogAuditWhenBackfilled() {
        when(tenantBootstrapService.ensurePlatformTemplatesInitialized()).thenReturn(true);

        boolean initialized = service.initializePlatformTemplates();

        assertThat(initialized).isTrue();
        verify(auditService).log(
            eq(AuthorizationAuditEventType.PLATFORM_TEMPLATE_INITIALIZE),
            isNull(),
            isNull(),
            isNull(),
            eq("PLATFORM"),
            isNull(),
            eq("tenant"),
            eq("system:tenant:template:initialize"),
            contains("\"result\":\"INITIALIZED\""),
            eq("SUCCESS"),
            isNull());
    }

    @Test
    void initializePlatformTemplates_shouldReturnFalseWhenAlreadyInitialized() {
        when(tenantBootstrapService.ensurePlatformTemplatesInitialized()).thenReturn(false);

        boolean initialized = service.initializePlatformTemplates();

        assertThat(initialized).isFalse();
        verify(auditService).log(
            eq(AuthorizationAuditEventType.PLATFORM_TEMPLATE_INITIALIZE),
            isNull(),
            isNull(),
            isNull(),
            eq("PLATFORM"),
            isNull(),
            eq("tenant"),
            eq("system:tenant:template:initialize"),
            contains("\"result\":\"NOOP\""),
            eq("SUCCESS"),
            isNull());
    }

    @Test
    void freeze_whenTenantAlreadyDecommissioned_shouldReject() {
        Tenant existing = tenant(11L, "DECOMMISSIONED", false);
        when(tenantRepository.findById(11L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.freeze(11L))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.RESOURCE_STATE_INVALID);
    }

    @Test
    void create_shouldValidateTenantQuotaSettings() {
        TenantCreateUpdateDto dto = createDto();
        dto.setMaxUsers(0);
        doThrow(new BusinessException(ErrorCode.INVALID_PARAMETER, "新建租户的最大用户数不能小于 1"))
            .when(tenantQuotaService)
            .validateQuotaSettingsForCreate(0, null);

        assertThatThrownBy(() -> service.create(dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("最大用户数不能小于 1");

        verify(tenantRepository, never()).save(any(Tenant.class));
    }

    @Test
    void update_shouldRejectQuotaLowerThanCurrentUsage() {
        Tenant existing = tenant(13L, "ACTIVE", true);
        existing.setMaxUsers(10);
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        dto.setMaxUsers(1);
        when(tenantRepository.findById(13L)).thenReturn(Optional.of(existing));
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.RESOURCE_STATE_INVALID, "最大用户数不能低于当前已用数量 2"))
            .when(tenantQuotaService)
            .assertMaxUsersNotBelowCurrentUsage(13L, 1);

        assertThatThrownBy(() -> service.update(13L, dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("最大用户数不能低于当前已用数量");
    }

    @Test
    void create_shouldSerializeStructuredAuditDetail() {
        TenantCreateUpdateDto dto = createDto();
        when(tenantRepository.existsByCode("acme-01")).thenReturn(false);
        when(tenantRepository.existsByDomain("acme.example.com")).thenReturn(false);
        when(userRepository.findUserIdByUsername("acme_admin")).thenReturn(Optional.empty());
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(42L);
            return tenant;
        });
        Role adminRole = new Role();
        adminRole.setId(7L);
        adminRole.setCode("ROLE_TENANT_ADMIN");
        when(roleRepository.findByCodeAndTenantId("ROLE_TENANT_ADMIN", 42L)).thenReturn(Optional.of(adminRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(101L);
            return user;
        });
        when(passwordEncoder.encode("Secret123")).thenReturn("{bcrypt}hashed");

        service.create(dto);

        verify(auditService).log(
            eq(AuthorizationAuditEventType.TENANT_CREATE),
            eq(42L),
            isNull(),
            isNull(),
            eq("TENANT"),
            eq(42L),
            eq("tenant"),
            eq("system:tenant:create"),
            contains("\"action\":\"TENANT_CREATE\""),
            eq("SUCCESS"),
            isNull());
        verify(auditService).log(
            eq(AuthorizationAuditEventType.TENANT_CREATE),
            eq(42L),
            isNull(),
            isNull(),
            eq("TENANT"),
            eq(42L),
            eq("tenant"),
            eq("system:tenant:create"),
            argThat(detail -> detail.contains("\"initialAdmin\"")
                && detail.contains("\"userId\":101")
                && detail.contains("\"username\":\"acme_admin\"")),
            eq("SUCCESS"),
            isNull());
    }

    @Test
    void delete_shouldLogAuthorizationAudit() {
        Tenant existing = tenant(12L, "ACTIVE", true);
        when(tenantRepository.findById(12L)).thenReturn(Optional.of(existing));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.delete(12L);

        assertThat(existing.getDeletedAt()).isNotNull();
        assertThat(existing.getLifecycleStatus()).isEqualTo("DECOMMISSIONED");
        assertThat(existing.isEnabled()).isFalse();
        verify(auditService).log(
            eq(AuthorizationAuditEventType.TENANT_DELETE),
            eq(12L),
            isNull(),
            isNull(),
            eq("TENANT"),
            eq(12L),
            eq("tenant"),
            eq("system:tenant:delete"),
            contains("\"deleted\":true"),
            eq("SUCCESS"),
            isNull());
    }

    private Tenant tenant(Long id, String lifecycleStatus, boolean enabled) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setCode("tenant-" + id);
        tenant.setName("Tenant " + id);
        tenant.setLifecycleStatus(lifecycleStatus);
        tenant.setEnabled(enabled);
        return tenant;
    }

    private TenantCreateUpdateDto createDto() {
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        dto.setCode(" Acme-01 ");
        dto.setName("Acme");
        dto.setDomain("acme.example.com");
        dto.setInitialAdminUsername("acme_admin");
        dto.setInitialAdminNickname("Acme Admin");
        dto.setInitialAdminEmail("admin@acme.example.com");
        dto.setInitialAdminPhone("13800138000");
        dto.setInitialAdminPassword("Secret123");
        dto.setInitialAdminConfirmPassword("Secret123");
        return dto;
    }
}
