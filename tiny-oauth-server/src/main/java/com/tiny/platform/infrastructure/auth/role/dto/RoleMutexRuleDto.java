package com.tiny.platform.infrastructure.auth.role.dto;

public record RoleMutexRuleDto(
    Long leftRoleId,
    Long rightRoleId
) {}

