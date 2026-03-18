package com.tiny.platform.infrastructure.auth.role.dto;

public record RoleHierarchyEdgeDto(
    Long childRoleId,
    Long parentRoleId
) {}

