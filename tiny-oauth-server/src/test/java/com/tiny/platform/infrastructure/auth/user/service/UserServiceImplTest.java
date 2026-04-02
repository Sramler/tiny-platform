package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.config.LoginProtectionProperties;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.LoginFailurePolicy;
import com.tiny.platform.infrastructure.auth.datascope.framework.DataScopeContext;
import com.tiny.platform.infrastructure.auth.datascope.framework.ResolvedDataScope;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.org.service.UserUnitService;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.role.service.RoleAssignmentSyncService;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.dto.UserCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserRequestDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.tenant.guard.TenantLifecycleGuard;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private LoginFailurePolicy loginFailurePolicy() {
        LoginProtectionProperties properties = new LoginProtectionProperties();
        properties.setMaxFailedAttempts(5);
        properties.setLockMinutes(15);
        return new LoginFailurePolicy(properties);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        DataScopeContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_should_call_ensureTenantMembership_after_save() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                roleAssignmentSyncService,
                mock(EffectiveRoleResolutionService.class),
                mock(TenantUserRepository.class),
                mock(UserUnitRepository.class),
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(1L);
        User toSave = new User();
        toSave.setUsername("newuser");
        User saved = new User();
        saved.setId(99L);
        saved.setUsername("newuser");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        User result = service.create(toSave);

        assertThat(result.getId()).isEqualTo(99L);
        // 停双写：不再写入 user.tenant_id，仅 membership 写入
        verify(roleAssignmentSyncService).ensureTenantMembership(99L, 1L, true);
    }

    @Test
    void should_clear_failed_login_state_when_admin_unlocks_user() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                mock(UserUnitRepository.class),
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(1L);

        User existingUser = new User();
        existingUser.setId(9L);
        existingUser.setUsername("alice");
        existingUser.setNickname("Alice");
        existingUser.setEnabled(true);
        existingUser.setAccountNonExpired(true);
        existingUser.setAccountNonLocked(false);
        existingUser.setCredentialsNonExpired(true);
        existingUser.setFailedLoginCount(7);
        existingUser.setLastFailedLoginAt(LocalDateTime.now().minusMinutes(3));

        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(1L, 9L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(9L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserCreateUpdateDto dto = new UserCreateUpdateDto();
        dto.setId(9L);
        dto.setUsername("alice");
        dto.setNickname("Alice");
        dto.setEnabled(true);
        dto.setAccountNonExpired(true);
        dto.setAccountNonLocked(true);
        dto.setCredentialsNonExpired(true);

        User updated = service.updateFromDto(dto);

        assertThat(updated.isAccountNonLocked()).isTrue();
        assertThat(updated.getFailedLoginCount()).isZero();
        assertThat(updated.getLastFailedLoginAt()).isNull();
        verify(userRepository).save(existingUser);
    }

    @Test
    void should_keep_failed_login_state_when_user_remains_locked() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                mock(UserUnitRepository.class),
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(1L);

        LocalDateTime failedAt = LocalDateTime.now().minusMinutes(2);
        User existingUser = new User();
        existingUser.setId(10L);
        existingUser.setUsername("bob");
        existingUser.setNickname("Bob");
        existingUser.setEnabled(true);
        existingUser.setAccountNonExpired(true);
        existingUser.setAccountNonLocked(false);
        existingUser.setCredentialsNonExpired(true);
        existingUser.setFailedLoginCount(4);
        existingUser.setLastFailedLoginAt(failedAt);

        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(1L, 10L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserCreateUpdateDto dto = new UserCreateUpdateDto();
        dto.setId(10L);
        dto.setUsername("bob");
        dto.setNickname("Bob");
        dto.setEnabled(true);
        dto.setAccountNonExpired(true);
        dto.setAccountNonLocked(false);
        dto.setCredentialsNonExpired(true);

        User updated = service.updateFromDto(dto);

        assertThat(updated.isAccountNonLocked()).isFalse();
        assertThat(updated.getFailedLoginCount()).isEqualTo(4);
        assertThat(updated.getLastFailedLoginAt()).isEqualTo(failedAt);
    }

    @Test
    void should_include_failed_login_fields_in_user_list_dto() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                mock(UserUnitRepository.class),
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(1L);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastFailedAt = now.minusMinutes(2);
        User existingUser = new User();
        existingUser.setId(11L);
        existingUser.setUsername("carol");
        existingUser.setNickname("Carol");
        existingUser.setEnabled(true);
        existingUser.setAccountNonExpired(true);
        existingUser.setAccountNonLocked(false);
        existingUser.setCredentialsNonExpired(true);
        existingUser.setLastLoginAt(now.minusMinutes(20));
        existingUser.setFailedLoginCount(5);
        existingUser.setLastFailedLoginAt(lastFailedAt);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(1L, "ACTIVE")).thenReturn(List.of(11L));
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), eq(pageable)))
            .thenReturn(new PageImpl<>(java.util.List.of(existingUser), pageable, 1));

        Page<UserResponseDto> page = service.users(new UserRequestDto(), pageable);

        assertThat(page.getContent()).hasSize(1);
        UserResponseDto dto = page.getContent().getFirst();
        assertThat(dto.getUsername()).isEqualTo("carol");
        assertThat(dto.isAccountNonLocked()).isFalse();
        assertThat(dto.getFailedLoginCount()).isEqualTo(5);
        assertThat(dto.getLastLoginAt()).isEqualTo(now.minusMinutes(20));
        assertThat(dto.getLastFailedLoginAt()).isEqualTo(lastFailedAt);
        assertThat(dto.isTemporarilyLocked()).isTrue();
        assertThat(dto.getLockRemainingMinutes()).isBetween(1, 15);
    }

    @Test
    void updateUserRoles_should_sync_role_assignments_without_legacy_user_role_writes() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                roleRepository,
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                roleAssignmentSyncService,
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                mock(UserUnitRepository.class),
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(1L);

        User user = new User();
        user.setId(5L);
        Role role = new com.tiny.platform.infrastructure.auth.role.domain.Role();
        role.setId(100L);

        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(1L, 5L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(roleRepository.findByIdInAndTenantId(List.of(100L), 1L)).thenReturn(List.of(role));

        service.updateUserRoles(5L, List.of(100L));

        verify(roleAssignmentSyncService).replaceUserScopedRoleAssignments(5L, 1L, "TENANT", null, List.of(100L));
    }

    @Test
    void getDirectRoleIdsByUserId_should_query_requested_scope() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                roleAssignmentSyncService,
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                mock(UserUnitRepository.class),
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);

        User user = new User();
        user.setId(22L);
        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(9L, 22L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(22L)).thenReturn(Optional.of(user));
        when(roleAssignmentSyncService.findActiveRoleIdsForUserInScope(22L, 9L, "DEPT", 200L))
            .thenReturn(List.of(100L, 101L));

        List<Long> result = service.getDirectRoleIdsByUserId(22L, "DEPT", 200L);

        assertThat(result).containsExactly(100L, 101L);
        verify(roleAssignmentSyncService).findActiveRoleIdsForUserInScope(22L, 9L, "DEPT", 200L);
    }

    @Test
    void findByUsername_should_prefer_membership_resolution() {
        UserRepository userRepository = mock(UserRepository.class);
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                mock(TenantUserRepository.class),
                mock(UserUnitRepository.class),
                authUserResolutionService,
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);
        User membershipUser = new User();
        membershipUser.setId(20L);
        membershipUser.setUsername("shared.alice");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("shared.alice", 9L)).thenReturn(Optional.of(membershipUser));

        Optional<User> result = service.findByUsername("shared.alice");

        assertThat(result).contains(membershipUser);
        verify(authUserResolutionService).resolveUserRecordInActiveTenant("shared.alice", 9L);
    }

    @Test
    void findByUsername_should_use_platform_resolution_when_platform_scope() {
        UserRepository userRepository = mock(UserRepository.class);
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                mock(TenantUserRepository.class),
                mock(UserUnitRepository.class),
                authUserResolutionService,
                tenantLifecycleGuard
        );

        TenantContext.setActiveScopeType("PLATFORM");
        TenantContext.setActiveTenantId(null);
        User platformUser = new User();
        platformUser.setId(1481L);
        platformUser.setUsername("platform_admin");
        when(authUserResolutionService.resolveUserRecordInPlatform("platform_admin"))
            .thenReturn(Optional.of(platformUser));

        Optional<User> result = service.findByUsername("platform_admin");

        assertThat(result).contains(platformUser);
        verify(authUserResolutionService).resolveUserRecordInPlatform("platform_admin");
    }

    @Test
    void getRoleIdsByUserId_should_support_membership_backed_user_lookup() {
        UserRepository userRepository = mock(UserRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                effectiveRoleResolutionService,
                tenantUserRepository,
                mock(UserUnitRepository.class),
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);

        User membershipUser = new User();
        membershipUser.setId(22L);
        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(9L, 22L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(22L)).thenReturn(Optional.of(membershipUser));
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(22L, 9L)).thenReturn(List.of(100L, 101L));

        List<Long> result = service.getRoleIdsByUserId(22L);

        assertThat(result).containsExactly(100L, 101L);
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(22L, 9L);
    }

    @Test
    void getRoleIdsByUserId_should_use_active_org_scope_for_effective_roles() {
        UserRepository userRepository = mock(UserRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                effectiveRoleResolutionService,
                tenantUserRepository,
                mock(UserUnitRepository.class),
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_ORG);
        TenantContext.setActiveScopeId(55L);

        User membershipUser = new User();
        membershipUser.setId(22L);
        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(9L, 22L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(22L)).thenReturn(Optional.of(membershipUser));
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(22L, 9L, "ORG", 55L))
                .thenReturn(List.of(200L));

        List<Long> result = service.getRoleIdsByUserId(22L);

        assertThat(result).containsExactly(200L);
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(22L, 9L, "ORG", 55L);
    }

    @Test
    void users_should_query_membership_visible_user_ids() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                userUnitRepository,
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);

        User membershipUser = new User();
        membershipUser.setId(30L);
        membershipUser.setUsername("shared.alice");
        membershipUser.setNickname("Shared Alice");
        membershipUser.setEnabled(true);
        membershipUser.setAccountNonExpired(true);
        membershipUser.setAccountNonLocked(true);
        membershipUser.setCredentialsNonExpired(true);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(9L, "ACTIVE")).thenReturn(List.of(30L));
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(membershipUser), pageable, 1));

        Page<UserResponseDto> page = service.users(new UserRequestDto(), pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getUsername()).isEqualTo("shared.alice");
        verify(tenantUserRepository).findUserIdsByTenantIdAndStatus(9L, "ACTIVE");
    }

    @Test
    void users_should_apply_data_scope_from_visible_units_and_self() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                userUnitRepository,
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SecurityUser(31L, 9L, "scoped-admin", "", List.of(), true, true, true, true),
                null,
                List.of()
        ));
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(java.util.Set.of(200L), java.util.Set.of(31L), true));

        User selfUser = new User();
        selfUser.setId(31L);
        selfUser.setUsername("scoped-admin");
        selfUser.setNickname("Scoped Admin");
        selfUser.setEnabled(true);
        selfUser.setAccountNonExpired(true);
        selfUser.setAccountNonLocked(true);
        selfUser.setCredentialsNonExpired(true);

        User deptUser = new User();
        deptUser.setId(32L);
        deptUser.setUsername("dept-user");
        deptUser.setNickname("Dept User");
        deptUser.setEnabled(true);
        deptUser.setAccountNonExpired(true);
        deptUser.setAccountNonLocked(true);
        deptUser.setCredentialsNonExpired(true);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(9L, "ACTIVE")).thenReturn(List.of(31L, 32L, 99L));
        when(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(9L, java.util.Set.of(200L), "ACTIVE"))
                .thenReturn(List.of(32L));
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(selfUser, deptUser), pageable, 2));

        Page<UserResponseDto> page = service.users(new UserRequestDto(), pageable);

        assertThat(page.getContent()).extracting(UserResponseDto::getUsername)
                .containsExactly("scoped-admin", "dept-user");
        verify(tenantUserRepository).findUserIdsByTenantIdAndStatus(9L, "ACTIVE");
        verify(userUnitRepository).findUserIdsByTenantIdAndUnitIdInAndStatus(9L, java.util.Set.of(200L), "ACTIVE");
    }

    /**
     * Contract B read chain: under ORG active scope, DEPT-shaped {@code role_data_scope} resolves to primary-dept
     * unit ids (e.g. 50L). {@code DataScopeAspect} would populate {@link DataScopeContext} accordingly; here we
     * inject the same {@link ResolvedDataScope} to prove {@link UserServiceImpl#users} intersects visibility with
     * that unit set — not with the active ORG id (600L).
     */
    @Test
    void users_read_chain_contract_b_org_scope_dept_rule_uses_primary_dept_visible_units() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                userUnitRepository,
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_ORG);
        TenantContext.setActiveScopeId(600L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SecurityUser(31L, 9L, "scoped-admin", "", List.of(), true, true, true, true),
                null,
                List.of()
        ));
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(java.util.Set.of(50L), java.util.Set.of(), false));

        User selfUser = new User();
        selfUser.setId(31L);
        selfUser.setUsername("scoped-admin");
        selfUser.setNickname("Scoped Admin");
        selfUser.setEnabled(true);
        selfUser.setAccountNonExpired(true);
        selfUser.setAccountNonLocked(true);
        selfUser.setCredentialsNonExpired(true);

        User primaryDeptPeer = new User();
        primaryDeptPeer.setId(32L);
        primaryDeptPeer.setUsername("primary-dept-peer");
        primaryDeptPeer.setNickname("Primary Dept Peer");
        primaryDeptPeer.setEnabled(true);
        primaryDeptPeer.setAccountNonExpired(true);
        primaryDeptPeer.setAccountNonLocked(true);
        primaryDeptPeer.setCredentialsNonExpired(true);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(9L, "ACTIVE")).thenReturn(List.of(31L, 32L));
        when(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(9L, java.util.Set.of(50L), "ACTIVE"))
                .thenReturn(List.of(32L));
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(selfUser, primaryDeptPeer), pageable, 2));

        Page<UserResponseDto> page = service.users(new UserRequestDto(), pageable);

        assertThat(page.getContent()).extracting(UserResponseDto::getUsername)
                .containsExactly("scoped-admin", "primary-dept-peer");
        verify(userUnitRepository).findUserIdsByTenantIdAndUnitIdInAndStatus(9L, java.util.Set.of(50L), "ACTIVE");
    }

    @Test
    void users_should_default_to_self_only_when_resolved_scope_is_self() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                userUnitRepository,
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SecurityUser(41L, 9L, "self-only", "", List.of(), true, true, true, true),
                null,
                List.of()
        ));
        DataScopeContext.set(ResolvedDataScope.selfOnly());

        User selfUser = new User();
        selfUser.setId(41L);
        selfUser.setUsername("self-only");
        selfUser.setNickname("Self Only");
        selfUser.setEnabled(true);
        selfUser.setAccountNonExpired(true);
        selfUser.setAccountNonLocked(true);
        selfUser.setCredentialsNonExpired(true);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(9L, "ACTIVE")).thenReturn(List.of(41L, 42L));
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(selfUser), pageable, 1));

        Page<UserResponseDto> page = service.users(new UserRequestDto(), pageable);

        assertThat(page.getContent()).extracting(UserResponseDto::getUsername)
                .containsExactly("self-only");
        verify(tenantUserRepository).findUserIdsByTenantIdAndStatus(9L, "ACTIVE");
        verify(userUnitRepository, never()).findUserIdsByTenantIdAndUnitIdInAndStatus(any(), any(), any());
    }

    @Test
    void users_should_apply_unit_scoped_visibility_without_self_or_custom_targets() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                userUnitRepository,
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(java.util.Set.of(300L), java.util.Set.of(), false));

        User deptUser = new User();
        deptUser.setId(52L);
        deptUser.setUsername("dept-only");
        deptUser.setNickname("Dept Only");
        deptUser.setEnabled(true);
        deptUser.setAccountNonExpired(true);
        deptUser.setAccountNonLocked(true);
        deptUser.setCredentialsNonExpired(true);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(9L, "ACTIVE")).thenReturn(List.of(52L, 53L));
        when(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(9L, java.util.Set.of(300L), "ACTIVE"))
                .thenReturn(List.of(52L));
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(deptUser), pageable, 1));

        Page<UserResponseDto> page = service.users(new UserRequestDto(), pageable);

        assertThat(page.getContent()).extracting(UserResponseDto::getUsername)
                .containsExactly("dept-only");
        verify(userUnitRepository).findUserIdsByTenantIdAndUnitIdInAndStatus(9L, java.util.Set.of(300L), "ACTIVE");
    }

    @Test
    void users_should_apply_custom_user_scope_without_unit_lookup() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                userUnitRepository,
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);
        DataScopeContext.set(ResolvedDataScope.ofUnitsAndUsers(java.util.Set.of(), java.util.Set.of(61L), false));

        User customUser = new User();
        customUser.setId(61L);
        customUser.setUsername("custom-target");
        customUser.setNickname("Custom Target");
        customUser.setEnabled(true);
        customUser.setAccountNonExpired(true);
        customUser.setAccountNonLocked(true);
        customUser.setCredentialsNonExpired(true);

        PageRequest pageable = PageRequest.of(0, 10);
        when(tenantUserRepository.findUserIdsByTenantIdAndStatus(9L, "ACTIVE")).thenReturn(List.of(61L, 62L));
        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(customUser), pageable, 1));

        Page<UserResponseDto> page = service.users(new UserRequestDto(), pageable);

        assertThat(page.getContent()).extracting(UserResponseDto::getUsername)
                .containsExactly("custom-target");
        verify(userUnitRepository, never()).findUserIdsByTenantIdAndUnitIdInAndStatus(any(), any(), any());
    }

    @Test
    void batchEnable_should_support_membership_backed_users() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(RoleRepository.class),
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                mock(UserUnitRepository.class),
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);

        User membershipUser = new User();
        membershipUser.setId(40L);
        membershipUser.setUsername("shared.bob");
        membershipUser.setEnabled(false);
        membershipUser.setAccountNonExpired(true);
        membershipUser.setAccountNonLocked(true);
        membershipUser.setCredentialsNonExpired(true);

        when(userRepository.findAllById(java.util.Set.of(40L))).thenReturn(List.of(membershipUser));
        when(tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(9L, java.util.Set.of(40L), "ACTIVE"))
                .thenReturn(List.of(40L));

        service.batchEnable(List.of(40L));

        assertThat(membershipUser.isEnabled()).isTrue();
        verify(userRepository).saveAll(List.of(membershipUser));
    }

    @Test
    void updateUserRoles_should_support_membership_backed_user_lookup() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                roleRepository,
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                roleAssignmentSyncService,
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                mock(UserUnitRepository.class),
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);

        User user = new User();
        user.setId(50L);
        Role role = new Role();
        role.setId(100L);

        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(9L, 50L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));
        when(roleRepository.findByIdInAndTenantId(List.of(100L), 9L)).thenReturn(List.of(role));

        service.updateUserRoles(50L, List.of(100L));

        verify(roleAssignmentSyncService).replaceUserScopedRoleAssignments(50L, 9L, "TENANT", null, List.of(100L));
    }

    @Test
    void updateUserRoles_should_support_dept_scope_assignments() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                roleRepository,
                mock(UserAuthenticationMethodRepository.class),
                loginFailurePolicy(),
                roleAssignmentSyncService,
                mock(EffectiveRoleResolutionService.class),
                tenantUserRepository,
                mock(UserUnitRepository.class),
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);

        User user = new User();
        user.setId(50L);
        Role role = new Role();
        role.setId(100L);

        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(9L, 50L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));
        when(roleRepository.findByIdInAndTenantId(List.of(100L), 9L)).thenReturn(List.of(role));

        service.updateUserRoles(50L, "DEPT", 200L, List.of(100L));

        verify(roleAssignmentSyncService).replaceUserScopedRoleAssignments(50L, 9L, "DEPT", 200L, List.of(100L));
    }

    @Test
    void createFromDto_should_persist_contact_fields_and_sync_user_units() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserAuthenticationMethodRepository authenticationMethodRepository = mock(UserAuthenticationMethodRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        UserUnitService userUnitService = mock(UserUnitService.class);
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
            userRepository,
            passwordEncoder,
            mock(RoleRepository.class),
            authenticationMethodRepository,
            loginFailurePolicy(),
            roleAssignmentSyncService,
            mock(EffectiveRoleResolutionService.class),
            mock(TenantUserRepository.class),
            mock(UserUnitRepository.class),
            userUnitService,
            authUserResolutionService,
            tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(7L);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 7L)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("P@ssw0rd")).thenReturn("{bcrypt}encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(88L);
            return saved;
        });

        UserCreateUpdateDto dto = new UserCreateUpdateDto();
        dto.setUsername("alice");
        dto.setNickname("Alice");
        dto.setEmail(" alice@example.com ");
        dto.setPhone("13800000000");
        dto.setPassword("P@ssw0rd");
        dto.setEnabled(true);
        dto.setAccountNonExpired(true);
        dto.setAccountNonLocked(true);
        dto.setCredentialsNonExpired(true);
        dto.setUnitIds(List.of(11L, 12L));
        dto.setPrimaryUnitId(11L);

        User created = service.createFromDto(dto);

        assertThat(created.getId()).isEqualTo(88L);
        assertThat(created.getEmail()).isEqualTo("alice@example.com");
        assertThat(created.getPhone()).isEqualTo("13800000000");
        verify(roleAssignmentSyncService).ensureTenantMembership(88L, 7L, true);
        verify(userUnitService).replaceUserUnits(7L, 88L, List.of(11L, 12L), 11L);
        verify(authenticationMethodRepository).save(any());
    }

    @Test
    void updateFromDto_should_sync_user_units_when_present() {
        UserRepository userRepository = mock(UserRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        UserUnitService userUnitService = mock(UserUnitService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        UserServiceImpl service = new UserServiceImpl(
            userRepository,
            mock(PasswordEncoder.class),
            mock(RoleRepository.class),
            mock(UserAuthenticationMethodRepository.class),
            loginFailurePolicy(),
            mock(RoleAssignmentSyncService.class),
            mock(EffectiveRoleResolutionService.class),
            tenantUserRepository,
            mock(UserUnitRepository.class),
            userUnitService,
            authUserResolutionService,
            tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(7L);

        User existingUser = new User();
        existingUser.setId(88L);
        existingUser.setUsername("alice");
        existingUser.setNickname("Alice");
        existingUser.setEnabled(true);
        existingUser.setAccountNonExpired(true);
        existingUser.setAccountNonLocked(true);
        existingUser.setCredentialsNonExpired(true);

        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(7L, 88L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(88L)).thenReturn(Optional.of(existingUser));
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 7L)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserCreateUpdateDto dto = new UserCreateUpdateDto();
        dto.setId(88L);
        dto.setUsername("alice");
        dto.setNickname("Alice Updated");
        dto.setEmail("updated@example.com");
        dto.setPhone("13800000002");
        dto.setEnabled(true);
        dto.setAccountNonExpired(true);
        dto.setAccountNonLocked(true);
        dto.setCredentialsNonExpired(true);
        dto.setUnitIds(List.of(15L));
        dto.setPrimaryUnitId(15L);

        User updated = service.updateFromDto(dto);

        assertThat(updated.getNickname()).isEqualTo("Alice Updated");
        assertThat(updated.getEmail()).isEqualTo("updated@example.com");
        assertThat(updated.getPhone()).isEqualTo("13800000002");
        verify(userUnitService).replaceUserUnits(7L, 88L, List.of(15L), 15L);
    }
}
