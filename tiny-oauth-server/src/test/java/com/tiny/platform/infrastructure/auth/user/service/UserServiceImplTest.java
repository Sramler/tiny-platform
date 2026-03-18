package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.config.LoginProtectionProperties;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.LoginFailurePolicy;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(1L);

        User existingUser = new User();
        existingUser.setId(9L);
        existingUser.setTenantId(1L);
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
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(1L);

        LocalDateTime failedAt = LocalDateTime.now().minusMinutes(2);
        User existingUser = new User();
        existingUser.setId(10L);
        existingUser.setTenantId(1L);
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
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(1L);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastFailedAt = now.minusMinutes(2);
        User existingUser = new User();
        existingUser.setId(11L);
        existingUser.setTenantId(1L);
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
        assertThat(dto.getRecordTenantId()).isEqualTo(1L);
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
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(1L);

        User user = new User();
        user.setId(5L);
        user.setTenantId(1L);
        Role role = new com.tiny.platform.infrastructure.auth.role.domain.Role();
        role.setId(100L);

        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(1L, 5L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(roleRepository.findByIdInAndTenantId(List.of(100L), 1L)).thenReturn(List.of(role));

        service.updateUserRoles(5L, List.of(100L));

        verify(roleAssignmentSyncService).replaceUserTenantRoleAssignments(5L, 1L, List.of(100L));
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
                authUserResolutionService,
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);
        User membershipUser = new User();
        membershipUser.setId(20L);
        membershipUser.setTenantId(1L);
        membershipUser.setUsername("shared.alice");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("shared.alice", 9L)).thenReturn(Optional.of(membershipUser));

        Optional<User> result = service.findByUsername("shared.alice");

        assertThat(result).contains(membershipUser);
        verify(authUserResolutionService).resolveUserRecordInActiveTenant("shared.alice", 9L);
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
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);

        User membershipUser = new User();
        membershipUser.setId(22L);
        membershipUser.setTenantId(1L);

        when(userRepository.findByIdAndTenantId(22L, 9L)).thenReturn(Optional.empty());
        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(9L, 22L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(22L)).thenReturn(Optional.of(membershipUser));
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(22L, 9L)).thenReturn(List.of(100L, 101L));

        List<Long> result = service.getRoleIdsByUserId(22L);

        assertThat(result).containsExactly(100L, 101L);
        verify(effectiveRoleResolutionService).findEffectiveRoleIdsForUserInTenant(22L, 9L);
    }

    @Test
    void users_should_query_membership_visible_user_ids() {
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
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);

        User membershipUser = new User();
        membershipUser.setId(30L);
        membershipUser.setTenantId(1L);
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
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);

        User membershipUser = new User();
        membershipUser.setId(40L);
        membershipUser.setTenantId(1L);
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
                mock(AuthUserResolutionService.class),
                tenantLifecycleGuard
        );

        TenantContext.setActiveTenantId(9L);

        User user = new User();
        user.setId(50L);
        user.setTenantId(1L);
        Role role = new Role();
        role.setId(100L);

        when(userRepository.findByIdAndTenantId(50L, 9L)).thenReturn(Optional.empty());
        when(tenantUserRepository.existsByTenantIdAndUserIdAndStatus(9L, 50L, "ACTIVE")).thenReturn(true);
        when(userRepository.findById(50L)).thenReturn(Optional.of(user));
        when(roleRepository.findByIdInAndTenantId(List.of(100L), 9L)).thenReturn(List.of(role));

        service.updateUserRoles(50L, List.of(100L));

        verify(roleAssignmentSyncService).replaceUserTenantRoleAssignments(50L, 9L, List.of(100L));
    }
}
