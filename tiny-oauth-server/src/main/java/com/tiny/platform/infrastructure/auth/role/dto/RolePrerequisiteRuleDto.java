package com.tiny.platform.infrastructure.auth.role.dto;

public record RolePrerequisiteRuleDto(
    Long roleId,
    Long requiredRoleId
) {}

