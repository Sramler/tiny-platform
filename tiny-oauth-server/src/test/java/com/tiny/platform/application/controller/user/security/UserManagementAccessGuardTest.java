package com.tiny.platform.application.controller.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class UserManagementAccessGuardTest {

    private final UserManagementAccessGuard guard = new UserManagementAccessGuard();

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void should_allow_read_with_fine_grained_permission() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        JwtAuthenticationToken authentication = jwtAuth("system:user:list");

        assertThat(guard.canRead(authentication)).isTrue();
    }

    @Test
    void should_deny_read_in_platform_scope_even_with_permission() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        JwtAuthenticationToken authentication = jwtAuth("system:user:list");

        assertThat(guard.canRead(authentication)).isFalse();
    }

    @Test
    void should_deny_read_without_required_authorities() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        JwtAuthenticationToken authentication = jwtAuth("ROLE_USER");

        assertThat(guard.canRead(authentication)).isFalse();
    }

    @Test
    void should_allow_platform_steward_read_when_platform_scope_and_required_authorities_present() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        JwtAuthenticationToken authentication = jwtAuth("system:user:list", "system:tenant:view");

        assertThat(guard.canPlatformStewardRead(authentication)).isTrue();
    }

    @Test
    void should_deny_platform_steward_read_when_tenant_authority_missing() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        JwtAuthenticationToken authentication = jwtAuth("system:user:list");

        assertThat(guard.canPlatformStewardRead(authentication)).isFalse();
    }

    private static JwtAuthenticationToken jwtAuth(String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "test-user")
            .build();
        return new JwtAuthenticationToken(
            jwt,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()
        );
    }
}
