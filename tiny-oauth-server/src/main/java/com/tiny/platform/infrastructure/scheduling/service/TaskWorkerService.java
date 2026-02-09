package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.scheduling.exception.SchedulingExceptions;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskHistory;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTask;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskType;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagTask;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskInstanceRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskHistoryRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskTypeRepository;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.TimeoutException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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
    private final ExecutorService executorService;

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
            SchedulingDagTaskRepository dagTaskRepository,
            SchedulingDagEdgeRepository dagEdgeRepository,
            TaskExecutorService taskExecutorService,
            DependencyCheckerService dependencyCheckerService,
            ObjectMapper objectMapper,
            @Qualifier("schedulingTaskExecutor") ExecutorService executorService) {
        this.taskInstanceRepository = taskInstanceRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.taskRepository = taskRepository;
        this.taskTypeRepository = taskTypeRepository;
        this.dagTaskRepository = dagTaskRepository;
        this.dagEdgeRepository = dagEdgeRepository;
        this.taskExecutorService = taskExecutorService;
        this.dependencyCheckerService = dependencyCheckerService;
        this.objectMapper = objectMapper;
        this.executorService = executorService;
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
        try {
            while (processed < MAX_TASKS_PER_CYCLE) {
                LocalDateTime now = LocalDateTime.now();
                Page<SchedulingTaskInstance> page = taskInstanceRepository
                        .findPendingReadyForExecution("PENDING", now, now, PageRequest.of(0, TASK_PAGE_SIZE));

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
                        self.executeTask(instance);
                        processed++;
                    }
                }

                if (page.getNumberOfElements() < TASK_PAGE_SIZE) {
                    break;
                }
            }

            if (processed > 0) {
                logger.info("本轮执行任务 {} 个", processed);
            }
        } catch (Exception e) {
            logger.error("处理待处理任务失败", e);
        }
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

    private static final Set<String> ACTIVE_STATUSES = Set.of("RESERVED", "RUNNING");

    /** 僵尸回收仅回收 RESERVED（已抢占未开跑），不回收 RUNNING */
    private static final Set<String> ZOMBIE_RECOVERY_STATUSES = Set.of("RESERVED");

    /**
     * 抢占任务（原子操作）。
     * REQUIRES_NEW 确保在定时线程中调用时始终开启新事务，使 @Modifying 的 reserveTaskInstance 在事务内执行，避免 TransactionRequiredException。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean reserveTask(SchedulingTaskInstance instance) {
        if (!canAcquire(instance)) {
            logger.debug("Worker {} 并发策略限制，无法抢占任务, instanceId: {}", workerId, instance.getId());
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = taskInstanceRepository.reserveTaskInstance(
                instance.getId(), "RESERVED", workerId, now);
        
        if (updated > 0) {
            logger.info("Worker {} 抢占任务成功, instanceId: {}", workerId, instance.getId());
            return true;
        } else {
            logger.debug("Worker {} 抢占任务失败（可能已被其他 Worker 抢占）, instanceId: {}", 
                    workerId, instance.getId());
            return false;
        }
    }

    private boolean canAcquire(SchedulingTaskInstance instance) {
        SchedulingTask task = taskRepository.findById(instance.getTaskId())
                .orElse(null);
        if (task == null) {
            return true;
        }
        String policy = task.getConcurrencyPolicy();
        if (policy == null || policy.isBlank()) {
            policy = "PARALLEL";
        }
        policy = policy.toUpperCase();

        switch (policy) {
            case "SEQUENTIAL":
                if (instance.getDagRunId() == null || instance.getNodeCode() == null) {
                    return true;
                }
                return !taskInstanceRepository.existsByDagRunIdAndNodeCodeAndStatusIn(
                        instance.getDagRunId(), instance.getNodeCode(), ACTIVE_STATUSES);
            case "SINGLETON":
                return !taskInstanceRepository.existsByTaskIdAndStatusIn(
                        instance.getTaskId(), ACTIVE_STATUSES);
            case "KEYED":
                String key = instance.getConcurrencyKey() != null && !instance.getConcurrencyKey().isBlank()
                        ? instance.getConcurrencyKey()
                        : getConcurrencyKey(instance);
                if (key == null) {
                    return true;
                }
                return !taskInstanceRepository.existsByTaskIdAndConcurrencyKeyAndStatusIn(
                        instance.getTaskId(), key, ACTIVE_STATUSES);
            case "PARALLEL":
            default:
                return true;
        }
    }

    private String getConcurrencyKey(SchedulingTaskInstance instance) {
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
    @Transactional
    public void executeTask(SchedulingTaskInstance instance) {
        logger.info("Worker {} 开始执行任务, instanceId: {}", workerId, instance.getId());

        // 执行前状态二次校验，避免已取消/已终态仍被执行
        SchedulingTaskInstance latest = taskInstanceRepository.findById(instance.getId()).orElse(null);
        if (latest == null) {
            logger.warn("任务实例已不存在，跳过执行, instanceId: {}", instance.getId());
            return;
        }
        String st = latest.getStatus();
        if (!"PENDING".equals(st) && !"RESERVED".equals(st) && !"RUNNING".equals(st)) {
            logger.warn("任务实例状态已变更为 {}，跳过执行, instanceId: {}", st, instance.getId());
            return;
        }

        // 1. 更新状态为 RUNNING
        instance.setStatus("RUNNING");
        taskInstanceRepository.save(instance);

        // 2. 创建执行历史记录
        SchedulingTaskHistory history = new SchedulingTaskHistory();
        history.setTaskInstanceId(instance.getId());
        history.setDagRunId(instance.getDagRunId());
        history.setDagId(instance.getDagId());
        history.setNodeCode(instance.getNodeCode());
        history.setTaskId(instance.getTaskId());
        history.setAttemptNo(instance.getAttemptNo());
        history.setStatus("RUNNING");
        history.setStartTime(LocalDateTime.now());
        history.setWorkerId(workerId);
        history = taskHistoryRepository.save(history);

        LocalDateTime startTime = LocalDateTime.now();
        TaskExecutorService.TaskExecutionResult result = null;

        try {
            // 3. 获取任务超时时间（秒）
            int timeoutSec = getTimeoutSec(instance);
            
            // 4. 执行任务（带超时控制）
            if (timeoutSec > 0) {
                // 有超时限制，使用 Future 实现超时控制
                Future<TaskExecutorService.TaskExecutionResult> future = executorService.submit(() -> {
                    return taskExecutorService.execute(instance);
                });
                
                try {
                    result = future.get(timeoutSec, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    // 任务超时，取消任务
                    future.cancel(true);
                    logger.warn("Worker {} 任务执行超时, instanceId: {}, 超时时间: {}秒", 
                            workerId, instance.getId(), timeoutSec);
                    String timeoutMsg = "TIMEOUT: 任务执行超时（超过 " + timeoutSec + " 秒）";
                    result = TaskExecutorService.TaskExecutionResult.failure(
                            timeoutMsg,
                            new TimeoutException("任务执行超时"));
                    // 立即写入 instance.errorMessage（与 result 分离），便于僵尸回收排除超时实例（若此后进程异常退出）
                    instance.setErrorMessage(timeoutMsg);
                    taskInstanceRepository.save(instance);
                } catch (ExecutionException e) {
                    // 任务执行异常
                    Throwable cause = e.getCause();
                    if (cause instanceof Exception) {
                        throw (Exception) cause;
                    } else {
                throw SchedulingExceptions.systemError("任务执行异常", cause);
                    }
                }
            } else {
                // 无超时限制，直接执行
                result = taskExecutorService.execute(instance);
            }

            // 4. 更新任务实例状态
            LocalDateTime endTime = LocalDateTime.now();
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();

            if (result.isSuccess()) {
                instance.setStatus("SUCCESS");
                instance.setResult(serializeResult(result.getResult()));
                instance.setLockedBy(null);
                instance.setLockTime(null);
                taskInstanceRepository.save(instance);

                // 更新历史记录
                history.setStatus("SUCCESS");
                history.setEndTime(endTime);
                history.setDurationMs(durationMs);
                history.setResult(serializeResult(result.getResult()));
                taskHistoryRepository.save(history);

                logger.info("Worker {} 任务执行成功, instanceId: {}, 耗时: {}ms", 
                        workerId, instance.getId(), durationMs);

                // 5. 检查并调度下游任务
                scheduleDownstreamTasks(instance);

            } else {
                // 执行失败，检查是否需要重试
                handleTaskFailure(instance, history, result, startTime, endTime, durationMs);
            }

        } catch (Exception e) {
            logger.error("Worker {} 任务执行异常, instanceId: {}", workerId, instance.getId(), e);
            handleTaskFailure(instance, history, 
                    TaskExecutorService.TaskExecutionResult.failure(e.getMessage(), e),
                    startTime, LocalDateTime.now(), 
                    java.time.Duration.between(startTime, LocalDateTime.now()).toMillis());
        }
    }

    /**
     * 处理任务失败
     */
    private void handleTaskFailure(
            SchedulingTaskInstance instance,
            SchedulingTaskHistory history,
            TaskExecutorService.TaskExecutionResult result,
            LocalDateTime startTime,
            LocalDateTime endTime,
            long durationMs) {

        // 从任务定义中获取最大重试次数
        int maxRetry = getMaxRetry(instance);
        int currentAttempt = instance.getAttemptNo();

        if (currentAttempt < maxRetry) {
            // 可以重试：按 retryPolicy 计算延迟，并同步 scheduledAt 使 Worker 只在到点后拾取
            int retryDelaySec = resolveRetryDelaySec(instance);
            LocalDateTime nextRetryAt = LocalDateTime.now().plusSeconds(retryDelaySec);
            instance.setStatus("PENDING");
            instance.setAttemptNo(currentAttempt + 1);
            instance.setNextRetryAt(nextRetryAt);
            instance.setScheduledAt(nextRetryAt);
            instance.setLockedBy(null);
            instance.setLockTime(null);
            instance.setResult(null);
            instance.setErrorMessage(null); // 清空上次失败原因，避免 PENDING 携带旧 errorMessage
            taskInstanceRepository.save(instance);

            history.setStatus("FAILED");
            history.setEndTime(endTime);
            history.setDurationMs(durationMs);
            history.setErrorMessage(result.getErrorMessage());
            taskHistoryRepository.save(history);

            logger.info("Worker {} 任务执行失败，将重试, instanceId: {}, 当前尝试: {}/{}", 
                    workerId, instance.getId(), currentAttempt, maxRetry);
        } else {
            // 达到最大重试次数，标记为失败
            instance.setStatus("FAILED");
            instance.setResult(null);
            instance.setErrorMessage(result.getErrorMessage()); // 保留失败原因，便于排查与僵尸回收排除
            instance.setLockedBy(null);
            instance.setLockTime(null);
            taskInstanceRepository.save(instance);

            history.setStatus("FAILED");
            history.setEndTime(endTime);
            history.setDurationMs(durationMs);
            history.setErrorMessage(result.getErrorMessage());
            if (result.getException() != null) {
                history.setStackTrace(getStackTrace(result.getException()));
            }
            taskHistoryRepository.save(history);

            logger.error("Worker {} 任务执行失败，已达最大重试次数, instanceId: {}", 
                    workerId, instance.getId());
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
            
            // 2. 从任务定义中获取
            SchedulingTask task = taskRepository.findById(instance.getTaskId())
                    .orElse(null);
            if (task != null && task.getTimeoutSec() != null && task.getTimeoutSec() > 0) {
                return task.getTimeoutSec();
            }
            
            // 3. 从任务类型中获取默认值
            if (task != null) {
                SchedulingTaskType taskType = taskTypeRepository.findById(task.getTypeId())
                        .orElse(null);
                if (taskType != null && taskType.getDefaultTimeoutSec() != null && taskType.getDefaultTimeoutSec() > 0) {
                    return taskType.getDefaultTimeoutSec();
                }
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
            
            // 2. 从任务定义中获取
            SchedulingTask task = taskRepository.findById(instance.getTaskId())
                    .orElse(null);
            if (task != null && task.getMaxRetry() != null && task.getMaxRetry() > 0) {
                return task.getMaxRetry();
            }
            
            // 3. 从任务类型中获取默认值
            if (task != null) {
                SchedulingTaskType taskType = taskTypeRepository.findById(task.getTypeId())
                        .orElse(null);
                if (taskType != null && taskType.getDefaultMaxRetry() != null && taskType.getDefaultMaxRetry() > 0) {
                    return taskType.getDefaultMaxRetry();
                }
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
            SchedulingTask task = taskRepository.findById(instance.getTaskId()).orElse(null);
            if (task == null || task.getRetryPolicy() == null || task.getRetryPolicy().isBlank()) {
                return DEFAULT_RETRY_DELAY_SEC;
            }
            JsonNode node = objectMapper.readTree(task.getRetryPolicy());
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
}

