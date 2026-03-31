package com.tiny.platform.core.oauth.service;

import java.time.LocalDateTime;

/**
 * 认证审计分页查询条件。
 */
public record AuthenticationAuditQuery(
    Long tenantId,
    Long userId,
    String username,
    String eventType,
    Boolean success,
    LocalDateTime startTime,
    LocalDateTime endTime
) {
}
