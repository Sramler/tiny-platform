package com.tiny.platform.infrastructure.auth.role.dto;

public record RoleCardinalityRuleDto(
    Long roleId,
    String scopeType,
    Integer maxAssignments
) {}

