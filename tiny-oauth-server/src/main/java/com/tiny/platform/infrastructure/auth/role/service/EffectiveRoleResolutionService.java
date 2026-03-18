package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 解析用户在某租户下的有效角色，仅基于 role_assignment。
 */
@Service
public class EffectiveRoleResolutionService {

    private final RoleRepository roleRepository;
    private final RoleAssignmentSyncService roleAssignmentSyncService;
    private final RoleAssignmentRepository roleAssignmentRepository;

    public EffectiveRoleResolutionService(RoleRepository roleRepository,
                                          RoleAssignmentSyncService roleAssignmentSyncService,
                                          RoleAssignmentRepository roleAssignmentRepository) {
        this.roleRepository = roleRepository;
        this.roleAssignmentSyncService = roleAssignmentSyncService;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<Long> findEffectiveRoleIdsForUserInTenant(Long userId, Long tenantId) {
        List<Long> assignmentRoleIds = roleAssignmentSyncService.findActiveRoleIdsForUserInTenant(userId, tenantId);
        if (!assignmentRoleIds.isEmpty()) {
            return assignmentRoleIds;
        }
        return List.of();
    }

    @Transactional(readOnly = true)
    public Set<Role> findEffectiveRolesForUserInTenant(Long userId, Long tenantId) {
        List<Long> effectiveRoleIds = findEffectiveRoleIdsForUserInTenant(userId, tenantId);
        if (!effectiveRoleIds.isEmpty()) {
            return new LinkedHashSet<>(roleRepository.findWithResourcesByIdInAndTenantIdOrderByIdAsc(effectiveRoleIds, tenantId));
        }
        return Set.of();
    }

    @Transactional(readOnly = true)
    public List<Long> findEffectiveUserIdsForRoleInTenant(Long roleId, Long tenantId) {
        List<Long> assignmentUserIds = roleAssignmentSyncService.findActiveUserIdsForRoleInTenant(roleId, tenantId);
        return assignmentUserIds;
    }
}
