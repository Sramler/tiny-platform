package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.role.domain.RoleAssignment;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.user.domain.TenantUser;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RoleAssignmentSyncService {

    private static final String ACTIVE = "ACTIVE";
    private static final String PRINCIPAL_TYPE_USER = "USER";
    private static final String SCOPE_TYPE_PLATFORM = "PLATFORM";
    private static final String SCOPE_TYPE_TENANT = "TENANT";
    private static final String SCOPE_TYPE_ORG = "ORG";
    private static final String SCOPE_TYPE_DEPT = "DEPT";

    private final TenantUserRepository tenantUserRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final UserUnitRepository userUnitRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RoleConstraintService roleConstraintService;
    private final AuthorizationAuditService auditService;

    public RoleAssignmentSyncService(TenantUserRepository tenantUserRepository,
                                     OrganizationUnitRepository organizationUnitRepository,
                                     UserUnitRepository userUnitRepository,
                                     RoleAssignmentRepository roleAssignmentRepository,
                                     RoleConstraintService roleConstraintService,
                                     AuthorizationAuditService auditService) {
        this.tenantUserRepository = tenantUserRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.userUnitRepository = userUnitRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.roleConstraintService = roleConstraintService;
        this.auditService = auditService;
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
        replaceUserScopedRoleAssignments(userId, tenantId, SCOPE_TYPE_TENANT, tenantId, roleIds);
    }

    @Transactional
    public void replaceUserPlatformRoleAssignments(Long userId, List<Long> roleIds) {
        if (userId == null || userId <= 0) {
            return;
        }
        List<Long> normalizedRoleIds = roleIds == null ? List.of() : roleIds;
        roleConstraintService.validateAssignmentsBeforeGrant(
            PRINCIPAL_TYPE_USER,
            userId,
            null,
            SCOPE_TYPE_PLATFORM,
            null,
            normalizedRoleIds
        );
        List<Long> previousRoleIds = roleAssignmentRepository.findActiveRoleIdsForUserInPlatform(userId, LocalDateTime.now());
        roleAssignmentRepository.deleteUserAssignmentsInPlatform(userId);
        savePlatformAssignments(userId, normalizedRoleIds);
        auditService.logSuccess(AuthorizationAuditEventType.ROLE_ASSIGNMENT_REPLACE,
            null, userId, null, SCOPE_TYPE_PLATFORM, null,
            buildReplaceDetail("previousRoleIds", previousRoleIds, "newRoleIds", normalizedRoleIds, null));
    }

    @Transactional
    public void replaceUserScopedRoleAssignments(Long userId, Long tenantId,
                                                 String scopeType, Long scopeId,
                                                 List<Long> roleIds) {
        if (userId == null || tenantId == null || tenantId <= 0) {
            return;
        }

        AssignmentScope scope = resolveScope(tenantId, scopeType, scopeId);
        ensureTenantMembership(userId, tenantId, false);
        assertUsersAssignableToScope(List.of(userId), tenantId, scope);
        // Phase2 RBAC3: validate before writing role_assignment (currently dry-run only).
        roleConstraintService.validateAssignmentsBeforeGrant(
            PRINCIPAL_TYPE_USER,
            userId,
            tenantId,
            scope.scopeType(),
            scope.scopeId(),
            roleIds == null ? List.of() : roleIds
        );
        List<Long> previousRoleIds = roleAssignmentRepository.findActiveRoleIdsForUserInScope(
            userId, tenantId, scope.scopeType(), scope.scopeId(), LocalDateTime.now());
        roleAssignmentRepository.deleteUserAssignmentsInScope(userId, tenantId, scope.scopeType(), scope.scopeId());
        saveScopedAssignments(userId, tenantId, scope, roleIds);
        auditService.logSuccess(AuthorizationAuditEventType.ROLE_ASSIGNMENT_REPLACE,
            tenantId, userId, null, scope.scopeType(), scope.scopeId(),
            buildReplaceDetail("previousRoleIds", previousRoleIds, "newRoleIds", roleIds, scope));
    }

    @Transactional
    public void replaceRoleTenantUserAssignments(Long roleId, Long tenantId, List<Long> userIds) {
        replaceRoleScopedUserAssignments(roleId, tenantId, SCOPE_TYPE_TENANT, tenantId, userIds);
    }

    @Transactional
    public void replaceRoleScopedUserAssignments(Long roleId, Long tenantId,
                                                 String scopeType, Long scopeId,
                                                 List<Long> userIds) {
        if (roleId == null || tenantId == null || tenantId <= 0) {
            return;
        }

        AssignmentScope scope = resolveScope(tenantId, scopeType, scopeId);
        List<Long> previousUserIds = roleAssignmentRepository.findActiveUserIdsForRoleInScope(
            roleId, tenantId, scope.scopeType(), scope.scopeId(), LocalDateTime.now());
        if (userIds == null || userIds.isEmpty()) {
            // Revocation/cleanup: when caller wants to set role users empty, we keep old behavior.
            roleAssignmentRepository.deleteRoleAssignmentsInScope(roleId, tenantId, scope.scopeType(), scope.scopeId());
            auditService.logSuccess(AuthorizationAuditEventType.ROLE_ASSIGNMENT_REPLACE,
                tenantId, null, roleId, scope.scopeType(), scope.scopeId(),
                buildReplaceDetail("previousUserIds", previousUserIds, "newUserIds", List.of(), scope));
            return;
        }

        Set<Long> distinctUserIds = new LinkedHashSet<>(userIds);
        assertUsersAssignableToScope(distinctUserIds, tenantId, scope);
        LocalDateTime now = LocalDateTime.now();
        Long grantedBy = resolveCurrentActorUserId();
        List<RoleAssignment> assignments = new ArrayList<>();
        for (Long userId : distinctUserIds) {
            ensureTenantMembership(userId, tenantId, false);
            // Phase2 RBAC3: validate before writing role_assignment (currently dry-run only).
            roleConstraintService.validateAssignmentsBeforeGrant(
                PRINCIPAL_TYPE_USER,
                userId,
                tenantId,
                scope.scopeType(),
                scope.scopeId(),
                List.of(roleId)
            );
            assignments.add(buildScopedAssignment(userId, roleId, tenantId, scope, now, grantedBy));
        }
        // Only after all validations passed, perform replace deletion.
        roleAssignmentRepository.deleteRoleAssignmentsInScope(roleId, tenantId, scope.scopeType(), scope.scopeId());
        roleAssignmentRepository.saveAll(assignments);
        auditService.logSuccess(AuthorizationAuditEventType.ROLE_ASSIGNMENT_REPLACE,
            tenantId, null, roleId, scope.scopeType(), scope.scopeId(),
            buildReplaceDetail("previousUserIds", previousUserIds, "newUserIds", new ArrayList<>(distinctUserIds), scope));
    }

    @Transactional(readOnly = true)
    public List<Long> findActiveRoleIdsForUserInTenant(Long userId, Long tenantId) {
        if (userId == null || tenantId == null || tenantId <= 0) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        ApplicableScopes scopes = resolveApplicableScopesForUser(userId, tenantId, new HashMap<>());
        Set<Long> roleIds = new LinkedHashSet<>();
        for (RoleAssignment assignment : roleAssignmentRepository.findActiveAssignmentsForUserInTenant(userId, tenantId, now)) {
            if (isAssignmentApplicable(assignment.getScopeType(), assignment.getScopeId(), scopes)) {
                roleIds.add(assignment.getRoleId());
            }
        }
        return roleIds.stream().sorted().toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findActiveRoleIdsForUserInPlatform(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        return roleAssignmentRepository.findActiveRoleIdsForUserInPlatform(userId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Long> findActiveRoleIdsForUserInScope(Long userId, Long tenantId, String scopeType, Long scopeId) {
        if (userId == null || tenantId == null || tenantId <= 0) {
            return List.of();
        }
        AssignmentScope scope = resolveScope(tenantId, scopeType, scopeId);
        return roleAssignmentRepository.findActiveRoleIdsForUserInScope(
            userId, tenantId, scope.scopeType(), scope.scopeId(), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Long> findActiveUserIdsForRoleInTenant(Long roleId, Long tenantId) {
        return findActiveUserIdsForRoleInScope(roleId, tenantId, SCOPE_TYPE_TENANT, tenantId);
    }

    @Transactional(readOnly = true)
    public List<Long> findActiveUserIdsForRolesInTenant(Collection<Long> roleIds, Long tenantId) {
        if (roleIds == null || roleIds.isEmpty() || tenantId == null || tenantId <= 0) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        Set<Long> activeUserIds = new LinkedHashSet<>();
        java.util.Map<Long, ApplicableScopes> scopeCache = new HashMap<>();
        for (RoleAssignment assignment : roleAssignmentRepository.findActiveAssignmentsForRoleIdsInTenant(
            new LinkedHashSet<>(roleIds), tenantId, now)) {
            Long principalId = assignment.getPrincipalId();
            if (principalId == null) {
                continue;
            }
            ApplicableScopes scopes = scopeCache.computeIfAbsent(
                principalId,
                userId -> resolveApplicableScopesForUser(userId, tenantId, new HashMap<>())
            );
            if (isAssignmentApplicable(assignment.getScopeType(), assignment.getScopeId(), scopes)) {
                activeUserIds.add(principalId);
            }
        }
        return activeUserIds.stream().sorted().toList();
    }

    @Transactional(readOnly = true)
    public List<Long> findActiveUserIdsForRoleInScope(Long roleId, Long tenantId, String scopeType, Long scopeId) {
        if (roleId == null || tenantId == null || tenantId <= 0) {
            return List.of();
        }
        AssignmentScope scope = resolveScope(tenantId, scopeType, scopeId);
        return roleAssignmentRepository.findActiveUserIdsForRoleInScope(
            roleId, tenantId, scope.scopeType(), scope.scopeId(), LocalDateTime.now());
    }

    private void saveScopedAssignments(Long userId, Long tenantId, AssignmentScope scope, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Long grantedBy = resolveCurrentActorUserId();
        Set<Long> distinctRoleIds = new LinkedHashSet<>(roleIds);
        List<RoleAssignment> assignments = new ArrayList<>();
        for (Long roleId : distinctRoleIds) {
            assignments.add(buildScopedAssignment(userId, roleId, tenantId, scope, now, grantedBy));
        }
        roleAssignmentRepository.saveAll(assignments);
    }

    private void savePlatformAssignments(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long grantedBy = resolveCurrentActorUserId();
        Set<Long> distinctRoleIds = new LinkedHashSet<>(roleIds);
        List<RoleAssignment> assignments = new ArrayList<>();
        for (Long roleId : distinctRoleIds) {
            RoleAssignment assignment = new RoleAssignment();
            assignment.setPrincipalType(PRINCIPAL_TYPE_USER);
            assignment.setPrincipalId(userId);
            assignment.setRoleId(roleId);
            assignment.setTenantId(null);
            assignment.setScopeType(SCOPE_TYPE_PLATFORM);
            assignment.setScopeId(null);
            assignment.setStatus(ACTIVE);
            assignment.setStartTime(now);
            assignment.setEndTime(null);
            assignment.setGrantedBy(grantedBy);
            assignment.setGrantedAt(now);
            assignments.add(assignment);
        }
        roleAssignmentRepository.saveAll(assignments);
    }

    private RoleAssignment buildScopedAssignment(Long userId, Long roleId,
                                                 Long tenantId, AssignmentScope scope,
                                                 LocalDateTime now, Long grantedBy) {
        RoleAssignment assignment = new RoleAssignment();
        assignment.setPrincipalType(PRINCIPAL_TYPE_USER);
        assignment.setPrincipalId(userId);
        assignment.setRoleId(roleId);
        assignment.setTenantId(tenantId);
        assignment.setScopeType(scope.scopeType());
        assignment.setScopeId(scope.scopeId());
        assignment.setStatus(ACTIVE);
        assignment.setStartTime(now);
        assignment.setEndTime(null);
        assignment.setGrantedBy(grantedBy);
        assignment.setGrantedAt(now);
        return assignment;
    }

    private Long resolveCurrentActorUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof com.tiny.platform.core.oauth.model.SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        return null;
    }

    private AssignmentScope resolveScope(Long tenantId, String scopeType, Long scopeId) {
        if (tenantId == null || tenantId <= 0) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "缺少租户信息");
        }
        String normalizedScopeType = scopeType == null || scopeType.isBlank()
            ? SCOPE_TYPE_TENANT
            : scopeType.trim().toUpperCase(Locale.ROOT);
        if (SCOPE_TYPE_TENANT.equals(normalizedScopeType)) {
            return new AssignmentScope(SCOPE_TYPE_TENANT, tenantId);
        }
        if (!SCOPE_TYPE_ORG.equals(normalizedScopeType) && !SCOPE_TYPE_DEPT.equals(normalizedScopeType)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "scopeType 仅支持 TENANT/ORG/DEPT");
        }
        if (scopeId == null || scopeId <= 0) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "ORG/DEPT scope 必须提供有效的 scopeId");
        }
        OrganizationUnit unit = organizationUnitRepository.findByIdAndTenantId(scopeId, tenantId)
            .orElseThrow(() -> BusinessException.notFound("组织/部门节点不存在"));
        if (!normalizedScopeType.equals(unit.getUnitType())) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "scopeType 与 scopeId 对应节点类型不匹配");
        }
        return new AssignmentScope(normalizedScopeType, scopeId);
    }

    private void assertUsersAssignableToScope(Collection<Long> userIds, Long tenantId, AssignmentScope scope) {
        if (userIds == null || userIds.isEmpty() || SCOPE_TYPE_TENANT.equals(scope.scopeType())) {
            return;
        }
        Set<Long> scopedUserIds = resolveScopedUserIds(tenantId, scope);
        if (!scopedUserIds.containsAll(userIds)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "所选用户不属于指定组织/部门范围");
        }
    }

    private Set<Long> resolveScopedUserIds(Long tenantId, AssignmentScope scope) {
        Set<Long> unitIds = new LinkedHashSet<>();
        if (SCOPE_TYPE_ORG.equals(scope.scopeType())) {
            unitIds.addAll(resolveOrgSubtreeUnitIds(tenantId, scope.scopeId()));
        } else {
            unitIds.add(scope.scopeId());
        }
        return new LinkedHashSet<>(userUnitRepository.findUserIdsByTenantIdAndUnitIdInAndStatus(tenantId, unitIds, ACTIVE));
    }

    private ApplicableScopes resolveApplicableScopesForUser(Long userId, Long tenantId,
                                                            java.util.Map<Long, OrganizationUnit> unitCache) {
        Set<Long> deptScopeIds = new LinkedHashSet<>();
        Set<Long> orgScopeIds = new LinkedHashSet<>();
        for (Long unitId : userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(tenantId, userId, ACTIVE)) {
            if (unitId == null || unitId <= 0) {
                continue;
            }
            collectUnitScopes(tenantId, unitId, deptScopeIds, orgScopeIds, unitCache);
        }
        return new ApplicableScopes(orgScopeIds, deptScopeIds);
    }

    private void collectUnitScopes(Long tenantId, Long unitId,
                                   Set<Long> deptScopeIds, Set<Long> orgScopeIds,
                                   java.util.Map<Long, OrganizationUnit> unitCache) {
        Set<Long> visited = new LinkedHashSet<>();
        Long currentUnitId = unitId;
        int guard = 0;
        while (currentUnitId != null && currentUnitId > 0 && visited.add(currentUnitId) && guard++ < 50) {
            OrganizationUnit unit = loadUnit(tenantId, currentUnitId, unitCache);
            if (unit == null) {
                break;
            }
            if (SCOPE_TYPE_ORG.equals(unit.getUnitType())) {
                orgScopeIds.add(unit.getId());
            } else if (SCOPE_TYPE_DEPT.equals(unit.getUnitType())) {
                deptScopeIds.add(unit.getId());
            }
            currentUnitId = unit.getParentId();
        }
    }

    private OrganizationUnit loadUnit(Long tenantId, Long unitId, java.util.Map<Long, OrganizationUnit> unitCache) {
        return unitCache.computeIfAbsent(unitId,
            id -> organizationUnitRepository.findByIdAndTenantId(id, tenantId).orElse(null));
    }

    private boolean isAssignmentApplicable(String scopeType, Long scopeId, ApplicableScopes scopes) {
        if (scopeType == null || scopeType.isBlank() || SCOPE_TYPE_TENANT.equals(scopeType)) {
            return true;
        }
        if (scopeId == null || scopeId <= 0) {
            return false;
        }
        if (SCOPE_TYPE_ORG.equals(scopeType)) {
            return scopes.orgScopeIds().contains(scopeId);
        }
        if (SCOPE_TYPE_DEPT.equals(scopeType)) {
            return scopes.deptScopeIds().contains(scopeId);
        }
        return false;
    }

    private Set<Long> resolveOrgSubtreeUnitIds(Long tenantId, Long rootUnitId) {
        Set<Long> result = new LinkedHashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(rootUnitId);
        while (!queue.isEmpty()) {
            Long currentUnitId = queue.removeFirst();
            if (!result.add(currentUnitId)) {
                continue;
            }
            queue.addAll(organizationUnitRepository.findChildIdsByTenantIdAndParentId(tenantId, currentUnitId));
        }
        return result;
    }

    private String buildReplaceDetail(String previousKey, List<Long> previousIds,
                                      String nextKey, List<Long> nextIds,
                                      AssignmentScope scope) {
        if (scope == null) {
            return "{"
                + "\"scopeType\":\"" + SCOPE_TYPE_PLATFORM + "\","
                + "\"scopeId\":null,"
                + "\"" + previousKey + "\":" + (previousIds != null ? previousIds : List.of()) + ","
                + "\"" + nextKey + "\":" + (nextIds != null ? nextIds : List.of())
                + "}";
        }
        return "{"
            + "\"scopeType\":\"" + scope.scopeType() + "\","
            + "\"scopeId\":" + scope.scopeId() + ","
            + "\"" + previousKey + "\":" + (previousIds != null ? previousIds : List.of()) + ","
            + "\"" + nextKey + "\":" + (nextIds != null ? nextIds : List.of())
            + "}";
    }

    // 用 static class 替代 record：部分静态分析器在未启用对应 Java 语法级别时会解析失败
    private static class AssignmentScope {
        private final String scopeType;
        private final Long scopeId;

        private AssignmentScope(String scopeType, Long scopeId) {
            this.scopeType = scopeType;
            this.scopeId = scopeId;
        }

        private String scopeType() {
            return scopeType;
        }

        private Long scopeId() {
            return scopeId;
        }
    }

    // 用 static class 替代 record：保留同名 accessor 以最大化兼容已有调用点
    private static class ApplicableScopes {
        private final Set<Long> orgScopeIds;
        private final Set<Long> deptScopeIds;

        private ApplicableScopes(Set<Long> orgScopeIds, Set<Long> deptScopeIds) {
            this.orgScopeIds = orgScopeIds;
            this.deptScopeIds = deptScopeIds;
        }

        private Set<Long> orgScopeIds() {
            return orgScopeIds;
        }

        private Set<Long> deptScopeIds() {
            return deptScopeIds;
        }
    }
}
