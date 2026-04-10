package com.tiny.platform.core.oauth.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUserTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldExposeRoleCodeAsAuthorities_fromRoles() {
        Role role = new Role();
        role.setCode("ROLE_ADMIN");
        role.setName("系统管理员");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        SecurityUser securityUser = new SecurityUser(user, "", 1L, Set.of(role), "perm-v1");

        assertThat(securityUser.getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly("ROLE_ADMIN");
        assertThat(securityUser.getPermissionsVersion()).isEqualTo("perm-v1");
        assertThat(securityUser.getActiveTenantId()).isEqualTo(1L);
    }

    @Test
    void convenienceConstructor_shouldPreferActiveTenantContext() {
        User user = new User();
        user.setId(2L);
        user.setUsername("shared.alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        TenantContext.setActiveTenantId(9L);

        SecurityUser securityUser = new SecurityUser(user);

        assertThat(securityUser.getActiveTenantId()).isEqualTo(9L);
        assertThat(securityUser.getActiveTenantId()).isEqualTo(9L);
    }

    @Test
    void convenienceConstructor_shouldFallbackToAuthenticationActiveTenant() {
        User authenticatedUser = new User();
        authenticatedUser.setId(99L);
        authenticatedUser.setUsername("session.alice");
        authenticatedUser.setEnabled(true);
        authenticatedUser.setAccountNonExpired(true);
        authenticatedUser.setAccountNonLocked(true);
        authenticatedUser.setCredentialsNonExpired(true);

        SecurityUser authenticatedPrincipal = new SecurityUser(authenticatedUser, "", 12L, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedPrincipal, null, authenticatedPrincipal.getAuthorities())
        );

        User user = new User();
        user.setId(2L);
        user.setUsername("shared.alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        SecurityUser securityUser = new SecurityUser(user);

        assertThat(securityUser.getActiveTenantId()).isEqualTo(12L);
        assertThat(securityUser.getActiveTenantId()).isEqualTo(12L);
    }

    @Test
    void jacksonShouldSerializeAndDeserializeActiveTenantIdOnly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        SecurityUser securityUser = new SecurityUser(
                3L,
                11L,
                "alice",
                "",
                java.util.List.of(),
                true,
                true,
                true,
                true,
                "perm-v2"
        );

        String json = mapper.writeValueAsString(securityUser);

        assertThat(json).contains("\"activeTenantId\":\"11\"");
        assertThat(json).doesNotContain("\"tenantId\"");

        SecurityUser restored = mapper.readValue(json, SecurityUser.class);

        assertThat(restored.getUserId()).isEqualTo(3L);
        assertThat(restored.getActiveTenantId()).isEqualTo(11L);
        assertThat(restored.getActiveTenantId()).isEqualTo(11L);
        assertThat(restored.getPermissionsVersion()).isEqualTo("perm-v2");
    }

    @Test
    void jsonCreatorPathShouldNotRecoverRoleCodesFromAuthoritiesWhenRoleCodesMissing() {
        SecurityUser restored = new SecurityUser(
                9L,
                21L,
                "legacy.alice",
                "",
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"), new SimpleGrantedAuthority("system:user:list")),
                null,
                true,
                true,
                true,
                true,
                "perm-v3"
        );
        assertThat(restored.getRoleCodes()).isEmpty();
    }
}
