package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
                tenantUserRepository,
                roleAssignmentSyncService,
                effectiveRoleResolutionService,
                mock(RoleConstraintService.class)
        );

        TenantContext.setActiveTenantId(1L);

        Role role = new Role();
        role.setId(9L);
        role.setTenantId(1L);
        role.setRoleLevel("TENANT");

        when(roleRepository.findById(9L)).thenReturn(Optional.of(role));
        when(tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(1L, java.util.Set.of(12L), "ACTIVE"))
                .thenReturn(List.of(12L));

        service.updateRoleUsers(9L, List.of(12L));

        verify(tenantUserRepository).findUserIdsByTenantIdAndUserIdInAndStatus(1L, java.util.Set.of(12L), "ACTIVE");
        verify(roleAssignmentSyncService).replaceRoleScopedUserAssignments(9L, 1L, "TENANT", null, List.of(12L));
    }

    @Test
    void updateRoleUsers_should_support_dept_scope_assignments() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                tenantUserRepository,
                roleAssignmentSyncService,
                effectiveRoleResolutionService,
                mock(RoleConstraintService.class)
        );

        TenantContext.setActiveTenantId(1L);

        Role role = new Role();
        role.setId(9L);
        role.setTenantId(1L);
        role.setRoleLevel("TENANT");

        when(roleRepository.findById(9L)).thenReturn(Optional.of(role));
        when(tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(1L, java.util.Set.of(12L), "ACTIVE"))
                .thenReturn(List.of(12L));

        service.updateRoleUsers(9L, "DEPT", 200L, List.of(12L));

        verify(roleAssignmentSyncService).replaceRoleScopedUserAssignments(9L, 1L, "DEPT", 200L, List.of(12L));
    }

    @Test
    void create_should_write_platform_template_when_platform_scope() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                mock(TenantUserRepository.class),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                mock(RoleConstraintService.class)
        );

        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        RoleCreateUpdateDto dto = new RoleCreateUpdateDto();
        dto.setCode("ROLE_PLATFORM_TEMPLATE");
        dto.setName("平台模板角色");

        when(roleRepository.save(org.mockito.ArgumentMatchers.any(Role.class))).thenAnswer(invocation -> {
            Role saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        var response = service.create(dto);

        assertThat(response.getId()).isEqualTo(99L);
        org.mockito.ArgumentCaptor<Role> captor = org.mockito.ArgumentCaptor.forClass(Role.class);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isNull();
        assertThat(captor.getValue().getRoleLevel()).isEqualTo("PLATFORM");
    }

    @Test
    void updateRolePermissions_should_write_when_platform_scope() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                mock(TenantUserRepository.class),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                mock(RoleConstraintService.class)
        );

        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        Role role = new Role();
        role.setId(9L);
        role.setTenantId(null);
        role.setRoleLevel("PLATFORM");

        when(roleRepository.findById(9L)).thenReturn(Optional.of(role));

        service.updateRolePermissions(9L, List.of(3001L));

        verify(roleRepository).deleteRolePermissionRelations(9L, null);
        verify(roleRepository, times(1)).addRolePermissionRelationByPermissionId(null, 9L, 3001L);
    }

    @Test
    void updateRolePermissions_should_write_when_tenant_scope() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                mock(TenantUserRepository.class),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                mock(RoleConstraintService.class)
        );

        TenantContext.setActiveTenantId(2L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        Role role = new Role();
        role.setId(7L);
        role.setTenantId(2L);
        role.setRoleLevel("TENANT");

        when(roleRepository.findById(7L)).thenReturn(Optional.of(role));

        service.updateRolePermissions(7L, List.of(4001L));

        verify(roleRepository).deleteRolePermissionRelations(7L, 2L);
        verify(roleRepository, times(1)).addRolePermissionRelationByPermissionId(2L, 7L, 4001L);
    }

    @Test
    void updateRolePermissions_should_deduplicate_permission_ids() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                mock(TenantUserRepository.class),
                mock(RoleAssignmentSyncService.class),
                mock(EffectiveRoleResolutionService.class),
                mock(RoleConstraintService.class)
        );

        TenantContext.setActiveTenantId(2L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        Role role = new Role();
        role.setId(7L);
        role.setTenantId(2L);
        role.setRoleLevel("TENANT");

        when(roleRepository.findById(7L)).thenReturn(Optional.of(role));

        service.updateRolePermissions(7L, List.of(4001L, 4001L));

        verify(roleRepository).deleteRolePermissionRelations(7L, 2L);
        verify(roleRepository, times(1)).addRolePermissionRelationByPermissionId(2L, 7L, 4001L);
    }
}
