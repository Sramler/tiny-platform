package com.tiny.platform.infrastructure.auth.audit.service;

import java.time.LocalDateTime;

/**
 * 授权审计分页查询条件。
 */
public record AuthorizationAuditQuery(
    Long tenantId,
    String eventType,
    Long actorUserId,
    Long targetUserId,
    String result,
    String resourcePermission,
    String detailReason,
    String carrierType,
    Integer requirementGroup,
    String decision,
    LocalDateTime startTime,
    LocalDateTime endTime
) {
}
