package com.tiny.platform.infrastructure.auth.role.integration;

import com.tiny.platform.infrastructure.auth.role.domain.RoleConstraintViolationLog;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleConstraintViolationLogRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import java.util.Collection;
import org.springframework.dao.EmptyResultDataAccessException;

/**
 * RBAC3 真库集成测试用：在测试结束（含断言失败）时回收数据，避免共享 E2E 库堆积 rbac3_* 用户与规则。
 */
public final class Rbac3RoleConstraintIntegrationTestCleanup {

    private Rbac3RoleConstraintIntegrationTestCleanup() {
    }

    public static void deleteRoleBestEffort(RoleRepository roleRepository, Long roleId) {
        if (roleId == null) {
            return;
        }
        try {
            roleRepository.deleteById(roleId);
        } catch (RuntimeException ignored) {
            // best-effort teardown (FK order / concurrent runs)
        }
    }

    /**
     * 删除指定用户在租户下的赋权、（可选）违例日志、tenant_user 行，并删除 user 行。
     */
    public static void purgeUserTenantArtifacts(
        Long tenantId,
        Long userId,
        RoleAssignmentRepository roleAssignmentRepository,
        RoleConstraintViolationLogRepository violationLogRepository,
        TenantUserRepository tenantUserRepository,
        UserRepository userRepository
    ) {
        try {
            roleAssignmentRepository.deleteUserAssignmentsInTenant(userId, tenantId);
        } catch (RuntimeException ignored) {
            // best-effort teardown
        }
        if (violationLogRepository != null) {
            violationLogRepository.findAll().stream()
                .filter(v -> tenantId.equals(v.getTenantId()) && userId.equals(v.getPrincipalId()))
                .map(RoleConstraintViolationLog::getId)
                .forEach(id -> {
                    try {
                        violationLogRepository.deleteById(id);
                    } catch (RuntimeException ignored) {
                        // best-effort
                    }
                });
        }
        tenantUserRepository.findByTenantIdAndUserId(tenantId, userId).ifPresent(tu -> {
            try {
                tenantUserRepository.delete(tu);
            } catch (RuntimeException ignored) {
                // best-effort
            }
        });
        try {
            userRepository.deleteById(userId);
        } catch (EmptyResultDataAccessException ignored) {
            // already gone
        } catch (RuntimeException ignored) {
            // best-effort
        }
    }

    /** enforce 测试无违例日志表时使用。 */
    public static void purgeUserTenantArtifacts(
        Long tenantId,
        Long userId,
        RoleAssignmentRepository roleAssignmentRepository,
        TenantUserRepository tenantUserRepository,
        UserRepository userRepository
    ) {
        purgeUserTenantArtifacts(
            tenantId,
            userId,
            roleAssignmentRepository,
            null,
            tenantUserRepository,
            userRepository
        );
    }

    public static void purgeUserTenantArtifactsBatch(
        Long tenantId,
        Collection<Long> userIds,
        RoleAssignmentRepository roleAssignmentRepository,
        RoleConstraintViolationLogRepository violationLogRepository,
        TenantUserRepository tenantUserRepository,
        UserRepository userRepository
    ) {
        for (Long userId : userIds) {
            purgeUserTenantArtifacts(
                tenantId,
                userId,
                roleAssignmentRepository,
                violationLogRepository,
                tenantUserRepository,
                userRepository
            );
        }
    }

    public static void purgeUserTenantArtifactsBatch(
        Long tenantId,
        Collection<Long> userIds,
        RoleAssignmentRepository roleAssignmentRepository,
        TenantUserRepository tenantUserRepository,
        UserRepository userRepository
    ) {
        for (Long userId : userIds) {
            purgeUserTenantArtifacts(
                tenantId,
                userId,
                roleAssignmentRepository,
                tenantUserRepository,
                userRepository
            );
        }
    }
}
