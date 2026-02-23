package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleRequestDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleResponseDto;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.repository.ResourceRepository;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Objects;

@Service
public class RoleServiceImpl implements RoleService {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    public RoleServiceImpl(RoleRepository roleRepository, UserRepository userRepository, ResourceRepository resourceRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.resourceRepository = resourceRepository;
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

        // 只查当前拥有该角色的用户ID
        List<Long> oldUserIds = userRepository.findUserIdsByRoleId(id, tenantId);
        for (Long userId : oldUserIds) {
            userRepository.deleteUserRoleRelation(userId, id, tenantId);
        }

        // 再为新分配的用户添加角色
        if (dto.getUserIds() != null) {
            for (Long userId : dto.getUserIds()) {
                userRepository.findByIdAndTenantId(userId, tenantId).ifPresent(user -> {
                    userRepository.addUserRoleRelation(tenantId, user.getId(), role.getId());
                });
            }
        }

        return toDto(roleRepository.save(role));
    }

    @Override
    public void delete(Long id) {
        roleRepository.findByIdAndTenantId(id, requireTenantId())
            .ifPresent(roleRepository::delete);
    }

    @Override
    public List<Long> getUserIdsByRoleId(Long roleId) {
        // 直接使用原生SQL查询用户ID列表，避免懒加载问题
        return userRepository.findUserIdsByRoleId(roleId, requireTenantId());
    }

    @Override
    @Transactional
    public void updateRoleUsers(Long roleId, List<Long> userIds) {
        Long tenantId = requireTenantId();
        // 查询角色是否存在
        Optional<Role> roleOpt = roleRepository.findByIdAndTenantId(roleId, tenantId);
        if (roleOpt.isEmpty()) return;
        
        // 1. 先删除该角色的所有用户关联
        List<Long> oldUserIds = userRepository.findUserIdsByRoleId(roleId, tenantId);
        for (Long userId : oldUserIds) {
            userRepository.deleteUserRoleRelation(userId, roleId, tenantId);
        }
        
        // 2. 为新分配的用户添加角色关联
        if (userIds != null && !userIds.isEmpty()) {
            for (Long userId : userIds) {
                // 检查用户是否存在
                if (userRepository.findByIdAndTenantId(userId, tenantId).isPresent()) {
                    userRepository.addUserRoleRelation(tenantId, userId, roleId);
                }
            }
        }
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
        return dto;
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new RuntimeException("缺少租户信息");
        }
        return tenantId;
    }
} 
