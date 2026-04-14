package com.tiny.platform.infrastructure.auth.permission.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class PermissionManagementDtos {

    private PermissionManagementDtos() {
    }

    public record PermissionRoleBindingDto(
        Long roleId,
        String roleCode,
        String roleName
    ) {
    }

    public record PermissionListItemDto(
        Long id,
        String permissionCode,
        String permissionName,
        String moduleCode,
        boolean enabled,
        int boundRoleCount,
        LocalDateTime updatedAt
    ) {
    }

    public record PermissionDetailDto(
        Long id,
        String permissionCode,
        String permissionName,
        String moduleCode,
        boolean enabled,
        LocalDateTime updatedAt,
        List<PermissionRoleBindingDto> boundRoles
    ) {
    }
}
