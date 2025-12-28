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
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TaskExecutorService taskExecutorService;
    private final DependencyCheckerService dependencyCheckerService;
    private final ObjectMapper objectMapper;

    private static final int TASK_PAGE_SIZE = 100;
    private static final int MAX_TASKS_PER_CYCLE = 500;

    private final String workerId;
    private final ExecutorService executorService;

    @Autowired
    public TaskWorkerService(
            SchedulingTaskInstanceRepository taskInstanceRepository,
            SchedulingTaskHistoryRepository taskHistoryRepository,
            SchedulingTaskRepository taskRepository,
            SchedulingTaskTypeRepository taskTypeRepository,
            SchedulingDagTaskRepository dagTaskRepository,
            TaskExecutorService taskExecutorService,
            DependencyCheckerService dependencyCheckerService,
            ObjectMapper objectMapper) {
        this.taskInstanceRepository = taskInstanceRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.taskRepository = taskRepository;
        this.taskTypeRepository = taskTypeRepository;
        this.dagTaskRepository = dagTaskRepository;
        this.taskExecutorService = taskExecutorService;
        this.dependencyCheckerService = dependencyCheckerService;
        this.objectMapper = objectMapper;
        this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
        // 创建线程池用于任务执行
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "task-executor-" + workerId);
            t.setDaemon(true);
            return t;
        });
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
                        .findByStatusAndScheduledAtLessThanEqual("PENDING", now, PageRequest.of(0, TASK_PAGE_SIZE));

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

                    if (reserveTask(instance)) {
                        executeTask(instance);
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

    private static final Set<String> ACTIVE_STATUSES = Set.of("RESERVED", "RUNNING");

    /**
     * 抢占任务（原子操作）
     */
    @Transactional
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
                String key = getConcurrencyKey(instance);
                if (key == null) {
                    return true;
                }
                return !taskInstanceRepository.existsByTaskIdAndNodeCodeAndStatusIn(
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
                    result = TaskExecutorService.TaskExecutionResult.failure(
                            "任务执行超时（超过 " + timeoutSec + " 秒）", 
                            new TimeoutException("任务执行超时"));
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
            // 可以重试
            instance.setStatus("PENDING");
            instance.setAttemptNo(currentAttempt + 1);
            instance.setNextRetryAt(LocalDateTime.now().plusSeconds(60)); // 60秒后重试
            instance.setLockedBy(null);
            instance.setLockTime(null);
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
     * 调度下游任务
     */
    @Transactional
    public void scheduleDownstreamTasks(SchedulingTaskInstance completedInstance) {
        // 1. 查找所有依赖此任务的下游任务
        List<SchedulingTaskInstance> downstreamInstances = taskInstanceRepository
                .findByDagRunId(completedInstance.getDagRunId())
                .stream()
                .filter(instance -> {
                    // TODO: 根据 DAG Edge 检查是否是下游任务
                    // 简化处理：检查是否有依赖关系
                    return dependencyCheckerService.isDownstreamTask(
                            completedInstance.getNodeCode(), 
                            instance.getNodeCode(),
                            completedInstance.getDagVersionId());
                })
                .filter(instance -> "PENDING".equals(instance.getStatus()) && instance.getScheduledAt() == null)
                .toList();

        // 2. 检查下游任务的依赖是否全部满足
        LocalDateTime now = LocalDateTime.now();
        for (SchedulingTaskInstance downstream : downstreamInstances) {
            if (dependencyCheckerService.checkDependencies(downstream)) {
                // 所有依赖已满足，设置调度时间
                downstream.setScheduledAt(now);
                taskInstanceRepository.save(downstream);
                logger.info("调度下游任务, instanceId: {}, nodeCode: {}", 
                        downstream.getId(), downstream.getNodeCode());
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
     * 获取异常堆栈
     */
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}

