package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.resource.repository.ResourceRepository;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleServiceImplTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getUserIdsByRoleId_should_prefer_role_assignment_view() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                mock(ResourceRepository.class),
                mock(TenantUserRepository.class),
                roleAssignmentSyncService,
                effectiveRoleResolutionService,
                mock(RoleConstraintService.class)
        );

        TenantContext.setActiveTenantId(1L);
        when(effectiveRoleResolutionService.findEffectiveUserIdsForRoleInTenant(7L, 1L)).thenReturn(List.of(3L, 4L));

        List<Long> result = service.getUserIdsByRoleId(7L);

        assertThat(result).containsExactly(3L, 4L);
        verify(effectiveRoleResolutionService).findEffectiveUserIdsForRoleInTenant(7L, 1L);
    }

    @Test
    void updateRoleUsers_should_sync_role_assignments_using_tenant_membership() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                mock(ResourceRepository.class),
                tenantUserRepository,
                roleAssignmentSyncService,
                effectiveRoleResolutionService,
                mock(RoleConstraintService.class)
        );

        TenantContext.setActiveTenantId(1L);

        Role role = new Role();
        role.setId(9L);

        when(roleRepository.findByIdAndTenantId(9L, 1L)).thenReturn(Optional.of(role));
        when(tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(1L, java.util.Set.of(12L), "ACTIVE"))
                .thenReturn(List.of(12L));

        service.updateRoleUsers(9L, List.of(12L));

        verify(tenantUserRepository).findUserIdsByTenantIdAndUserIdInAndStatus(1L, java.util.Set.of(12L), "ACTIVE");
        verify(roleAssignmentSyncService).replaceRoleTenantUserAssignments(9L, 1L, List.of(12L));
    }
}
