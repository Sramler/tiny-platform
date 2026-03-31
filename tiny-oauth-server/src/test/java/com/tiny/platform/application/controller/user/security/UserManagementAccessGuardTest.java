package com.tiny.platform.application.controller.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class UserManagementAccessGuardTest {

    private final UserManagementAccessGuard guard = new UserManagementAccessGuard();

    @Test
    void should_allow_read_with_fine_grained_permission() {
        JwtAuthenticationToken authentication = jwtAuth("system:user:list");

        assertThat(guard.canRead(authentication)).isTrue();
    }

    @Test
    void should_deny_read_without_required_authorities() {
        JwtAuthenticationToken authentication = jwtAuth("ROLE_USER");

        assertThat(guard.canRead(authentication)).isFalse();
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
