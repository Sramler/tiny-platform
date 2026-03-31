package com.tiny.platform.application.controller.audit.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationAuditAccessGuardTest {

    private final AuthorizationAuditAccessGuard guard = new AuthorizationAuditAccessGuard();

    @Test
    void should_allow_view_export_and_purge_separately() {
        JwtAuthenticationToken authentication = jwtAuth(
            "system:audit:auth:view",
            "system:audit:auth:export",
            "system:audit:auth:purge"
        );

        assertThat(guard.canView(authentication)).isTrue();
        assertThat(guard.canExport(authentication)).isTrue();
        assertThat(guard.canPurge(authentication)).isTrue();
    }

    @Test
    void should_deny_export_without_export_authority() {
        JwtAuthenticationToken authentication = jwtAuth("system:audit:auth:view");

        assertThat(guard.canView(authentication)).isTrue();
        assertThat(guard.canExport(authentication)).isFalse();
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
