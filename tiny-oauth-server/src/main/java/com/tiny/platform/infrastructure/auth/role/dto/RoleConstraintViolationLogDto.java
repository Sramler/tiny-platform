package com.tiny.platform.infrastructure.auth.role.dto;

import java.time.LocalDateTime;

public record RoleConstraintViolationLogDto(
    Long id,
    String principalType,
    Long principalId,
    String scopeType,
    Long scopeId,
    String violationType,
    String violationCode,
    String directRoleIds,
    String effectiveRoleIds,
    String details,
    LocalDateTime createdAt
) {}

