package com.tiny.platform.infrastructure.idempotent.metrics;

import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentMetricsServiceTest {

    @Test
    void snapshot_should_aggregate_counts_and_rates() {
        MutableClock clock = new MutableClock("2026-03-08T12:00:00Z");
        IdempotentMetricsService metricsService = new IdempotentMetricsService(
            new SimpleMeterRegistry(),
            Duration.ofMinutes(15),
            clock
        );
        IdempotentKey userCreate = IdempotentKey.of("http", "200|8|POST /sys/users", "k1");
        IdempotentKey userUpdate = IdempotentKey.of("http", "200|8|PUT /sys/users/1", "k2");

        metricsService.recordPass(userCreate);
        metricsService.recordSuccess(userCreate);
        metricsService.recordDuplicate(userCreate, IdempotentState.SUCCESS);
        metricsService.recordPass(userUpdate);
        metricsService.recordFailure(userUpdate);
        metricsService.recordStoreError(userUpdate);
        metricsService.recordValidationRejected("format");

        IdempotentMetricsSnapshot snapshot = metricsService.snapshot();
        assertThat(snapshot.windowMinutes()).isEqualTo(15);
        assertThat(snapshot.windowEndEpochMillis() - snapshot.windowStartEpochMillis())
            .isEqualTo(Duration.ofMinutes(15).toMillis());
        assertThat(snapshot.passCount()).isEqualTo(2);
        assertThat(snapshot.hitCount()).isEqualTo(1);
        assertThat(snapshot.successCount()).isEqualTo(1);
        assertThat(snapshot.failureCount()).isEqualTo(1);
        assertThat(snapshot.storeErrorCount()).isEqualTo(1);
        assertThat(snapshot.validationRejectCount()).isEqualTo(1);
        assertThat(snapshot.rejectCount()).isEqualTo(2);
        assertThat(snapshot.totalCheckCount()).isEqualTo(5);
        assertThat(snapshot.conflictRate()).isEqualTo(1.0d / 3.0d);
        assertThat(snapshot.storageErrorRate()).isEqualTo(1.0d / 5.0d);
    }

    @Test
    void top_scopes_should_strip_tenant_user_prefix_and_apply_limit() {
        MutableClock clock = new MutableClock("2026-03-08T12:00:00Z");
        IdempotentMetricsService metricsService = new IdempotentMetricsService(
            null,
            Duration.ofMinutes(5),
            clock
        );
        IdempotentKey create = IdempotentKey.of("http", "200|8|POST /sys/users", "k1");
        IdempotentKey update = IdempotentKey.of("http", "200|9|PUT /sys/users/1", "k2");

        metricsService.recordPass(create);
        metricsService.recordDuplicate(create, IdempotentState.SUCCESS);
        metricsService.recordPass(create);
        metricsService.recordPass(update);

        List<Map<String, Object>> topScopes = metricsService.topScopes(1);
        assertThat(topScopes).hasSize(1);
        assertThat(topScopes.getFirst()).containsEntry("key", "POST /sys/users");
        assertThat(topScopes.getFirst()).containsEntry("count", 3L);
    }

    @Test
    void snapshot_should_only_keep_recent_window_and_evict_stale_hot_scopes() {
        MutableClock clock = new MutableClock("2026-03-08T12:00:00Z");
        IdempotentMetricsService metricsService = new IdempotentMetricsService(
            null,
            Duration.ofMinutes(5),
            clock
        );
        IdempotentKey staleKey = IdempotentKey.of("http", "200|8|POST /sys/users", "stale");
        IdempotentKey freshKey = IdempotentKey.of("http", "200|8|POST /process/start", "fresh");

        metricsService.recordPass(staleKey);
        metricsService.recordDuplicate(staleKey, IdempotentState.SUCCESS);

        clock.advance(Duration.ofMinutes(6));

        metricsService.recordPass(freshKey);
        metricsService.recordStoreError(freshKey);

        IdempotentMetricsSnapshot snapshot = metricsService.snapshot();
        assertThat(snapshot.passCount()).isEqualTo(1);
        assertThat(snapshot.hitCount()).isZero();
        assertThat(snapshot.storeErrorCount()).isEqualTo(1);
        assertThat(snapshot.totalCheckCount()).isEqualTo(2);

        List<Map<String, Object>> topScopes = metricsService.topScopes(5);
        assertThat(topScopes).containsExactly(Map.of("key", "POST /process/start", "count", 2L));
    }

    @Test
    void service_should_delegate_window_queries_and_writes_to_database_repository_when_present() {
        MutableClock clock = new MutableClock("2026-03-08T12:34:00Z");
        DatabaseIdempotentMetricsRepository repository = mock(DatabaseIdempotentMetricsRepository.class);
        IdempotentMetricsService metricsService = new IdempotentMetricsService(
            null,
            Duration.ofMinutes(30),
            clock,
            repository
        );
        IdempotentKey key = IdempotentKey.of("http", "200|8|POST /sys/users", "k1");
        LocalDateTime bucketMinute = LocalDateTime.of(2026, 3, 8, 12, 34);
        LocalDateTime windowStart = LocalDateTime.of(2026, 3, 8, 12, 5);
        LocalDateTime windowEnd = LocalDateTime.of(2026, 3, 8, 12, 35);

        metricsService.recordPass(key);
        metricsService.recordStoreError(key);
        metricsService.recordValidationRejected("format");

        verify(repository).recordPass(bucketMinute, 200L, "POST /sys/users");
        verify(repository).recordStoreError(bucketMinute, 200L, "POST /sys/users");
        verify(repository).recordValidationRejected(bucketMinute, null);

        when(repository.aggregateWindow(8L, windowStart, windowEnd))
            .thenReturn(new IdempotentMetricsAggregate(6, 2, 5, 1, 1, 1));
        when(repository.topScopes(8L, windowStart, windowEnd, 3))
            .thenReturn(List.of(Map.of("key", "POST /sys/users", "count", 4L)));

        IdempotentMetricsSnapshot snapshot = metricsService.snapshot(8L);
        assertThat(snapshot.passCount()).isEqualTo(6);
        assertThat(snapshot.hitCount()).isEqualTo(2);
        assertThat(snapshot.windowStartEpochMillis()).isEqualTo(windowStart.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli());
        assertThat(snapshot.windowEndEpochMillis()).isEqualTo(windowEnd.atZone(ZoneId.of("UTC")).toInstant().toEpochMilli());
        assertThat(metricsService.topScopes(3, 8L)).containsExactly(Map.of("key", "POST /sys/users", "count", 4L));
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> currentInstant;
        private final ZoneId zone;

        private MutableClock(String initialInstant) {
            this(Instant.parse(initialInstant), ZoneId.of("UTC"));
        }

        private MutableClock(Instant initialInstant, ZoneId zone) {
            this.currentInstant = new AtomicReference<>(initialInstant);
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant.get(), zone);
        }

        @Override
        public Instant instant() {
            return currentInstant.get();
        }

        private void advance(Duration duration) {
            currentInstant.updateAndGet(instant -> instant.plus(duration));
        }
    }
}
