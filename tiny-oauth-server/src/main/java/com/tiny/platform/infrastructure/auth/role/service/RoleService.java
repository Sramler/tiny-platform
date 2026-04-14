package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleRequestDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface RoleService {
    Page<RoleResponseDto> roles(RoleRequestDto query, Pageable pageable);
    Optional<Role> findById(Long id);
    RoleResponseDto create(RoleCreateUpdateDto dto);
    RoleResponseDto update(Long id, RoleCreateUpdateDto dto);
    void delete(Long id);
    // 获取该角色下所有已分配用户ID
    List<Long> getUserIdsByRoleId(Long roleId);
    // 获取该角色在指定作用域下直接分配的用户ID
    List<Long> getDirectUserIdsByRoleId(Long roleId, String scopeType, Long scopeId);
    // 保存角色与用户的分配关系
    void updateRoleUsers(Long roleId, List<Long> userIds);
    // 按作用域保存角色与用户的分配关系
    void updateRoleUsers(Long roleId, String scopeType, Long scopeId, List<Long> userIds);
    // 保存角色与权限的分配关系（主契约；CARD-13D 已移除按 resource carrier id 写入的兼容路径）
    void updateRolePermissions(Long roleId, List<Long> permissionIds);

    // 获取角色已分配权限ID列表（主契约）
    List<Long> getPermissionIdsByRoleId(Long roleId);

    // 获取角色已分配资源ID列表（兼容契约）
    List<Long> getResourceIdsByRoleId(Long roleId);
} 
