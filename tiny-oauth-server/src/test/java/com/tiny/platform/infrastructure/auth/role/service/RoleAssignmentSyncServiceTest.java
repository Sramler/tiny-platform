package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.role.domain.RoleAssignment;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.TenantUser;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("nullness")
class RoleAssignmentSyncServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findActiveRoleIdsForUserInTenant_should_include_applicable_org_and_dept_assignments() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        OrganizationUnitRepository organizationUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        RoleConstraintService roleConstraintService = mock(RoleConstraintService.class);
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        RoleAssignmentSyncService service = new RoleAssignmentSyncService(
            tenantUserRepository,
            organizationUnitRepository,
            userUnitRepository,
            roleAssignmentRepository,
            roleConstraintService,
            auditService
        );

        OrganizationUnit org = new OrganizationUnit();
        org.setId(100L);
        org.setTenantId(1L);
        org.setUnitType("ORG");

        OrganizationUnit dept = new OrganizationUnit();
        dept.setId(200L);
        dept.setTenantId(1L);
        dept.setUnitType("DEPT");
        dept.setParentId(100L);

        when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 5L, "ACTIVE"))
            .thenReturn(List.of(200L));
        when(organizationUnitRepository.findByIdAndTenantId(200L, 1L)).thenReturn(Optional.of(dept));
        when(organizationUnitRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(org));
        when(roleAssignmentRepository.findActiveAssignmentsForUserInTenant(eq(5L), eq(1L), any()))
            .thenReturn(List.of(
                assignment(5L, 10L, 1L, "TENANT", 1L),
                assignment(5L, 11L, 1L, "ORG", 100L),
                assignment(5L, 12L, 1L, "DEPT", 200L),
                assignment(5L, 13L, 1L, "DEPT", 201L)
            ));

        List<Long> result = service.findActiveRoleIdsForUserInTenant(5L, 1L);

        assertThat(result).containsExactly(10L, 11L, 12L);
    }

    @Test
    void findActiveUserIdsForRolesInTenant_should_include_users_whose_current_scope_still_matches() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        OrganizationUnitRepository organizationUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        RoleConstraintService roleConstraintService = mock(RoleConstraintService.class);
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        RoleAssignmentSyncService service = new RoleAssignmentSyncService(
            tenantUserRepository,
            organizationUnitRepository,
            userUnitRepository,
            roleAssignmentRepository,
            roleConstraintService,
            auditService
        );

        OrganizationUnit org = new OrganizationUnit();
        org.setId(100L);
        org.setTenantId(1L);
        org.setUnitType("ORG");

        OrganizationUnit dept = new OrganizationUnit();
        dept.setId(200L);
        dept.setTenantId(1L);
        dept.setUnitType("DEPT");
        dept.setParentId(100L);

        when(roleAssignmentRepository.findActiveAssignmentsForRoleIdsInTenant(eq(java.util.Set.of(10L, 11L)), eq(1L), any()))
            .thenReturn(List.of(
                assignment(5L, 10L, 1L, "TENANT", 1L),
                assignment(6L, 10L, 1L, "ORG", 100L),
                assignment(7L, 11L, 1L, "DEPT", 200L),
                assignment(8L, 11L, 1L, "DEPT", 201L)
            ));
        when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 6L, "ACTIVE"))
            .thenReturn(List.of(200L));
        when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 7L, "ACTIVE"))
            .thenReturn(List.of(200L));
        when(userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(1L, 8L, "ACTIVE"))
            .thenReturn(List.of(202L));
        when(organizationUnitRepository.findByIdAndTenantId(200L, 1L)).thenReturn(Optional.of(dept));
        when(organizationUnitRepository.findByIdAndTenantId(202L, 1L)).thenReturn(Optional.empty());
        when(organizationUnitRepository.findByIdAndTenantId(100L, 1L)).thenReturn(Optional.of(org));

        List<Long> result = service.findActiveUserIdsForRolesInTenant(java.util.Set.of(10L, 11L), 1L);

        assertThat(result).containsExactly(5L, 6L, 7L);
    }

    @Test
    void ensureTenantMembership_should_activate_existing_membership() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        OrganizationUnitRepository organizationUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        RoleConstraintService roleConstraintService = mock(RoleConstraintService.class);
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        RoleAssignmentSyncService service = new RoleAssignmentSyncService(
            tenantUserRepository,
            organizationUnitRepository,
            userUnitRepository,
            roleAssignmentRepository,
            roleConstraintService,
            auditService
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
        OrganizationUnitRepository organizationUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        RoleConstraintService roleConstraintService = mock(RoleConstraintService.class);
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        RoleAssignmentSyncService service = new RoleAssignmentSyncService(
            tenantUserRepository,
            organizationUnitRepository,
            userUnitRepository,
            roleAssignmentRepository,
            roleConstraintService,
            auditService
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
        verify(roleAssignmentRepository).deleteUserAssignmentsInScope(5L, 1L, "TENANT", 1L);
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
                    assertThat(assignment.getGrantedBy()).isNull();
                    assertThat(assignment.getGrantedAt()).isNotNull();
                });
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void replaceUserTenantRoleAssignments_should_record_grantedBy_from_authenticated_actor() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        OrganizationUnitRepository organizationUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        RoleConstraintService roleConstraintService = mock(RoleConstraintService.class);
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        RoleAssignmentSyncService service = new RoleAssignmentSyncService(
            tenantUserRepository,
            organizationUnitRepository,
            userUnitRepository,
            roleAssignmentRepository,
            roleConstraintService,
            auditService
        );

        when(tenantUserRepository.findByTenantIdAndUserId(1L, 5L)).thenReturn(Optional.empty());

        User actor = new User();
        actor.setId(88L);
        actor.setUsername("grant.admin");
        actor.setEnabled(true);
        actor.setAccountNonExpired(true);
        actor.setAccountNonLocked(true);
        actor.setCredentialsNonExpired(true);
        SecurityUser securityUser = new SecurityUser(actor, "", 1L, java.util.Set.of());
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                securityUser,
                null,
                securityUser.getAuthorities()
            )
        );

        service.replaceUserTenantRoleAssignments(5L, 1L, List.of(10L, 11L));

        ArgumentCaptor<List<RoleAssignment>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(roleAssignmentRepository).saveAll((Iterable<RoleAssignment>) captor.capture());
        assertThat(captor.getValue())
            .allSatisfy(assignment -> {
                assertThat(assignment.getGrantedBy()).isEqualTo(88L);
                assertThat(assignment.getGrantedAt()).isNotNull();
            });
    }

    @Test
    void replaceUserTenantRoleAssignments_should_fail_and_not_delete_when_compat_roles_frozen() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        OrganizationUnitRepository organizationUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        RoleConstraintService roleConstraintService = mock(RoleConstraintService.class);
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);

        RoleAssignmentSyncService service = new RoleAssignmentSyncService(
            tenantUserRepository,
            organizationUnitRepository,
            userUnitRepository,
            roleAssignmentRepository,
            roleConstraintService,
            auditService
        );

        when(tenantUserRepository.findByTenantIdAndUserId(1L, 5L)).thenReturn(Optional.empty());
        doThrow(new BusinessException(
            ErrorCode.RESOURCE_STATE_INVALID,
            "兼容性角色已冻结（ROLE_SYSTEM_ADMIN / ROLE_TENANT_USER），不允许新增授权"
        )).when(roleConstraintService).validateAssignmentsBeforeGrant(
            eq("USER"),
            eq(5L),
            eq(1L),
            eq("TENANT"),
            eq(1L),
            any()
        );

        assertThatThrownBy(() ->
                service.replaceUserTenantRoleAssignments(5L, 1L, List.of(10L, 5L))
            )
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("兼容性角色已冻结");

        // 核心验收点：失败时不会误删原有 role_assignment，也不会新增任何 assignment。
        verify(roleAssignmentRepository, never()).deleteUserAssignmentsInScope(5L, 1L, "TENANT", 1L);
        verify(roleAssignmentRepository, never()).saveAll(any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void replaceRoleTenantUserAssignments_should_ensure_membership_without_clearing_default_flag() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        OrganizationUnitRepository organizationUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        RoleConstraintService roleConstraintService = mock(RoleConstraintService.class);
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        RoleAssignmentSyncService service = new RoleAssignmentSyncService(
            tenantUserRepository,
            organizationUnitRepository,
            userUnitRepository,
            roleAssignmentRepository,
            roleConstraintService,
            auditService
        );

        TenantUser existingDefaultMembership = new TenantUser();
        existingDefaultMembership.setTenantId(1L);
        existingDefaultMembership.setUserId(9L);
        existingDefaultMembership.setIsDefault(true);
        existingDefaultMembership.setStatus("LEFT");

        when(tenantUserRepository.findByTenantIdAndUserId(1L, 9L)).thenReturn(Optional.of(existingDefaultMembership));
        when(tenantUserRepository.findByTenantIdAndUserId(1L, 10L)).thenReturn(Optional.empty());

        service.replaceRoleTenantUserAssignments(7L, 1L, List.of(9L, 10L));

        verify(roleAssignmentRepository).deleteRoleAssignmentsInScope(7L, 1L, "TENANT", 1L);
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

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void replaceUserScopedRoleAssignments_should_persist_dept_scope_assignments() {
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        OrganizationUnitRepository organizationUnitRepository = mock(OrganizationUnitRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        RoleAssignmentRepository roleAssignmentRepository = mock(RoleAssignmentRepository.class);
        RoleConstraintService roleConstraintService = mock(RoleConstraintService.class);
        AuthorizationAuditService auditService = mock(AuthorizationAuditService.class);
        RoleAssignmentSyncService service = new RoleAssignmentSyncService(
            tenantUserRepository,
            organizationUnitRepository,
            userUnitRepository,
            roleAssignmentRepository,
            roleConstraintService,
            auditService
        );

        OrganizationUnit dept = new OrganizationUnit();
        dept.setId(200L);
        dept.setTenantId(1L);
        dept.setUnitType("DEPT");
        when(organizationUnitRepository.findByIdAndTenantId(200L, 1L)).thenReturn(Optional.of(dept));
        when(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(1L, java.util.Set.of(200L), "ACTIVE"))
            .thenReturn(List.of(5L));
        when(tenantUserRepository.findByTenantIdAndUserId(1L, 5L)).thenReturn(Optional.empty());

        service.replaceUserScopedRoleAssignments(5L, 1L, "DEPT", 200L, List.of(10L, 11L));

        verify(roleConstraintService).validateAssignmentsBeforeGrant(
            "USER",
            5L,
            1L,
            "DEPT",
            200L,
            List.of(10L, 11L)
        );
        verify(roleAssignmentRepository).deleteUserAssignmentsInScope(5L, 1L, "DEPT", 200L);
        ArgumentCaptor<List<RoleAssignment>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(roleAssignmentRepository).saveAll((Iterable<RoleAssignment>) captor.capture());
        assertThat(captor.getValue()).extracting(RoleAssignment::getScopeType).containsOnly("DEPT");
        assertThat(captor.getValue()).extracting(RoleAssignment::getScopeId).containsOnly(200L);
    }

    private RoleAssignment assignment(Long principalId, Long roleId, Long tenantId, String scopeType, Long scopeId) {
        RoleAssignment assignment = new RoleAssignment();
        assignment.setPrincipalType("USER");
        assignment.setPrincipalId(principalId);
        assignment.setRoleId(roleId);
        assignment.setTenantId(tenantId);
        assignment.setScopeType(scopeType);
        assignment.setScopeId(scopeId);
        assignment.setStatus("ACTIVE");
        assignment.setStartTime(java.time.LocalDateTime.now().minusMinutes(5));
        assignment.setEndTime(null);
        return assignment;
    }
}
