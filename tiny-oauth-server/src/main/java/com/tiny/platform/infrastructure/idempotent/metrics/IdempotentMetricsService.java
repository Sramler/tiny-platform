package com.tiny.platform.infrastructure.idempotent.metrics;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.idempotent.core.key.IdempotentKey;
import com.tiny.platform.infrastructure.idempotent.core.record.IdempotentState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 幂等性指标服务。
 *
 * <p>同时维护内存快照与 Micrometer counter，便于治理接口和运维监控复用。</p>
 */
public class IdempotentMetricsService {

    private static final long DEFAULT_WINDOW_MINUTES = 60;
    private static final long MIN_WINDOW_MINUTES = 5;
    private static final long MAX_WINDOW_MINUTES = 24 * 60;

    private final long windowMinutes;
    private final Clock clock;
    @Nullable
    private final DatabaseIdempotentMetricsRepository databaseRepository;
    private final ConcurrentMap<Long, MetricsBucket> buckets = new ConcurrentHashMap<>();

    private final Counter passCounter;
    private final Counter hitCounter;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter storeErrorCounter;
    private final Counter validationRejectCounter;

    public IdempotentMetricsService(@Nullable MeterRegistry meterRegistry) {
        this(meterRegistry, Duration.ofMinutes(DEFAULT_WINDOW_MINUTES), Clock.systemDefaultZone(), null);
    }

    public IdempotentMetricsService(@Nullable MeterRegistry meterRegistry,
                                    Duration metricsWindow,
                                    Clock clock) {
        this(meterRegistry, metricsWindow, clock, null);
    }

    public IdempotentMetricsService(@Nullable MeterRegistry meterRegistry,
                                    Duration metricsWindow,
                                    Clock clock,
                                    @Nullable DatabaseIdempotentMetricsRepository databaseRepository) {
        this.windowMinutes = normalizeWindowMinutes(metricsWindow);
        this.clock = clock != null ? clock : Clock.systemDefaultZone();
        this.databaseRepository = databaseRepository;
        this.passCounter = meterRegistry != null
            ? Counter.builder("tiny.idempotent.pass.total")
                .description("Number of first idempotent requests accepted for execution")
                .register(meterRegistry)
            : null;
        this.hitCounter = meterRegistry != null
            ? Counter.builder("tiny.idempotent.hit.total")
                .description("Number of duplicate idempotent requests detected")
                .register(meterRegistry)
            : null;
        this.successCounter = meterRegistry != null
            ? Counter.builder("tiny.idempotent.success.total")
                .description("Number of idempotent protected executions completed successfully")
                .register(meterRegistry)
            : null;
        this.failureCounter = meterRegistry != null
            ? Counter.builder("tiny.idempotent.failure.total")
                .description("Number of idempotent protected executions failed and released for retry")
                .register(meterRegistry)
            : null;
        this.storeErrorCounter = meterRegistry != null
            ? Counter.builder("tiny.idempotent.store.error.total")
                .description("Number of idempotent storage errors")
                .register(meterRegistry)
            : null;
        this.validationRejectCounter = meterRegistry != null
            ? Counter.builder("tiny.idempotent.validation.reject.total")
                .description("Number of idempotent requests rejected by key validation")
                .register(meterRegistry)
            : null;
    }

    public void recordPass(@Nullable IdempotentKey key) {
        Long activeTenantId = resolveActiveTenantId(key);
        String normalizedScope = normalizeScope(key);
        if (databaseRepository != null) {
            databaseRepository.recordPass(currentBucketMinute(), activeTenantId, normalizedScope);
        } else {
            MetricsBucket bucket = currentBucket();
            bucket.passCount.increment();
            recordHotScope(bucket, normalizedScope);
        }
        increment(passCounter);
    }

    public void recordDuplicate(@Nullable IdempotentKey key, @Nullable IdempotentState existingState) {
        Long activeTenantId = resolveActiveTenantId(key);
        String normalizedScope = normalizeScope(key);
        if (databaseRepository != null) {
            databaseRepository.recordDuplicate(currentBucketMinute(), activeTenantId, normalizedScope);
        } else {
            MetricsBucket bucket = currentBucket();
            bucket.hitCount.increment();
            recordHotScope(bucket, normalizedScope);
        }
        increment(hitCounter);
    }

    public void recordSuccess(@Nullable IdempotentKey key) {
        Long activeTenantId = resolveActiveTenantId(key);
        String normalizedScope = normalizeScope(key);
        if (databaseRepository != null) {
            databaseRepository.recordSuccess(currentBucketMinute(), activeTenantId, normalizedScope);
        } else {
            currentBucket().successCount.increment();
        }
        increment(successCounter);
    }

    public void recordFailure(@Nullable IdempotentKey key) {
        Long activeTenantId = resolveActiveTenantId(key);
        String normalizedScope = normalizeScope(key);
        if (databaseRepository != null) {
            databaseRepository.recordFailure(currentBucketMinute(), activeTenantId, normalizedScope);
        } else {
            currentBucket().failureCount.increment();
        }
        increment(failureCounter);
    }

