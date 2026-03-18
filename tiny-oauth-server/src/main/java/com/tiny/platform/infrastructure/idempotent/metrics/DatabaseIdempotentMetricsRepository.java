package com.tiny.platform.infrastructure.idempotent.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 基于数据库的幂等指标分钟聚合仓储。
 */
public class DatabaseIdempotentMetricsRepository {

    static final String TABLE_NAME = "sys_idempotent_metric_minute";
    static final String ALL_SCOPE = "__all__";

    private static final Logger log = LoggerFactory.getLogger(DatabaseIdempotentMetricsRepository.class);
    private static final long DEFAULT_RETENTION_DAYS = 7;
    private static final long MIN_RETENTION_DAYS = 1;
    private static final long MAX_RETENTION_DAYS = 90;

    private final JdbcTemplate jdbcTemplate;
    private final long retentionDays;
    private final Clock clock;

    public DatabaseIdempotentMetricsRepository(JdbcTemplate jdbcTemplate,
                                               Duration retention,
                                               Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.retentionDays = normalizeRetentionDays(retention);
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
    }

    public void recordPass(LocalDateTime bucketMinute, @Nullable Long activeTenantId, @Nullable String scope) {
        record(bucketMinute, activeTenantId, scope, 1, 0, 0, 0, 0, 0, 1);
    }

    public void recordDuplicate(LocalDateTime bucketMinute, @Nullable Long activeTenantId, @Nullable String scope) {
        record(bucketMinute, activeTenantId, scope, 0, 1, 0, 0, 0, 0, 1);
    }

    public void recordSuccess(LocalDateTime bucketMinute, @Nullable Long activeTenantId, @Nullable String scope) {
        record(bucketMinute, activeTenantId, scope, 0, 0, 1, 0, 0, 0, 0);
    }

    public void recordFailure(LocalDateTime bucketMinute, @Nullable Long activeTenantId, @Nullable String scope) {
        record(bucketMinute, activeTenantId, scope, 0, 0, 0, 1, 0, 0, 0);
    }

    public void recordStoreError(LocalDateTime bucketMinute, @Nullable Long activeTenantId, @Nullable String scope) {
        record(bucketMinute, activeTenantId, scope, 0, 0, 0, 0, 1, 0, 1);
    }

    public void recordValidationRejected(LocalDateTime bucketMinute, @Nullable Long activeTenantId) {
        record(bucketMinute, activeTenantId, null, 0, 0, 0, 0, 0, 1, 0);
    }

