package com.tiny.platform.infrastructure.idempotent.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 幂等分钟聚合指标清理任务。
 */
@Component
@ConditionalOnBean(DatabaseIdempotentMetricsRepository.class)
@ConditionalOnProperty(prefix = "tiny.idempotent.ops", name = "metrics-store", havingValue = "database", matchIfMissing = true)
public class IdempotentMetricsCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotentMetricsCleanupScheduler.class);

    private final DatabaseIdempotentMetricsRepository repository;

    public IdempotentMetricsCleanupScheduler(DatabaseIdempotentMetricsRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${tiny.idempotent.ops.metrics-cleanup-fixed-delay-ms:600000}")
    public void cleanupExpiredMetrics() {
        int removed = repository.cleanupExpiredMetrics();
        if (removed > 0) {
            log.info("Cleaned expired idempotent metric buckets count={}", removed);
        }
    }
}
