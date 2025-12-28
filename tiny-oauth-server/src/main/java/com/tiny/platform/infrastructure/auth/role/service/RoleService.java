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
    // 保存角色与用户的分配关系
    void updateRoleUsers(Long roleId, List<Long> userIds);
    // 保存角色与资源的分配关系
    void updateRoleResources(Long roleId, List<Long> resourceIds);

    // 获取角色已分配资源ID列表
    List<Long> getResourceIdsByRoleId(Long roleId);
} 