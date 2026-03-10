package com.tiny.platform.infrastructure.idempotent.metrics;

/**
 * 幂等性指标快照
 */
public record IdempotentMetricsSnapshot(
    long windowMinutes,
    long windowStartEpochMillis,
    long windowEndEpochMillis,
    long passCount,
    long hitCount,
    long successCount,
    long failureCount,
    long storeErrorCount,
    long validationRejectCount,
    long rejectCount,
    long totalCheckCount,
    double conflictRate,
    double storageErrorRate
) {
}
