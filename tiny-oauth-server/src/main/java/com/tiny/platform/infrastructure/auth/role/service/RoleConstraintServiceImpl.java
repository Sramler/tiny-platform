package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.domain.RoleHierarchy;
import com.tiny.platform.infrastructure.auth.role.domain.RoleConstraintViolationLog;
import com.tiny.platform.infrastructure.auth.role.domain.RoleCardinality;
import com.tiny.platform.infrastructure.auth.role.domain.RoleMutex;
import com.tiny.platform.infrastructure.auth.role.domain.RolePrerequisite;
import com.tiny.platform.infrastructure.auth.role.repository.RoleAssignmentRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleCardinalityRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleHierarchyRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleMutexRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RolePrerequisiteRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * RoleConstraintService 的最小实现骨架。
 *
 * <p>Phase2：实现 RBAC3 约束的 dry-run 校验与审计（写入 role_constraint_violation_log）。</p>
 *
 * <p>租户域：默认不阻断赋权（dry-run）；当 {@code tiny.platform.auth.rbac3.enforce=true} 时，
 * 发现违例将抛出业务异常以阻断写操作（可选 {@code enforce-tenant-ids} 白名单）。</p>
 *
 * <p>平台域（tenantId=null）：默认 {@code tiny.platform.auth.rbac3.enforce-platform-assignments=true}，
 * 与全局 {@code rbac3.enforce} 取或：任一为 true 且违例则阻断，避免平台赋权长期停留在“可配不生效”。</p>
 */
@Service
public class RoleConstraintServiceImpl implements RoleConstraintService {

    private static final Logger logger = LoggerFactory.getLogger(RoleConstraintServiceImpl.class);

    @Value("${tiny.platform.auth.rbac3.enforce:false}")
    private boolean rbac3Enforce;

    /**
     * When true (default), platform-scope assignments enforce RBAC3 violations even if {@link #rbac3Enforce} is false.
     */
    @Value("${tiny.platform.auth.rbac3.enforce-platform-assignments:true}")
    private boolean enforcePlatformAssignments;

    /**
     * Optional allowlist for enforcement. If empty, enforcement applies to all tenants.
     * Example: "1,2,3"
     */
    @Value("${tiny.platform.auth.rbac3.enforce-tenant-ids:}")
    private String rbac3EnforceTenantIds;

    private volatile Set<Long> rbac3EnforceTenantIdSet;

    private final RoleHierarchyRepository roleHierarchyRepository;
    private final RoleMutexRepository roleMutexRepository;
    private final RoleConstraintViolationLogWriteService violationLogWriteService;
    private final RolePrerequisiteRepository rolePrerequisiteRepository;
    private final RoleCardinalityRepository roleCardinalityRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RoleRepository roleRepository;

