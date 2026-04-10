package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.PermissionRefactorObservabilityProperties;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityUserAuthorityServiceTest {

    @Test
    void should_choose_new_path_when_new_covers_old_and_apply_deny() {
        PermissionAuthorityReadRepository repository = mock(PermissionAuthorityReadRepository.class);
        SecurityUserAuthorityService service = new SecurityUserAuthorityService(repository, new PermissionRefactorObservabilityProperties());

        Role role = role(1L, "ROLE_ADMIN");
        when(repository.findPermissionCodesByRoleIds(Set.of(1L), 1L))
                .thenReturn(Set.of("order:read", "order:write"));
        when(repository.findEnabledFlagsByPermissionCodes(Set.of("order:read", "order:write"), 1L))
                .thenReturn(Map.of("order:read", true, "order:write", true));

        assertThat(service.buildAuthorities(10L, 1L, "TENANT", 1L, Set.of(role)))
                .extracting(a -> a.getAuthority())
                .containsExactlyInAnyOrder("order:read", "order:write");
    }

    @Test
    void should_return_role_only_when_no_permission_mapping_exists() {
        PermissionAuthorityReadRepository repository = mock(PermissionAuthorityReadRepository.class);
        SecurityUserAuthorityService service = new SecurityUserAuthorityService(repository, new PermissionRefactorObservabilityProperties());

        Role role = role(2L, "ROLE_AUDITOR");
        when(repository.findPermissionCodesByRoleIds(Set.of(2L), 2L))
                .thenReturn(Set.of());

        assertThat(service.buildAuthorities(20L, 2L, "TENANT", 200L, Set.of(role)))
                .extracting(a -> a.getAuthority())
                .isEmpty();
    }

    @Test
    void should_deny_disabled_and_unknown_permissions_even_with_old_fallback() {
        PermissionAuthorityReadRepository repository = mock(PermissionAuthorityReadRepository.class);
        SecurityUserAuthorityService service = new SecurityUserAuthorityService(repository, new PermissionRefactorObservabilityProperties());

        Role role = role(3L, "ROLE_OPERATOR");
        when(repository.findPermissionCodesByRoleIds(Set.of(3L), 3L))
                .thenReturn(Set.of("job:run"));
        Map<String, Boolean> enabledFlags = new LinkedHashMap<>();
        enabledFlags.put("job:run", false);
        when(repository.findEnabledFlagsByPermissionCodes(Set.of("job:run"), 3L))
                .thenReturn(enabledFlags);

        assertThat(service.buildAuthorities(30L, 3L, "TENANT", 300L, Set.of(role)))
                .extracting(a -> a.getAuthority())
                .isEmpty();
    }

    @Test
    void should_emit_org_scope_signals_for_fallback_and_deny() {
        PermissionAuthorityReadRepository repository = mock(PermissionAuthorityReadRepository.class);
        SecurityUserAuthorityService service = new SecurityUserAuthorityService(repository, new PermissionRefactorObservabilityProperties());

        Role role = role(4L, "ROLE_ORG_OPERATOR");
        when(repository.findPermissionCodesByRoleIds(Set.of(4L), 1L))
                .thenReturn(Set.of("org:read"));
        Map<String, Boolean> enabledFlags = new LinkedHashMap<>();
        enabledFlags.put("org:read", false);
        when(repository.findEnabledFlagsByPermissionCodes(Set.of("org:read"), 1L))
                .thenReturn(enabledFlags);

        assertThat(service.buildAuthorities(40L, 1L, "ORG", 5L, Set.of(role)))
                .extracting(a -> a.getAuthority())
                .isEmpty();
    }

    private Role role(Long id, String roleCode) {
        Role role = new Role();
        role.setId(id);
        role.setCode(roleCode);
        return role;
    }
}