    public IdempotentMetricsAggregate aggregateWindow(@Nullable Long activeTenantId,
                                                      LocalDateTime windowStartInclusive,
                                                      LocalDateTime windowEndExclusive) {
        String sql = "SELECT "
            + "COALESCE(SUM(pass_count), 0) AS pass_count, "
            + "COALESCE(SUM(hit_count), 0) AS hit_count, "
            + "COALESCE(SUM(success_count), 0) AS success_count, "
            + "COALESCE(SUM(failure_count), 0) AS failure_count, "
            + "COALESCE(SUM(store_error_count), 0) AS store_error_count, "
            + "COALESCE(SUM(validation_reject_count), 0) AS validation_reject_count "
            + "FROM " + TABLE_NAME + " WHERE scope = ? AND bucket_minute >= ? AND bucket_minute < ?";
        Object[] args = activeTenantQueryArgs(activeTenantId, windowStartInclusive, windowEndExclusive);
        if (isActiveTenantFilterEnabled(activeTenantId)) {
            sql += " AND tenant_id = ?";
        }
        try {
            IdempotentMetricsAggregate aggregate = jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new IdempotentMetricsAggregate(
                    rs.getLong("pass_count"),
                    rs.getLong("hit_count"),
                    rs.getLong("success_count"),
                    rs.getLong("failure_count"),
                    rs.getLong("store_error_count"),
                    rs.getLong("validation_reject_count")
                ),
                args
            );
            return aggregate != null ? aggregate : IdempotentMetricsAggregate.empty();
        } catch (Exception e) {
            log.warn("查询幂等指标聚合失败: activeTenantId={}, start={}, end={}, error={}",
                normalizeActiveTenantId(activeTenantId), windowStartInclusive, windowEndExclusive, e.getMessage());
            return IdempotentMetricsAggregate.empty();
        }
    }

    public List<Map<String, Object>> topScopes(@Nullable Long activeTenantId,
                                               LocalDateTime windowStartInclusive,
                                               LocalDateTime windowEndExclusive,
                                               int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String sql = "SELECT scope, COALESCE(SUM(hot_count), 0) AS total_hot_count "
            + "FROM " + TABLE_NAME + " "
            + "WHERE scope <> ? AND bucket_minute >= ? AND bucket_minute < ? "
            + (isActiveTenantFilterEnabled(activeTenantId) ? "AND tenant_id = ? " : "")
            + "GROUP BY scope "
            + "HAVING COALESCE(SUM(hot_count), 0) > 0 "
            + "ORDER BY total_hot_count DESC, scope ASC "
            + "LIMIT " + safeLimit;
        Object[] args = activeTenantQueryArgs(activeTenantId, windowStartInclusive, windowEndExclusive);
        try {
            return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> Map.<String, Object>of(
                    "key", rs.getString("scope"),
                    "count", rs.getLong("total_hot_count")
                ),
                args
            );
        } catch (Exception e) {
            log.warn("查询幂等热点 scope 失败: activeTenantId={}, start={}, end={}, limit={}, error={}",
                normalizeActiveTenantId(activeTenantId), windowStartInclusive, windowEndExclusive, safeLimit, e.getMessage());
            return List.of();
        }
    }

    private void record(LocalDateTime bucketMinute,
                        @Nullable Long activeTenantId,
                        @Nullable String scope,
                        long passCount,
                        long hitCount,
                        long successCount,
                        long failureCount,
                        long storeErrorCount,
                        long validationRejectCount,
                        long hotCount) {
        LocalDateTime normalizedBucket = bucketMinute.withSecond(0).withNano(0);
        LocalDateTime now = LocalDateTime.now(clock);
        long normalizedActiveTenantId = normalizeActiveTenantId(activeTenantId);
        upsert(normalizedBucket, normalizedActiveTenantId, ALL_SCOPE, passCount, hitCount, successCount, failureCount,
            storeErrorCount, validationRejectCount, hotCount, now);
        if (StringUtils.hasText(scope)) {
            upsert(normalizedBucket, normalizedActiveTenantId, scope, passCount, hitCount, successCount, failureCount,
                storeErrorCount, validationRejectCount, hotCount, now);
        }
    }

    private void upsert(LocalDateTime bucketMinute,
                        long activeTenantId,
                        String scope,
                        long passCount,
                        long hitCount,
                        long successCount,
                        long failureCount,
                        long storeErrorCount,
                        long validationRejectCount,
                        long hotCount,
                        LocalDateTime now) {
        String updateSql = "UPDATE " + TABLE_NAME + " SET "
            + "pass_count = pass_count + ?, "
            + "hit_count = hit_count + ?, "
            + "success_count = success_count + ?, "
            + "failure_count = failure_count + ?, "
            + "store_error_count = store_error_count + ?, "
            + "validation_reject_count = validation_reject_count + ?, "
            + "hot_count = hot_count + ?, "
            + "updated_time = ? "
            + "WHERE bucket_minute = ? AND tenant_id = ? AND scope = ?";
        try {
            int updated = jdbcTemplate.update(
                updateSql,
                passCount,
                hitCount,
                successCount,
                failureCount,
                storeErrorCount,
                validationRejectCount,
                hotCount,
                Timestamp.valueOf(now),
                Timestamp.valueOf(bucketMinute),
                activeTenantId,
                scope
            );
            if (updated > 0) {
                return;
            }

            String insertSql = "INSERT INTO " + TABLE_NAME + " ("
                + "bucket_minute, tenant_id, scope, pass_count, hit_count, success_count, failure_count, "
                + "store_error_count, validation_reject_count, hot_count, created_time, updated_time"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            jdbcTemplate.update(
                insertSql,
                Timestamp.valueOf(bucketMinute),
                activeTenantId,
                scope,
                passCount,
                hitCount,
                successCount,
                failureCount,
                storeErrorCount,
                validationRejectCount,
                hotCount,
                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
            );
        } catch (Exception insertOrUpdateError) {
            try {
                jdbcTemplate.update(
                    updateSql,
                    passCount,
                    hitCount,
                    successCount,
                    failureCount,
                    storeErrorCount,
                    validationRejectCount,
                    hotCount,
                    Timestamp.valueOf(now),
                    Timestamp.valueOf(bucketMinute),
                    activeTenantId,
                    scope
                );
            } catch (Exception retryError) {
                log.warn("写入幂等分钟指标失败: bucketMinute={}, activeTenantId={}, scope={}, error={}",
                    bucketMinute, activeTenantId, scope, retryError.getMessage());
            }
        }
    }

    public int cleanupExpiredMetrics() {
        return cleanupExpiredBuckets(LocalDateTime.now(clock));
    }

    int cleanupExpiredBuckets(LocalDateTime now) {
        try {
            LocalDateTime retentionCutoff = now.minusDays(retentionDays).withSecond(0).withNano(0);
            int deleted = jdbcTemplate.update(
                "DELETE FROM " + TABLE_NAME + " WHERE bucket_minute < ?",
                Timestamp.valueOf(retentionCutoff)
            );
            if (deleted > 0) {
                log.debug("清理过期幂等分钟指标: {} 条", deleted);
            }
            return deleted;
        } catch (Exception e) {
            log.warn("清理幂等分钟指标失败: {}", e.getMessage());
        }
        return 0;
    }

    private Object[] activeTenantQueryArgs(@Nullable Long activeTenantId,
                                     LocalDateTime windowStartInclusive,
                                     LocalDateTime windowEndExclusive) {
        if (isActiveTenantFilterEnabled(activeTenantId)) {
            return new Object[] {
                ALL_SCOPE,
                Timestamp.valueOf(windowStartInclusive),
                Timestamp.valueOf(windowEndExclusive),
                normalizeActiveTenantId(activeTenantId)
            };
        }
        return new Object[] {
            ALL_SCOPE,
            Timestamp.valueOf(windowStartInclusive),
            Timestamp.valueOf(windowEndExclusive)
        };
    }

    private boolean isActiveTenantFilterEnabled(@Nullable Long activeTenantId) {
        return normalizeActiveTenantId(activeTenantId) > 0;
    }

    private long normalizeActiveTenantId(@Nullable Long activeTenantId) {
        if (activeTenantId == null || activeTenantId <= 0) {
            return 0L;
        }
        return activeTenantId;
    }

    private long normalizeRetentionDays(Duration retention) {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            return DEFAULT_RETENTION_DAYS;
        }
        long days = retention.toDays();
        if (days <= 0) {
            days = DEFAULT_RETENTION_DAYS;
        }
        return Math.max(MIN_RETENTION_DAYS, Math.min(MAX_RETENTION_DAYS, days));
    }
}
