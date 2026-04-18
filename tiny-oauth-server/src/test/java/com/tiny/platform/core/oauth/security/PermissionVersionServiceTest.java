package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.PermissionRefactorObservabilityProperties;
import com.tiny.platform.infrastructure.auth.org.domain.UserUnit;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.domain.RoleAssignment;
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
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionAuthorityReadRepository permissionAuthorityReadRepository = mock(PermissionAuthorityReadRepository.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service =
            new PermissionVersionService(
                tenantUserRepository,
                userUnitRepository,
                roleAssignmentRepository,
                effectiveRoleResolutionService,
                permissionAuthorityReadRepository,
                permissionVersionReadRepository,
                new PermissionRefactorObservabilityProperties(),
                fixedClock
            );

        TenantUser tenantUser = new TenantUser();
        tenantUser.setUpdatedAt(LocalDateTime.of(2026, 3, 10, 9, 30));
        Role role = roleWithCode("ROLE_ADMIN");
        RoleAssignment assignment = assignmentUpdatedAt(LocalDateTime.of(2026, 3, 11, 8, 15));

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.of(tenantUser));
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of());
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(
            7L,
            9L,
            LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)
        )).thenReturn(java.util.List.of(assignment));
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of(role));
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L))
            .thenReturn(java.util.List.of(11L));
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenReturn(java.util.List.of(new PermissionVersionReadRepository.PermissionSnapshot("dict:type:view", true, 9L, LocalDateTime.of(2026, 3, 10, 9, 30))));
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L), 9L))
            .thenReturn(java.util.List.of());

        String first = service.resolvePermissionsVersion(7L, 9L);
        String second = service.resolvePermissionsVersion(7L, 9L);

        assertThat(first).hasSize(64);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void should_change_version_when_assignment_source_changes() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionAuthorityReadRepository permissionAuthorityReadRepository = mock(PermissionAuthorityReadRepository.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service =
            new PermissionVersionService(
                tenantUserRepository,
                userUnitRepository,
                roleAssignmentRepository,
                effectiveRoleResolutionService,
                permissionAuthorityReadRepository,
                permissionVersionReadRepository,
                new PermissionRefactorObservabilityProperties(),
                fixedClock
            );

        TenantUser tenantUser = new TenantUser();
        tenantUser.setUpdatedAt(LocalDateTime.of(2026, 3, 10, 9, 30));
        Role role = roleWithCode("ROLE_ADMIN");

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.of(tenantUser));
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of());
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(
            7L,
            9L,
            LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)
        )).thenReturn(java.util.List.of(assignmentUpdatedAt(LocalDateTime.of(2026, 3, 11, 8, 15))));
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of(role));
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L))
            .thenReturn(java.util.List.of(11L));
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenReturn(java.util.List.of(new PermissionVersionReadRepository.PermissionSnapshot("dict:type:view", true, 9L, LocalDateTime.of(2026, 3, 10, 9, 30))));
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L), 9L))
            .thenReturn(java.util.List.of());

        String baseline = service.resolvePermissionsVersion(7L, 9L);

        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(
            7L,
            9L,
            LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)
        )).thenReturn(java.util.List.of(assignmentUpdatedAt(LocalDateTime.of(2026, 3, 12, 8, 15))));

        String updated = service.resolvePermissionsVersion(7L, 9L);

        assertThat(updated).isNotEqualTo(baseline);
    }

    @Test
    void should_change_version_when_scoped_assignment_timestamp_changes_even_if_authorities_same() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionAuthorityReadRepository permissionAuthorityReadRepository = mock(PermissionAuthorityReadRepository.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service =
            new PermissionVersionService(
                tenantUserRepository,
                userUnitRepository,
                roleAssignmentRepository,
                effectiveRoleResolutionService,
                permissionAuthorityReadRepository,
                permissionVersionReadRepository,
                new PermissionRefactorObservabilityProperties(),
                fixedClock
            );

        TenantUser tenantUser = new TenantUser();
        tenantUser.setUpdatedAt(LocalDateTime.of(2026, 3, 10, 9, 30));
        Role role = roleWithCode("ROLE_ADMIN");

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.of(tenantUser));
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of());
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of(role));
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L))
            .thenReturn(java.util.List.of(11L));
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenReturn(java.util.List.of(new PermissionVersionReadRepository.PermissionSnapshot("dict:type:view", true, 9L, LocalDateTime.of(2026, 3, 10, 9, 30))));
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L), 9L))
            .thenReturn(java.util.List.of());
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(
            7L,
            9L,
            LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)
        )).thenReturn(java.util.List.of(scopedAssignmentUpdatedAt("DEPT", 200L, LocalDateTime.of(2026, 3, 11, 8, 15))));

        String baseline = service.resolvePermissionsVersion(7L, 9L);

        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(
            7L,
            9L,
            LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)
        )).thenReturn(java.util.List.of(scopedAssignmentUpdatedAt("DEPT", 200L, LocalDateTime.of(2026, 3, 12, 8, 15))));

        String updated = service.resolvePermissionsVersion(7L, 9L);

        assertThat(updated).isNotEqualTo(baseline);
    }

    @Test
    void should_change_version_when_user_unit_membership_changes_even_if_authorities_same() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionAuthorityReadRepository permissionAuthorityReadRepository = mock(PermissionAuthorityReadRepository.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service =
            new PermissionVersionService(
                tenantUserRepository,
                userUnitRepository,
                roleAssignmentRepository,
                effectiveRoleResolutionService,
                permissionAuthorityReadRepository,
                permissionVersionReadRepository,
                new PermissionRefactorObservabilityProperties(),
                fixedClock
            );

        TenantUser tenantUser = new TenantUser();
        tenantUser.setUpdatedAt(LocalDateTime.of(2026, 3, 10, 9, 30));
        Role role = roleWithCode("ROLE_ADMIN");
        UserUnit userUnit = new UserUnit();
        userUnit.setUpdatedAt(LocalDateTime.of(2026, 3, 11, 8, 15));

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.of(tenantUser));
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(
            7L,
            9L,
            LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)
        )).thenReturn(java.util.List.of());
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of(role));
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L))
            .thenReturn(java.util.List.of(11L));
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenReturn(java.util.List.of(new PermissionVersionReadRepository.PermissionSnapshot("dict:type:view", true, 9L, LocalDateTime.of(2026, 3, 10, 9, 30))));
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L), 9L))
            .thenReturn(java.util.List.of());
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of(userUnit));

        String baseline = service.resolvePermissionsVersion(7L, 9L);

        userUnit.setUpdatedAt(LocalDateTime.of(2026, 3, 12, 8, 15));
        String updated = service.resolvePermissionsVersion(7L, 9L);

        assertThat(updated).isNotEqualTo(baseline);
    }

    @Test
    void should_generate_different_versions_for_org_and_dept_active_scopes() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionAuthorityReadRepository permissionAuthorityReadRepository = mock(PermissionAuthorityReadRepository.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service =
            new PermissionVersionService(
                tenantUserRepository,
                userUnitRepository,
                roleAssignmentRepository,
                effectiveRoleResolutionService,
                permissionAuthorityReadRepository,
                permissionVersionReadRepository,
                new PermissionRefactorObservabilityProperties(),
                fixedClock
            );

        TenantUser tenantUser = new TenantUser();
        tenantUser.setUpdatedAt(LocalDateTime.of(2026, 3, 10, 9, 30));
        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.of(tenantUser));
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of());

        LocalDateTime now = LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC);
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(7L, 9L, now))
            .thenReturn(java.util.List.of(
                scopedAssignmentUpdatedAt("TENANT", 9L, LocalDateTime.of(2026, 3, 11, 8, 15)),
                scopedAssignmentUpdatedAt("ORG", 100L, LocalDateTime.of(2026, 3, 11, 8, 15)),
                scopedAssignmentUpdatedAt("DEPT", 200L, LocalDateTime.of(2026, 3, 11, 8, 15))
            ));

        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L, "ORG", 100L))
            .thenReturn(java.util.List.of(11L));
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L, "DEPT", 200L))
            .thenReturn(java.util.List.of(12L));

        when(permissionAuthorityReadRepository.findEnabledPermissionCodesByRoleIds(Set.of(11L), 9L))
            .thenReturn(Set.of("system:user:list"));
        when(permissionAuthorityReadRepository.findEnabledPermissionCodesByRoleIds(Set.of(12L), 9L))
            .thenReturn(Set.of("system:user:view"));
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenReturn(java.util.List.of());
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(12L), 9L))
            .thenReturn(java.util.List.of());
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L), 9L))
            .thenReturn(java.util.List.of());
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(12L), 9L))
            .thenReturn(java.util.List.of());

        String orgVersion = service.resolvePermissionsVersion(7L, 9L, "ORG", 100L);
        String deptVersion = service.resolvePermissionsVersion(7L, 9L, "DEPT", 200L);

        assertThat(orgVersion).hasSize(64);
        assertThat(deptVersion).hasSize(64);
        assertThat(deptVersion).isNotEqualTo(orgVersion);
    }

    @Test
    void should_change_version_when_role_permission_snapshot_changes() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service = new PermissionVersionService(
            tenantUserRepository, userUnitRepository, roleAssignmentRepository, effectiveRoleResolutionService, mock(PermissionAuthorityReadRepository.class), permissionVersionReadRepository, new PermissionRefactorObservabilityProperties(), fixedClock
        );

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.empty());
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of());
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(7L, 9L, LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)))
            .thenReturn(java.util.List.of());
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of());
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L))
            .thenReturn(java.util.List.of(11L));
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L), 9L))
            .thenReturn(java.util.List.of());
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenReturn(java.util.List.of(new PermissionVersionReadRepository.PermissionSnapshot("report:view", true, 9L, LocalDateTime.of(2026, 3, 10, 8, 0))));

        String baseline = service.resolvePermissionsVersion(7L, 9L);

        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenReturn(java.util.List.of(
                new PermissionVersionReadRepository.PermissionSnapshot("report:view", true, 9L, LocalDateTime.of(2026, 3, 10, 8, 0)),
                new PermissionVersionReadRepository.PermissionSnapshot("report:export", true, 9L, LocalDateTime.of(2026, 3, 10, 8, 0))
            ));

        String updated = service.resolvePermissionsVersion(7L, 9L);
        assertThat(updated).isNotEqualTo(baseline);
    }

    @Test
    void should_change_version_when_permission_enabled_changes() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service = new PermissionVersionService(
            tenantUserRepository, userUnitRepository, roleAssignmentRepository, effectiveRoleResolutionService, mock(PermissionAuthorityReadRepository.class), permissionVersionReadRepository, new PermissionRefactorObservabilityProperties(), fixedClock
        );

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.empty());
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of());
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(7L, 9L, LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)))
            .thenReturn(java.util.List.of());
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of());
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L))
            .thenReturn(java.util.List.of(11L));
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L), 9L))
            .thenReturn(java.util.List.of());
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenReturn(java.util.List.of(new PermissionVersionReadRepository.PermissionSnapshot("report:view", true, 9L, LocalDateTime.of(2026, 3, 10, 8, 0))));

        String baseline = service.resolvePermissionsVersion(7L, 9L);

        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenReturn(java.util.List.of(new PermissionVersionReadRepository.PermissionSnapshot("report:view", false, 9L, LocalDateTime.of(2026, 3, 10, 8, 0))));

        String updated = service.resolvePermissionsVersion(7L, 9L);
        assertThat(updated).isNotEqualTo(baseline);
    }

    @Test
    void should_not_change_version_when_disabled_permission_is_filtered_out() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service = new PermissionVersionService(
            tenantUserRepository, userUnitRepository, roleAssignmentRepository, effectiveRoleResolutionService, mock(PermissionAuthorityReadRepository.class), permissionVersionReadRepository, new PermissionRefactorObservabilityProperties(), fixedClock
        );

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.empty());
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of());
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(7L, 9L, LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)))
            .thenReturn(java.util.List.of());
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of());
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L))
            .thenReturn(java.util.List.of(11L));
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L), 9L))
            .thenReturn(java.util.List.of());

        java.util.concurrent.atomic.AtomicInteger callDisabledFilter = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.List<PermissionVersionReadRepository.PermissionSnapshot> disabledAt8ForFilter = java.util.List.of(
            new PermissionVersionReadRepository.PermissionSnapshot("report:view", false, 9L, LocalDateTime.of(2026, 3, 10, 8, 0))
        );
        java.util.List<PermissionVersionReadRepository.PermissionSnapshot> disabledAt9ForFilter = java.util.List.of(
            new PermissionVersionReadRepository.PermissionSnapshot("report:view", false, 9L, LocalDateTime.of(2026, 3, 10, 9, 0))
        );
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenAnswer(invocation -> callDisabledFilter.getAndIncrement() == 0 ? disabledAt8ForFilter : disabledAt9ForFilter);

        String baseline = service.resolvePermissionsVersion(7L, 9L);
        String updated = service.resolvePermissionsVersion(7L, 9L);

        // disabled permission should be excluded from the digest input entirely
        assertThat(updated).isEqualTo(baseline);
    }

    @Test
    void should_not_change_version_when_unknown_permission_code_is_filtered_out() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service = new PermissionVersionService(
            tenantUserRepository, userUnitRepository, roleAssignmentRepository, effectiveRoleResolutionService, mock(PermissionAuthorityReadRepository.class), permissionVersionReadRepository, new PermissionRefactorObservabilityProperties(), fixedClock
        );

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.empty());
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of());
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(7L, 9L, LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)))
            .thenReturn(java.util.List.of());
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of());
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L))
            .thenReturn(java.util.List.of(11L));
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L), 9L))
            .thenReturn(java.util.List.of());

        java.util.concurrent.atomic.AtomicInteger callUnknownFilter = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.List<PermissionVersionReadRepository.PermissionSnapshot> emptyForUnknown = java.util.List.of();
        java.util.List<PermissionVersionReadRepository.PermissionSnapshot> unknownBlankForUnknown = java.util.List.of(
            new PermissionVersionReadRepository.PermissionSnapshot("   ", true, 9L, LocalDateTime.of(2026, 3, 10, 9, 0))
        );
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenAnswer(invocation -> callUnknownFilter.getAndIncrement() == 0 ? emptyForUnknown : unknownBlankForUnknown);

        String baseline = service.resolvePermissionsVersion(7L, 9L);
        String updated = service.resolvePermissionsVersion(7L, 9L);

        // blank/unknown permission_code should not be included in digest input
        assertThat(updated).isEqualTo(baseline);
    }

    @Test
    void should_change_version_when_permission_enabled_toggles_from_false_to_true() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service = new PermissionVersionService(
            tenantUserRepository, userUnitRepository, roleAssignmentRepository, effectiveRoleResolutionService, mock(PermissionAuthorityReadRepository.class), permissionVersionReadRepository, new PermissionRefactorObservabilityProperties(), fixedClock
        );

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.empty());
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of());
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(7L, 9L, LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)))
            .thenReturn(java.util.List.of());
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of());
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L))
            .thenReturn(java.util.List.of(11L));
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L), 9L))
            .thenReturn(java.util.List.of());

        java.util.concurrent.atomic.AtomicInteger callToggle = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.List<PermissionVersionReadRepository.PermissionSnapshot> disabledAt8ForToggle = java.util.List.of(
            new PermissionVersionReadRepository.PermissionSnapshot("report:view", false, 9L, LocalDateTime.of(2026, 3, 10, 8, 0))
        );
        java.util.List<PermissionVersionReadRepository.PermissionSnapshot> enabledAt9ForToggle = java.util.List.of(
            new PermissionVersionReadRepository.PermissionSnapshot("report:view", true, 9L, LocalDateTime.of(2026, 3, 10, 9, 0))
        );
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenAnswer(invocation -> callToggle.getAndIncrement() == 0 ? disabledAt8ForToggle : enabledAt9ForToggle);

        String baseline = service.resolvePermissionsVersion(7L, 9L);
        String updated = service.resolvePermissionsVersion(7L, 9L);

        assertThat(updated).isNotEqualTo(baseline);
    }

    @Test
    void should_change_version_when_permission_enabled_toggles_from_true_to_false() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service = new PermissionVersionService(
            tenantUserRepository, userUnitRepository, roleAssignmentRepository, effectiveRoleResolutionService, mock(PermissionAuthorityReadRepository.class), permissionVersionReadRepository, new PermissionRefactorObservabilityProperties(), fixedClock
        );

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.empty());
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of());
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(7L, 9L, LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)))
            .thenReturn(java.util.List.of());
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of());
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L))
            .thenReturn(java.util.List.of(11L));
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L), 9L))
            .thenReturn(java.util.List.of());

        java.util.concurrent.atomic.AtomicInteger callToggle = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.List<PermissionVersionReadRepository.PermissionSnapshot> enabledAt9ForToggle = java.util.List.of(
            new PermissionVersionReadRepository.PermissionSnapshot("report:view", true, 9L, LocalDateTime.of(2026, 3, 10, 9, 0))
        );
        java.util.List<PermissionVersionReadRepository.PermissionSnapshot> disabledAt8ForToggle = java.util.List.of(
            new PermissionVersionReadRepository.PermissionSnapshot("report:view", false, 9L, LocalDateTime.of(2026, 3, 10, 8, 0))
        );
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenAnswer(invocation -> callToggle.getAndIncrement() == 0 ? enabledAt9ForToggle : disabledAt8ForToggle);

        String baseline = service.resolvePermissionsVersion(7L, 9L);
        String updated = service.resolvePermissionsVersion(7L, 9L);

        assertThat(updated).isNotEqualTo(baseline);
    }

    @Test
    void should_stay_stable_when_all_permission_inputs_filtered_out_to_empty() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service = new PermissionVersionService(
            tenantUserRepository, userUnitRepository, roleAssignmentRepository, effectiveRoleResolutionService, mock(PermissionAuthorityReadRepository.class), permissionVersionReadRepository, new PermissionRefactorObservabilityProperties(), fixedClock
        );

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.empty());
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of());
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(7L, 9L, LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)))
            .thenReturn(java.util.List.of());
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of());
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L))
            .thenReturn(java.util.List.of(11L));
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L), 9L))
            .thenReturn(java.util.List.of());

        // baseline: all snapshots are either disabled or blank permissionCode => filtered input should be empty
        java.util.List<PermissionVersionReadRepository.PermissionSnapshot> allFilteredAt8 = java.util.List.of(
            new PermissionVersionReadRepository.PermissionSnapshot("report:view", false, 9L, LocalDateTime.of(2026, 3, 10, 8, 0)),
            new PermissionVersionReadRepository.PermissionSnapshot("   ", true, 9L, LocalDateTime.of(2026, 3, 10, 8, 30))
        );
        // updated: even if updatedAt changes, since everything is filtered out, digest input remains empty
        java.util.List<PermissionVersionReadRepository.PermissionSnapshot> allFilteredAt9 = java.util.List.of(
            new PermissionVersionReadRepository.PermissionSnapshot("report:view", false, 9L, LocalDateTime.of(2026, 3, 10, 9, 0)),
            new PermissionVersionReadRepository.PermissionSnapshot("   ", true, 9L, LocalDateTime.of(2026, 3, 10, 9, 30))
        );

        java.util.concurrent.atomic.AtomicInteger callAllFiltered = new java.util.concurrent.atomic.AtomicInteger(0);
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L), 9L))
            .thenAnswer(invocation -> callAllFiltered.getAndIncrement() == 0 ? allFilteredAt8 : allFilteredAt9);

        String baseline = service.resolvePermissionsVersion(7L, 9L);
        String updated = service.resolvePermissionsVersion(7L, 9L);

        assertThat(updated).isEqualTo(baseline);
    }

    @Test
    void should_change_version_when_role_hierarchy_snapshot_changes() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service = new PermissionVersionService(
            tenantUserRepository, userUnitRepository, roleAssignmentRepository, effectiveRoleResolutionService, mock(PermissionAuthorityReadRepository.class), permissionVersionReadRepository, new PermissionRefactorObservabilityProperties(), fixedClock
        );

        when(tenantUserRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.Optional.empty());
        when(userUnitRepository.findByTenantIdAndUserIdAndStatus(9L, 7L, "ACTIVE"))
            .thenReturn(java.util.List.of());
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(7L, 9L, LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)))
            .thenReturn(java.util.List.of());
        when(effectiveRoleResolutionService.findEffectiveRolesForUserInTenant(7L, 9L))
            .thenReturn(Set.of());
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInTenant(7L, 9L))
            .thenReturn(java.util.List.of(11L, 12L));
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L, 12L), 9L))
            .thenReturn(java.util.List.of());
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L, 12L), 9L))
            .thenReturn(java.util.List.of(
                new PermissionVersionReadRepository.RoleHierarchySnapshot(12L, 11L, 9L, LocalDateTime.of(2026, 3, 10, 8, 0))
            ));

        String baseline = service.resolvePermissionsVersion(7L, 9L);

        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L, 12L), 9L))
            .thenReturn(java.util.List.of(
                new PermissionVersionReadRepository.RoleHierarchySnapshot(12L, 11L, 9L, LocalDateTime.of(2026, 3, 11, 8, 0))
            ));

        String updated = service.resolvePermissionsVersion(7L, 9L);
        assertThat(updated).isNotEqualTo(baseline);
    }

    @Test
    void should_change_platform_version_when_hierarchy_snapshot_changes() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        PermissionVersionReadRepository permissionVersionReadRepository = mock(PermissionVersionReadRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-12T00:00:00Z"), ZoneOffset.UTC);
        PermissionVersionService service = new PermissionVersionService(
            tenantUserRepository, userUnitRepository, roleAssignmentRepository, effectiveRoleResolutionService, mock(PermissionAuthorityReadRepository.class), permissionVersionReadRepository, new PermissionRefactorObservabilityProperties(), fixedClock
        );

        when(roleAssignmentRepository.findLatestUpdatedAtForActiveUserInPlatform(7L, LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)))
            .thenReturn(LocalDateTime.of(2026, 3, 11, 8, 0));
        when(roleAssignmentRepository.findActiveRoleIdsForUserInPlatform(7L, LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC)))
            .thenReturn(java.util.List.of(11L));
        when(effectiveRoleResolutionService.findEffectiveRoleIdsForUserInPlatform(7L))
            .thenReturn(java.util.List.of(11L, 12L));
        when(permissionVersionReadRepository.findPermissionSnapshotsByRoleIds(Set.of(11L, 12L), null))
            .thenReturn(java.util.List.of());
        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L, 12L), null))
            .thenReturn(java.util.List.of(
                new PermissionVersionReadRepository.RoleHierarchySnapshot(12L, 11L, null, LocalDateTime.of(2026, 3, 10, 8, 0))
            ));

        String baseline = service.resolvePermissionsVersion(7L, null, "PLATFORM", null);

        when(permissionVersionReadRepository.findRoleHierarchySnapshotsByRoleIds(java.util.List.of(11L, 12L), null))
            .thenReturn(java.util.List.of(
                new PermissionVersionReadRepository.RoleHierarchySnapshot(12L, 11L, null, LocalDateTime.of(2026, 3, 11, 8, 0))
            ));

        String updated = service.resolvePermissionsVersion(7L, null, "PLATFORM", null);
        assertThat(updated).isNotEqualTo(baseline);
    }

    private Role roleWithCode(String roleCode) {
        Role role = new Role();
        role.setCode(roleCode);
        return role;
    }

    private RoleAssignment assignmentUpdatedAt(LocalDateTime updatedAt) {
        return scopedAssignmentUpdatedAt("TENANT", 9L, updatedAt);
    }

    private RoleAssignment scopedAssignmentUpdatedAt(String scopeType, Long scopeId, LocalDateTime updatedAt) {
        RoleAssignment assignment = new RoleAssignment();
        assignment.setScopeType(scopeType);
        assignment.setScopeId(scopeId);
        assignment.setUpdatedAt(updatedAt);
        return assignment;
    }
}
