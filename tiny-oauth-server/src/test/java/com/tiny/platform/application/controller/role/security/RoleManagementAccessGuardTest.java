package com.tiny.platform.application.controller.role.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RoleManagementAccessGuardTest {

    private final RoleManagementAccessGuard guard = new RoleManagementAccessGuard();

    @Test
    void should_allow_read_with_fine_grained_permission() {
        JwtAuthenticationToken authentication = jwtAuth("system:role:list");

        assertThat(guard.canRead(authentication)).isTrue();
    }

    @Test
    void should_deny_read_without_required_authorities() {
        JwtAuthenticationToken authentication = jwtAuth("ROLE_USER");

        assertThat(guard.canRead(authentication)).isFalse();
    }

    @Test
    void should_require_explicit_constraint_permissions() {
        JwtAuthenticationToken legacyAssign = jwtAuth("system:role:permission:assign");
        JwtAuthenticationToken constraintEdit = jwtAuth("system:role:constraint:edit");
        JwtAuthenticationToken constraintView = jwtAuth("system:role:constraint:view");
        JwtAuthenticationToken violationView = jwtAuth("system:role:constraint:violation:view");

        assertThat(guard.canManageRoleConstraints(legacyAssign)).isFalse();
        assertThat(guard.canViewRoleConstraints(legacyAssign)).isFalse();
        assertThat(guard.canViewRoleConstraintViolations(legacyAssign)).isFalse();
        assertThat(guard.canManageRoleConstraints(constraintEdit)).isTrue();
        assertThat(guard.canViewRoleConstraints(constraintView)).isTrue();
        assertThat(guard.canViewRoleConstraintViolations(violationView)).isTrue();
        assertThat(guard.canReadRoleCatalog(constraintEdit)).isTrue();
        assertThat(guard.canReadRoleCatalog(constraintView)).isTrue();
        assertThat(guard.canReadRoleCatalog(violationView)).isFalse();
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
