package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.RoleAssignment;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.user.domain.TenantUser;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("nullness")
class RoleAssignmentSyncServiceTest {

    @Test
    void ensureTenantMembership_should_activate_existing_membership() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        RoleConstraintService roleConstraintService = mock(RoleConstraintService.class);
        RoleAssignmentSyncService service = new RoleAssignmentSyncService(
            tenantUserRepository,
            roleAssignmentRepository,
            roleConstraintService
        );

        TenantUser existing = new TenantUser();
        existing.setTenantId(1L);
        existing.setUserId(2L);
        existing.setStatus("LEFT");
        existing.setIsDefault(false);
        when(tenantUserRepository.findByTenantIdAndUserId(1L, 2L)).thenReturn(Optional.of(existing));

        service.ensureTenantMembership(2L, 1L, true);

        verify(tenantUserRepository).save(existing);
        verify(roleConstraintService, never()).validateAssignmentsBeforeGrant(any(), any(), any(), any(), any(), any());
        assertThat(existing.getStatus()).isEqualTo("ACTIVE");
        assertThat(existing.getIsDefault()).isTrue();
        assertThat(existing.getLeftAt()).isNull();
        assertThat(existing.getLastActivatedAt()).isNotNull();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void replaceUserTenantRoleAssignments_should_replace_assignments_with_distinct_roles() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        RoleConstraintService roleConstraintService = mock(RoleConstraintService.class);
        RoleAssignmentSyncService service = new RoleAssignmentSyncService(
            tenantUserRepository,
            roleAssignmentRepository,
            roleConstraintService
        );

        when(tenantUserRepository.findByTenantIdAndUserId(1L, 5L)).thenReturn(Optional.empty());

        service.replaceUserTenantRoleAssignments(5L, 1L, List.of(10L, 10L, 11L));

        verify(tenantUserRepository).save((TenantUser) any());
        verify(roleConstraintService).validateAssignmentsBeforeGrant(
            "USER",
            5L,
            1L,
            "TENANT",
            1L,
            List.of(10L, 10L, 11L)
        );
        verify(roleAssignmentRepository).deleteUserAssignmentsInTenant(5L, 1L);
        ArgumentCaptor<List<RoleAssignment>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(roleAssignmentRepository).saveAll((Iterable<RoleAssignment>) captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue())
                .extracting(RoleAssignment::getRoleId)
                .containsExactly(10L, 11L);
        assertThat(captor.getValue())
                .allSatisfy(assignment -> {
                    assertThat(assignment.getPrincipalType()).isEqualTo("USER");
                    assertThat(assignment.getPrincipalId()).isEqualTo(5L);
                    assertThat(assignment.getTenantId()).isEqualTo(1L);
                    assertThat(assignment.getScopeType()).isEqualTo("TENANT");
                    assertThat(assignment.getScopeId()).isEqualTo(1L);
                    assertThat(assignment.getStatus()).isEqualTo("ACTIVE");
                    assertThat(assignment.getGrantedAt()).isNotNull();
                });
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void replaceRoleTenantUserAssignments_should_ensure_membership_without_clearing_default_flag() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        RoleConstraintService roleConstraintService = mock(RoleConstraintService.class);
        RoleAssignmentSyncService service = new RoleAssignmentSyncService(
            tenantUserRepository,
            roleAssignmentRepository,
            roleConstraintService
        );

        TenantUser existingDefaultMembership = new TenantUser();
        existingDefaultMembership.setTenantId(1L);
        existingDefaultMembership.setUserId(9L);
        existingDefaultMembership.setIsDefault(true);
        existingDefaultMembership.setStatus("LEFT");

        when(tenantUserRepository.findByTenantIdAndUserId(1L, 9L)).thenReturn(Optional.of(existingDefaultMembership));
        when(tenantUserRepository.findByTenantIdAndUserId(1L, 10L)).thenReturn(Optional.empty());

        service.replaceRoleTenantUserAssignments(7L, 1L, List.of(9L, 10L));

        verify(roleAssignmentRepository).deleteRoleAssignmentsInTenant(7L, 1L);
        verify(tenantUserRepository).save(existingDefaultMembership);
        assertThat(existingDefaultMembership.getIsDefault()).isTrue();
        assertThat(existingDefaultMembership.getStatus()).isEqualTo("ACTIVE");
        verify(tenantUserRepository, times(2)).save((TenantUser) any());
        verify(roleConstraintService, times(2)).validateAssignmentsBeforeGrant(
            eq("USER"),
            any(),
            any(),
            any(),
            any(),
            any()
        );
        ArgumentCaptor<List<RoleAssignment>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(roleAssignmentRepository).saveAll((Iterable<RoleAssignment>) captor.capture());
        assertThat(captor.getValue())
                .extracting(RoleAssignment::getPrincipalId)
                .containsExactly(9L, 10L);
    }
}
