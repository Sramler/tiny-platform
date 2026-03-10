package com.tiny.platform.infrastructure.export.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.export.core.AggregateStrategy;
import com.tiny.platform.infrastructure.export.core.DataProvider;
import com.tiny.platform.infrastructure.export.core.ExportRequest;
import com.tiny.platform.infrastructure.export.core.FilterAwareDataProvider;
import com.tiny.platform.infrastructure.export.core.SheetConfig;
import com.tiny.platform.infrastructure.export.core.TopInfoDecorator;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskEntity;
import com.tiny.platform.infrastructure.export.util.HeaderBuilder;
import com.tiny.platform.infrastructure.export.writer.WriterAdapter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PostConstruct;

/**
 * ExportService —— 导出编排器
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final String TRACE_PREFIX = "[EXPORT_TRACE]";

    private static final int DEFAULT_EXPIRE_DAYS = 7;
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final int PREFETCH_QUEUE_MIN_CAPACITY = 256;
    private static final int PREFETCH_QUEUE_MAX_CAPACITY = 2000;
    private static final long PREFETCH_LOG_ROW_INTERVAL = 50_000L;
    private static final Duration RECOVERY_HEARTBEAT_TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 3;

    private final WriterAdapter writerAdapter;
    private final Map<String, DataProvider<?>> providers;
    private final TopInfoDecorator topInfoDecorator;
    private final Map<String, AggregateStrategy> aggregateMap;
    private final ThreadPoolTaskExecutor executor;
    private final ExportTaskService exportTaskService;
    private final ObjectMapper objectMapper;
    private final int queueRejectThreshold;
    private final int maxSystemConcurrent;
    private final int maxUserConcurrent;
    private final int maxPageSize;
    private final long maxSyncRows;
    private final MeterRegistry meterRegistry;
    private final Counter exportSyncCounter;
    private final Counter exportAsyncSubmitCounter;
    private final Counter exportAsyncSuccessCounter;
    private final Counter exportAsyncFailedCounter;
    private final Counter exportAsyncRejectedCounter;

    private final Map<String, RuntimeTask> runtimeTasks = new ConcurrentHashMap<>();
    private final Object runtimeTaskLock = new Object();
    private final String workerId = UUID.randomUUID().toString();

    public ExportService(WriterAdapter writerAdapter,
                         Map<String, DataProvider<?>> providers,
                         TopInfoDecorator topInfoDecorator,
                         Map<String, AggregateStrategy> aggregateMap,
                         @Qualifier("exportExecutor") ThreadPoolTaskExecutor executor,
                         ExportTaskService exportTaskService,
                         ObjectMapper objectMapper,
                         @Value("${export.executor.queue-reject-threshold:900}") int queueRejectThreshold,
                         @Value("${export.concurrent.max-system:10}") int maxSystemConcurrent,
                         @Value("${export.concurrent.max-user:3}") int maxUserConcurrent,
                         @Value("${export.max-page-size:10000}") int maxPageSize,
                         @Value("${export.sync.max-rows:100000}") long maxSyncRows,
                         MeterRegistry meterRegistry) {
        this.writerAdapter = writerAdapter;
        this.providers = providers;
        this.topInfoDecorator = topInfoDecorator;
        this.aggregateMap = aggregateMap;
        this.executor = executor;
        this.exportTaskService = exportTaskService;
        this.objectMapper = objectMapper;
        this.queueRejectThreshold = queueRejectThreshold;
        this.maxSystemConcurrent = Math.max(1, maxSystemConcurrent);
        this.maxUserConcurrent = Math.max(1, maxUserConcurrent);
        this.maxPageSize = Math.max(1000, maxPageSize);
        this.maxSyncRows = Math.max(0, maxSyncRows);
        this.meterRegistry = meterRegistry;
        this.exportSyncCounter = Counter.builder("tiny.export.sync.total")
            .description("Number of synchronous export requests")
            .register(meterRegistry);
        this.exportAsyncSubmitCounter = Counter.builder("tiny.export.async.submit.total")
            .description("Number of asynchronous export submissions")
            .register(meterRegistry);
        this.exportAsyncSuccessCounter = Counter.builder("tiny.export.async.success.total")
            .description("Number of asynchronous export tasks finished successfully")
            .register(meterRegistry);
        this.exportAsyncFailedCounter = Counter.builder("tiny.export.async.failed.total")
            .description("Number of asynchronous export tasks failed")
            .register(meterRegistry);
        this.exportAsyncRejectedCounter = Counter.builder("tiny.export.async.rejected.total")
            .description("Number of asynchronous export submissions rejected by backpressure")
            .register(meterRegistry);
        Gauge.builder("tiny.export.runtime.running", runtimeTasks, Map::size)
            .description("Current number of running export tasks in runtime map")
            .register(meterRegistry);
        Gauge.builder("tiny.export.executor.queue.size", executor,
                x -> x.getThreadPoolExecutor() == null ? 0 : x.getThreadPoolExecutor().getQueue().size())
            .description("Current queue size of export executor")
            .register(meterRegistry);
        Gauge.builder("tiny.export.executor.active.count", executor, ThreadPoolTaskExecutor::getActiveCount)
            .description("Current active thread count in export executor")
            .register(meterRegistry);
    }

    @PostConstruct
    public void recoverPendingTasksOnStartup() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusSeconds(RECOVERY_HEARTBEAT_TIMEOUT.getSeconds());
            exportTaskService.recoverStuckTasks(threshold);
            List<ExportTaskEntity> pendingTasks = exportTaskService.findPendingTasks();
            for (ExportTaskEntity task : pendingTasks) {
                resumeTask(task);
            }
        } catch (Exception ex) {
            log.error("Failed to recover export tasks on startup", ex);
        }
    }

    /**
     * 同步导出
     */
    public void exportSync(ExportRequest request, OutputStream out, String currentUserId) throws Exception {
        Instant start = Instant.now();
        validateRequest(request);
        executeWithConcurrency(currentUserId, () -> performExport(request, out, null));
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        exportSyncCounter.increment();
        recordDuration("sync", "success", durationMs);
        Map<String, Object> extras = new HashMap<>();
        extras.put("sheetCount", request.getSheets() == null ? 0 : request.getSheets().size());
        logTrace("exportSync", null, currentUserId, durationMs, extras);
    }

    public void assertSyncExportWithinRowLimit(ExportRequest request) {
        if (maxSyncRows <= 0) {
            return;
        }
        long estimatedRows = estimateTotalRows(request);
        if (estimatedRows < 0 || estimatedRows <= maxSyncRows) {
            return;
        }
        throw new BusinessException(
            ErrorCode.UNPROCESSABLE_ENTITY,
            "同步导出预计数据量为" + estimatedRows + "行，超过限制" + maxSyncRows + "行，请改用异步导出"
        );
    }

    /**
     * 异步导出 —— 持久化任务并提交线程池
     */
    public String submitAsync(ExportRequest request, String currentUserId) {
        Instant start = Instant.now();
        validateRequest(request);
        validateAsyncQueueBeforeSubmit();
        String runtimeId = acquireRuntimeTaskSlotForSubmit(currentUserId);

        String taskId = UUID.randomUUID().toString();
        String serializedRequest = serializeRequest(request);
        int sheetCount = request.getSheets() == null ? 0 : request.getSheets().size();
        boolean taskCreated = false;
        try {
            exportTaskService.createPendingTask(taskId, currentUserId, currentUserId, sheetCount, serializedRequest,
                LocalDateTime.now().plusDays(DEFAULT_EXPIRE_DAYS));
            taskCreated = true;
            scheduleTaskExecution(taskId, request, currentUserId, runtimeId, true);
        } catch (RuntimeException ex) {
            releaseRuntimeTask(runtimeId);
            if (taskCreated) {
                exportTaskService.deleteByTaskId(taskId);
            }
            throw ex;
        }
        exportAsyncSubmitCounter.increment();
        Map<String, Object> extras = new HashMap<>();
        extras.put("sheetCount", request.getSheets() == null ? 0 : request.getSheets().size());
        logTrace("submitAsync", taskId, currentUserId, Duration.between(start, Instant.now()).toMillis(), extras);
        return taskId;
    }

    private void scheduleTaskExecution(String taskId, ExportRequest request, String userId) {
        scheduleTaskExecution(taskId, request, userId, null, false);
    }

    private void scheduleTaskExecution(String taskId,
                                       ExportRequest request,
                                       String userId,
                                       String reservedRuntimeId,
                                       boolean failFastOnReject) {
        // 提交前做轻量级限流/观测：若队列过深，直接失败提示稍后再试
        if (executor.getThreadPoolExecutor().getQueue().size() > queueRejectThreshold) {
            if (failFastOnReject) {
                exportAsyncRejectedCounter.increment();
                throw tooManyRequests("任务排队过多，请稍后重试");
            }
            exportTaskService.markFailed(taskId, "任务排队过多，请稍后重试", "QUEUE_SATURATED");
            exportAsyncRejectedCounter.increment();
            Map<String, Object> extras = new HashMap<>();
            extras.put("queueSize", executor.getThreadPoolExecutor().getQueue().size());
            extras.put("active", executor.getActiveCount());
            logTrace("runTask.rejected.queue", taskId, userId, 0, extras);
            return;
        }

        try {
            CompletableFuture.runAsync(() -> runTask(taskId, request, userId, reservedRuntimeId), executor)
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        log.error("async export task failed taskId={}", taskId, ex);
                        exportTaskService.markFailed(taskId, ex.getMessage(), ex.getClass().getSimpleName());
                        exportAsyncFailedCounter.increment();
                        Map<String, Object> extras = new HashMap<>();
                        extras.put("error", ex.getMessage());
                        logTrace("runTask.failed", taskId, userId, 0, extras);
                    }
                });
        } catch (RejectedExecutionException rex) {
            if (failFastOnReject) {
                exportAsyncRejectedCounter.increment();
                throw tooManyRequests("任务排队过多，请稍后重试", rex);
            }
            log.warn("export task rejected by executor queue taskId={}", taskId, rex);
            exportTaskService.markFailed(taskId, "任务排队过多，请稍后重试", "REJECTED");
            exportAsyncRejectedCounter.increment();
            Map<String, Object> extras = new HashMap<>();
            extras.put("error", "executor_rejected");
            extras.put("queueSize", executor.getThreadPoolExecutor().getQueue().size());
            extras.put("active", executor.getActiveCount());
            logTrace("runTask.rejected", taskId, userId, 0, extras);
        }
    }

    private void runTask(String taskId, ExportRequest request, String userId) {
        runTask(taskId, request, userId, null);
    }

    private void runTask(String taskId, ExportRequest request, String userId, String reservedRuntimeId) {
        Instant start = Instant.now();
        exportTaskService.markRunning(taskId, workerId);
        TaskProgressReporter reporter = new TaskProgressReporter(exportTaskService, taskId, estimateTotalRows(request));
        reporter.flush(true);
        Path tmpFile = null;
        boolean success = false;
        String runtimeId = reservedRuntimeId;
        try {
            if (runtimeId == null) {
                runtimeId = acquireRuntimeTaskSlot(userId);
            }
            tmpFile = Files.createTempFile("export-" + taskId, ".xlsx");
            Path finalTmpFile = tmpFile;
            Path absoluteFile = finalTmpFile.toAbsolutePath();
            Path finalAbsoluteFile = absoluteFile;
            try (OutputStream os = Files.newOutputStream(finalTmpFile)) {
                performExport(request, os, reporter);
            }
            Long totalRowsValue = reporter.getTotalRowsOrNull();
            if (totalRowsValue == null) {
                long processed = reporter.getProcessedRows();
                totalRowsValue = processed > 0 ? processed : null;
            }
            exportTaskService.markSuccess(taskId, finalAbsoluteFile.toString(),
                "/export/task/" + taskId + "/download", totalRowsValue);
            Map<String, Object> extras = new HashMap<>();
            extras.put("processedRows", reporter.getProcessedRows());
            extras.put("totalRows", totalRowsValue);
            extras.put("sheetCount", request.getSheets() == null ? 0 : request.getSheets().size());
            extras.put("tempFile", finalAbsoluteFile.toString());
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            logTrace("runTask.success", taskId, userId, durationMs, extras);
            exportAsyncSuccessCounter.increment();
            recordDuration("async", "success", durationMs);
            success = true;
        } catch (Exception ex) {
            log.error("async export failed taskId={}", taskId, ex);
            reporter.flush(true);
            exportTaskService.markFailed(taskId, ex.getMessage(), ex.getClass().getSimpleName());
            Map<String, Object> extras = new HashMap<>();
            extras.put("error", ex.getMessage());
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            logTrace("runTask.failed", taskId, userId, durationMs, extras);
            exportAsyncFailedCounter.increment();
            recordDuration("async", "failed", durationMs);
        } finally {
            if (runtimeId != null) {
                releaseRuntimeTask(runtimeId);
            }
            if (!success && tmpFile != null) {
                try {
                    Files.deleteIfExists(tmpFile);
                } catch (Exception ex) {
                    log.warn("failed to cleanup tmp export file taskId={} file={}", taskId, tmpFile, ex);
                }
            }
        }
    }

    private void performExport(ExportRequest request, OutputStream out, TaskProgressReporter reporter) throws Exception {
        Instant buildStart = Instant.now();
        List<SheetWriteModel> sheetModels = buildSheetModels(request, reporter);
        long buildMs = Duration.between(buildStart, Instant.now()).toMillis();

        Instant writeStart = Instant.now();
        writerAdapter.writeMultiSheet(out, sheetModels);
        long writeMs = Duration.between(writeStart, Instant.now()).toMillis();

        if (reporter != null) reporter.flush(true);
        Map<String, Object> extras = new HashMap<>();
        extras.put("buildMs", buildMs);
        extras.put("writeMs", writeMs);
        extras.put("sheetCount", request.getSheets() == null ? 0 : request.getSheets().size());
        logTrace("performExport", null, null, buildMs + writeMs, extras);
    }

    private void validateRequest(ExportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        if (request.getSheets() == null || request.getSheets().isEmpty()) {
            throw new IllegalArgumentException("sheets 不能为空，至少包含一个 sheet");
        }
    }

    private void executeWithConcurrency(String userId, ExportCallback callback) throws Exception {
        String runtimeId = acquireRuntimeTaskSlot(userId);
        try {
            callback.run();
        } finally {
            releaseRuntimeTask(runtimeId);
        }
    }

    private String acquireRuntimeTaskSlot(String userId) {
        synchronized (runtimeTaskLock) {
            validateConcurrencyBeforeStart(userId);
            String runtimeId = UUID.randomUUID().toString();
            runtimeTasks.put(runtimeId, new RuntimeTask(runtimeId, userId, Instant.now()));
            return runtimeId;
        }
    }

    private String acquireRuntimeTaskSlotForSubmit(String userId) {
        try {
            return acquireRuntimeTaskSlot(userId);
        } catch (IllegalStateException ex) {
            exportAsyncRejectedCounter.increment();
            throw tooManyRequests(ex.getMessage(), ex);
        }
    }

    private void releaseRuntimeTask(String runtimeId) {
        synchronized (runtimeTaskLock) {
            runtimeTasks.remove(runtimeId);
        }
    }

    private void validateConcurrencyBeforeStart(String userId) {
        long runningSystem = runtimeTasks.size();
        if (runningSystem >= maxSystemConcurrent) {
            throw new IllegalStateException("系统当前导出任务过多，请稍后重试");
        }
        long runningUser = runtimeTasks.values().stream()
            .filter(t -> Objects.equals(t.userId, userId))
            .count();
        if (runningUser >= maxUserConcurrent) {
            throw new IllegalStateException("您有过多并发导出任务，请稍后重试");
        }
        Map<String, Object> extras = new HashMap<>();
        extras.put("runningSystem", runningSystem);
        extras.put("runningUser", runningUser);
        extras.put("maxSystemConcurrent", maxSystemConcurrent);
        extras.put("maxUserConcurrent", maxUserConcurrent);
        logTrace("concurrencyChecked", null, userId, 0, extras);
    }

    private void validateAsyncQueueBeforeSubmit() {
        if (executor.getThreadPoolExecutor().getQueue().size() <= queueRejectThreshold) {
            return;
        }
        exportAsyncRejectedCounter.increment();
        throw tooManyRequests("任务排队过多，请稍后重试");
    }

    private BusinessException tooManyRequests(String message) {
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS, message);
    }

    private BusinessException tooManyRequests(String message, Throwable cause) {
        return new BusinessException(ErrorCode.TOO_MANY_REQUESTS, message, cause);
    }

    private List<SheetWriteModel> buildSheetModels(ExportRequest request, TaskProgressReporter reporter) {
        List<SheetWriteModel> sheetModels = new ArrayList<>();
        int configuredPageSize = request.getPageSize();
        int requestedPageSize = configuredPageSize <= 0 ? DEFAULT_PAGE_SIZE : configuredPageSize;
        int pageSize = Math.min(requestedPageSize, maxPageSize);
        if (requestedPageSize > maxPageSize) {
            log.warn("export pageSize capped requested={} maxAllowed={}", requestedPageSize, maxPageSize);
        }
        for (SheetConfig sc : request.getSheets()) {
            String exportType = sc.getExportType();
            DataProvider<?> provider = providers.get(exportType);
            if (provider == null) {
                throw new IllegalArgumentException("未注册的 exportType: " + exportType);
            }
            String sheetName = sc.getSheetName() == null ? exportType : sc.getSheetName();
            if (sc.getColumns() == null || sc.getColumns().isEmpty()) {
                throw new IllegalArgumentException("sheet " + sheetName + " 未配置 columns");
            }

            HeaderBuilder.HeadAndFields hf = HeaderBuilder.build(sc.getColumns());
            List<List<String>> head = hf.head;
            List<String> leafFields = hf.leafFields;

            List<List<String>> topInfoRows = topInfoDecorator.getTopInfoRows(request, exportType);
            if (topInfoRows == null) {
                topInfoRows = Collections.emptyList();
            }

            AggregateStrategy strategy = sc.getAggregateKey() != null ? aggregateMap.get(sc.getAggregateKey()) : null;

            Map<String, Object> sumMap = Collections.synchronizedMap(new HashMap<>());
            for (String f : leafFields) {
                sumMap.put(f, null);
            }

            FilterAwareDataProvider<?> filterAwareProvider =
                provider instanceof FilterAwareDataProvider<?> fa ? fa : null;
            Map<String, Object> filtersSnapshot = null;
            if (sc.getFilters() != null || !leafFields.isEmpty()) {
                Map<String, Object> mutableFilters = new LinkedHashMap<>();
                if (sc.getFilters() != null) {
                    mutableFilters.putAll(sc.getFilters());
                }
                mutableFilters.put("__leafFields", Collections.unmodifiableList(new ArrayList<>(leafFields)));
                filtersSnapshot = Collections.unmodifiableMap(mutableFilters);
            }
            int queueCapacity = Math.max(PREFETCH_QUEUE_MIN_CAPACITY, Math.min(pageSize, PREFETCH_QUEUE_MAX_CAPACITY));
            Iterator<List<Object>> rowIterator = new PrefetchRowIterator(
                provider,
                filterAwareProvider,
                filtersSnapshot,
                pageSize,
                leafFields,
                strategy,
                sumMap,
                reporter,
                exportType,
                sheetName,
                queueCapacity
            );

            SheetWriteModel model = new SheetWriteModel(sheetName, head, rowIterator, topInfoRows, leafFields, strategy, sumMap);
            sheetModels.add(model);
        }
        return sheetModels;
    }

    private List<Object> convertItemToRow(Object item, List<String> leafFields) {
        List<Object> row = new ArrayList<>(leafFields.size());
        if (item instanceof Map<?, ?> map) {
            for (String f : leafFields) {
                row.add(map.get(f));
            }
        } else {
            for (String f : leafFields) {
                try {
                    var field = item.getClass().getDeclaredField(f);
                    field.setAccessible(true);
                    row.add(field.get(item));
                } catch (Exception e) {
                    row.add(null);
                }
            }
        }
        return row;
    }

    private String serializeRequest(ExportRequest request) {
        if (request == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("导出请求序列化失败", e);
        }
    }

    private ExportRequest deserializeRequest(String payload) throws JsonProcessingException {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        return objectMapper.readValue(payload, ExportRequest.class);
    }

    private long estimateTotalRows(ExportRequest request) {
        if (request == null || request.getSheets() == null) {
            return -1;
        }
        long sum = 0;
        for (SheetConfig sc : request.getSheets()) {
            DataProvider<?> provider = providers.get(sc.getExportType());
            if (provider == null) continue;
            long estimate;
            if (provider instanceof FilterAwareDataProvider<?> filterAwareProvider) {
                try {
                    filterAwareProvider.setFilters(sc.getFilters());
                    estimate = provider.estimateTotal();
                } finally {
                    filterAwareProvider.clearFilters();
                }
            } else {
                estimate = provider.estimateTotal();
            }
            if (estimate > 0) {
                sum += estimate;
            }
        }
        return sum == 0 ? -1 : sum;
    }

    private void resumeTask(ExportTaskEntity task) {
        try {
            Integer attempt = task.getAttempt();
            if (attempt != null && attempt >= MAX_ATTEMPTS) {
                exportTaskService.markFailed(task.getTaskId(), "超过最大重试次数，停止恢复", "RETRY_EXCEEDED");
                return;
            }
            ExportRequest req = deserializeRequest(task.getQueryParams());
            if (req == null) {
                exportTaskService.markFailed(task.getTaskId(), "缺少导出请求参数，无法恢复", "RECOVERY_MISSING_REQUEST");
                return;
            }
            scheduleTaskExecution(task.getTaskId(), req, task.getUserId());
            log.info("Recovered export task {}", task.getTaskId());
        } catch (Exception ex) {
            log.error("Failed to resume export task {}", task.getTaskId(), ex);
            exportTaskService.markFailed(task.getTaskId(), "恢复失败: " + ex.getMessage(), "RECOVERY_ERROR");
        }
    }

    private void logTrace(String phase, String taskId, String userId, long durationMs, Map<String, ?> extras) {
        if (!log.isInfoEnabled()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        if (extras != null) {
            payload.putAll(extras);
        }
        // 补充线程池观测数据，便于定位排队与并发情况
        if (executor != null && executor.getThreadPoolExecutor() != null) {
            payload.putIfAbsent("poolActive", executor.getActiveCount());
            payload.putIfAbsent("poolSize", executor.getPoolSize());
            payload.putIfAbsent("queueSize", executor.getThreadPoolExecutor().getQueue().size());
        }
        // 透传 traceId（若上游已写入 MDC）
        String traceId = MDC.get("traceId");
        log.info("{} phase={} taskId={} userId={} durationMs={} traceId={} extras={}", TRACE_PREFIX,
            phase, taskId, userId, durationMs, traceId, payload);
    }

    private void recordDuration(String mode, String result, long durationMs) {
        if (durationMs < 0) {
            return;
        }
        meterRegistry.timer("tiny.export.duration.ms", "mode", mode, "result", result)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    private static final class  TaskProgressReporter {
        private static final long ROW_INTERVAL = 1000;
        private static final long TIME_INTERVAL_MS = 5000;

        private final ExportTaskService taskService;
        private final String taskId;
        private final long totalRows;
        private final AtomicLong processedRows = new AtomicLong();
        private volatile long lastReportedRows = 0;
        private volatile long lastReportTime = System.currentTimeMillis();

        private TaskProgressReporter(ExportTaskService taskService, String taskId, long totalRows) {
            this.taskService = taskService;
            this.taskId = taskId;
            this.totalRows = totalRows;
        }

        void increment(long delta) {
            processedRows.addAndGet(delta);
            maybeFlush(false);
        }

        void flush(boolean force) {
            maybeFlush(force);
        }

        Long getTotalRowsOrNull() {
            return totalRows > 0 ? totalRows : null;
        }

        long getProcessedRows() {
            return processedRows.get();
        }

        private synchronized void maybeFlush(boolean force) {
            long current = processedRows.get();
            long now = System.currentTimeMillis();
            if (!force && current - lastReportedRows < ROW_INTERVAL && now - lastReportTime < TIME_INTERVAL_MS) {
                return;
            }
            Integer progressValue = totalRows > 0 ? (int) Math.min(99, (current * 100L) / totalRows) : null;
            Long totalValue = totalRows > 0 ? totalRows : null;
            taskService.markProgress(taskId, progressValue, current, totalValue);
            lastReportedRows = current;
            lastReportTime = now;
        }
    }

    @FunctionalInterface
    private interface ExportCallback {
        void run() throws Exception;
    }

    /**
     * 预取流水线迭代器：
     * 生产线程负责 DB 读取 + 行映射 + 聚合累计，消费线程只做写出。
     */
    private final class PrefetchRowIterator implements Iterator<List<Object>>, AutoCloseable {
        private final DataProvider<?> provider;
        private final FilterAwareDataProvider<?> filterAwareProvider;
        private final Map<String, Object> filters;
        private final int pageSize;
        private final List<String> leafFields;
        private final AggregateStrategy strategy;
        private final Map<String, Object> sumMap;
        private final TaskProgressReporter reporter;
        private final String exportType;
        private final String sheetName;
        private final BlockingQueue<RowEnvelope> queue;
        private final Thread producerThread;
        private final AtomicBoolean producerStarted = new AtomicBoolean(false);

        private volatile boolean closed = false;
        private volatile Throwable producerError = null;
        private volatile boolean endReached = false;
        private RowEnvelope current;

        private PrefetchRowIterator(DataProvider<?> provider,
                                    FilterAwareDataProvider<?> filterAwareProvider,
                                    Map<String, Object> filters,
                                    int pageSize,
                                    List<String> leafFields,
                                    AggregateStrategy strategy,
                                    Map<String, Object> sumMap,
                                    TaskProgressReporter reporter,
                                    String exportType,
                                    String sheetName,
                                    int queueCapacity) {
            this.provider = provider;
            this.filterAwareProvider = filterAwareProvider;
            this.filters = filters;
            this.pageSize = pageSize;
            this.leafFields = leafFields;
            this.strategy = strategy;
            this.sumMap = sumMap;
            this.reporter = reporter;
            this.exportType = exportType;
            this.sheetName = sheetName;
            this.queue = new ArrayBlockingQueue<>(Math.max(1, queueCapacity));
            this.producerThread = new Thread(this::runProducer, "export-prefetch-" + UUID.randomUUID());
            this.producerThread.setDaemon(true);
        }

        @Override
        public boolean hasNext() {
            ensureCurrent();
            if (current == null) {
                if (producerError != null) {
                    throw new IllegalStateException("导出预取失败: " + producerError.getMessage(), producerError);
                }
                return false;
            }
            return true;
        }

        @Override
        public List<Object> next() {
            ensureCurrent();
            if (current == null) {
                throw new NoSuchElementException();
            }
            List<Object> row = current.row;
            current = null;
            return row;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            queue.clear();
            if (producerStarted.get()) {
                producerThread.interrupt();
            }
        }

        private void ensureCurrent() {
            ensureProducerStarted();
            if (current != null || endReached || closed) {
                return;
            }
            try {
                while (!closed) {
                    RowEnvelope envelope = queue.poll(200, TimeUnit.MILLISECONDS);
                    if (envelope == null) {
                        if (producerStarted.get() && !producerThread.isAlive()) {
                            endReached = true;
                            return;
                        }
                        continue;
                    }
                    if (envelope.end) {
                        endReached = true;
                        return;
                    }
                    current = envelope;
                    return;
                }
                endReached = true;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("导出消费线程被中断", ex);
            }
        }

        private void ensureProducerStarted() {
            if (closed) {
                return;
            }
            if (producerStarted.compareAndSet(false, true)) {
                producerThread.start();
            }
        }

        private void runProducer() {
            long rows = 0L;
            long fetchNanos = 0L;
            long mapNanos = 0L;
            long queueWaitNanos = 0L;
            long startedAt = System.nanoTime();
            Iterator<?> source = null;
            try {
                source = createSourceIterator();
                while (!closed && source.hasNext()) {
                    long fetchStart = System.nanoTime();
                    Object item = source.next();
                    fetchNanos += System.nanoTime() - fetchStart;

                    long mapStart = System.nanoTime();
                    List<Object> row = convertItemToRow(item, leafFields);
                    if (strategy != null) {
                        for (int i = 0; i < leafFields.size(); i++) {
                            String f = leafFields.get(i);
                            Object val = row.get(i);
                            if (strategy.isAggregate(f)) {
                                synchronized (sumMap) {
                                    sumMap.put(f, strategy.accumulate(f, val, sumMap.get(f)));
                                }
                            }
                        }
                    }
                    if (reporter != null) {
                        reporter.increment(1);
                    }
                    mapNanos += System.nanoTime() - mapStart;
                    rows++;

                    while (!closed) {
                        long queueStart = System.nanoTime();
                        boolean offered = queue.offer(new RowEnvelope(row, false), 200, TimeUnit.MILLISECONDS);
                        queueWaitNanos += System.nanoTime() - queueStart;
                        if (offered) {
                            break;
                        }
                    }

                    if (rows % PREFETCH_LOG_ROW_INTERVAL == 0) {
                        logPrefetchProgress(rows, fetchNanos, mapNanos, queueWaitNanos);
                    }
                }
            } catch (Throwable ex) {
                producerError = ex;
            } finally {
                clearSourceFilters();
                offerEndMarker();
                long elapsedNanos = System.nanoTime() - startedAt;
                Map<String, Object> extras = new HashMap<>();
                extras.put("sheetName", sheetName);
                extras.put("exportType", exportType);
                extras.put("rows", rows);
                extras.put("elapsedMs", TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
                extras.put("fetchMs", TimeUnit.NANOSECONDS.toMillis(fetchNanos));
                extras.put("mapMs", TimeUnit.NANOSECONDS.toMillis(mapNanos));
                extras.put("queueWaitMs", TimeUnit.NANOSECONDS.toMillis(queueWaitNanos));
                extras.put("queueSize", queue.size());
                extras.put("queueCapacity", queue.size() + queue.remainingCapacity());
                if (producerError != null) {
                    extras.put("error", producerError.getClass().getSimpleName() + ":" + producerError.getMessage());
                }
                logTrace("prefetch.summary", null, null, TimeUnit.NANOSECONDS.toMillis(elapsedNanos), extras);
            }
        }

        private Iterator<?> createSourceIterator() {
            if (filterAwareProvider == null) {
                return provider.fetchIterator(pageSize);
            }
            filterAwareProvider.clearFilters();
            if (filters != null && !filters.isEmpty()) {
                filterAwareProvider.setFilters(filters);
            }
            return provider.fetchIterator(pageSize);
        }

        private void clearSourceFilters() {
            if (filterAwareProvider == null) {
                return;
            }
            try {
                filterAwareProvider.clearFilters();
            } catch (Throwable ex) {
                log.warn("failed to clear export filters sheet={} exportType={}", sheetName, exportType, ex);
            }
        }

        private void logPrefetchProgress(long rows, long fetchNanos, long mapNanos, long queueWaitNanos) {
            Map<String, Object> extras = new HashMap<>();
            extras.put("sheetName", sheetName);
            extras.put("exportType", exportType);
            extras.put("rows", rows);
            extras.put("fetchMs", TimeUnit.NANOSECONDS.toMillis(fetchNanos));
            extras.put("mapMs", TimeUnit.NANOSECONDS.toMillis(mapNanos));
            extras.put("queueWaitMs", TimeUnit.NANOSECONDS.toMillis(queueWaitNanos));
            extras.put("queueSize", queue.size());
            logTrace("prefetch.progress", null, null, 0, extras);
        }

        private void offerEndMarker() {
            while (!closed) {
                try {
                    if (queue.offer(new RowEnvelope(Collections.emptyList(), true), 100, TimeUnit.MILLISECONDS)) {
                        return;
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private record RowEnvelope(List<Object> row, boolean end) { }

    private record RuntimeTask(String runtimeId, String userId, Instant startTime) { }
}
