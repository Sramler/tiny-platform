package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleRequestDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleResponseDto;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.CarrierProjectionRepository;
import com.tiny.platform.infrastructure.auth.resource.repository.RoleResourcePermissionBindingView;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class RoleServiceImpl implements RoleService {
    private static final String ROLE_LEVEL_PLATFORM = "PLATFORM";
    private static final String ROLE_LEVEL_TENANT = "TENANT";
    private final RoleRepository roleRepository;
    private final CarrierProjectionRepository carrierProjectionRepository;
    private final TenantUserRepository tenantUserRepository;
    private final RoleAssignmentSyncService roleAssignmentSyncService;
    private final EffectiveRoleResolutionService effectiveRoleResolutionService;
    private final RoleConstraintService roleConstraintService;

    public RoleServiceImpl(RoleRepository roleRepository,
                           CarrierProjectionRepository carrierProjectionRepository,
                           TenantUserRepository tenantUserRepository,
                           RoleAssignmentSyncService roleAssignmentSyncService,
                           EffectiveRoleResolutionService effectiveRoleResolutionService,
                           RoleConstraintService roleConstraintService) {
        this.roleRepository = roleRepository;
        this.carrierProjectionRepository = carrierProjectionRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.roleAssignmentSyncService = roleAssignmentSyncService;
        this.effectiveRoleResolutionService = effectiveRoleResolutionService;
        this.roleConstraintService = roleConstraintService;
    }

    @Override
    public Page<RoleResponseDto> roles(RoleRequestDto query, Pageable pageable) {
        Long tenantId = currentManagedTenantId();
        String roleLevel = currentRoleLevel();
        Specification<Role> spec = (root, cq, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (tenantId == null) {
                predicates.add(cb.isNull(root.get("tenantId")));
            } else {
                predicates.add(cb.equal(root.get("tenantId"), tenantId));
            }
            predicates.add(cb.equal(root.get("roleLevel"), roleLevel));
            
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
        return findManagedRole(id);
    }

    @Override
    public RoleResponseDto create(RoleCreateUpdateDto dto) {
        assertPlatformTemplateUsersSupported(dto.getUserIds());
        Long tenantId = currentManagedTenantId();
        Role role = new Role();
        BeanUtils.copyProperties(dto, role);
        role.setTenantId(tenantId);
        role.setRoleLevel(currentRoleLevel());
        Role saved = roleRepository.save(role);
        if (dto.getUserIds() != null && !dto.getUserIds().isEmpty()) {
            updateRoleUsers(saved.getId(), dto.getUserIds());
        }
        return toDto(saved);
    }

    @Override
    @Transactional
    public RoleResponseDto update(Long id, RoleCreateUpdateDto dto) {
        assertPlatformTemplateUsersSupported(dto.getUserIds());
        Long tenantId = requireTenantId();
        Role role = findManagedRole(id).orElseThrow(() -> new RuntimeException("角色不存在"));
        BeanUtils.copyProperties(dto, role, "id");
        role.setTenantId(currentManagedTenantId());
        role.setRoleLevel(currentRoleLevel());
        assertUsersVisibleInTenant(dto.getUserIds(), tenantId);
        updateRoleUsers(role.getId(), dto.getUserIds());

        return toDto(roleRepository.save(role));
    }

    @Override
    public void delete(Long id) {
        findManagedRole(id)
            .ifPresent(roleRepository::delete);
    }

    @Override
    public List<Long> getUserIdsByRoleId(Long roleId) {
        if (TenantContext.isPlatformScope()) {
            return List.of();
        }
        Long tenantId = requireTenantId();
        return effectiveRoleResolutionService.findEffectiveUserIdsForRoleInTenant(roleId, tenantId);
    }

    @Override
    @Transactional
    public void updateRoleUsers(Long roleId, List<Long> userIds) {
        updateRoleUsers(roleId, "TENANT", null, userIds);
    }

    @Override
    public List<Long> getDirectUserIdsByRoleId(Long roleId, String scopeType, Long scopeId) {
        if (TenantContext.isPlatformScope()) {
            return List.of();
        }
        Long tenantId = requireTenantId();
        Optional<Role> roleOpt = findManagedRole(roleId);
        if (roleOpt.isEmpty()) {
            return List.of();
        }
        return roleAssignmentSyncService.findActiveUserIdsForRoleInScope(roleId, tenantId, scopeType, scopeId);
    }

    @Override
    @Transactional
    public void updateRoleUsers(Long roleId, String scopeType, Long scopeId, List<Long> userIds) {
        if (TenantContext.isPlatformScope()) {
            if (userIds == null || userIds.isEmpty()) {
                return;
            }
            throw new RuntimeException("平台模板角色不支持直接分配用户");
        }
        Long tenantId = requireTenantId();
        // 查询角色是否存在
        Optional<Role> roleOpt = findManagedRole(roleId);
        if (roleOpt.isEmpty()) return;
        assertUsersVisibleInTenant(userIds, tenantId);
        roleAssignmentSyncService.replaceRoleScopedUserAssignments(roleId, tenantId, scopeType, scopeId, userIds);
    }

    @Override
    @Transactional
    public void updateRoleResources(Long roleId, List<Long> resourceIds) {
        updateRolePermissions(roleId, new ArrayList<>(resolvePermissionIdsFromResourceIds(resourceIds)));
    }

    @Override
    @Transactional
    public void updateRolePermissions(Long roleId, List<Long> permissionIds) {
        Long tenantId = currentManagedTenantId();
        Optional<Role> roleOpt = findManagedRole(roleId);
        if (roleOpt.isEmpty()) return;
        LinkedHashSet<Long> normalizedPermissionIds = permissionIds == null
            ? new LinkedHashSet<>()
            : permissionIds.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        // Validate first, then mutate relations to avoid "deleted old grants, failed new grants" middle state.
        roleRepository.deleteRolePermissionRelations(roleId, tenantId);
        normalizedPermissionIds.forEach(permissionId ->
            roleRepository.addRolePermissionRelationByPermissionId(tenantId, roleId, permissionId)
        );
    }

    @Override
    public List<Long> getResourceIdsByRoleId(Long roleId) {
        if (findManagedRole(roleId).isEmpty()) {
            return List.of();
        }
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

    private Long currentManagedTenantId() {
        return TenantContext.isPlatformScope() ? null : requireTenantId();
    }

    private String currentRoleLevel() {
        return TenantContext.isPlatformScope() ? ROLE_LEVEL_PLATFORM : ROLE_LEVEL_TENANT;
    }

    private Optional<Role> findManagedRole(Long id) {
        return roleRepository.findById(id)
            .filter(this::matchesManagedScope);
    }

    private boolean matchesManagedScope(Role role) {
        if (role == null) {
            return false;
        }
        return Objects.equals(role.getTenantId(), currentManagedTenantId())
            && currentRoleLevel().equalsIgnoreCase(role.getRoleLevel());
    }

    private void assertPlatformTemplateUsersSupported(List<Long> userIds) {
        if (TenantContext.isPlatformScope() && userIds != null && !userIds.isEmpty()) {
            throw new RuntimeException("平台模板角色不支持直接分配用户");
        }
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

    private void assertPermissionBindingsReady(List<Long> requestedResourceIds,
                                               List<RoleResourcePermissionBindingView> resources) {
        List<RoleResourcePermissionBindingView> resolvedResources = resources == null ? List.of() : resources;
        LinkedHashSet<Long> requestedIds = requestedResourceIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(requestedResourceIds);
        LinkedHashSet<Long> resolvedIds = resolvedResources.stream()
            .map(RoleResourcePermissionBindingView::getId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (resolvedIds.size() != requestedIds.size()) {
            List<Long> missingIds = requestedIds.stream()
                .filter(id -> !resolvedIds.contains(id))
                .toList();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "资源作用域校验失败: " + missingIds);
        }
        List<Long> invalidResourceIds = resolvedResources.stream()
            .filter(resource -> resource != null && StringUtils.hasText(resource.getPermission()))
            .filter(resource -> resource.getRequiredPermissionId() == null)
            .map(RoleResourcePermissionBindingView::getId)
            .filter(Objects::nonNull)
            .toList();
        if (!invalidResourceIds.isEmpty()) {
            throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "资源权限绑定缺失，请先修复 required_permission_id 后再授权: " + invalidResourceIds
            );
        }
    }

    private LinkedHashSet<Long> resolvePermissionIdsFromResourceIds(List<Long> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return new LinkedHashSet<>();
        }
        String resourceLevel = currentRoleLevel();
        Long tenantId = currentManagedTenantId();
        List<RoleResourcePermissionBindingView> resources = carrierProjectionRepository.findPermissionBindingViewsByIdsAndScope(
            resourceIds,
            tenantId,
            resourceLevel
        );
        assertPermissionBindingsReady(resourceIds, resources);
        LinkedHashSet<Long> permissionIds = new LinkedHashSet<>();
        for (RoleResourcePermissionBindingView resource : resources) {
            if (resource != null && resource.getRequiredPermissionId() != null) {
                permissionIds.add(resource.getRequiredPermissionId());
            }
        }
        return permissionIds;
    }
}
