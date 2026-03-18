package com.tiny.platform.infrastructure.auth.role.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tiny.platform.infrastructure.auth.role.domain.RoleHierarchy;
import com.tiny.platform.infrastructure.auth.role.domain.RoleMutex;
import com.tiny.platform.infrastructure.auth.role.domain.RolePrerequisite;
import com.tiny.platform.infrastructure.auth.role.domain.RoleCardinality;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleCardinalityRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleHierarchyRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleMutexRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RolePrerequisiteRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoleConstraintServiceImplTest {

    @Mock
    private RoleHierarchyRepository roleHierarchyRepository;

    @Mock
    private RoleMutexRepository roleMutexRepository;

    @Mock
    private RoleConstraintViolationLogWriteService violationLogWriteService;

    @Mock
    private RolePrerequisiteRepository rolePrerequisiteRepository;

    @Mock
    private RoleCardinalityRepository roleCardinalityRepository;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @InjectMocks
    private RoleConstraintServiceImpl service;

    @Test
    void validateAssignmentsBeforeGrant_shouldSkipWhenNoRoles() {
        service.validateAssignmentsBeforeGrant("USER", 1L, 1L, "TENANT", 1L, List.of());
        verify(roleHierarchyRepository, never()).findByTenantIdAndChildRoleIdIn(any(), any());
        verify(roleMutexRepository, never()).findByTenantIdAndRoleIds(any(), any());
        verify(rolePrerequisiteRepository, never()).findByTenantIdAndRoleIdIn(any(), any());
        verify(roleCardinalityRepository, never()).findByTenantIdAndScopeTypeAndRoleIdIn(any(), any(), any());
    }

    @Test
    void validateAssignmentsBeforeGrant_shouldQueryRepository() {
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(any(), any())).thenReturn(List.of());
        when(roleMutexRepository.findByTenantIdAndRoleIds(any(), any())).thenReturn(List.of());
        when(rolePrerequisiteRepository.findByTenantIdAndRoleIdIn(any(), any())).thenReturn(List.of());
        when(roleCardinalityRepository.findByTenantIdAndScopeTypeAndRoleIdIn(any(), any(), any())).thenReturn(List.of());

        service.validateAssignmentsBeforeGrant("USER", 1L, 1L, "TENANT", 1L, List.of(10L, 20L));
        verify(roleHierarchyRepository).findByTenantIdAndChildRoleIdIn(any(), any());
        verify(roleMutexRepository).findByTenantIdAndRoleIds(any(), any());
        verify(rolePrerequisiteRepository).findByTenantIdAndRoleIdIn(any(), any());
        verify(roleCardinalityRepository).findByTenantIdAndScopeTypeAndRoleIdIn(any(), any(), any());
    }

    @Test
    void validateAssignmentsBeforeGrant_shouldDetectConflictInDryRun() {
        RoleMutex mutex = new RoleMutex();
        mutex.setTenantId(1L);
        mutex.setLeftRoleId(10L);
        mutex.setRightRoleId(20L);
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(any(), any())).thenReturn(List.of());
        when(roleMutexRepository.findByTenantIdAndRoleIds(any(), any())).thenReturn(List.of(mutex));
        when(rolePrerequisiteRepository.findByTenantIdAndRoleIdIn(any(), any())).thenReturn(List.of());
        when(roleCardinalityRepository.findByTenantIdAndScopeTypeAndRoleIdIn(any(), any(), any())).thenReturn(List.of());

        service.validateAssignmentsBeforeGrant("USER", 1L, 1L, "TENANT", 1L, List.of(10L, 20L));

        verify(roleHierarchyRepository).findByTenantIdAndChildRoleIdIn(any(), any());
        verify(roleMutexRepository).findByTenantIdAndRoleIds(any(), any());
        verify(rolePrerequisiteRepository).findByTenantIdAndRoleIdIn(any(), any());
        verify(roleCardinalityRepository).findByTenantIdAndScopeTypeAndRoleIdIn(any(), any(), any());
        // No exception expected (dry-run only).
    }

    @Test
    void validateAssignmentsBeforeGrant_shouldExpandHierarchyBeforeMutexCheck() {
        RoleHierarchy edge = new RoleHierarchy();
        edge.setTenantId(1L);
        edge.setChildRoleId(20L);
        edge.setParentRoleId(99L);

        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(any(), any()))
            .thenReturn(List.of(edge))
            .thenReturn(List.of());
        when(roleMutexRepository.findByTenantIdAndRoleIds(any(), any())).thenReturn(List.of());
        when(rolePrerequisiteRepository.findByTenantIdAndRoleIdIn(any(), any())).thenReturn(List.of());
        when(roleCardinalityRepository.findByTenantIdAndScopeTypeAndRoleIdIn(any(), any(), any())).thenReturn(List.of());

        service.validateAssignmentsBeforeGrant("USER", 1L, 1L, "TENANT", 1L, List.of(20L));

        verify(roleHierarchyRepository, times(2)).findByTenantIdAndChildRoleIdIn(any(), any());
        verify(roleMutexRepository).findByTenantIdAndRoleIds(
            any(),
            argThat(roleIds -> roleIds.containsAll(List.of(20L, 99L)))
        );
        verify(rolePrerequisiteRepository).findByTenantIdAndRoleIdIn(any(), any());
        verify(roleCardinalityRepository).findByTenantIdAndScopeTypeAndRoleIdIn(any(), any(), any());
    }

    @Test
    void validateAssignmentsBeforeGrant_shouldQueryPrerequisitesForDirectRoles() {
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(any(), any())).thenReturn(List.of());
        when(roleMutexRepository.findByTenantIdAndRoleIds(any(), any())).thenReturn(List.of());
        RolePrerequisite rp = new RolePrerequisite();
        rp.setTenantId(1L);
        rp.setRoleId(20L);
        rp.setRequiredRoleId(99L);
        when(rolePrerequisiteRepository.findByTenantIdAndRoleIdIn(any(), any())).thenReturn(List.of(rp));
        when(roleCardinalityRepository.findByTenantIdAndScopeTypeAndRoleIdIn(any(), any(), any())).thenReturn(List.of());

        service.validateAssignmentsBeforeGrant("USER", 1L, 1L, "TENANT", 1L, List.of(20L));

        verify(rolePrerequisiteRepository).findByTenantIdAndRoleIdIn(any(), any());
    }

    @Test
    void validateAssignmentsBeforeGrant_shouldCheckCardinalityForUserTenant() {
        when(roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(any(), any())).thenReturn(List.of());
        when(roleMutexRepository.findByTenantIdAndRoleIds(any(), any())).thenReturn(List.of());
        when(rolePrerequisiteRepository.findByTenantIdAndRoleIdIn(any(), any())).thenReturn(List.of());

        RoleCardinality c = new RoleCardinality();
        c.setTenantId(1L);
        c.setScopeType("TENANT");
        c.setRoleId(10L);
        c.setMaxAssignments(1);
        when(roleCardinalityRepository.findByTenantIdAndScopeTypeAndRoleIdIn(any(), any(), any())).thenReturn(List.of(c));

        when(roleAssignmentRepository.findActiveRoleIdsForUserInTenant(any(), any(), any())).thenReturn(List.of());
        when(roleAssignmentRepository.findActiveUserIdsForRoleInTenant(any(), any(), any())).thenReturn(List.of(1L));

        service.validateAssignmentsBeforeGrant("USER", 2L, 1L, "TENANT", 1L, List.of(10L));

        verify(roleCardinalityRepository).findByTenantIdAndScopeTypeAndRoleIdIn(any(), any(), any());
        verify(roleAssignmentRepository).findActiveRoleIdsForUserInTenant(any(), any(), any(LocalDateTime.class));
        verify(roleAssignmentRepository).findActiveUserIdsForRoleInTenant(any(), any(), any(LocalDateTime.class));
    }
}

