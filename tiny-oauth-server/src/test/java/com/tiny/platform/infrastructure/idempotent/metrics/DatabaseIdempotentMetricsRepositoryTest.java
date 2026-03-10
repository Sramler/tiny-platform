package com.tiny.platform.infrastructure.idempotent.metrics;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseIdempotentMetricsRepositoryTest {

    @Test
    void record_should_insert_cleanup_and_retry_update_when_insert_conflicts() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DatabaseIdempotentMetricsRepository repository = new DatabaseIdempotentMetricsRepository(
            jdbcTemplate,
            Duration.ofDays(7),
            Clock.fixed(Instant.parse("2026-03-08T12:00:00Z"), ZoneId.of("UTC"))
        );
        LocalDateTime bucketMinute = LocalDateTime.of(2026, 3, 8, 12, 0);

        when(jdbcTemplate.update(startsWith("UPDATE sys_idempotent_metric_minute SET"), any(), any(), any(), any(), any(),
            any(), any(), any(Timestamp.class), any(Timestamp.class), any(), anyString()))
            .thenReturn(0, 0, 1, 1);
        when(jdbcTemplate.update(startsWith("INSERT INTO sys_idempotent_metric_minute"), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("dup"), new RuntimeException("dup"))
            .thenReturn(1, 1);

        assertThatCode(() -> repository.recordPass(bucketMinute, 8L, "POST /sys/users")).doesNotThrowAnyException();
    }

    @Test
    void cleanup_should_return_deleted_row_count() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DatabaseIdempotentMetricsRepository repository = new DatabaseIdempotentMetricsRepository(
            jdbcTemplate,
            Duration.ofDays(7),
            Clock.fixed(Instant.parse("2026-03-08T12:00:00Z"), ZoneId.of("UTC"))
        );
        when(jdbcTemplate.update(startsWith("DELETE FROM sys_idempotent_metric_minute WHERE bucket_minute < ?"), any(Timestamp.class)))
            .thenReturn(3);

        assertThat(repository.cleanupExpiredMetrics()).isEqualTo(3);
    }

    @Test
    void aggregate_and_top_scopes_should_return_query_results_and_fallback_on_failure() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DatabaseIdempotentMetricsRepository repository = new DatabaseIdempotentMetricsRepository(
            jdbcTemplate,
            Duration.ofDays(7),
            Clock.systemUTC()
        );
        LocalDateTime start = LocalDateTime.of(2026, 3, 8, 11, 0);
        LocalDateTime end = LocalDateTime.of(2026, 3, 8, 12, 0);

        when(jdbcTemplate.queryForObject(
            startsWith("SELECT COALESCE(SUM(pass_count), 0)"),
            ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<IdempotentMetricsAggregate>>any(),
            eq(DatabaseIdempotentMetricsRepository.ALL_SCOPE),
            any(Timestamp.class),
            any(Timestamp.class),
            eq(8L)
        )).thenReturn(new IdempotentMetricsAggregate(10, 2, 9, 1, 1, 3));

        when(jdbcTemplate.query(
            startsWith("SELECT scope, COALESCE(SUM(hot_count), 0)"),
            ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Map<String, Object>>>any(),
            eq(DatabaseIdempotentMetricsRepository.ALL_SCOPE),
            any(Timestamp.class),
            any(Timestamp.class),
            eq(8L)
        )).thenReturn(List.of(Map.of("key", "POST /sys/users", "count", 5L)));

        assertThat(repository.aggregateWindow(8L, start, end))
            .isEqualTo(new IdempotentMetricsAggregate(10, 2, 9, 1, 1, 3));
        assertThat(repository.topScopes(8L, start, end, 5))
            .containsExactly(Map.of("key", "POST /sys/users", "count", 5L));

        when(jdbcTemplate.queryForObject(
            startsWith("SELECT COALESCE(SUM(pass_count), 0)"),
            ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<IdempotentMetricsAggregate>>any(),
            eq(DatabaseIdempotentMetricsRepository.ALL_SCOPE),
            any(Timestamp.class),
            any(Timestamp.class),
            eq(8L)
        )).thenThrow(new RuntimeException("aggregate-fail"));
        when(jdbcTemplate.query(
            startsWith("SELECT scope, COALESCE(SUM(hot_count), 0)"),
            ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<Map<String, Object>>>any(),
            eq(DatabaseIdempotentMetricsRepository.ALL_SCOPE),
            any(Timestamp.class),
            any(Timestamp.class),
            eq(8L)
        )).thenThrow(new RuntimeException("top-fail"));

        assertThat(repository.aggregateWindow(8L, start, end)).isEqualTo(IdempotentMetricsAggregate.empty());
        assertThat(repository.topScopes(8L, start, end, 5)).isEmpty();
    }
}
