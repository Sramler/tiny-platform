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
        Specification<Role> spec = (root, cq, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            
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
        return roleRepository.findById(id);
    }

    @Override
    public RoleResponseDto create(RoleCreateUpdateDto dto) {
        Role role = new Role();
        BeanUtils.copyProperties(dto, role);
        if (dto.getUserIds() != null) {
            List<User> users = dto.getUserIds().stream()
                .map(uid -> userRepository.findById(uid).orElse(null))
                .filter(Objects::nonNull)
                .toList();
            role.setUsers(new HashSet<>(users));
        }
        return toDto(roleRepository.save(role));
    }

    @Override
    @Transactional
    public RoleResponseDto update(Long id, RoleCreateUpdateDto dto) {
        Role role = roleRepository.findById(id).orElseThrow(() -> new RuntimeException("角色不存在"));
        BeanUtils.copyProperties(dto, role, "id");

        // 只查当前拥有该角色的用户ID
        List<Long> oldUserIds = userRepository.findUserIdsByRoleId(id);
        for (Long userId : oldUserIds) {
            userRepository.findById(userId).ifPresent(user -> {
                user.getRoles().removeIf(r -> r.getId().equals(id));
                userRepository.save(user);
            });
        }

        // 再为新分配的用户添加角色
        if (dto.getUserIds() != null) {
            for (Long userId : dto.getUserIds()) {
                userRepository.findById(userId).ifPresent(user -> {
                    user.getRoles().add(role);
                    userRepository.save(user);
                });
            }
        }

        return toDto(roleRepository.save(role));
    }

    @Override
    public void delete(Long id) {
        roleRepository.deleteById(id);
    }

    @Override
    public List<Long> getUserIdsByRoleId(Long roleId) {
        // 直接使用原生SQL查询用户ID列表，避免懒加载问题
        return userRepository.findUserIdsByRoleId(roleId);
    }

    @Override
    @Transactional
    public void updateRoleUsers(Long roleId, List<Long> userIds) {
        // 查询角色是否存在
        Optional<Role> roleOpt = roleRepository.findById(roleId);
        if (roleOpt.isEmpty()) return;
        
        // 1. 先删除该角色的所有用户关联
        List<Long> oldUserIds = userRepository.findUserIdsByRoleId(roleId);
        for (Long userId : oldUserIds) {
            userRepository.deleteUserRoleRelation(userId, roleId);
        }
        
        // 2. 为新分配的用户添加角色关联
        if (userIds != null && !userIds.isEmpty()) {
            for (Long userId : userIds) {
                // 检查用户是否存在
                if (userRepository.findById(userId).isPresent()) {
                    userRepository.addUserRoleRelation(userId, roleId);
                }
            }
        }
    }

    @Override
    public void updateRoleResources(Long roleId, List<Long> resourceIds) {
        Optional<Role> roleOpt = roleRepository.findById(roleId);
        if (roleOpt.isEmpty()) return;
        Role role = roleOpt.get();
        List<Resource> resources = new ArrayList<>();
        if (resourceIds != null && !resourceIds.isEmpty()) {
            resources = resourceRepository.findAllById(resourceIds);
        }
        role.setResources(new HashSet<>(resources));
        roleRepository.save(role);
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
} 