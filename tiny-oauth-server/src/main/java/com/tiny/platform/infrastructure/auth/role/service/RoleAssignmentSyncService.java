package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.RoleAssignment;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.user.domain.TenantUser;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoleAssignmentSyncService {

    private static final String ACTIVE = "ACTIVE";
    private static final String PRINCIPAL_TYPE_USER = "USER";
    private static final String SCOPE_TYPE_TENANT = "TENANT";

    private final TenantUserRepository tenantUserRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RoleConstraintService roleConstraintService;

    public RoleAssignmentSyncService(TenantUserRepository tenantUserRepository,
                                     RoleAssignmentRepository roleAssignmentRepository,
                                     RoleConstraintService roleConstraintService) {
        this.tenantUserRepository = tenantUserRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.roleConstraintService = roleConstraintService;
    }

    @Transactional
    public void ensureTenantMembership(Long userId, Long tenantId, boolean isDefault) {
        if (userId == null || tenantId == null || tenantId <= 0) {
            return;
        }

        TenantUser membership = tenantUserRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(TenantUser::new);
        Boolean existingDefault = membership.getIsDefault();
        membership.setTenantId(tenantId);
        membership.setUserId(userId);
        membership.setStatus(ACTIVE);
        membership.setIsDefault(Boolean.TRUE.equals(existingDefault) || isDefault);
        membership.setLeftAt(null);
        membership.setLastActivatedAt(LocalDateTime.now());
        tenantUserRepository.save(membership);
    }

    @Transactional
    public void replaceUserTenantRoleAssignments(Long userId, Long tenantId, List<Long> roleIds) {
        if (userId == null || tenantId == null || tenantId <= 0) {
            return;
        }

        ensureTenantMembership(userId, tenantId, false);
        // Phase2 RBAC3: validate before writing role_assignment (currently dry-run only).
        roleConstraintService.validateAssignmentsBeforeGrant(
            PRINCIPAL_TYPE_USER,
            userId,
            tenantId,
            SCOPE_TYPE_TENANT,
            tenantId,
            roleIds == null ? List.of() : roleIds
        );
        roleAssignmentRepository.deleteUserAssignmentsInTenant(userId, tenantId);
        saveTenantAssignments(userId, tenantId, roleIds);
    }

    @Transactional
    public void replaceRoleTenantUserAssignments(Long roleId, Long tenantId, List<Long> userIds) {
        if (roleId == null || tenantId == null || tenantId <= 0) {
            return;
        }

        roleAssignmentRepository.deleteRoleAssignmentsInTenant(roleId, tenantId);
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<RoleAssignment> assignments = new ArrayList<>();
        for (Long userId : new LinkedHashSet<>(userIds)) {
            ensureTenantMembership(userId, tenantId, false);
            // Phase2 RBAC3: validate before writing role_assignment (currently dry-run only).
            roleConstraintService.validateAssignmentsBeforeGrant(
                PRINCIPAL_TYPE_USER,
                userId,
                tenantId,
                SCOPE_TYPE_TENANT,
                tenantId,
                List.of(roleId)
            );
            assignments.add(buildTenantAssignment(userId, roleId, tenantId, now));
        }
        roleAssignmentRepository.saveAll(assignments);
    }

    @Transactional(readOnly = true)
    public List<Long> findActiveRoleIdsForUserInTenant(Long userId, Long tenantId) {
        if (userId == null || tenantId == null || tenantId <= 0) {
            return List.of();
        }
        return roleAssignmentRepository.findActiveRoleIdsForUserInTenant(userId, tenantId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Long> findActiveUserIdsForRoleInTenant(Long roleId, Long tenantId) {
        if (roleId == null || tenantId == null || tenantId <= 0) {
            return List.of();
        }
        return roleAssignmentRepository.findActiveUserIdsForRoleInTenant(roleId, tenantId, LocalDateTime.now());
    }

    private void saveTenantAssignments(Long userId, Long tenantId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Set<Long> distinctRoleIds = new LinkedHashSet<>(roleIds);
        List<RoleAssignment> assignments = new ArrayList<>();
        for (Long roleId : distinctRoleIds) {
            assignments.add(buildTenantAssignment(userId, roleId, tenantId, now));
        }
        roleAssignmentRepository.saveAll(assignments);
    }

    private RoleAssignment buildTenantAssignment(Long userId, Long roleId, Long tenantId, LocalDateTime now) {
        RoleAssignment assignment = new RoleAssignment();
        assignment.setPrincipalType(PRINCIPAL_TYPE_USER);
        assignment.setPrincipalId(userId);
        assignment.setRoleId(roleId);
        assignment.setTenantId(tenantId);
        assignment.setScopeType(SCOPE_TYPE_TENANT);
        assignment.setScopeId(tenantId);
        assignment.setStatus(ACTIVE);
        assignment.setStartTime(now);
        assignment.setEndTime(null);
        assignment.setGrantedAt(now);
        return assignment;
    }
}
