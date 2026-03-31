package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.domain.RoleHierarchy;
import com.tiny.platform.infrastructure.auth.role.repository.RoleHierarchyRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 解析用户在某租户下的有效角色，仅基于 role_assignment。
 */
@Service
public class EffectiveRoleResolutionService {

    private final RoleRepository roleRepository;
    private final RoleAssignmentSyncService roleAssignmentSyncService;
    private final RoleHierarchyRepository roleHierarchyRepository;

    public EffectiveRoleResolutionService(RoleRepository roleRepository,
                                          RoleAssignmentSyncService roleAssignmentSyncService,
                                          RoleHierarchyRepository roleHierarchyRepository) {
        this.roleRepository = roleRepository;
        this.roleAssignmentSyncService = roleAssignmentSyncService;
        this.roleHierarchyRepository = roleHierarchyRepository;
    }

    @Transactional(readOnly = true)
    public List<Long> findEffectiveRoleIdsForUserInTenant(Long userId, Long tenantId) {
        return findEffectiveRoleIdsForUserInTenant(userId, tenantId, "TENANT", tenantId);
    }

    /**
     * 解析用户在租户内、指定 active scope 下的有效角色。
     *
     * <p>说明：</p>
     * <ul>
     *   <li>{@code activeScopeType=TENANT}：只包含租户级赋权（兼容既有行为）。</li>
     *   <li>{@code activeScopeType=ORG/DEPT}：包含租户级赋权 + 当前 scope 下赋权（不自动合并其它 org/dept）。</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public List<Long> findEffectiveRoleIdsForUserInTenant(Long userId, Long tenantId, String activeScopeType, Long activeScopeId) {
        if (userId == null || tenantId == null || tenantId <= 0) {
            return List.of();
        }
        String normalizedScopeType = activeScopeType == null ? "TENANT" : activeScopeType.trim().toUpperCase(java.util.Locale.ROOT);
        List<Long> assignmentRoleIds;
        if ("ORG".equals(normalizedScopeType) || "DEPT".equals(normalizedScopeType)) {
            List<Long> tenantRoles = roleAssignmentSyncService.findActiveRoleIdsForUserInScope(userId, tenantId, "TENANT", tenantId);
            List<Long> scopedRoles = roleAssignmentSyncService.findActiveRoleIdsForUserInScope(userId, tenantId, normalizedScopeType, activeScopeId);
            java.util.Set<Long> merged = new java.util.LinkedHashSet<>();
            merged.addAll(tenantRoles);
            merged.addAll(scopedRoles);
            assignmentRoleIds = merged.stream().toList();
        } else {
            assignmentRoleIds = roleAssignmentSyncService.findActiveRoleIdsForUserInScope(userId, tenantId, "TENANT", tenantId);
        }
        if (!assignmentRoleIds.isEmpty()) {
            return expandRoleHierarchy(tenantId, assignmentRoleIds).stream()
                .sorted()
                .collect(Collectors.toList());
        }
        return List.of();
    }

    @Transactional(readOnly = true)
    public List<Long> findEffectiveRoleIdsForUserInPlatform(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        List<Long> assignmentRoleIds = roleAssignmentSyncService.findActiveRoleIdsForUserInPlatform(userId);
        if (!assignmentRoleIds.isEmpty()) {
            return assignmentRoleIds.stream().distinct().sorted().collect(Collectors.toList());
        }
        return List.of();
    }

    @Transactional(readOnly = true)
    public Set<Role> findEffectiveRolesForUserInTenant(Long userId, Long tenantId) {
        List<Long> effectiveRoleIds = findEffectiveRoleIdsForUserInTenant(userId, tenantId);
        if (!effectiveRoleIds.isEmpty()) {
            return new LinkedHashSet<>(roleRepository.findByIdInAndTenantIdOrderByIdAsc(effectiveRoleIds, tenantId));
        }
        return Set.of();
    }

    @Transactional(readOnly = true)
    public Set<Role> findEffectiveRolesForUserInPlatform(Long userId) {
        List<Long> effectiveRoleIds = findEffectiveRoleIdsForUserInPlatform(userId);
        if (!effectiveRoleIds.isEmpty()) {
            return new LinkedHashSet<>(roleRepository.findByIdInAndTenantIdIsNullOrderByIdAsc(effectiveRoleIds));
        }
        return Set.of();
    }

    @Transactional(readOnly = true)
    public List<Long> findEffectiveUserIdsForRoleInTenant(Long roleId, Long tenantId) {
        Set<Long> effectiveRoleIds = expandChildRoleHierarchy(tenantId, List.of(roleId));
        if (effectiveRoleIds.isEmpty()) {
            return List.of();
        }
        return roleAssignmentSyncService.findActiveUserIdsForRolesInTenant(effectiveRoleIds, tenantId);
    }

    private Set<Long> expandRoleHierarchy(Long tenantId, Collection<Long> directRoleIds) {
        if (tenantId == null || tenantId <= 0 || directRoleIds == null || directRoleIds.isEmpty()) {
            return Set.of();
        }

        Set<Long> effective = new LinkedHashSet<>(directRoleIds);
        Set<Long> frontier = new LinkedHashSet<>(directRoleIds);

        // child -> parent expansion. Keep runtime semantics aligned with RBAC3 constraint checks.
        int guard = 0;
        while (!frontier.isEmpty() && guard++ < 50) {
            List<RoleHierarchy> edges = roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(tenantId, frontier);
            if (edges.isEmpty()) {
                break;
            }
            Set<Long> next = new LinkedHashSet<>();
            for (RoleHierarchy edge : edges) {
                Long parentRoleId = edge.getParentRoleId();
                if (parentRoleId == null) {
                    continue;
                }
                if (effective.add(parentRoleId)) {
                    next.add(parentRoleId);
                }
            }
            frontier = next;
        }

        return effective;
    }

    private Set<Long> expandChildRoleHierarchy(Long tenantId, Collection<Long> parentRoleIds) {
        if (tenantId == null || tenantId <= 0 || parentRoleIds == null || parentRoleIds.isEmpty()) {
            return Set.of();
        }

        Set<Long> effective = new LinkedHashSet<>(parentRoleIds);
        Set<Long> frontier = new LinkedHashSet<>(parentRoleIds);

        // parent -> child expansion. Used only for reverse lookup of effective principals.
        int guard = 0;
        while (!frontier.isEmpty() && guard++ < 50) {
            List<RoleHierarchy> edges = roleHierarchyRepository.findByTenantIdAndParentRoleIdIn(tenantId, frontier);
            if (edges.isEmpty()) {
                break;
            }
            Set<Long> next = new LinkedHashSet<>();
            for (RoleHierarchy edge : edges) {
                Long childRoleId = edge.getChildRoleId();
                if (childRoleId == null) {
                    continue;
                }
                if (effective.add(childRoleId)) {
                    next.add(childRoleId);
                }
            }
            frontier = next;
        }

        return effective;
    }
}
