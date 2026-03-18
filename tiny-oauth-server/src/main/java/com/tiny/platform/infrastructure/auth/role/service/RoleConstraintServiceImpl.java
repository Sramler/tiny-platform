package com.tiny.platform.infrastructure.auth.role.service;

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
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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
 * <p>默认不阻断赋权（dry-run）；当 {@code tiny.platform.auth.rbac3.enforce=true} 时，
 * 发现违例将抛出业务异常以阻断写操作。</p>
 */
@Service
public class RoleConstraintServiceImpl implements RoleConstraintService {

    private static final Logger logger = LoggerFactory.getLogger(RoleConstraintServiceImpl.class);

    @Value("${tiny.platform.auth.rbac3.enforce:false}")
    private boolean rbac3Enforce;

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

    public RoleConstraintServiceImpl(
        RoleHierarchyRepository roleHierarchyRepository,
        RoleMutexRepository roleMutexRepository,
        RoleConstraintViolationLogWriteService violationLogWriteService,
        RolePrerequisiteRepository rolePrerequisiteRepository,
        RoleCardinalityRepository roleCardinalityRepository,
        RoleAssignmentRepository roleAssignmentRepository
    ) {
        this.roleHierarchyRepository = roleHierarchyRepository;
        this.roleMutexRepository = roleMutexRepository;
        this.violationLogWriteService = violationLogWriteService;
        this.rolePrerequisiteRepository = rolePrerequisiteRepository;
        this.roleCardinalityRepository = roleCardinalityRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
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
        // Phase 2 最小落地：仅实现 role_mutex 互斥检查。
        // 当前阶段不抛出异常（不改变现有行为）；仅输出 debug 日志，供后续 dry-run / enforce 演进。
        if (tenantId == null || roleIdsToGrant == null || roleIdsToGrant.isEmpty()) {
            return;
        }

        boolean violated = false;

        Set<Long> directRoleIds = new LinkedHashSet<>(roleIdsToGrant);
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

        List<RoleMutex> rules = roleMutexRepository.findByTenantIdAndRoleIds(tenantId, effectiveRoleIds);
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
                log.setScopeType(scopeType == null ? "" : scopeType);
                log.setScopeId(scopeId);
                log.setViolationType("MUTEX");
                log.setViolationCode("ROLE_CONFLICT_MUTEX");
                log.setDirectRoleIds(joinIds(directRoleIds));
                log.setEffectiveRoleIds(joinIds(effectiveRoleIds));
                log.setDetails("{\"conflicts\":\"" + String.join(",", conflicts) + "\"}");
                violationLogWriteService.write(log);
                violated = true;
            }
        }

        // role_prerequisite: check prerequisites for directly granted roles against effective roles.
        List<RolePrerequisite> prerequisites =
            rolePrerequisiteRepository.findByTenantIdAndRoleIdIn(tenantId, directRoleIds);
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
                log.setScopeType(scopeType == null ? "" : scopeType);
                log.setScopeId(scopeId);
                log.setViolationType("PREREQUISITE");
                log.setViolationCode("ROLE_CONFLICT_PREREQUISITE_MISSING");
                log.setDirectRoleIds(joinIds(directRoleIds));
                log.setEffectiveRoleIds(joinIds(effectiveRoleIds));
                log.setDetails("{\"missingByRole\":\"" + missingByRole + "\"}");
                violationLogWriteService.write(log);
                violated = true;
            }
        }

        // role_cardinality: limit number of active assignments within scope.
        // Phase2 dry-run: only supports USER + TENANT path with best-effort counting.
        if ("USER".equals(principalType) && principalId != null && "TENANT".equals(scopeType) && scopeId != null) {
            String st = scopeType;
            List<RoleCardinality> limits =
                roleCardinalityRepository.findByTenantIdAndScopeTypeAndRoleIdIn(tenantId, st, effectiveRoleIds);
            if (!limits.isEmpty()) {
                LocalDateTime now = LocalDateTime.now();
                List<Long> existingRoleIds = roleAssignmentRepository.findActiveRoleIdsForUserInTenant(principalId, tenantId, now);

                List<String> exceeded = new ArrayList<>();
                for (RoleCardinality limit : limits) {
                    Long roleId = limit.getRoleId();
                    Integer max = limit.getMaxAssignments();
                    if (roleId == null || max == null || max <= 0) {
                        continue;
                    }

                    int current = roleAssignmentRepository
                        .findActiveUserIdsForRoleInTenant(roleId, tenantId, now)
                        .size();
                    int delta = existingRoleIds.contains(roleId) ? 0 : 1;
                    if (current + delta > max) {
                        exceeded.add(roleId + ":" + (current + delta) + "/" + max);
                    }
                }

                if (!exceeded.isEmpty()) {
                    logger.debug(
                        "RBAC3 role_cardinality exceeded (dry-run): tenantId={}, principalType={}, principalId={}, scopeType={}, scopeId={}, exceeded={}",
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
                    log.setScopeType(scopeType);
                    log.setScopeId(scopeId);
                    log.setViolationType("CARDINALITY");
                    log.setViolationCode("ROLE_CONFLICT_CARDINALITY_EXCEEDED");
                    log.setDirectRoleIds(joinIds(directRoleIds));
                    log.setEffectiveRoleIds(joinIds(effectiveRoleIds));
                    log.setDetails("{\"exceeded\":\"" + String.join(",", exceeded) + "\"}");
                    violationLogWriteService.write(log);
                    violated = true;
                }
            }
        }

        if (violated && shouldEnforceForTenant(tenantId)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "角色约束冲突，禁止赋权");
        }
    }

    private boolean shouldEnforceForTenant(Long tenantId) {
        if (!rbac3Enforce) {
            return false;
        }
        if (tenantId == null) {
            return false;
        }
        Set<Long> allow = getEnforceTenantIdSet();
        if (allow.isEmpty()) {
            return true; // backward compatible: enforce=true means global enforce by default
        }
        return allow.contains(tenantId);
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

    private Set<Long> expandRoleHierarchy(Long tenantId, Collection<Long> directRoleIds) {
        if (directRoleIds == null || directRoleIds.isEmpty()) {
            return Set.of();
        }

        Set<Long> effective = new LinkedHashSet<>(directRoleIds);
        Set<Long> frontier = new LinkedHashSet<>(directRoleIds);

        // Iterate upwards: child -> parent. Use visited set to avoid infinite loops on bad configs.
        // (Phase2 enforce will reject cycles at write time; until then we fail-safe by stopping expansion.)
        int guard = 0;
        while (!frontier.isEmpty() && guard++ < 50) {
            List<RoleHierarchy> edges = roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(tenantId, frontier);
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