    public void recordStoreError(@Nullable IdempotentKey key) {
        Long activeTenantId = resolveActiveTenantId(key);
        String normalizedScope = normalizeScope(key);
        if (databaseRepository != null) {
            databaseRepository.recordStoreError(currentBucketMinute(), activeTenantId, normalizedScope);
        } else {
            MetricsBucket bucket = currentBucket();
            bucket.storeErrorCount.increment();
            recordHotScope(bucket, normalizedScope);
        }
        increment(storeErrorCounter);
    }

    public void recordValidationRejected(String reason) {
        if (databaseRepository != null) {
            databaseRepository.recordValidationRejected(currentBucketMinute(), resolveCurrentActiveTenantId());
        } else {
            currentBucket().validationRejectCount.increment();
        }
        increment(validationRejectCounter);
    }

    public IdempotentMetricsSnapshot snapshot() {
        return snapshot(null);
    }

    public IdempotentMetricsSnapshot snapshot(@Nullable Long activeTenantId) {
        WindowBounds bounds = currentWindowBounds();
        IdempotentMetricsAggregate aggregate = aggregateWindow(bounds, activeTenantId);
        long pass = aggregate.passCount();
        long hit = aggregate.hitCount();
        long success = aggregate.successCount();
        long failure = aggregate.failureCount();
        long storeError = aggregate.storeErrorCount();
        long validationReject = aggregate.validationRejectCount();
        long reject = hit + validationReject;
        long totalChecks = pass + hit + storeError + validationReject;

        return new IdempotentMetricsSnapshot(
            windowMinutes,
            bounds.windowStartEpochMillis(),
            bounds.windowEndEpochMillis(),
            pass,
            hit,
            success,
            failure,
            storeError,
            validationReject,
            reject,
            totalChecks,
            ratio(hit, pass + hit),
            ratio(storeError, totalChecks)
        );
    }

    public List<Map<String, Object>> topScopes(int limit) {
        return topScopes(limit, null);
    }

