package com.tiny.platform.infrastructure.auth.user.dto;

import java.time.LocalDateTime;

public final class PlatformUserManagementDtos {

    private PlatformUserManagementDtos() {
    }

    public record PlatformUserCreateDto(
        Long userId,
        String displayName,
        String status
    ) {
    }

    public record PlatformUserListItemDto(
        Long userId,
        String username,
        String nickname,
        String displayName,
        boolean userEnabled,
        String platformStatus,
        boolean hasPlatformRoleAssignment,
        LocalDateTime updatedAt
    ) {
    }

    public record PlatformUserDetailDto(
        Long userId,
        String username,
        String nickname,
        String displayName,
        String email,
        String phone,
        boolean userEnabled,
        boolean accountNonExpired,
        boolean accountNonLocked,
        boolean credentialsNonExpired,
        String platformStatus,
        boolean hasPlatformRoleAssignment,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
    }
}
