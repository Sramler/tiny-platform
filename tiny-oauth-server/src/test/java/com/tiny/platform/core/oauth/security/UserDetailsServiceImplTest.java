package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserDetailsServiceImplTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void should_reject_when_tenant_missing_or_user_not_found() {
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(authUserResolutionService);

        TenantContext.clear();
        assertThatThrownBy(() -> service.loadUserByUsername("alice"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("缺少租户信息");

        TenantContext.setActiveTenantId(1L);
        when(authUserResolutionService.resolveUserInActiveTenant("alice", 1L)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.loadUserByUsername("alice"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("用户不存在");
    }

    @Test
    void should_load_user_without_mutating_login_metadata_and_return_security_user() {
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(authUserResolutionService);
        TenantContext.setActiveTenantId(2L);

        User user = new User();
        user.setId(10L);
        user.setTenantId(2L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        Role role = new Role();
        role.setCode("ROLE_ADMIN");
        role.setName("系统管理员");

        when(authUserResolutionService.resolveUserInActiveTenant("alice", 2L))
                .thenReturn(java.util.Optional.of(new AuthResolvedUser(user, 2L, Set.of(role))));

        SecurityUser result = (SecurityUser) service.loadUserByUsername("alice");

        assertThat(result.getUserId()).isEqualTo(10L);
        assertThat(result.getActiveTenantId()).isEqualTo(2L);
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains("ROLE_ADMIN");
        assertThat(user.getLastLoginAt()).isNull();
    }

    @Test
    void should_prefer_resolver_for_membership_user_and_use_effective_roles() {
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(authUserResolutionService, permissionVersionService);
        TenantContext.setActiveTenantId(9L);

        User user = new User();
        user.setId(12L);
        user.setTenantId(1L);
        user.setUsername("shared.alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        Role role = new Role();
        role.setCode("ROLE_AUDITOR");
        role.setName("租户审计员");

        when(authUserResolutionService.resolveUserInActiveTenant("shared.alice", 9L))
                .thenReturn(Optional.of(new AuthResolvedUser(user, 9L, Set.of(role))));
        when(permissionVersionService.resolvePermissionsVersion(12L, 9L)).thenReturn("perm-v1");

        SecurityUser result = (SecurityUser) service.loadUserByUsername("shared.alice");

        assertThat(result.getUserId()).isEqualTo(12L);
        assertThat(result.getActiveTenantId()).isEqualTo(9L);
        assertThat(result.getPermissionsVersion()).isEqualTo("perm-v1");
        assertThat(result.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains("ROLE_AUDITOR");
    }

    @Test
    void should_fallback_to_session_active_tenant_when_context_missing() {
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(authUserResolutionService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 6L);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        User user = new User();
        user.setId(10L);
        user.setTenantId(1L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        Role role = new Role();
        role.setCode("ROLE_ADMIN");
        role.setName("系统管理员");

        when(authUserResolutionService.resolveUserInActiveTenant("alice", 6L))
                .thenReturn(java.util.Optional.of(new AuthResolvedUser(user, 6L, Set.of(role))));

        SecurityUser result = (SecurityUser) service.loadUserByUsername("alice");

        assertThat(result.getActiveTenantId()).isEqualTo(6L);
    }
}
