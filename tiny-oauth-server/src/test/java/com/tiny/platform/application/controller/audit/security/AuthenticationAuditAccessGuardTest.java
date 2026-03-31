package com.tiny.platform.application.controller.audit.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticationAuditAccessGuardTest {

    private final AuthenticationAuditAccessGuard guard = new AuthenticationAuditAccessGuard();

    @Test
    void should_allow_view_and_export_separately() {
        JwtAuthenticationToken authentication = jwtAuth(
            "system:audit:authentication:view",
            "system:audit:authentication:export"
        );

        assertThat(guard.canView(authentication)).isTrue();
        assertThat(guard.canExport(authentication)).isTrue();
    }

    @Test
    void should_deny_export_without_export_authority() {
        JwtAuthenticationToken authentication = jwtAuth("system:audit:authentication:view");

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
