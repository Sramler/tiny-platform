package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.scheduling.exception.SchedulingExceptions;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagRun;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskHistory;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTask;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskExecutionSnapshot;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskType;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagTask;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskInstanceRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskHistoryRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskTypeRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagRunRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagEdgeRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.TimeoutException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;

/**
 * 任务 Worker 服务
 * 负责从队列中抢占任务并执行
 */
@Service
public class TaskWorkerService {

    private static final Logger logger = LoggerFactory.getLogger(TaskWorkerService.class);

    private final SchedulingTaskInstanceRepository taskInstanceRepository;
    private final SchedulingTaskHistoryRepository taskHistoryRepository;
    private final SchedulingTaskRepository taskRepository;
    private final SchedulingTaskTypeRepository taskTypeRepository;
    private final SchedulingDagRunRepository dagRunRepository;
    private final SchedulingDagTaskRepository dagTaskRepository;
    private final SchedulingDagEdgeRepository dagEdgeRepository;
    private final TaskExecutorService taskExecutorService;
    private final DependencyCheckerService dependencyCheckerService;
    private final ObjectMapper objectMapper;

    private static final int TASK_PAGE_SIZE = 100;
    private static final int MAX_TASKS_PER_CYCLE = 500;
    private static final int DEFAULT_RETRY_DELAY_SEC = 60;

    @Value("${scheduling.worker.lock-timeout-sec:300}")
    private int lockTimeoutSec;

    private final String workerId;
    private final ExecutorService taskExecutionExecutor;
    private final ExecutorService dispatchExecutor;

    /** 自注入，用于通过代理调用 @Transactional 方法，避免同类内部调用导致事务不生效 */
    @Autowired
    @Lazy
    private TaskWorkerService self;

    @Autowired
    public TaskWorkerService(
            SchedulingTaskInstanceRepository taskInstanceRepository,
            SchedulingTaskHistoryRepository taskHistoryRepository,
            SchedulingTaskRepository taskRepository,
            SchedulingTaskTypeRepository taskTypeRepository,
            SchedulingDagRunRepository dagRunRepository,
            SchedulingDagTaskRepository dagTaskRepository,
            SchedulingDagEdgeRepository dagEdgeRepository,
            TaskExecutorService taskExecutorService,
            DependencyCheckerService dependencyCheckerService,
            ObjectMapper objectMapper,
            @Qualifier("schedulingTaskExecutor") ExecutorService taskExecutionExecutor,
            @Qualifier("schedulingDispatchExecutor") ExecutorService dispatchExecutor) {
        this.taskInstanceRepository = taskInstanceRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.taskRepository = taskRepository;
        this.taskTypeRepository = taskTypeRepository;
        this.dagRunRepository = dagRunRepository;
        this.dagTaskRepository = dagTaskRepository;
        this.dagEdgeRepository = dagEdgeRepository;
        this.taskExecutorService = taskExecutorService;
        this.dependencyCheckerService = dependencyCheckerService;
        this.objectMapper = objectMapper;
        this.taskExecutionExecutor = taskExecutionExecutor;
        this.dispatchExecutor = dispatchExecutor;
        this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        logger.info("Worker 启动, workerId: {}", workerId);
    }

    /**
     * 定时扫描并执行待处理的任务
     * 每 5 秒执行一次
     */
    @Scheduled(fixedDelay = 5000)
    public void processPendingTasks() {
        int processed = 0;
        int pageIndex = 0;
        try {
            while (processed < MAX_TASKS_PER_CYCLE) {
                LocalDateTime now = LocalDateTime.now();
                Page<SchedulingTaskInstance> page = taskInstanceRepository
                        .findPendingReadyForExecution("PENDING", now, now, PageRequest.of(pageIndex, TASK_PAGE_SIZE));

                if (!page.hasContent()) {
                    break;
                }

                for (SchedulingTaskInstance instance : page.getContent()) {
                    if (processed >= MAX_TASKS_PER_CYCLE) {
                        return;
                    }

                    if (!dependencyCheckerService.checkDependencies(instance)) {
                        logger.debug("任务实例 {} 的依赖未满足，跳过", instance.getId());
                        continue;
                    }

                    if (self.reserveTask(instance)) {
                        dispatchTask(instance);
                        processed++;
                    }
                }

                if (!page.hasNext()) {
                    break;
                }
                pageIndex++;
            }

            if (processed > 0) {
                logger.info("本轮执行任务 {} 个", processed);
            }
        } catch (Exception e) {
            logger.error("处理待处理任务失败", e);
        }
    }

