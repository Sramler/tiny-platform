package com.tiny.platform.application.controller.tenant.security;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenantManagementAccessGuardTest {

    private final TenantManagementAccessGuard guard = new TenantManagementAccessGuard();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void should_allow_platform_admin_with_fine_grained_permissions() {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        JwtAuthenticationToken authentication = jwtAuth(
                "system:tenant:list", "system:tenant:create",
                "system:tenant:edit", "system:tenant:template:initialize", "system:tenant:delete"
        );

        assertThat(guard.canRead(authentication)).isTrue();
        assertThat(guard.canCreate(authentication)).isTrue();
        assertThat(guard.canUpdate(authentication)).isTrue();
        assertThat(guard.canInitializePlatformTemplate(authentication)).isTrue();
        assertThat(guard.canDelete(authentication)).isTrue();
    }

    @Test
    void should_deny_role_admin_alone_without_fine_grained_permissions() {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        JwtAuthenticationToken authentication = jwtAuth("ROLE_ADMIN");

        assertThat(guard.canRead(authentication)).isFalse();
        assertThat(guard.canCreate(authentication)).isFalse();
        assertThat(guard.canUpdate(authentication)).isFalse();
        assertThat(guard.canInitializePlatformTemplate(authentication)).isFalse();
        assertThat(guard.canDelete(authentication)).isFalse();
    }

    @Test
    void should_deny_when_not_platform_scope() {
        TenantContext.setActiveTenantId(999L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        JwtAuthenticationToken authentication = jwtAuth("system:tenant:list", "system:tenant:view");

        assertThat(guard.canRead(authentication)).isFalse();
    }

    @Test
    void should_deny_when_scope_type_not_set() {
        TenantContext.setActiveTenantId(1L);

        JwtAuthenticationToken authentication = jwtAuth("system:tenant:list");

        assertThat(guard.canRead(authentication)).isFalse();
    }

    private static JwtAuthenticationToken jwtAuth(String... authorities) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "test-user")
                .build();
        return new JwtAuthenticationToken(
                jwt,
                java.util.Arrays.stream(authorities)
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
    }
}