    public List<Map<String, Object>> topScopes(int limit, @Nullable Long activeTenantId) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        if (databaseRepository != null) {
            WindowBounds bounds = currentWindowBounds();
            return databaseRepository.topScopes(activeTenantId, bounds.windowStartInclusive(), bounds.windowEndExclusive(), safeLimit);
        }
        return aggregateHotScopes().entrySet().stream()
            .map(entry -> Map.<String, Object>of("key", entry.getKey(), "count", entry.getValue()))
            .sorted(Comparator.<Map<String, Object>, Long>comparing(entry -> (Long) entry.get("count"))
                .reversed()
                .thenComparing(entry -> entry.get("key").toString()))
            .limit(safeLimit)
            .toList();
    }

    private MetricsBucket currentBucket() {
        long currentMinute = currentEpochMinute();
        cleanupBuckets(currentMinute);
        return buckets.computeIfAbsent(currentMinute, ignored -> new MetricsBucket());
    }

    private IdempotentMetricsAggregate aggregateWindow(WindowBounds bounds, @Nullable Long activeTenantId) {
        if (databaseRepository != null) {
            return databaseRepository.aggregateWindow(activeTenantId, bounds.windowStartInclusive(), bounds.windowEndExclusive());
        }
        long endMinuteInclusive = currentEpochMinute();
        cleanupBuckets(endMinuteInclusive);
        long startMinuteInclusive = Math.max(0, endMinuteInclusive - windowMinutes + 1);

        long passCount = 0;
        long hitCount = 0;
        long successCount = 0;
        long failureCount = 0;
        long storeErrorCount = 0;
        long validationRejectCount = 0;

        for (Map.Entry<Long, MetricsBucket> entry : buckets.entrySet()) {
            long bucketMinute = entry.getKey();
            if (bucketMinute < startMinuteInclusive || bucketMinute > endMinuteInclusive) {
                continue;
            }
            MetricsBucket bucket = entry.getValue();
            passCount += bucket.passCount.sum();
            hitCount += bucket.hitCount.sum();
            successCount += bucket.successCount.sum();
            failureCount += bucket.failureCount.sum();
            storeErrorCount += bucket.storeErrorCount.sum();
            validationRejectCount += bucket.validationRejectCount.sum();
        }

        return new IdempotentMetricsAggregate(
            passCount,
            hitCount,
            successCount,
            failureCount,
            storeErrorCount,
            validationRejectCount
        );
    }

    private Map<String, Long> aggregateHotScopes() {
        long endMinuteInclusive = currentEpochMinute();
        cleanupBuckets(endMinuteInclusive);
        long startMinuteInclusive = Math.max(0, endMinuteInclusive - windowMinutes + 1);
        ConcurrentMap<String, LongAdder> hotScopes = new ConcurrentHashMap<>();
        for (Map.Entry<Long, MetricsBucket> entry : buckets.entrySet()) {
            long bucketMinute = entry.getKey();
            if (bucketMinute < startMinuteInclusive || bucketMinute > endMinuteInclusive) {
                continue;
            }
            mergeScopes(hotScopes, entry.getValue().hotScopes);
        }
        Map<String, Long> aggregatedHotScopes = new ConcurrentHashMap<>();
        for (Map.Entry<String, LongAdder> entry : hotScopes.entrySet()) {
            aggregatedHotScopes.put(entry.getKey(), entry.getValue().sum());
        }
        return aggregatedHotScopes;
    }

    private void recordHotScope(MetricsBucket bucket, @Nullable String hotScope) {
        if (!StringUtils.hasText(hotScope)) {
            return;
        }
        bucket.hotScopes.computeIfAbsent(hotScope, ignored -> new LongAdder()).increment();
    }

    private String normalizeScope(@Nullable IdempotentKey key) {
        if (key == null || !StringUtils.hasText(key.getScope())) {
            return null;
        }

        String scope = key.getScope();
        int separatorIndex = scope.lastIndexOf('|');
        if (separatorIndex >= 0 && separatorIndex + 1 < scope.length()) {
            return scope.substring(separatorIndex + 1);
        }
        return scope;
    }

    @Nullable
    private Long resolveActiveTenantId(@Nullable IdempotentKey key) {
        Long currentActiveTenantId = resolveCurrentActiveTenantId();
        if (currentActiveTenantId != null && currentActiveTenantId > 0) {
            return currentActiveTenantId;
        }
        if (key == null || !StringUtils.hasText(key.getScope())) {
            return null;
        }
        int separatorIndex = key.getScope().indexOf('|');
        if (separatorIndex <= 0) {
            return null;
        }
        String tenantPrefix = key.getScope().substring(0, separatorIndex);
        try {
            long activeTenantId = Long.parseLong(tenantPrefix);
            return activeTenantId > 0 ? activeTenantId : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private Long resolveCurrentActiveTenantId() {
        Long activeTenantId = TenantContext.getActiveTenantId();
        return activeTenantId != null && activeTenantId > 0 ? activeTenantId : null;
    }

    private void mergeScopes(ConcurrentMap<String, LongAdder> aggregate,
                             ConcurrentMap<String, LongAdder> bucketScopes) {
        for (Map.Entry<String, LongAdder> entry : bucketScopes.entrySet()) {
            aggregate.computeIfAbsent(entry.getKey(), ignored -> new LongAdder()).add(entry.getValue().sum());
        }
    }

    private void cleanupBuckets(long currentMinute) {
        long thresholdMinute = Math.max(0, currentMinute - windowMinutes);
        buckets.entrySet().removeIf(entry -> entry.getKey() < thresholdMinute);
    }

    private LocalDateTime currentBucketMinute() {
        return LocalDateTime.ofInstant(Instant.now(clock), clock.getZone()).withSecond(0).withNano(0);
    }

    private WindowBounds currentWindowBounds() {
        LocalDateTime endExclusive = currentBucketMinute().plusMinutes(1);
        LocalDateTime startInclusive = endExclusive.minusMinutes(windowMinutes);
        return new WindowBounds(
            startInclusive,
            endExclusive,
            startInclusive.atZone(clock.getZone()).toInstant().toEpochMilli(),
            endExclusive.atZone(clock.getZone()).toInstant().toEpochMilli()
        );
    }

    private long currentEpochMinute() {
        return Instant.now(clock).getEpochSecond() / 60;
    }

    private long normalizeWindowMinutes(Duration metricsWindow) {
        if (metricsWindow == null || metricsWindow.isNegative() || metricsWindow.isZero()) {
            return DEFAULT_WINDOW_MINUTES;
        }
        long minutes = metricsWindow.toMinutes();
        if (minutes <= 0) {
            minutes = DEFAULT_WINDOW_MINUTES;
        }
        return Math.max(MIN_WINDOW_MINUTES, Math.min(MAX_WINDOW_MINUTES, minutes));
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return (double) numerator / denominator;
    }

    private void increment(@Nullable Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    private static final class MetricsBucket {
        private final LongAdder passCount = new LongAdder();
        private final LongAdder hitCount = new LongAdder();
        private final LongAdder successCount = new LongAdder();
        private final LongAdder failureCount = new LongAdder();
        private final LongAdder storeErrorCount = new LongAdder();
        private final LongAdder validationRejectCount = new LongAdder();
        private final ConcurrentMap<String, LongAdder> hotScopes = new ConcurrentHashMap<>();
    }

    private record WindowBounds(
        LocalDateTime windowStartInclusive,
        LocalDateTime windowEndExclusive,
        long windowStartEpochMillis,
        long windowEndEpochMillis
    ) {
    }
}
