package com.tiny.platform.infrastructure.auth.role.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 将平台角色替换写入独立事务，便于审批通过时与申请状态更新解耦（RBAC3 失败时不污染 role_assignment）。
 */
@Service
public class PlatformRoleAssignmentApplyExecutor {

    private final RoleAssignmentSyncService roleAssignmentSyncService;

    public PlatformRoleAssignmentApplyExecutor(RoleAssignmentSyncService roleAssignmentSyncService) {
        this.roleAssignmentSyncService = roleAssignmentSyncService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void replacePlatformRoles(Long userId, List<Long> nextRoleIds) {
        roleAssignmentSyncService.replaceUserPlatformRoleAssignments(userId, nextRoleIds);
    }
}
