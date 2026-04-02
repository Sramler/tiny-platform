package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.Set;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        SecurityUserAuthorityService authorityService = mock(SecurityUserAuthorityService.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(authUserResolutionService, null, authorityService);

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
        SecurityUserAuthorityService authorityService = mock(SecurityUserAuthorityService.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(authUserResolutionService, null, authorityService);
        TenantContext.setActiveTenantId(2L);

        User user = new User();
        user.setId(10L);
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
        java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities =
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
        doReturn(authorities).when(authorityService).buildAuthorities(10L, 2L, "TENANT", 2L, Set.of(role));

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
        SecurityUserAuthorityService authorityService = mock(SecurityUserAuthorityService.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(authUserResolutionService, permissionVersionService, authorityService);
        TenantContext.setActiveTenantId(9L);

        User user = new User();
        user.setId(12L);
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
        when(permissionVersionService.resolvePermissionsVersion(12L, 9L, TenantContextContract.SCOPE_TYPE_TENANT, 9L)).thenReturn("perm-v1");
        java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities =
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AUDITOR"));
        doReturn(authorities).when(authorityService).buildAuthorities(12L, 9L, "TENANT", 9L, Set.of(role));

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
        SecurityUserAuthorityService authorityService = mock(SecurityUserAuthorityService.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(authUserResolutionService, null, authorityService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 6L);
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        User user = new User();
        user.setId(10L);
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
        java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities =
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
        doReturn(authorities).when(authorityService).buildAuthorities(10L, 6L, "TENANT", 6L, Set.of(role));

        SecurityUser result = (SecurityUser) service.loadUserByUsername("alice");

        assertThat(result.getActiveTenantId()).isEqualTo(6L);
    }

    @Test
    void should_bucket_org_scope_by_scope_type_and_scope_id() {
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        SecurityUserAuthorityService authorityService = mock(SecurityUserAuthorityService.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(authUserResolutionService, permissionVersionService, authorityService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 9L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "ORG");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 101L);
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        User user = new User();
        user.setId(20L);
        user.setUsername("org.alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        Role role = new Role();
        role.setId(99L);
        role.setCode("ROLE_ORG_ADMIN");

        when(authUserResolutionService.resolveUserInActiveTenant("org.alice", 9L))
            .thenReturn(Optional.of(new AuthResolvedUser(user, 9L, Set.of(role))));
        java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities =
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ORG_ADMIN"));
        doReturn(authorities).when(authorityService).buildAuthorities(20L, 9L, "ORG", 101L, Set.of(role));
        when(permissionVersionService.resolvePermissionsVersion(20L, 9L, "ORG", 101L))
            .thenReturn("perm-org-1");

        SecurityUser result = (SecurityUser) service.loadUserByUsername("org.alice");

        assertThat(result.getPermissionsVersion()).isEqualTo("perm-org-1");
        verify(authorityService).buildAuthorities(20L, 9L, "ORG", 101L, Set.of(role));
        verify(permissionVersionService).resolvePermissionsVersion(20L, 9L, "ORG", 101L);
    }

    @Test
    void should_bucket_dept_scope_for_permissions_version_and_authorities() {
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        PermissionVersionService permissionVersionService = mock(PermissionVersionService.class);
        SecurityUserAuthorityService authorityService = mock(SecurityUserAuthorityService.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(authUserResolutionService, permissionVersionService, authorityService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 9L);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, "DEPT");
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, 202L);
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        User user = new User();
        user.setId(21L);
        user.setUsername("dept.bob");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        Role role = new Role();
        role.setId(88L);
        role.setCode("ROLE_DEPT_LEAD");

        when(authUserResolutionService.resolveUserInActiveTenant("dept.bob", 9L))
            .thenReturn(Optional.of(new AuthResolvedUser(user, 9L, Set.of(role))));
        java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities =
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_DEPT_LEAD"));
        doReturn(authorities).when(authorityService).buildAuthorities(21L, 9L, "DEPT", 202L, Set.of(role));
        when(permissionVersionService.resolvePermissionsVersion(21L, 9L, "DEPT", 202L))
            .thenReturn("perm-dept-2");

        SecurityUser result = (SecurityUser) service.loadUserByUsername("dept.bob");

        assertThat(result.getPermissionsVersion()).isEqualTo("perm-dept-2");
        verify(authorityService).buildAuthorities(21L, 9L, "DEPT", 202L, Set.of(role));
        verify(permissionVersionService).resolvePermissionsVersion(21L, 9L, "DEPT", 202L);
    }

    @Test
    void guard_should_use_authority_service_as_primary_authority_entry() {
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        SecurityUserAuthorityService authorityService = mock(SecurityUserAuthorityService.class);
        UserDetailsServiceImpl service = new UserDetailsServiceImpl(authUserResolutionService, null, authorityService);
        TenantContext.setActiveTenantId(11L);

        User user = new User();
        user.setId(44L);
        user.setUsername("guard.user");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        Role role = new Role();
        role.setCode("ROLE_ADMIN");
        role.setName("管理员");

        when(authUserResolutionService.resolveUserInActiveTenant("guard.user", 11L))
            .thenReturn(Optional.of(new AuthResolvedUser(user, 11L, Set.of(role))));
        java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities =
            java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("perm.only.from.service")
            );
        doReturn(authorities).when(authorityService).buildAuthorities(44L, 11L, "TENANT", 11L, Set.of(role));

        SecurityUser result = (SecurityUser) service.loadUserByUsername("guard.user");

        assertThat(result.getAuthorities())
            .extracting(authority -> authority.getAuthority())
            .contains("perm.only.from.service");
        verify(authorityService).buildAuthorities(44L, 11L, "TENANT", 11L, Set.of(role));
    }

    @Test
    void guard_runtime_constructor_contract_should_expose_injectable_authority_service() {
        Constructor<?>[] constructors = UserDetailsServiceImpl.class.getDeclaredConstructors();
        Constructor<?> runtimeConstructor = java.util.Arrays.stream(constructors)
            .filter(constructor -> constructor.getParameterCount() == 3)
            .findFirst()
            .orElseThrow();

        Class<?>[] parameterTypes = runtimeConstructor.getParameterTypes();
        assertThat(parameterTypes[0]).isEqualTo(AuthUserResolutionService.class);
        assertThat(parameterTypes[1]).isEqualTo(PermissionVersionService.class);
        assertThat(parameterTypes[2]).isEqualTo(SecurityUserAuthorityService.class);
        assertThat(runtimeConstructor.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)).isTrue();
        assertThat(Modifier.isPublic(runtimeConstructor.getModifiers())).isTrue();
        assertThat(java.util.Arrays.stream(constructors))
            .allMatch(constructor -> constructor.getParameterCount() == 3);
    }

    @Test
    void guard_constructor_should_fail_fast_when_authority_service_missing() {
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        assertThatThrownBy(() -> new UserDetailsServiceImpl(authUserResolutionService, null, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("SecurityUserAuthorityService");
    }
}
