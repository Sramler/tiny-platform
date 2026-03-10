package com.tiny.platform.infrastructure.idempotent.metrics;

/**
 * 幂等指标聚合结果。
 */
public record IdempotentMetricsAggregate(
    long passCount,
    long hitCount,
    long successCount,
    long failureCount,
    long storeErrorCount,
    long validationRejectCount
) {

    public static IdempotentMetricsAggregate empty() {
        return new IdempotentMetricsAggregate(0, 0, 0, 0, 0, 0);
    }
}
