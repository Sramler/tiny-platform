package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.RoleCardinality;
import com.tiny.platform.infrastructure.auth.role.domain.RoleHierarchy;
import com.tiny.platform.infrastructure.auth.role.domain.RoleMutex;
import com.tiny.platform.infrastructure.auth.role.domain.RolePrerequisite;
import com.tiny.platform.infrastructure.auth.role.repository.RoleCardinalityRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleHierarchyRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RoleMutexRepository;
import com.tiny.platform.infrastructure.auth.role.repository.RolePrerequisiteRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase2 RBAC3: rule write-side guard & normalization.
 *
 * <p>Provide a single place to enforce basic invariants (cycle prevention, normalization, validation)
 * before rules are persisted and later used in runtime enforcement.</p>
 */
@Service
public class RoleConstraintRuleAdminService {

    private final RoleHierarchyRepository roleHierarchyRepository;
    private final RoleMutexRepository roleMutexRepository;
    private final RoleCardinalityRepository roleCardinalityRepository;
    private final RolePrerequisiteRepository rolePrerequisiteRepository;

    public RoleConstraintRuleAdminService(
        RoleHierarchyRepository roleHierarchyRepository,
        RoleMutexRepository roleMutexRepository,
        RoleCardinalityRepository roleCardinalityRepository,
        RolePrerequisiteRepository rolePrerequisiteRepository
    ) {
        this.roleHierarchyRepository = roleHierarchyRepository;
        this.roleMutexRepository = roleMutexRepository;
        this.roleCardinalityRepository = roleCardinalityRepository;
        this.rolePrerequisiteRepository = rolePrerequisiteRepository;
    }

