package com.tiny.platform.infrastructure.idempotent.metrics;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentMetricsCleanupSchedulerTest {

    @Test
    void cleanup_task_should_delegate_to_repository() {
        DatabaseIdempotentMetricsRepository repository = mock(DatabaseIdempotentMetricsRepository.class);
        when(repository.cleanupExpiredMetrics()).thenReturn(3);

        IdempotentMetricsCleanupScheduler scheduler = new IdempotentMetricsCleanupScheduler(repository);
        scheduler.cleanupExpiredMetrics();

        verify(repository).cleanupExpiredMetrics();
    }
}
