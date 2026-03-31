package com.tiny.platform.core.oauth.service;

import java.util.List;

/**
 * 认证审计概览统计。
 */
public record AuthenticationAuditSummary(
    long totalCount,
    long successCount,
    long failureCount,
    long loginSuccessCount,
    long loginFailureCount,
    List<EventTypeCount> eventTypeCounts
) {

    public AuthenticationAuditSummary {
        eventTypeCounts = eventTypeCounts == null ? List.of() : List.copyOf(eventTypeCounts);
    }

    public record EventTypeCount(String eventType, long count) {}
}
