package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleRequestDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleResponseDto;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.repository.ResourceRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
public class RoleServiceImpl implements RoleService {
    private final RoleRepository roleRepository;
    private final ResourceRepository resourceRepository;
    private final TenantUserRepository tenantUserRepository;
    private final RoleAssignmentSyncService roleAssignmentSyncService;
    private final EffectiveRoleResolutionService effectiveRoleResolutionService;
    private final RoleConstraintService roleConstraintService;

    public RoleServiceImpl(RoleRepository roleRepository,
                           ResourceRepository resourceRepository,
                           TenantUserRepository tenantUserRepository,
                           RoleAssignmentSyncService roleAssignmentSyncService,
                           EffectiveRoleResolutionService effectiveRoleResolutionService,
                           RoleConstraintService roleConstraintService) {
        this.roleRepository = roleRepository;
        this.resourceRepository = resourceRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.roleAssignmentSyncService = roleAssignmentSyncService;
        this.effectiveRoleResolutionService = effectiveRoleResolutionService;
        this.roleConstraintService = roleConstraintService;
    }

    @Override
    public Page<RoleResponseDto> roles(RoleRequestDto query, Pageable pageable) {
        Long tenantId = requireTenantId();
        Specification<Role> spec = (root, cq, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            
            if (query.getName() != null && !query.getName().trim().isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + query.getName().trim() + "%"));
            }
            
            if (query.getCode() != null && !query.getCode().trim().isEmpty()) {
                predicates.add(cb.like(root.get("code"), "%" + query.getCode().trim() + "%"));
            }
            
            if (predicates.isEmpty()) {
                return cb.conjunction();
            } else {
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            }
        };
        Page<Role> page = roleRepository.findAll(spec, pageable);
        return page.map(this::toDto);
    }

    @Override
    public Optional<Role> findById(Long id) {
        return roleRepository.findByIdAndTenantId(id, requireTenantId());
    }

    @Override
    public RoleResponseDto create(RoleCreateUpdateDto dto) {
        Long tenantId = requireTenantId();
        Role role = new Role();
        BeanUtils.copyProperties(dto, role);
        role.setTenantId(tenantId);
        Role saved = roleRepository.save(role);
        if (dto.getUserIds() != null && !dto.getUserIds().isEmpty()) {
            updateRoleUsers(saved.getId(), dto.getUserIds());
        }
        return toDto(saved);
    }

    @Override
    @Transactional
    public RoleResponseDto update(Long id, RoleCreateUpdateDto dto) {
        Long tenantId = requireTenantId();
        Role role = roleRepository.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new RuntimeException("角色不存在"));
        BeanUtils.copyProperties(dto, role, "id");
        assertUsersVisibleInTenant(dto.getUserIds(), tenantId);
        // Phase 2 之前仅预留 RBAC3 约束校验入口，当前实现为 no-op。
        // 这里以“ROLE + TENANT”为粒度传入，将当前角色自身作为待检查对象，后续实现可以据此检查：
        // - 角色本身是否违反层级/互斥/基数等约束
        // - 或在将来迁移到 RoleAssignmentSyncService 时复用同一调用形状
        roleConstraintService.validateAssignmentsBeforeGrant(
            "ROLE",
            role.getId(),
            tenantId,
            "TENANT",
            tenantId,
            List.of(role.getId())
        );
        roleAssignmentSyncService.replaceRoleTenantUserAssignments(role.getId(), tenantId, dto.getUserIds());

        return toDto(roleRepository.save(role));
    }

    @Override
    public void delete(Long id) {
        roleRepository.findByIdAndTenantId(id, requireTenantId())
            .ifPresent(roleRepository::delete);
    }

    @Override
    public List<Long> getUserIdsByRoleId(Long roleId) {
        Long tenantId = requireTenantId();
        return effectiveRoleResolutionService.findEffectiveUserIdsForRoleInTenant(roleId, tenantId);
    }

    @Override
    @Transactional
    public void updateRoleUsers(Long roleId, List<Long> userIds) {
        Long tenantId = requireTenantId();
        // 查询角色是否存在
        Optional<Role> roleOpt = roleRepository.findByIdAndTenantId(roleId, tenantId);
        if (roleOpt.isEmpty()) return;
        assertUsersVisibleInTenant(userIds, tenantId);
        // Phase 2 之前仅预留 RBAC3 约束校验入口，当前实现为 no-op。
        // 这里同样以“ROLE + TENANT”为粒度传入，方便后续在真正接入 RBAC3 时，
        // 将此调用迁移/下沉到 RoleAssignmentSyncService 等统一赋权入口。
        roleConstraintService.validateAssignmentsBeforeGrant(
            "ROLE",
            roleId,
            tenantId,
            "TENANT",
            tenantId,
            List.of(roleId)
        );
        roleAssignmentSyncService.replaceRoleTenantUserAssignments(roleId, tenantId, userIds);
    }

    @Override
    public void updateRoleResources(Long roleId, List<Long> resourceIds) {
        Long tenantId = requireTenantId();
        Optional<Role> roleOpt = roleRepository.findByIdAndTenantId(roleId, tenantId);
        if (roleOpt.isEmpty()) return;
        roleRepository.deleteRoleResourceRelations(roleId, tenantId);
        if (resourceIds != null && !resourceIds.isEmpty()) {
            List<Resource> resources = resourceRepository.findByIdInAndTenantId(resourceIds, tenantId);
            for (Resource resource : resources) {
                roleRepository.addRoleResourceRelation(tenantId, roleId, resource.getId());
            }
        }
    }

    @Override
    public List<Long> getResourceIdsByRoleId(Long roleId) {
        return roleRepository.findResourceIdsByRoleId(roleId);
    }

    private RoleResponseDto toDto(Role role) {
        RoleResponseDto dto = new RoleResponseDto();
        BeanUtils.copyProperties(role, dto);
        dto.setRecordTenantId(role.getTenantId());
        return dto;
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getActiveTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户信息");
        }
        return tenantId;
    }

    private void assertUsersVisibleInTenant(List<Long> userIds, Long tenantId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        var requestedIds = new LinkedHashSet<>(userIds);
        var visibleIds = new LinkedHashSet<>(tenantUserRepository.findUserIdsByTenantIdAndUserIdInAndStatus(tenantId, requestedIds, "ACTIVE"));
        if (visibleIds.size() != requestedIds.size()) {
            throw new RuntimeException("部分用户不存在");
        }
    }
}