    public RoleConstraintServiceImpl(
        RoleHierarchyRepository roleHierarchyRepository,
        RoleMutexRepository roleMutexRepository,
        RoleConstraintViolationLogWriteService violationLogWriteService,
        RolePrerequisiteRepository rolePrerequisiteRepository,
        RoleCardinalityRepository roleCardinalityRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        RoleRepository roleRepository
    ) {
        this.roleHierarchyRepository = roleHierarchyRepository;
        this.roleMutexRepository = roleMutexRepository;
        this.violationLogWriteService = violationLogWriteService;
        this.rolePrerequisiteRepository = rolePrerequisiteRepository;
        this.roleCardinalityRepository = roleCardinalityRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public void validateAssignmentsBeforeGrant(
        String principalType,
        Long principalId,
        Long tenantId,
        String scopeType,
        Long scopeId,
        List<Long> roleIdsToGrant
    ) {
        if (roleIdsToGrant == null || roleIdsToGrant.isEmpty()) {
            return;
        }
        boolean platformScope = tenantId == null;
        if (!platformScope && (tenantId == null || tenantId <= 0)) {
            return;
        }
        String normalizedScopeType = scopeType == null ? "" : scopeType.trim().toUpperCase(java.util.Locale.ROOT);

        Set<Long> directRoleIds = new LinkedHashSet<>(roleIdsToGrant);
        if (platformScope) {
            assertPlatformAssignableRoleIds(directRoleIds);
        }

        boolean violated = false;
        List<String> violationSummaries = new ArrayList<>();
        Set<Long> effectiveRoleIds = expandRoleHierarchy(tenantId, directRoleIds);
        if (!effectiveRoleIds.equals(directRoleIds)) {
            logger.debug(
                "RBAC3 role_hierarchy expanded (dry-run): tenantId={}, principalType={}, principalId={}, scopeType={}, scopeId={}, directRoleIds={}, effectiveRoleIds={}",
                tenantId,
                principalType,
                principalId,
                scopeType,
                scopeId,
                directRoleIds,
                effectiveRoleIds
            );
        }

        List<RoleMutex> rules = platformScope
            ? roleMutexRepository.findByTenantIdIsNullAndRoleIds(effectiveRoleIds)
            : roleMutexRepository.findByTenantIdAndRoleIds(tenantId, effectiveRoleIds);
        if (!rules.isEmpty()) {
            List<String> conflicts = new ArrayList<>();
            for (RoleMutex rule : rules) {
                Long left = rule.getLeftRoleId();
                Long right = rule.getRightRoleId();
                if (left == null || right == null) {
                    continue;
                }
                if (effectiveRoleIds.contains(left) && effectiveRoleIds.contains(right)) {
                    conflicts.add(left + "&" + right);
                }
            }

            if (!conflicts.isEmpty()) {
                logger.debug(
                    "RBAC3 role_mutex violation (dry-run): tenantId={}, principalType={}, principalId={}, scopeType={}, scopeId={}, roleIdsToGrant={}, conflicts={}",
                    tenantId,
                    principalType,
                    principalId,
                    scopeType,
                    scopeId,
                    effectiveRoleIds,
                    conflicts
                );

                RoleConstraintViolationLog log = new RoleConstraintViolationLog();
                log.setTenantId(tenantId);
                log.setPrincipalType(principalType == null ? "" : principalType);
                log.setPrincipalId(principalId);
                log.setScopeType(normalizedScopeType);
                log.setScopeId(scopeId);
                log.setViolationType("MUTEX");
                log.setViolationCode("ROLE_CONFLICT_MUTEX");
                log.setDirectRoleIds(joinIds(directRoleIds));
                log.setEffectiveRoleIds(joinIds(effectiveRoleIds));
                log.setDetails("{\"conflicts\":\"" + String.join(",", conflicts) + "\"}");
                violationLogWriteService.write(log);
                violated = true;
                violationSummaries.add("互斥角色冲突: " + String.join(", ", conflicts));
            }
        }

        // role_prerequisite: check prerequisites for directly granted roles against effective roles.
        List<RolePrerequisite> prerequisites = platformScope
            ? rolePrerequisiteRepository.findByTenantIdIsNullAndRoleIdIn(directRoleIds)
            : rolePrerequisiteRepository.findByTenantIdAndRoleIdIn(tenantId, directRoleIds);
        if (!prerequisites.isEmpty()) {
            Map<Long, List<Long>> missingByRole = new LinkedHashMap<>();
            for (RolePrerequisite rp : prerequisites) {
                Long roleId = rp.getRoleId();
                Long requiredRoleId = rp.getRequiredRoleId();
                if (roleId == null || requiredRoleId == null) {
                    continue;
                }
                if (!effectiveRoleIds.contains(requiredRoleId)) {
                    missingByRole.computeIfAbsent(roleId, ignored -> new ArrayList<>()).add(requiredRoleId);
                }
            }

            if (!missingByRole.isEmpty()) {
                logger.debug(
                    "RBAC3 role_prerequisite missing (dry-run): tenantId={}, principalType={}, principalId={}, scopeType={}, scopeId={}, directRoleIds={}, missingByRole={}",
                    tenantId,
                    principalType,
                    principalId,
                    scopeType,
                    scopeId,
                    directRoleIds,
                    missingByRole
                );

                RoleConstraintViolationLog log = new RoleConstraintViolationLog();
                log.setTenantId(tenantId);
                log.setPrincipalType(principalType == null ? "" : principalType);
                log.setPrincipalId(principalId);
                log.setScopeType(normalizedScopeType);
                log.setScopeId(scopeId);
                log.setViolationType("PREREQUISITE");
                log.setViolationCode("ROLE_CONFLICT_PREREQUISITE_MISSING");
                log.setDirectRoleIds(joinIds(directRoleIds));
                log.setEffectiveRoleIds(joinIds(effectiveRoleIds));
                log.setDetails("{\"missingByRole\":\"" + missingByRole + "\"}");
                violationLogWriteService.write(log);
                violated = true;
                violationSummaries.add("缺少前置角色: " + formatMissingByRole(missingByRole));
            }
        }

        // role_cardinality: limit number of active assignments within scope.
        // Supports USER principal with PLATFORM/TENANT/ORG/DEPT scope types.
        if ("USER".equals(principalType)
            && principalId != null
            && !normalizedScopeType.isBlank()
            && (platformScope || scopeId != null)) {
            String st = normalizedScopeType;
            List<RoleCardinality> limits = platformScope
                ? roleCardinalityRepository.findByTenantIdIsNullAndScopeTypeAndRoleIdIn(st, effectiveRoleIds)
                : roleCardinalityRepository.findByTenantIdAndScopeTypeAndRoleIdIn(tenantId, st, effectiveRoleIds);
            if (!limits.isEmpty()) {
                LocalDateTime now = LocalDateTime.now();
                List<Long> existingRoleIds;
                if (platformScope) {
                    existingRoleIds = roleAssignmentRepository.findActiveRoleIdsForUserInPlatform(principalId, now);
                } else if ("TENANT".equals(st)) {
                    existingRoleIds = roleAssignmentRepository.findActiveRoleIdsForUserInTenant(principalId, tenantId, now);
                } else {
                    existingRoleIds = roleAssignmentRepository.findActiveRoleIdsForUserInScope(principalId, tenantId, st, scopeId, now);
                }

                List<String> exceeded = new ArrayList<>();
                for (RoleCardinality limit : limits) {
                    Long roleId = limit.getRoleId();
                    Integer max = limit.getMaxAssignments();
                    if (roleId == null || max == null || max <= 0) {
                        continue;
                    }

                    long current;
                    if (platformScope) {
                        current = roleAssignmentRepository.findActiveUserIdsForRoleInPlatform(roleId, now).size();
                    } else if ("TENANT".equals(st)) {
                        current = roleAssignmentRepository
                            .findActiveUserIdsForRoleInTenant(roleId, tenantId, now)
                            .size();
                    } else {
                        current = roleAssignmentRepository
                            .countActiveUsersForRoleInScope(roleId, tenantId, st, scopeId, now);
                    }
                    int delta = existingRoleIds.contains(roleId) ? 0 : 1;
                    if (current + delta > max) {
                        exceeded.add(roleId + ":" + (current + delta) + "/" + max);
                    }
                }

                if (!exceeded.isEmpty()) {
                    logger.debug(
                        "RBAC3 role_cardinality exceeded: tenantId={}, principalType={}, principalId={}, scopeType={}, scopeId={}, exceeded={}",
                        tenantId,
                        principalType,
                        principalId,
                        scopeType,
                        scopeId,
                        exceeded
                    );

                    RoleConstraintViolationLog log = new RoleConstraintViolationLog();
                    log.setTenantId(tenantId);
                    log.setPrincipalType(principalType);
                    log.setPrincipalId(principalId);
                    log.setScopeType(normalizedScopeType);
                    log.setScopeId(scopeId);
                    log.setViolationType("CARDINALITY");
                    log.setViolationCode("ROLE_CONFLICT_CARDINALITY_EXCEEDED");
                    log.setDirectRoleIds(joinIds(directRoleIds));
                    log.setEffectiveRoleIds(joinIds(effectiveRoleIds));
                    log.setDetails("{\"exceeded\":\"" + String.join(",", exceeded) + "\"}");
                    violationLogWriteService.write(log);
                    violated = true;
                    violationSummaries.add("角色基数超限: " + String.join(", ", exceeded));
                }
            }
        }

        if (violated && shouldEnforceForScope(tenantId, platformScope)) {
            String detail = buildEnforceMessage(normalizedScopeType, scopeId, violationSummaries);
            logger.info(
                "RBAC3 enforce blocked assignment: tenantId={}, principalType={}, principalId={}, scopeType={}, scopeId={}, detail={}",
                tenantId,
                principalType,
                principalId,
                normalizedScopeType,
                scopeId,
                detail
            );
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, detail);
        }
    }

