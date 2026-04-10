package com.tiny.platform.application.oauth.workflow;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.camunda.bpm.engine.IdentityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.EnumSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CamundaIdentityBridgeFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_use_role_codes_from_details_when_principal_is_username_in_mfa_session() throws Exception {
        IdentityService identityService = mock(IdentityService.class);
        TenantResolver tenantResolver = mock(TenantResolver.class);
        CamundaIdentityBridgeFilter filter = new CamundaIdentityBridgeFilter(identityService, tenantResolver);

        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        SecurityUser securityUser = new SecurityUser(
                user,
                "",
                9L,
                List.of(new SimpleGrantedAuthority("system:org:list")),
                java.util.Set.of("ROLE_TENANT_ADMIN"),
                "perm-v1"
        );

        MultiFactorAuthenticationToken auth = new MultiFactorAuthenticationToken(
                "alice",
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL,
                EnumSet.of(
                        MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                        MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
                ),
                List.of(new SimpleGrantedAuthority("system:org:list"))
        );
        auth.setAuthenticated(true);
        auth.setDetails(securityUser);
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(tenantResolver.resolveTenantIds(eq(request), eq(auth))).thenReturn(List.of("9"));

        filter.doFilter(request, response, chain);

        verify(identityService).setAuthentication("alice", List.of("TENANT_ADMIN"), List.of("9"));
    }

    @Test
    void should_use_role_codes_from_security_user_principal_in_normal_session() throws Exception {
        IdentityService identityService = mock(IdentityService.class);
        TenantResolver tenantResolver = mock(TenantResolver.class);
        CamundaIdentityBridgeFilter filter = new CamundaIdentityBridgeFilter(identityService, tenantResolver);

        User user = new User();
        user.setId(8L);
        user.setUsername("bob");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        SecurityUser securityUser = new SecurityUser(
                user,
                "",
                11L,
                List.of(new SimpleGrantedAuthority("system:user:list")),
                java.util.Set.of("ROLE_PLATFORM_ADMIN"),
                "perm-v2"
        );

        UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated(
                securityUser,
                "n/a",
                List.of(new SimpleGrantedAuthority("system:user:list"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(tenantResolver.resolveTenantIds(eq(request), eq(auth))).thenReturn(List.of("11"));

        filter.doFilter(request, response, chain);

        verify(identityService).setAuthentication("bob", List.of("PLATFORM_ADMIN"), List.of("11"));
    }

    @Test
    void should_return_empty_groups_for_legacy_jwt_without_role_codes_after_fallback_removal() throws Exception {
        IdentityService identityService = mock(IdentityService.class);
        TenantResolver tenantResolver = mock(TenantResolver.class);
        CamundaIdentityBridgeFilter filter = new CamundaIdentityBridgeFilter(identityService, tenantResolver);

        Jwt legacyJwt = Jwt.withTokenValue("legacy")
                .header("alg", "none")
                .claim("sub", "legacy.alice")
                .claim("authorities", List.of("ROLE_TENANT_ADMIN", "system:user:list"))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(
                legacyJwt,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"), new SimpleGrantedAuthority("system:user:list"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(tenantResolver.resolveTenantIds(eq(request), eq(auth))).thenReturn(List.of("9"));

        filter.doFilter(request, response, chain);

        verify(identityService).setAuthentication("legacy.alice", List.of(), List.of("9"));
    }
}
