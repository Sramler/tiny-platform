package com.tiny.platform.core.oauth.security;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.role.service.EffectiveRoleResolutionService;
import com.tiny.platform.infrastructure.auth.user.domain.TenantUser;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionVersionServiceTest {

    @Test
    void should_generate_stable_version_from_authorities_and_assignment_sources() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service =
            new PermissionVersionService(
                tenantUserRepository,
                roleAssignmentRepository,
                effectiveRoleResolutionService,
                fixedClock
            );

        TenantUser tenantUser = new TenantUser();
        tenantUser.setUpdatedAt(LocalDateTime.of(2026, 3, 10, 9, 30));
        Role role = roleWithAuthorities("ROLE_ADMIN", "dict:type:view", "scheduling:console:view");

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.of(tenantUser));
        when(roleAssignmentRepository.findLatestUpdatedAtForActiveUserInTenant(
            7L,
            9L,
            LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)
        )).thenReturn(LocalDateTime.of(2026, 3, 11, 8, 15));
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of(role));

        String first = service.resolvePermissionsVersion(7L, 9L);
        String second = service.resolvePermissionsVersion(7L, 9L);

        assertThat(first).hasSize(64);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void should_change_version_when_assignment_source_changes() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service =
            new PermissionVersionService(
                tenantUserRepository,
                roleAssignmentRepository,
                effectiveRoleResolutionService,
                fixedClock
            );

        TenantUser tenantUser = new TenantUser();
        tenantUser.setUpdatedAt(LocalDateTime.of(2026, 3, 10, 9, 30));
        Role role = roleWithAuthorities("ROLE_ADMIN", "dict:type:view");

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.of(tenantUser));
        when(roleAssignmentRepository.findLatestUpdatedAtForActiveUserInTenant(
            7L,
            9L,
            LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)
        )).thenReturn(LocalDateTime.of(2026, 3, 11, 8, 15));
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of(role));

        String baseline = service.resolvePermissionsVersion(7L, 9L);

        when(roleAssignmentRepository.findLatestUpdatedAtForActiveUserInTenant(
            7L,
            9L,
            LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)
        )).thenReturn(LocalDateTime.of(2026, 3, 12, 8, 15));

        String updated = service.resolvePermissionsVersion(7L, 9L);

        assertThat(updated).isNotEqualTo(baseline);
    }

    private Role roleWithAuthorities(String roleCode, String... permissions) {
        Role role = new Role();
        role.setCode(roleCode);
        java.util.LinkedHashSet<Resource> resources = new java.util.LinkedHashSet<>();
        for (String permission : permissions) {
            Resource resource = new Resource();
            resource.setPermission(permission);
            resources.add(resource);
        }
        role.setResources(resources);
        return role;
    }
}
