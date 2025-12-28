package com.tiny.platform.infrastructure.export.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.export.core.AggregateStrategy;
import com.tiny.platform.infrastructure.export.core.DataProvider;
import com.tiny.platform.infrastructure.export.core.ExportRequest;
import com.tiny.platform.infrastructure.export.core.FilterAwareDataProvider;
import com.tiny.platform.infrastructure.export.core.SheetConfig;
import com.tiny.platform.infrastructure.export.core.TopInfoDecorator;
import com.tiny.platform.infrastructure.export.persistence.ExportTaskEntity;
import com.tiny.platform.infrastructure.export.util.HeaderBuilder;
import com.tiny.platform.infrastructure.export.writer.WriterAdapter;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PostConstruct;

/**
 * ExportService —— 导出编排器
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final String TRACE_PREFIX = "[EXPORT_TRACE]";

    private static final int MAX_SYSTEM_CONCURRENT = 10;
    private static final int MAX_USER_CONCURRENT = 3;
    private static final int DEFAULT_EXPIRE_DAYS = 7;
    private static final int DEFAULT_PAGE_SIZE = 1000;
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

    private final Map<String, RuntimeTask> runtimeTasks = new ConcurrentHashMap<>();
    private final String workerId = UUID.randomUUID().toString();

    public ExportService(WriterAdapter writerAdapter,
                         Map<String, DataProvider<?>> providers,
                         TopInfoDecorator topInfoDecorator,
                         Map<String, AggregateStrategy> aggregateMap,
                         @Qualifier("exportExecutor") ThreadPoolTaskExecutor executor,
                         ExportTaskService exportTaskService,
                         ObjectMapper objectMapper,
                         @Value("${export.executor.queue-reject-threshold:900}") int queueRejectThreshold) {
        this.writerAdapter = writerAdapter;
        this.providers = providers;
        this.topInfoDecorator = topInfoDecorator;
        this.aggregateMap = aggregateMap;
        this.executor = executor;
        this.exportTaskService = exportTaskService;
        this.objectMapper = objectMapper;
        this.queueRejectThreshold = queueRejectThreshold;
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
        Map<String, Object> extras = new HashMap<>();
        extras.put("sheetCount", request.getSheets() == null ? 0 : request.getSheets().size());
        logTrace("exportSync", null, currentUserId, Duration.between(start, Instant.now()).toMillis(), extras);
    }

    /**
     * 异步导出 —— 持久化任务并提交线程池
     */
    public String submitAsync(ExportRequest request, String currentUserId) {
        Instant start = Instant.now();
        validateRequest(request);

        String taskId = UUID.randomUUID().toString();
        String serializedRequest = serializeRequest(request);
        int sheetCount = request.getSheets() == null ? 0 : request.getSheets().size();
        exportTaskService.createPendingTask(taskId, currentUserId, currentUserId, sheetCount, serializedRequest,
            LocalDateTime.now().plusDays(DEFAULT_EXPIRE_DAYS));
        scheduleTaskExecution(taskId, request, currentUserId);
        Map<String, Object> extras = new HashMap<>();
        extras.put("sheetCount", request.getSheets() == null ? 0 : request.getSheets().size());
        logTrace("submitAsync", taskId, currentUserId, Duration.between(start, Instant.now()).toMillis(), extras);
        return taskId;
    }

    private void scheduleTaskExecution(String taskId, ExportRequest request, String userId) {
        // 提交前做轻量级限流/观测：若队列过深，直接失败提示稍后再试
        if (executor.getThreadPoolExecutor().getQueue().size() > queueRejectThreshold) {
            exportTaskService.markFailed(taskId, "任务排队过多，请稍后重试", "QUEUE_SATURATED");
            Map<String, Object> extras = new HashMap<>();
            extras.put("queueSize", executor.getThreadPoolExecutor().getQueue().size());
            extras.put("active", executor.getActiveCount());
            logTrace("runTask.rejected.queue", taskId, userId, 0, extras);
            return;
        }

        try {
            CompletableFuture.runAsync(() -> runTask(taskId, request, userId), executor)
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        log.error("async export task failed taskId={}", taskId, ex);
                        exportTaskService.markFailed(taskId, ex.getMessage(), ex.getClass().getSimpleName());
                        Map<String, Object> extras = new HashMap<>();
                        extras.put("error", ex.getMessage());
                        logTrace("runTask.failed", taskId, userId, 0, extras);
                    }
                });
        } catch (RejectedExecutionException rex) {
            log.warn("export task rejected by executor queue taskId={}", taskId, rex);
            exportTaskService.markFailed(taskId, "任务排队过多，请稍后重试", "REJECTED");
            Map<String, Object> extras = new HashMap<>();
            extras.put("error", "executor_rejected");
            extras.put("queueSize", executor.getThreadPoolExecutor().getQueue().size());
            extras.put("active", executor.getActiveCount());
            logTrace("runTask.rejected", taskId, userId, 0, extras);
        }
    }

    private void runTask(String taskId, ExportRequest request, String userId) {
        Instant start = Instant.now();
        exportTaskService.markRunning(taskId, workerId);
        TaskProgressReporter reporter = new TaskProgressReporter(exportTaskService, taskId, estimateTotalRows(request));
        reporter.flush(true);
        Path tmpFile = null;
        try {
            tmpFile = Files.createTempFile("export-" + taskId, ".xlsx");
            Path finalTmpFile = tmpFile;
            Path absoluteFile = finalTmpFile.toAbsolutePath();
            Path finalAbsoluteFile = absoluteFile;
            executeWithConcurrency(userId, () -> {
                try (OutputStream os = Files.newOutputStream(finalTmpFile)) {
                    performExport(request, os, reporter);
                }
            });
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
            logTrace("runTask.success", taskId, userId, Duration.between(start, Instant.now()).toMillis(), extras);
        } catch (Exception ex) {
            log.error("async export failed taskId={}", taskId, ex);
            reporter.flush(true);
            exportTaskService.markFailed(taskId, ex.getMessage(), ex.getClass().getSimpleName());
            Map<String, Object> extras = new HashMap<>();
            extras.put("error", ex.getMessage());
            logTrace("runTask.failed", taskId, userId, Duration.between(start, Instant.now()).toMillis(), extras);
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
        validateConcurrencyBeforeStart(userId);
        String runtimeId = registerRuntimeTask(userId);
        try {
            callback.run();
        } finally {
            runtimeTasks.remove(runtimeId);
        }
    }

    private String registerRuntimeTask(String userId) {
        String runtimeId = UUID.randomUUID().toString();
        runtimeTasks.put(runtimeId, new RuntimeTask(runtimeId, userId, Instant.now()));
        return runtimeId;
    }

    private void validateConcurrencyBeforeStart(String userId) {
        long runningSystem = runtimeTasks.size();
        if (runningSystem >= MAX_SYSTEM_CONCURRENT) {
            throw new IllegalStateException("系统当前导出任务过多，请稍后重试");
        }
        long runningUser = runtimeTasks.values().stream()
            .filter(t -> Objects.equals(t.userId, userId))
            .count();
        if (runningUser >= MAX_USER_CONCURRENT) {
            throw new IllegalStateException("您有过多并发导出任务，请稍后重试");
        }
        Map<String, Object> extras = new HashMap<>();
        extras.put("runningSystem", runningSystem);
        extras.put("runningUser", runningUser);
        logTrace("concurrencyChecked", null, userId, 0, extras);
    }

    private List<SheetWriteModel> buildSheetModels(ExportRequest request, TaskProgressReporter reporter) {
        List<SheetWriteModel> sheetModels = new ArrayList<>();
        int configuredPageSize = request.getPageSize();
        int pageSize = configuredPageSize <= 0 ? DEFAULT_PAGE_SIZE : configuredPageSize;
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

            Map<String, Object> sumMap = new HashMap<>();
            for (String f : leafFields) {
                sumMap.put(f, null);
            }

            // 如果 DataProvider 支持过滤能力，则在当前导出上下文中传入 SheetConfig.filters
            Iterator<?> dataIt;
            if (provider instanceof FilterAwareDataProvider) {
                FilterAwareDataProvider<?> filterAware = (FilterAwareDataProvider<?>) provider;
                try {
                    if (sc.getFilters() != null) {
                        filterAware.setFilters(sc.getFilters());
                    }
                    dataIt = provider.fetchIterator(pageSize);
                } finally {
                    filterAware.clearFilters();
                }
            } else {
                dataIt = provider.fetchIterator(pageSize);
            }
            Iterator<List<Object>> rowIterator = new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return dataIt.hasNext();
                }

                @Override
                public List<Object> next() {
                    Object item = dataIt.next();
                    List<Object> row = convertItemToRow(item, leafFields);
                    if (strategy != null) {
                        for (int i = 0; i < leafFields.size(); i++) {
                            String f = leafFields.get(i);
                            Object val = row.get(i);
                            if (strategy.isAggregate(f)) {
                                sumMap.put(f, strategy.accumulate(f, val, sumMap.get(f)));
                            }
                        }
                    }
                    if (reporter != null) reporter.increment(1);
                    return row;
                }
            };

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
            long estimate = provider.estimateTotal();
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

    private record RuntimeTask(String runtimeId, String userId, Instant startTime) { }
}