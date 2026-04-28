package com.tiny.platform.infrastructure.auth.role.dto;

public record PlatformRoleOptionDto(
    Long roleId,
    String code,
    String name,
    String description,
    boolean enabled,
    boolean builtin
) {
}