    private boolean shouldEnforceForScope(Long tenantId, boolean platformScope) {
        if (platformScope) {
            return enforcePlatformAssignments || rbac3Enforce;
        }
        if (!rbac3Enforce) {
            return false;
        }
        Set<Long> allow = getEnforceTenantIdSet();
        if (allow.isEmpty()) {
            return true; // backward compatible: enforce=true means global enforce by default
        }
        return allow.contains(tenantId);
    }

    /**
     * Fail-closed：平台赋权不得引用租户角色 ID 或非 PLATFORM 模板角色，避免 tenant roleId 进入平台 RBAC3 语义。
     */
    private void assertPlatformAssignableRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        List<Long> idList = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Role> roles = roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(idList);
        if (roles.size() != idList.size()
            || roles.stream().anyMatch(role -> role.getTenantId() != null
                || !"PLATFORM".equalsIgnoreCase(role.getRoleLevel()))) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "平台赋权仅允许 tenant_id IS NULL + role_level=PLATFORM 的平台角色");
        }
    }

    private Set<Long> getEnforceTenantIdSet() {
        Set<Long> cached = rbac3EnforceTenantIdSet;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (rbac3EnforceTenantIdSet != null) {
                return rbac3EnforceTenantIdSet;
            }
            rbac3EnforceTenantIdSet = parseTenantIds(rbac3EnforceTenantIds);
            return rbac3EnforceTenantIdSet;
        }
    }

    private Set<Long> parseTenantIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        Set<Long> ids = new HashSet<>();
        for (String part : raw.split(",")) {
            String s = part == null ? "" : part.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                // ignore invalid entries to keep config fail-safe
            }
        }
        return ids.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(ids);
    }

    private Set<Long> parseCsvLongIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        Set<Long> ids = new HashSet<>();
        for (String part : raw.split(",")) {
            String s = part == null ? "" : part.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(s));
            } catch (NumberFormatException ignored) {
                // fail-safe: ignore invalid entries
            }
        }
        return ids.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(ids);
    }

    private String joinIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Long id : ids) {
            if (id != null) {
                joiner.add(String.valueOf(id));
            }
        }
        String joined = joiner.toString();
        return joined.isBlank() ? null : joined;
    }

    private String formatMissingByRole(Map<Long, List<Long>> missingByRole) {
        if (missingByRole == null || missingByRole.isEmpty()) {
            return "";
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : missingByRole.entrySet()) {
            entries.add(entry.getKey() + " <- " + joinIds(entry.getValue()));
        }
        return String.join("; ", entries);
    }

    private String buildEnforceMessage(String scopeType, Long scopeId, List<String> violationSummaries) {
        StringBuilder message = new StringBuilder("RBAC3 enforce 已阻断本次赋权");
        if (scopeType != null && !scopeType.isBlank()) {
            message.append("（scope=").append(scopeType);
            if (scopeId != null) {
                message.append(":").append(scopeId);
            }
            message.append("）");
        }
        if (violationSummaries != null && !violationSummaries.isEmpty()) {
            message.append(": ").append(String.join("；", violationSummaries));
        }
        return message.toString();
    }

    private Set<Long> expandRoleHierarchy(Long tenantId, Collection<Long> directRoleIds) {
        if (directRoleIds == null || directRoleIds.isEmpty()) {
            return Set.of();
        }
        boolean platformScope = tenantId == null;

        Set<Long> effective = new LinkedHashSet<>(directRoleIds);
        Set<Long> frontier = new LinkedHashSet<>(directRoleIds);

        // Iterate upwards: child -> parent. Use visited set to avoid infinite loops on bad configs.
        // (Phase2 enforce will reject cycles at write time; until then we fail-safe by stopping expansion.)
        int guard = 0;
        while (!frontier.isEmpty() && guard++ < 50) {
            List<RoleHierarchy> edges = platformScope
                ? roleHierarchyRepository.findByTenantIdIsNullAndChildRoleIdIn(frontier)
                : roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(tenantId, frontier);
            if (edges.isEmpty()) {
                break;
            }
            Set<Long> next = new LinkedHashSet<>();
            for (RoleHierarchy edge : edges) {
                Long parent = edge.getParentRoleId();
                if (parent == null) {
                    continue;
                }
                if (effective.add(parent)) {
                    next.add(parent);
                }
            }
            frontier = next;
        }

        return effective;
    }
}