    private void dispatchTask(SchedulingTaskInstance instance) {
        dispatchExecutor.execute(() -> {
            try {
                self.executeTask(instance);
            } catch (Exception e) {
                logger.error("Worker {} 异步派发任务失败, instanceId: {}", workerId, instance.getId(), e);
            }
        });
    }

    /** 僵尸任务回收：每 30 秒将超时未完成的 RESERVED/RUNNING 置为 PENDING、清空锁，并设 scheduledAt=now 以便立即重排 */
    @Scheduled(fixedDelay = 30000)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverZombieTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusSeconds(lockTimeoutSec);
        int n = taskInstanceRepository.recoverZombies(ZOMBIE_RECOVERY_STATUSES, threshold, now);
        if (n > 0) {
            logger.warn("僵尸任务回收: 将 {} 个超时实例置为 PENDING 并重排 (lock_time < {})", n, threshold);
        }
    }

    /** RUNNING 期间会持续刷新 lockTime，超时未刷新可视为僵尸实例。 */
    private static final Set<String> ZOMBIE_RECOVERY_STATUSES = Set.of("RESERVED", "RUNNING");

    /**
     * 抢占任务（原子操作）。
     * REQUIRES_NEW 确保在定时线程中调用时始终开启新事务，使 @Modifying 的 reserveTaskInstance 在事务内执行，避免 TransactionRequiredException。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserveTask(SchedulingTaskInstance instance) {
        LocalDateTime now = LocalDateTime.now();
        String concurrencyPolicy = resolveConcurrencyPolicy(instance);
        int updated;
        switch (concurrencyPolicy) {
            case "SEQUENTIAL":
                if (instance.getDagRunId() == null || !StringUtils.hasText(instance.getNodeCode())) {
                    updated = taskInstanceRepository.reserveTaskInstance(
                            instance.getId(), "RESERVED", workerId, now);
                } else {
                    updated = taskInstanceRepository.reserveSequentialTaskInstance(
                            instance.getId(),
                            "RESERVED",
                            workerId,
                            now,
                            instance.getDagRunId(),
                            instance.getNodeCode());
                }
                break;
            case "SINGLETON":
                updated = taskInstanceRepository.reserveSingletonTaskInstance(
                        instance.getId(),
                        "RESERVED",
                        workerId,
                        now,
                        instance.getTaskId());
                break;
            case "KEYED":
                String concurrencyKey = resolveConcurrencyKey(instance);
                if (!StringUtils.hasText(concurrencyKey)) {
                    updated = taskInstanceRepository.reserveTaskInstance(
                            instance.getId(), "RESERVED", workerId, now);
                } else {
                    updated = taskInstanceRepository.reserveKeyedTaskInstance(
                            instance.getId(),
                            "RESERVED",
                            workerId,
                            now,
                            instance.getTaskId(),
                            concurrencyKey);
                }
                break;
            case "PARALLEL":
            default:
                updated = taskInstanceRepository.reserveTaskInstance(
                        instance.getId(), "RESERVED", workerId, now);
                break;
        }
        
        if (updated > 0) {
            logger.info("Worker {} 抢占任务成功, instanceId: {}, policy: {}", workerId, instance.getId(), concurrencyPolicy);
            return true;
        } else {
            logger.debug("Worker {} 抢占任务失败（可能并发策略限制或已被其他 Worker 抢占）, instanceId: {}, policy: {}",
                    workerId, instance.getId(), concurrencyPolicy);
            return false;
        }
    }

    private String resolveConcurrencyPolicy(SchedulingTaskInstance instance) {
        SchedulingTaskExecutionSnapshot snapshot = readExecutionSnapshot(instance);
        SchedulingTaskExecutionSnapshot.TaskSnapshot taskSnapshot = snapshot != null ? snapshot.getTask() : null;
        String policy = taskSnapshot != null ? taskSnapshot.getConcurrencyPolicy() : null;
        if (!StringUtils.hasText(policy)) {
            SchedulingTask task = findTask(instance);
            policy = task != null ? task.getConcurrencyPolicy() : null;
        }
        if (!StringUtils.hasText(policy)) {
            policy = "PARALLEL";
        }
        return policy.toUpperCase();
    }

    private String resolveConcurrencyKey(SchedulingTaskInstance instance) {
        if (StringUtils.hasText(instance.getConcurrencyKey())) {
            return instance.getConcurrencyKey();
        }
        if (instance.getNodeCode() != null) {
            return dagTaskRepository.findByDagVersionIdAndNodeCode(
                    instance.getDagVersionId(), instance.getNodeCode())
                    .map(task -> {
                        if (task.getParallelGroup() != null && !task.getParallelGroup().isBlank()) {
                            return task.getParallelGroup();
                        }
                        return instance.getNodeCode();
                    })
                    .orElse(instance.getNodeCode());
        }
        return "TASK-" + instance.getTaskId();
    }

    /**
     * 执行任务
     */
    public void executeTask(SchedulingTaskInstance instance) {
        logger.info("Worker {} 开始执行任务, instanceId: {}", workerId, instance.getId());
        RunningTaskState runningTaskState = self.markTaskRunning(instance);
        if (runningTaskState == null) {
            return;
        }

        SchedulingTaskInstance runningInstance = runningTaskState.instance();
        SchedulingTaskHistory history = runningTaskState.history();
        LocalDateTime startTime = history.getStartTime() != null ? history.getStartTime() : LocalDateTime.now();
        SchedulingExecutionContext executionContext = buildExecutionContext(runningInstance);

        try {
            int timeoutSec = getTimeoutSec(runningInstance);
            Future<TaskExecutorService.TaskExecutionResult> future = submitWithExecutionContext(
                    executionContext,
                    () -> taskExecutorService.execute(executionContext, runningInstance));
            TaskExecutorService.TaskExecutionResult result = awaitTaskResult(future, runningInstance, timeoutSec);
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();

            if (isCancellationRequested(runningInstance.getId(), runningInstance.getTenantId())) {
                self.markTaskCancelled(runningInstance, history.getId(), endTime, durationMs);
                logger.info("Worker {} 任务已取消，跳过完成态回写, instanceId: {}", workerId, runningInstance.getId());
                return;
            }

            if (result.isSuccess()) {
                SchedulingTaskInstance completedInstance = self.markTaskSuccess(
                        runningInstance,
                        history.getId(),
                        result,
                        endTime,
                        durationMs);
                logger.info("Worker {} 任务执行成功, instanceId: {}, 耗时: {}ms", 
                        workerId, runningInstance.getId(), durationMs);
                if (completedInstance != null) {
                    scheduleDownstreamTasks(completedInstance);
                }
            } else {
                self.handleTaskFailure(runningInstance, history.getId(), result, endTime, durationMs);
            }
        } catch (Exception e) {
            logger.error("Worker {} 任务执行异常, instanceId: {}", workerId, runningInstance.getId(), e);
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();
            if (isCancellationRequested(runningInstance.getId(), runningInstance.getTenantId())) {
                self.markTaskCancelled(runningInstance, history.getId(), endTime, durationMs);
                return;
            }
            self.handleTaskFailure(runningInstance, history.getId(),
                    TaskExecutorService.TaskExecutionResult.failure(e.getMessage(), e),
                    endTime, durationMs);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RunningTaskState markTaskRunning(SchedulingTaskInstance instance) {
        SchedulingTaskInstance latest = findTaskInstance(instance.getId(), instance.getTenantId()).orElse(null);
        if (latest == null) {
            logger.warn("任务实例已不存在，跳过执行, instanceId: {}", instance.getId());
            return null;
        }

        String status = latest.getStatus();
        if (!"PENDING".equals(status) && !"RESERVED".equals(status)) {
            logger.warn("任务实例状态已变更为 {}，跳过执行, instanceId: {}", status, instance.getId());
            return null;
        }

        latest.setStatus("RUNNING");
        latest.setLockedBy(workerId);
        latest.setLockTime(LocalDateTime.now());
        latest = taskInstanceRepository.save(latest);

        SchedulingTaskHistory history = new SchedulingTaskHistory();
        history.setTaskInstanceId(latest.getId());
        history.setDagRunId(latest.getDagRunId());
        history.setDagId(latest.getDagId());
        history.setNodeCode(latest.getNodeCode());
        history.setTaskId(latest.getTaskId());
        history.setTenantId(latest.getTenantId());
        history.setAttemptNo(latest.getAttemptNo());
        history.setStatus("RUNNING");
        history.setStartTime(LocalDateTime.now());
        history.setParams(latest.getParams());
        history.setWorkerId(workerId);
        history = taskHistoryRepository.save(history);
        return new RunningTaskState(latest, history);
    }

    private SchedulingExecutionContext buildExecutionContext(SchedulingTaskInstance instance) {
        SchedulingExecutionContext.Builder builder = SchedulingExecutionContext.builder()
                .tenantId(instance.getTenantId())
                .dagId(instance.getDagId())
                .dagRunId(instance.getDagRunId())
                .dagVersionId(instance.getDagVersionId());
        if (instance.getDagRunId() != null) {
            Optional<SchedulingDagRun> dagRun = findDagRun(instance.getDagRunId(), instance.getTenantId());
            dagRun.ifPresent(run -> {
                builder.triggerType(run.getTriggerType());
                builder.username(run.getTriggeredBy());
            });
        }
        return builder.build();
    }

    private SchedulingTask findTask(SchedulingTaskInstance instance) {
        if (instance.getTenantId() != null) {
            return taskRepository.findByIdAndTenantId(instance.getTaskId(), instance.getTenantId()).orElse(null);
        }
        return taskRepository.findById(instance.getTaskId()).orElse(null);
    }

    private SchedulingTaskType findTaskType(Long taskTypeId, Long tenantId) {
        if (taskTypeId == null) {
            return null;
        }
        if (tenantId != null) {
            return taskTypeRepository.findByIdAndTenantId(taskTypeId, tenantId).orElse(null);
        }
        return taskTypeRepository.findById(taskTypeId).orElse(null);
    }

    private SchedulingTaskExecutionSnapshot readExecutionSnapshot(SchedulingTaskInstance instance) {
        if (!StringUtils.hasText(instance.getExecutionSnapshot())) {
            return null;
        }
        try {
            return objectMapper.readValue(instance.getExecutionSnapshot(), SchedulingTaskExecutionSnapshot.class);
        } catch (Exception e) {
            throw SchedulingExceptions.validation("任务实例执行快照损坏: %s", instance.getId());
        }
    }

    private ExecutionTaskConfig resolveTaskConfig(SchedulingTaskInstance instance) {
        SchedulingTaskExecutionSnapshot snapshot = readExecutionSnapshot(instance);
        SchedulingTaskExecutionSnapshot.TaskSnapshot taskSnapshot = snapshot != null ? snapshot.getTask() : null;
        if (taskSnapshot != null) {
            return new ExecutionTaskConfig(
                    taskSnapshot.getTaskTypeId(),
                    taskSnapshot.getParams(),
                    taskSnapshot.getTimeoutSec(),
                    taskSnapshot.getMaxRetry(),
                    taskSnapshot.getRetryPolicy());
        }

        SchedulingTask task = findTask(instance);
        if (task == null) {
            return new ExecutionTaskConfig(null, null, null, null, null);
        }
        return new ExecutionTaskConfig(
                task.getTypeId(),
                task.getParams(),
                task.getTimeoutSec(),
                task.getMaxRetry(),
                task.getRetryPolicy());
    }

    private ExecutionTaskTypeConfig resolveTaskTypeConfig(
            SchedulingTaskInstance instance,
            ExecutionTaskConfig taskConfig) {
        SchedulingTaskExecutionSnapshot snapshot = readExecutionSnapshot(instance);
        SchedulingTaskExecutionSnapshot.TaskTypeSnapshot taskTypeSnapshot = snapshot != null ? snapshot.getTaskType() : null;
        if (taskTypeSnapshot != null) {
            return new ExecutionTaskTypeConfig(
                    taskTypeSnapshot.getDefaultTimeoutSec(),
                    taskTypeSnapshot.getDefaultMaxRetry());
        }

        SchedulingTaskType taskType = findTaskType(taskConfig.taskTypeId(), instance.getTenantId());
        if (taskType == null) {
            return new ExecutionTaskTypeConfig(null, null);
        }
        return new ExecutionTaskTypeConfig(
                taskType.getDefaultTimeoutSec(),
                taskType.getDefaultMaxRetry());
    }

    private Optional<SchedulingTaskInstance> findTaskInstance(Long instanceId, Long tenantId) {
        if (tenantId != null) {
            return taskInstanceRepository.findByIdAndTenantId(instanceId, tenantId);
        }
        return taskInstanceRepository.findById(instanceId);
    }

    private Optional<SchedulingDagRun> findDagRun(Long dagRunId, Long tenantId) {
        if (dagRunId == null) {
            return Optional.empty();
        }
        if (tenantId != null) {
            return dagRunRepository.findByIdAndTenantId(dagRunId, tenantId);
        }
        return dagRunRepository.findById(dagRunId);
    }

    private <T> Future<T> submitWithExecutionContext(
            SchedulingExecutionContext executionContext,
            Callable<T> callable) {
        Long previousTenantId = TenantContext.getTenantId();
        String previousTenantSource = TenantContext.getTenantSource();
        try {
            applyExecutionContextTenant(executionContext);
            return taskExecutionExecutor.submit(callable);
        } finally {
            restoreTenantContext(previousTenantId, previousTenantSource);
        }
    }

    private TaskExecutorService.TaskExecutionResult awaitTaskResult(
            Future<TaskExecutorService.TaskExecutionResult> future,
            SchedulingTaskInstance instance,
            int timeoutSec) throws Exception {
        long heartbeatIntervalMillis = resolveHeartbeatIntervalMillis();
        long deadlineNanos = timeoutSec > 0
                ? System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec)
                : Long.MAX_VALUE;

        while (true) {
            long waitMillis = heartbeatIntervalMillis;
            if (timeoutSec > 0) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    future.cancel(true);
                    return timeoutResult(instance.getId(), timeoutSec);
                }
                waitMillis = Math.max(1L, Math.min(heartbeatIntervalMillis, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
            }

            try {
                return future.get(waitMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                if (isCancellationRequested(instance.getId(), instance.getTenantId())) {
                    future.cancel(true);
                    return cancelledResult();
                }
                self.touchTaskHeartbeat(instance.getId(), instance.getTenantId());
                if (timeoutSec > 0 && System.nanoTime() >= deadlineNanos) {
                    future.cancel(true);
                    return timeoutResult(instance.getId(), timeoutSec);
                }
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception exception) {
                    throw exception;
                }
                throw SchedulingExceptions.systemError("任务执行异常", cause);
            }
        }
    }

    private long resolveHeartbeatIntervalMillis() {
        int heartbeatSec = Math.max(1, Math.min(30, Math.max(1, lockTimeoutSec / 3)));
        return TimeUnit.SECONDS.toMillis(heartbeatSec);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touchTaskHeartbeat(Long instanceId, Long tenantId) {
        findTaskInstance(instanceId, tenantId).ifPresent(latest -> {
            if ("RUNNING".equals(latest.getStatus()) || "RESERVED".equals(latest.getStatus())) {
                latest.setLockTime(LocalDateTime.now());
                taskInstanceRepository.save(latest);
            }
        });
    }

    private boolean isCancellationRequested(Long instanceId, Long tenantId) {
        return findTaskInstance(instanceId, tenantId)
                .map(latest -> "CANCELLED".equals(latest.getStatus()))
                .orElse(false);
    }

    private TaskExecutorService.TaskExecutionResult timeoutResult(Long instanceId, int timeoutSec) {
        logger.warn("Worker {} 任务执行超时, instanceId: {}, 超时时间: {}秒",
                workerId, instanceId, timeoutSec);
        String timeoutMsg = "TIMEOUT: 任务执行超时（超过 " + timeoutSec + " 秒）";
        return TaskExecutorService.TaskExecutionResult.failure(
                timeoutMsg,
                new TimeoutException("任务执行超时"));
    }

    private TaskExecutorService.TaskExecutionResult cancelledResult() {
        return TaskExecutorService.TaskExecutionResult.failure(
                "CANCELLED: 任务已取消",
                new CancellationException("任务已取消"));
    }

    private void applyExecutionContextTenant(SchedulingExecutionContext executionContext) {
        TenantContext.clear();
        if (executionContext != null && executionContext.getTenantId() != null) {
            TenantContext.setTenantId(executionContext.getTenantId());
            TenantContext.setTenantSource(TenantContext.SOURCE_UNKNOWN);
        }
    }

    private void restoreTenantContext(Long previousTenantId, String previousTenantSource) {
        TenantContext.clear();
        if (previousTenantId != null) {
            TenantContext.setTenantId(previousTenantId);
        }
        if (previousTenantSource != null) {
            TenantContext.setTenantSource(previousTenantSource);
        }
    }

    /**
     * 处理任务失败
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleTaskFailure(
            SchedulingTaskInstance instance,
            Long historyId,
            TaskExecutorService.TaskExecutionResult result,
            LocalDateTime endTime,
            long durationMs) {
        SchedulingTaskInstance latest = findTaskInstance(instance.getId(), instance.getTenantId()).orElse(null);
        SchedulingTaskHistory history = taskHistoryRepository.findByIdAndTenantId(historyId, instance.getTenantId()).orElse(null);
        if (latest == null) {
            return;
        }
        if ("CANCELLED".equals(latest.getStatus())) {
            finalizeCancelled(latest, history, endTime, durationMs);
            return;
        }

        // 从任务定义中获取最大重试次数
        int maxRetry = getMaxRetry(latest);
        int currentAttempt = latest.getAttemptNo();

        if (currentAttempt < maxRetry) {
            // 可以重试：按 retryPolicy 计算延迟，并同步 scheduledAt 使 Worker 只在到点后拾取
            int retryDelaySec = resolveRetryDelaySec(latest);
            LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(retryDelaySec);
            latest.setStatus("PENDING");
            latest.setAttemptNo(currentAttempt + 1);
            latest.setNextRetryAt(nextRetryAt);
            latest.setScheduledAt(nextRetryAt);
            latest.setLockedBy(null);
            latest.setLockTime(null);
            latest.setResult(null);
            latest.setErrorMessage(null);
            taskInstanceRepository.save(latest);

            if (history != null) {
                history.setStatus("FAILED");
                history.setEndTime(endTime);
                history.setDurationMs(durationMs);
                history.setErrorMessage(result.getErrorMessage());
                taskHistoryRepository.save(history);
            }

            logger.info("Worker {} 任务执行失败，将重试, instanceId: {}, 当前尝试: {}/{}", 
                    workerId, latest.getId(), currentAttempt, maxRetry);
        } else {
            // 达到最大重试次数，标记为失败
            latest.setStatus("FAILED");
            latest.setResult(null);
            latest.setErrorMessage(result.getErrorMessage());
            latest.setLockedBy(null);
            latest.setLockTime(null);
            taskInstanceRepository.save(latest);

            if (history != null) {
                history.setStatus("FAILED");
                history.setEndTime(endTime);
                history.setDurationMs(durationMs);
                history.setErrorMessage(result.getErrorMessage());
                if (result.getException() != null) {
                    history.setStackTrace(getStackTrace(result.getException()));
                }
                taskHistoryRepository.save(history);
            }

            logger.error("Worker {} 任务执行失败，已达最大重试次数, instanceId: {}", 
                    workerId, latest.getId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SchedulingTaskInstance markTaskSuccess(
            SchedulingTaskInstance instance,
            Long historyId,
            TaskExecutorService.TaskExecutionResult result,
            LocalDateTime endTime,
            long durationMs) {
        SchedulingTaskInstance latest = findTaskInstance(instance.getId(), instance.getTenantId()).orElse(null);
        SchedulingTaskHistory history = taskHistoryRepository.findByIdAndTenantId(historyId, instance.getTenantId()).orElse(null);
        if (latest == null) {
            return null;
        }
        if ("CANCELLED".equals(latest.getStatus())) {
            finalizeCancelled(latest, history, endTime, durationMs);
            return null;
        }

        latest.setStatus("SUCCESS");
        latest.setResult(serializeResult(result.getResult()));
        latest.setErrorMessage(null);
        latest.setLockedBy(null);
        latest.setLockTime(null);
        latest = taskInstanceRepository.save(latest);

        if (history != null) {
            history.setStatus("SUCCESS");
            history.setEndTime(endTime);
            history.setDurationMs(durationMs);
            history.setResult(serializeResult(result.getResult()));
            taskHistoryRepository.save(history);
        }
        return latest;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markTaskCancelled(
            SchedulingTaskInstance instance,
            Long historyId,
            LocalDateTime endTime,
            long durationMs) {
        SchedulingTaskInstance latest = findTaskInstance(instance.getId(), instance.getTenantId()).orElse(null);
        SchedulingTaskHistory history = taskHistoryRepository.findByIdAndTenantId(historyId, instance.getTenantId()).orElse(null);
        finalizeCancelled(latest, history, endTime, durationMs);
    }

    private void finalizeCancelled(
            SchedulingTaskInstance latest,
            SchedulingTaskHistory history,
            LocalDateTime endTime,
            long durationMs) {
        if (latest != null) {
            latest.setStatus("CANCELLED");
            latest.setResult(null);
            latest.setLockedBy(null);
            latest.setLockTime(null);
            taskInstanceRepository.save(latest);
        }
        if (history != null) {
            history.setStatus("CANCELLED");
            history.setEndTime(endTime);
            history.setDurationMs(durationMs);
            taskHistoryRepository.save(history);
        }
    }

    /**
     * 调度下游任务：仅按边查出下游节点编码，再只加载这些节点的 PENDING 实例。
     */
    @Transactional
    public void scheduleDownstreamTasks(SchedulingTaskInstance completedInstance) {
        List<String> downstreamNodeCodes = dagEdgeRepository
                .findByDagVersionIdAndFromNodeCode(completedInstance.getDagVersionId(), completedInstance.getNodeCode())
                .stream()
                .map(edge -> edge.getToNodeCode())
                .toList();
        if (downstreamNodeCodes.isEmpty()) {
            return;
        }
        List<SchedulingTaskInstance> downstreamInstances = taskInstanceRepository
                .findByDagRunIdAndNodeCodeInAndStatusAndScheduledAtIsNull(
                        completedInstance.getDagRunId(), downstreamNodeCodes, "PENDING");

        LocalDateTime now = LocalDateTime.now();
        for (SchedulingTaskInstance downstream : downstreamInstances) {
            if (dependencyCheckerService.checkDependencies(downstream)) {
                downstream.setScheduledAt(now);
                taskInstanceRepository.save(downstream);
                logger.info("节点 {} 完成，调度下游节点 {}, taskInstanceId: {}",
                        completedInstance.getNodeCode(), downstream.getNodeCode(), downstream.getId());
            }
        }
    }

    /**
     * 获取任务超时时间（秒）
     * 优先级：节点级 timeoutSec > 任务级 timeoutSec > 任务类型默认 timeoutSec
     */
    private int getTimeoutSec(SchedulingTaskInstance instance) {
        try {
            ExecutionTaskConfig taskConfig = resolveTaskConfig(instance);
            // 1. 尝试从节点定义中获取
            if (instance.getDagVersionId() != null && instance.getNodeCode() != null) {
                List<SchedulingDagTask> dagTasks = dagTaskRepository.findByDagVersionId(instance.getDagVersionId());
                for (SchedulingDagTask dagTask : dagTasks) {
                    if (dagTask.getNodeCode().equals(instance.getNodeCode())) {
                        if (dagTask.getTimeoutSec() != null && dagTask.getTimeoutSec() > 0) {
                            return dagTask.getTimeoutSec();
                        }
                        break;
                    }
                }
            }

            // 2. 从任务快照/定义中获取
            if (taskConfig.timeoutSec() != null && taskConfig.timeoutSec() > 0) {
                return taskConfig.timeoutSec();
            }

            // 3. 从任务类型快照/定义中获取默认值
            ExecutionTaskTypeConfig taskTypeConfig = resolveTaskTypeConfig(instance, taskConfig);
            if (taskTypeConfig.defaultTimeoutSec() != null && taskTypeConfig.defaultTimeoutSec() > 0) {
                return taskTypeConfig.defaultTimeoutSec();
            }
        } catch (Exception e) {
            logger.warn("获取任务超时时间失败, instanceId: {}, 使用默认值 0（无限制）", instance.getId(), e);
        }
        
        // 默认无超时限制
        return 0;
    }

    /**
     * 序列化任务结果
     */
    private String serializeResult(Object result) {
        if (result == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.warn("任务结果序列化失败，使用 toString 兜底: {}", e.getMessage());
            return result.toString();
        }
    }

    /**
     * 获取最大重试次数
     * 优先级：节点级 maxRetry > 任务级 maxRetry > 任务类型默认 maxRetry
     */
    private int getMaxRetry(SchedulingTaskInstance instance) {
        try {
            ExecutionTaskConfig taskConfig = resolveTaskConfig(instance);
            // 1. 尝试从节点定义中获取
            if (instance.getDagVersionId() != null && instance.getNodeCode() != null) {
                List<SchedulingDagTask> dagTasks = dagTaskRepository.findByDagVersionId(instance.getDagVersionId());
                for (SchedulingDagTask dagTask : dagTasks) {
                    if (dagTask.getNodeCode().equals(instance.getNodeCode())) {
                        if (dagTask.getMaxRetry() != null && dagTask.getMaxRetry() > 0) {
                            return dagTask.getMaxRetry();
                        }
                        break;
                    }
                }
            }

            // 2. 从任务快照/定义中获取
            if (taskConfig.maxRetry() != null && taskConfig.maxRetry() > 0) {
                return taskConfig.maxRetry();
            }

            // 3. 从任务类型快照/定义中获取默认值
            ExecutionTaskTypeConfig taskTypeConfig = resolveTaskTypeConfig(instance, taskConfig);
            if (taskTypeConfig.defaultMaxRetry() != null && taskTypeConfig.defaultMaxRetry() > 0) {
                return taskTypeConfig.defaultMaxRetry();
            }
        } catch (Exception e) {
            logger.warn("获取最大重试次数失败, instanceId: {}, 使用默认值 0", instance.getId(), e);
        }
        
        // 默认不重试
        return 0;
    }

    /**
     * 从任务 retryPolicy（JSON）解析重试延迟秒数，支持最小字段 {"delaySec": 60}，默认 60 秒。
     */
    private int resolveRetryDelaySec(SchedulingTaskInstance instance) {
        try {
            ExecutionTaskConfig taskConfig = resolveTaskConfig(instance);
            if (!StringUtils.hasText(taskConfig.retryPolicy())) {
                return DEFAULT_RETRY_DELAY_SEC;
            }
            JsonNode node = objectMapper.readTree(taskConfig.retryPolicy());
            if (node != null && node.has("delaySec") && node.get("delaySec").isNumber()) {
                int sec = node.get("delaySec").asInt();
                return sec > 0 ? sec : DEFAULT_RETRY_DELAY_SEC;
            }
        } catch (Exception e) {
            logger.warn("解析 retryPolicy 失败, instanceId: {}, 使用默认延迟 {} 秒", instance.getId(), DEFAULT_RETRY_DELAY_SEC, e);
        }
        return DEFAULT_RETRY_DELAY_SEC;
    }

    /**
     * 获取异常堆栈
     */
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    public record RunningTaskState(SchedulingTaskInstance instance, SchedulingTaskHistory history) {}

    private record ExecutionTaskConfig(
            Long taskTypeId,
            String params,
            Integer timeoutSec,
            Integer maxRetry,
            String retryPolicy) {}

    private record ExecutionTaskTypeConfig(
            Integer defaultTimeoutSec,
            Integer defaultMaxRetry) {}
}
