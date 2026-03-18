package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EffectiveRoleResolutionServiceTest {

    @Test
    void findEffectiveRoleIdsForUserInTenant_should_prefer_assignments() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService service = new EffectiveRoleResolutionService(
                roleRepository,
                roleAssignmentSyncService,
                roleAssignmentRepository
        );

        when(roleAssignmentSyncService.findActiveRoleIdsForUserInTenant(5L, 1L)).thenReturn(List.of(10L, 11L));

        List<Long> result = service.findEffectiveRoleIdsForUserInTenant(5L, 1L);

        assertThat(result).containsExactly(10L, 11L);
        verify(roleAssignmentSyncService).findActiveRoleIdsForUserInTenant(5L, 1L);
        verifyNoInteractions(roleRepository);
    }

    @Test
    void findEffectiveRolesForUserInTenant_should_return_empty_when_assignments_absent() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService service = new EffectiveRoleResolutionService(
                roleRepository,
                roleAssignmentSyncService,
                roleAssignmentRepository
        );

        when(roleAssignmentSyncService.findActiveRoleIdsForUserInTenant(6L, 2L)).thenReturn(List.of());

        Set<Role> result = service.findEffectiveRolesForUserInTenant(6L, 2L);

        assertThat(result).isEmpty();
        verify(roleAssignmentSyncService).findActiveRoleIdsForUserInTenant(6L, 2L);
        verifyNoInteractions(roleRepository);
    }

    @Test
    void findEffectiveUserIdsForRoleInTenant_should_return_empty_when_assignments_absent() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        EffectiveRoleResolutionService service = new EffectiveRoleResolutionService(
                roleRepository,
                roleAssignmentSyncService,
                roleAssignmentRepository
        );

        when(roleAssignmentSyncService.findActiveUserIdsForRoleInTenant(8L, 3L)).thenReturn(List.of());

        List<Long> result = service.findEffectiveUserIdsForRoleInTenant(8L, 3L);

        assertThat(result).isEmpty();
        verify(roleAssignmentSyncService).findActiveUserIdsForRoleInTenant(8L, 3L);
        verifyNoInteractions(roleRepository);
    }
}