    @Transactional
    public void upsertRoleHierarchyEdge(Long tenantId, Long childRoleId, Long parentRoleId) {
        requirePositive(tenantId, "tenantId");
        requirePositive(childRoleId, "childRoleId");
        requirePositive(parentRoleId, "parentRoleId");
        if (Objects.equals(childRoleId, parentRoleId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "role_hierarchy 不允许 parent=child");
        }
        if (wouldCreateCycle(tenantId, childRoleId, parentRoleId)) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "role_hierarchy 不允许形成环");
        }

        roleHierarchyRepository.deleteByTenantIdAndChildRoleIdAndParentRoleId(tenantId, childRoleId, parentRoleId);
        RoleHierarchy edge = new RoleHierarchy();
        edge.setTenantId(tenantId);
        edge.setChildRoleId(childRoleId);
        edge.setParentRoleId(parentRoleId);
        roleHierarchyRepository.save(edge);
    }

    @Transactional(readOnly = true)
    public List<RoleHierarchy> listRoleHierarchyEdges(Long tenantId) {
        requirePositive(tenantId, "tenantId");
        return roleHierarchyRepository.findByTenantId(tenantId);
    }

    @Transactional
    public void deleteRoleHierarchyEdge(Long tenantId, Long childRoleId, Long parentRoleId) {
        requirePositive(tenantId, "tenantId");
        requirePositive(childRoleId, "childRoleId");
        requirePositive(parentRoleId, "parentRoleId");
        roleHierarchyRepository.deleteByTenantIdAndChildRoleIdAndParentRoleId(tenantId, childRoleId, parentRoleId);
    }

    @Transactional
    public void upsertRoleMutex(Long tenantId, Long roleIdA, Long roleIdB) {
        requirePositive(tenantId, "tenantId");
        requirePositive(roleIdA, "roleIdA");
        requirePositive(roleIdB, "roleIdB");
        if (Objects.equals(roleIdA, roleIdB)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "role_mutex 不允许 self-mutex");
        }
        long left = Math.min(roleIdA, roleIdB);
        long right = Math.max(roleIdA, roleIdB);

        roleMutexRepository.deleteByTenantIdAndLeftRoleIdAndRightRoleId(tenantId, left, right);
        RoleMutex rule = new RoleMutex();
        rule.setTenantId(tenantId);
        rule.setLeftRoleId(left);
        rule.setRightRoleId(right);
        roleMutexRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public List<RoleMutex> listRoleMutexRules(Long tenantId) {
        requirePositive(tenantId, "tenantId");
        return roleMutexRepository.findByTenantId(tenantId);
    }

    @Transactional
    public void deleteRoleMutex(Long tenantId, Long roleIdA, Long roleIdB) {
        requirePositive(tenantId, "tenantId");
        requirePositive(roleIdA, "roleIdA");
        requirePositive(roleIdB, "roleIdB");
        long left = Math.min(roleIdA, roleIdB);
        long right = Math.max(roleIdA, roleIdB);
        roleMutexRepository.deleteByTenantIdAndLeftRoleIdAndRightRoleId(tenantId, left, right);
    }

    @Transactional
    public void upsertRoleCardinality(Long tenantId, Long roleId, String scopeType, int maxAssignments) {
        requirePositive(tenantId, "tenantId");
        requirePositive(roleId, "roleId");
        String st = scopeType == null ? "" : scopeType.trim();
        if (!"TENANT".equals(st) && !"PLATFORM".equals(st)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "role_cardinality 暂仅支持 scope_type=TENANT/PLATFORM");
        }
        if (maxAssignments <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "role_cardinality.max_assignments 必须为正整数");
        }

        roleCardinalityRepository.deleteByTenantIdAndRoleIdAndScopeType(tenantId, roleId, st);

        RoleCardinality rc = new RoleCardinality();
        rc.setTenantId(tenantId);
        rc.setRoleId(roleId);
        rc.setScopeType(st);
        rc.setMaxAssignments(maxAssignments);
        roleCardinalityRepository.save(rc);
    }

    @Transactional(readOnly = true)
    public List<RoleCardinality> listRoleCardinalityRules(Long tenantId) {
        requirePositive(tenantId, "tenantId");
        return roleCardinalityRepository.findByTenantId(tenantId);
    }

    @Transactional
    public void deleteRoleCardinality(Long tenantId, Long roleId, String scopeType) {
        requirePositive(tenantId, "tenantId");
        requirePositive(roleId, "roleId");
        String st = scopeType == null ? "" : scopeType.trim();
        roleCardinalityRepository.deleteByTenantIdAndRoleIdAndScopeType(tenantId, roleId, st);
    }

    @Transactional
    public void upsertRolePrerequisite(Long tenantId, Long roleId, Long requiredRoleId) {
        requirePositive(tenantId, "tenantId");
        requirePositive(roleId, "roleId");
        requirePositive(requiredRoleId, "requiredRoleId");
        if (Objects.equals(roleId, requiredRoleId)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "role_prerequisite 不允许 self prerequisite");
        }

        rolePrerequisiteRepository.deleteByTenantIdAndRoleIdAndRequiredRoleId(tenantId, roleId, requiredRoleId);
        RolePrerequisite rp = new RolePrerequisite();
        rp.setTenantId(tenantId);
        rp.setRoleId(roleId);
        rp.setRequiredRoleId(requiredRoleId);
        rolePrerequisiteRepository.save(rp);
    }

    @Transactional(readOnly = true)
    public List<RolePrerequisite> listRolePrerequisiteRules(Long tenantId) {
        requirePositive(tenantId, "tenantId");
        return rolePrerequisiteRepository.findByTenantId(tenantId);
    }

    @Transactional
    public void deleteRolePrerequisite(Long tenantId, Long roleId, Long requiredRoleId) {
        requirePositive(tenantId, "tenantId");
        requirePositive(roleId, "roleId");
        requirePositive(requiredRoleId, "requiredRoleId");
        rolePrerequisiteRepository.deleteByTenantIdAndRoleIdAndRequiredRoleId(tenantId, roleId, requiredRoleId);
    }

    private void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, field + " 必须为正整数");
        }
    }

    /**
     * Adding edge (child -> parent) creates a cycle iff parent can reach child via existing parent links.
     */
    private boolean wouldCreateCycle(Long tenantId, Long childRoleId, Long parentRoleId) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(parentRoleId);
        visited.add(parentRoleId);

        int guard = 0;
        while (!queue.isEmpty() && guard++ < 200) {
            Long node = queue.removeFirst();
            if (Objects.equals(node, childRoleId)) {
                return true;
            }
            List<RoleHierarchy> edges = roleHierarchyRepository.findByTenantIdAndChildRoleIdIn(tenantId, List.of(node));
            for (RoleHierarchy edge : edges) {
                Long next = edge.getParentRoleId();
                if (next == null) {
                    continue;
                }
                if (visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return false;
    }
}

