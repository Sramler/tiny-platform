package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.domain.RoleHierarchy;
import com.tiny.platform.infrastructure.auth.role.repository.RoleHierarchyRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EffectiveRoleResolutionServiceTest {

    @Test
    void findEffectiveRoleIdsForUserInTenant_should_prefer_assignments() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        RoleHierarchyRepository roleHierarchyRepository = mock(RoleHierarchyRepository.class);
        EffectiveRoleResolutionService service = new EffectiveRoleResolutionService(
                roleRepository,
                roleAssignmentSyncService,
                roleHierarchyRepository
        );

        when(roleAssignmentSyncService.findActiveRoleIdsForUserInScope(5L, 1L, "TENANT", 1L)).thenReturn(List.of(10L, 11L));
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(1L, Set.of(10L, 11L))).thenReturn(List.of());

        List<Long> result = service.findEffectiveRoleIdsForUserInTenant(5L, 1L);

        assertThat(result).containsExactly(10L, 11L);
        verify(roleAssignmentSyncService).findActiveRoleIdsForUserInScope(5L, 1L, "TENANT", 1L);
        verify(roleHierarchyRepository).findByTenantIdAndChildRoleIdIn(1L, Set.of(10L, 11L));
        verifyNoInteractions(roleRepository);
    }

    @Test
    void findEffectiveRoleIdsForUserInTenant_should_expandSingleLevelHierarchy() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        RoleHierarchyRepository roleHierarchyRepository = mock(RoleHierarchyRepository.class);
        EffectiveRoleResolutionService service = new EffectiveRoleResolutionService(
                roleRepository,
                roleAssignmentSyncService,
                roleHierarchyRepository
        );

        when(roleAssignmentSyncService.findActiveRoleIdsForUserInScope(5L, 1L, "TENANT", 1L)).thenReturn(List.of(10L));
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(1L, Set.of(10L)))
                .thenReturn(List.of(hierarchy(1L, 20L, 10L)));
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(1L, Set.of(20L)))
                .thenReturn(List.of());

        List<Long> result = service.findEffectiveRoleIdsForUserInTenant(5L, 1L);

        assertThat(result).containsExactly(10L, 20L);
    }

    @Test
    void findEffectiveRoleIdsForUserInTenant_should_expandMultiLevelHierarchy() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        RoleHierarchyRepository roleHierarchyRepository = mock(RoleHierarchyRepository.class);
        EffectiveRoleResolutionService service = new EffectiveRoleResolutionService(
                roleRepository,
                roleAssignmentSyncService,
                roleHierarchyRepository
        );

        when(roleAssignmentSyncService.findActiveRoleIdsForUserInScope(7L, 2L, "TENANT", 2L)).thenReturn(List.of(10L));
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(2L, Set.of(10L)))
                .thenReturn(List.of(hierarchy(2L, 20L, 10L)));
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(2L, Set.of(20L)))
                .thenReturn(List.of(hierarchy(2L, 30L, 20L)));
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(2L, Set.of(30L)))
                .thenReturn(List.of());

        List<Long> result = service.findEffectiveRoleIdsForUserInTenant(7L, 2L);

        assertThat(result).containsExactly(10L, 20L, 30L);
    }

    @Test
    void findEffectiveRoleIdsForUserInTenant_should_stopOnCyclesWithoutLoopingForever() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        RoleHierarchyRepository roleHierarchyRepository = mock(RoleHierarchyRepository.class);
        EffectiveRoleResolutionService service = new EffectiveRoleResolutionService(
                roleRepository,
                roleAssignmentSyncService,
                roleHierarchyRepository
        );

        when(roleAssignmentSyncService.findActiveRoleIdsForUserInScope(9L, 3L, "TENANT", 3L)).thenReturn(List.of(10L));
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(3L, Set.of(10L)))
                .thenReturn(List.of(hierarchy(3L, 20L, 10L)));
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(3L, Set.of(20L)))
                .thenReturn(List.of(hierarchy(3L, 10L, 20L)));

        List<Long> result = service.findEffectiveRoleIdsForUserInTenant(9L, 3L);

        assertThat(result).containsExactly(10L, 20L);
        verify(roleHierarchyRepository).findByTenantIdAndChildRoleIdIn(3L, Set.of(10L));
        verify(roleHierarchyRepository).findByTenantIdAndChildRoleIdIn(3L, Set.of(20L));
    }

    @Test
    void findEffectiveRolesForUserInTenant_should_return_empty_when_assignments_absent() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        RoleHierarchyRepository roleHierarchyRepository = mock(RoleHierarchyRepository.class);
        EffectiveRoleResolutionService service = new EffectiveRoleResolutionService(
                roleRepository,
                roleAssignmentSyncService,
                roleHierarchyRepository
        );

        when(roleAssignmentSyncService.findActiveRoleIdsForUserInScope(6L, 2L, "TENANT", 2L)).thenReturn(List.of());

        Set<Role> result = service.findEffectiveRolesForUserInTenant(6L, 2L);

        assertThat(result).isEmpty();
        verify(roleAssignmentSyncService).findActiveRoleIdsForUserInScope(6L, 2L, "TENANT", 2L);
        verifyNoInteractions(roleRepository);
    }

    @Test
    void findEffectiveRolesForUserInTenant_should_loadExpandedRoles() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        RoleHierarchyRepository roleHierarchyRepository = mock(RoleHierarchyRepository.class);
        EffectiveRoleResolutionService service = new EffectiveRoleResolutionService(
                roleRepository,
                roleAssignmentSyncService,
                roleHierarchyRepository
        );
        Role role10 = new Role();
        role10.setId(10L);
        Role role20 = new Role();
        role20.setId(20L);

        when(roleAssignmentSyncService.findActiveRoleIdsForUserInScope(6L, 2L, "TENANT", 2L)).thenReturn(List.of(10L));
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(2L, Set.of(10L)))
                .thenReturn(List.of(hierarchy(2L, 20L, 10L)));
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(2L, Set.of(20L)))
                .thenReturn(List.of());
        when(roleRepository.findByIdInAndTenantIdOrderByIdAsc(eq(List.of(10L, 20L)), eq(2L)))
                .thenReturn(List.of(role10, role20));

        Set<Role> result = service.findEffectiveRolesForUserInTenant(6L, 2L);

        assertThat(result).containsExactly(role10, role20);
        verify(roleRepository).findByIdInAndTenantIdOrderByIdAsc(List.of(10L, 20L), 2L);
    }

    @Test
    void findEffectiveUserIdsForRoleInTenant_should_return_empty_when_assignments_absent() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        RoleHierarchyRepository roleHierarchyRepository = mock(RoleHierarchyRepository.class);
        EffectiveRoleResolutionService service = new EffectiveRoleResolutionService(
                roleRepository,
                roleAssignmentSyncService,
                roleHierarchyRepository
        );

        when(roleHierarchyRepository.findByTenantIdAndParentRoleIdIn(3L, Set.of(8L))).thenReturn(List.of());
        when(roleAssignmentSyncService.findActiveUserIdsForRolesInTenant(Set.of(8L), 3L)).thenReturn(List.of());

        List<Long> result = service.findEffectiveUserIdsForRoleInTenant(8L, 3L);

        assertThat(result).isEmpty();
        verify(roleHierarchyRepository).findByTenantIdAndParentRoleIdIn(3L, Set.of(8L));
        verify(roleAssignmentSyncService).findActiveUserIdsForRolesInTenant(Set.of(8L), 3L);
        verifyNoInteractions(roleRepository);
    }

    @Test
    void findEffectiveUserIdsForRoleInTenant_should_include_descendant_role_assignments() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        RoleHierarchyRepository roleHierarchyRepository = mock(RoleHierarchyRepository.class);
        EffectiveRoleResolutionService service = new EffectiveRoleResolutionService(
            roleRepository,
            roleAssignmentSyncService,
            roleHierarchyRepository
        );

        when(roleHierarchyRepository.findByTenantIdAndParentRoleIdIn(4L, Set.of(10L)))
            .thenReturn(List.of(hierarchy(4L, 10L, 20L)));
        when(roleHierarchyRepository.findByTenantIdAndParentRoleIdIn(4L, Set.of(20L)))
            .thenReturn(List.of(hierarchy(4L, 20L, 30L)));
        when(roleHierarchyRepository.findByTenantIdAndParentRoleIdIn(4L, Set.of(30L)))
            .thenReturn(List.of());
        when(roleAssignmentSyncService.findActiveUserIdsForRolesInTenant(Set.of(10L, 20L, 30L), 4L))
            .thenReturn(List.of(3L, 4L, 5L));

        List<Long> result = service.findEffectiveUserIdsForRoleInTenant(10L, 4L);

        assertThat(result).containsExactly(3L, 4L, 5L);
        verify(roleAssignmentSyncService).findActiveUserIdsForRolesInTenant(Set.of(10L, 20L, 30L), 4L);
    }

    @Test
    void findEffectiveUserIdsForRoleInTenant_should_stopOnCyclesWhenExpandingChildren() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        RoleHierarchyRepository roleHierarchyRepository = mock(RoleHierarchyRepository.class);
        EffectiveRoleResolutionService service = new EffectiveRoleResolutionService(
            roleRepository,
            roleAssignmentSyncService,
            roleHierarchyRepository
        );

        when(roleHierarchyRepository.findByTenantIdAndParentRoleIdIn(5L, Set.of(10L)))
            .thenReturn(List.of(hierarchy(5L, 10L, 20L)));
        when(roleHierarchyRepository.findByTenantIdAndParentRoleIdIn(5L, Set.of(20L)))
            .thenReturn(List.of(hierarchy(5L, 20L, 10L)));
        when(roleAssignmentSyncService.findActiveUserIdsForRolesInTenant(Set.of(10L, 20L), 5L))
            .thenReturn(List.of(9L));

        List<Long> result = service.findEffectiveUserIdsForRoleInTenant(10L, 5L);

        assertThat(result).containsExactly(9L);
        verify(roleHierarchyRepository).findByTenantIdAndParentRoleIdIn(5L, Set.of(10L));
        verify(roleHierarchyRepository).findByTenantIdAndParentRoleIdIn(5L, Set.of(20L));
        verify(roleAssignmentSyncService).findActiveUserIdsForRolesInTenant(Set.of(10L, 20L), 5L);
    }

    private RoleHierarchy hierarchy(Long tenantId, Long parentRoleId, Long childRoleId) {
        RoleHierarchy edge = new RoleHierarchy();
        edge.setTenantId(tenantId);
        edge.setParentRoleId(parentRoleId);
        edge.setChildRoleId(childRoleId);
        return edge;
    }
}
