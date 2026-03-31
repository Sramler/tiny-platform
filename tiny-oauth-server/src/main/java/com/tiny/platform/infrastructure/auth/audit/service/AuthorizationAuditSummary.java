package com.tiny.platform.infrastructure.auth.audit.service;

import java.util.List;

/**
 * 授权审计概览统计。
 */
public record AuthorizationAuditSummary(
    long totalCount,
    long successCount,
    long deniedCount,
    List<EventTypeCount> eventTypeCounts
) {

    public AuthorizationAuditSummary {
        eventTypeCounts = eventTypeCounts == null ? List.of() : List.copyOf(eventTypeCounts);
    }

    public record EventTypeCount(String eventType, long count) {}
}
