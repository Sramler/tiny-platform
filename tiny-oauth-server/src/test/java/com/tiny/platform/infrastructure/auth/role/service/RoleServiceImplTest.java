package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.resource.repository.CarrierProjectionRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.RoleResourcePermissionBindingView;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.anyLong;
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
        CarrierProjectionRepository carrierProjectionRepository = mock(CarrierProjectionRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                carrierProjectionRepository,
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
        CarrierProjectionRepository carrierProjectionRepository = mock(CarrierProjectionRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                carrierProjectionRepository,
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
        CarrierProjectionRepository carrierProjectionRepository = mock(CarrierProjectionRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        RoleAssignmentSyncService roleAssignmentSyncService = mock(RoleAssignmentSyncService.class);
        EffectiveRoleResolutionService effectiveRoleResolutionService = mock(EffectiveRoleResolutionService.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                carrierProjectionRepository,
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
        CarrierProjectionRepository carrierProjectionRepository = mock(CarrierProjectionRepository.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                carrierProjectionRepository,
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
    void updateRoleResources_should_write_role_permission_only_when_platform_scope() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        CarrierProjectionRepository carrierProjectionRepository = mock(CarrierProjectionRepository.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                carrierProjectionRepository,
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
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(12L), null, "PLATFORM"))
            .thenReturn(List.of(binding(12L, null, 3001L)));

        service.updateRoleResources(9L, List.of(12L));

        verify(roleRepository).deleteRolePermissionRelations(9L, null);
        verify(roleRepository, times(1)).addRolePermissionRelationByPermissionId(null, 9L, 3001L);
    }

    @Test
    void updateRoleResources_should_write_role_permission_only_when_tenant_scope() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        CarrierProjectionRepository carrierProjectionRepository = mock(CarrierProjectionRepository.class);
        RoleServiceImpl service = new RoleServiceImpl(
                roleRepository,
                carrierProjectionRepository,
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
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(88L), 2L, "TENANT"))
            .thenReturn(List.of(binding(88L, null, 4001L)));

        service.updateRoleResources(7L, List.of(88L));

        verify(roleRepository).deleteRolePermissionRelations(7L, 2L);
        verify(roleRepository, times(1)).addRolePermissionRelationByPermissionId(2L, 7L, 4001L);
    }

    @Test
    void updateRoleResources_should_fail_closed_when_permission_binding_missing() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        CarrierProjectionRepository carrierProjectionRepository = mock(CarrierProjectionRepository.class);
        RoleServiceImpl service = new RoleServiceImpl(
            roleRepository,
            carrierProjectionRepository,
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
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(88L), 2L, "TENANT"))
            .thenReturn(List.of(binding(88L, "system:user:list", null)));

        assertThatThrownBy(() -> service.updateRoleResources(7L, List.of(88L)))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.BUSINESS_ERROR);

        verify(roleRepository, never()).deleteRolePermissionRelations(7L, 2L);
        verify(roleRepository, never()).addRolePermissionRelationByPermissionId(anyLong(), anyLong(), anyLong());
    }

    @Test
    void updateRoleResources_should_fail_closed_when_resource_out_of_scope_or_missing() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        CarrierProjectionRepository carrierProjectionRepository = mock(CarrierProjectionRepository.class);
        RoleServiceImpl service = new RoleServiceImpl(
            roleRepository,
            carrierProjectionRepository,
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
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(88L, 89L), 2L, "TENANT"))
            .thenReturn(List.of(binding(88L, null, 4001L)));

        assertThatThrownBy(() -> service.updateRoleResources(7L, List.of(88L, 89L)))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.BUSINESS_ERROR);

        verify(roleRepository, never()).deleteRolePermissionRelations(7L, 2L);
    }

    @Test
    void updateRoleResources_should_report_missing_resource_ids_in_fail_closed_message() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        CarrierProjectionRepository carrierProjectionRepository = mock(CarrierProjectionRepository.class);
        RoleServiceImpl service = new RoleServiceImpl(
            roleRepository,
            carrierProjectionRepository,
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
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(88L, 89L), 2L, "TENANT"))
            .thenReturn(List.of(binding(88L, null, 4001L)));

        assertThatThrownBy(() -> service.updateRoleResources(7L, List.of(88L, 89L)))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("89")
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.BUSINESS_ERROR);

        verify(roleRepository, never()).deleteRolePermissionRelations(7L, 2L);
    }

    @Test
    void updateRoleResources_should_deduplicate_same_permission_across_multiple_carriers() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        CarrierProjectionRepository carrierProjectionRepository = mock(CarrierProjectionRepository.class);
        RoleServiceImpl service = new RoleServiceImpl(
            roleRepository,
            carrierProjectionRepository,
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
        when(carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(List.of(88L, 89L), 2L, "TENANT"))
            .thenReturn(List.of(
                binding(88L, "system:user:list", 4001L),
                binding(89L, "system:user:list:alt", 4001L)
            ));

        service.updateRoleResources(7L, List.of(88L, 89L));

        verify(roleRepository).deleteRolePermissionRelations(7L, 2L);
        verify(roleRepository, times(1)).addRolePermissionRelationByPermissionId(2L, 7L, 4001L);
    }

    private RoleResourcePermissionBindingView binding(Long id, String permission, Long requiredPermissionId) {
        return new RoleResourcePermissionBindingView() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public String getPermission() {
                return permission;
            }

            @Override
            public Long getRequiredPermissionId() {
                return requiredPermissionId;
            }
        };
    }
}
