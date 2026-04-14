package com.tiny.platform.infrastructure.auth.permission.service;

import com.tiny.platform.infrastructure.auth.permission.dto.PermissionManagementDtos.PermissionDetailDto;
import com.tiny.platform.infrastructure.auth.permission.dto.PermissionManagementDtos.PermissionListItemDto;

import java.util.List;
import java.util.Optional;

public interface PermissionManagementService {

    List<PermissionListItemDto> list(String keyword, String moduleCode, Boolean enabled);

    Optional<PermissionDetailDto> get(Long permissionId);

    boolean updateEnabled(Long permissionId, boolean enabled);
}
