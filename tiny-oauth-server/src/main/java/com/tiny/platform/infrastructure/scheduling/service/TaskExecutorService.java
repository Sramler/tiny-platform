package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.scheduling.exception.SchedulingExceptions;
import com.tiny.platform.infrastructure.scheduling.security.SchedulingErrorSanitizer;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskExecutionSnapshot;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTask;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskType;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagTask;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskTypeRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * 任务执行器服务
 * 负责执行具体的任务
 */
@Service
public class TaskExecutorService {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecutorService.class);

    private final SchedulingTaskRepository taskRepository;
    private final SchedulingTaskTypeRepository taskTypeRepository;
    private final SchedulingDagTaskRepository dagTaskRepository;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final JsonSchemaValidationService jsonSchemaValidationService;
    private final TaskExecutorRegistry taskExecutorRegistry;

    @Autowired
    public TaskExecutorService(
            SchedulingTaskRepository taskRepository,
            SchedulingTaskTypeRepository taskTypeRepository,
            SchedulingDagTaskRepository dagTaskRepository,
            ApplicationContext applicationContext,
            ObjectMapper objectMapper,
            JsonSchemaValidationService jsonSchemaValidationService,
            TaskExecutorRegistry taskExecutorRegistry) {
        this.taskRepository = taskRepository;
        this.taskTypeRepository = taskTypeRepository;
        this.dagTaskRepository = dagTaskRepository;
        this.applicationContext = applicationContext;
        this.objectMapper = objectMapper;
        this.jsonSchemaValidationService = jsonSchemaValidationService;
        this.taskExecutorRegistry = taskExecutorRegistry;
    }

    /**
     * 执行任务实例
     */
    public TaskExecutionResult execute(SchedulingTaskInstance instance) {
        return execute(SchedulingExecutionContext.builder()
                .executionTenantId(instance.getTenantId())
                .dagId(instance.getDagId())
                .dagRunId(instance.getDagRunId())
                .dagVersionId(instance.getDagVersionId())
                .build(), instance);
    }

    public TaskExecutionResult execute(SchedulingExecutionContext executionContext, SchedulingTaskInstance instance) {
        logger.info("开始执行任务实例, executionTenantId: {}, runId: {}, instanceId: {}, taskId: {}, nodeCode: {}",
                executionContext != null ? executionContext.getExecutionTenantId() : null,
                executionContext != null ? executionContext.getDagRunId() : null,
                instance.getId(), instance.getTaskId(), instance.getNodeCode());

        try {
            Object result = runWithTenantContext(executionContext, () -> {
                Long executionTenantId = executionContext != null ? executionContext.getExecutionTenantId() : null;
                SchedulingTaskExecutionSnapshot snapshot = readExecutionSnapshot(instance);
                ExecutionTaskConfig taskConfig = resolveTaskConfig(instance, snapshot, executionTenantId);
                ExecutionTaskTypeConfig taskTypeConfig = resolveTaskTypeConfig(snapshot, taskConfig, executionTenantId);

                Map<String, Object> params = parseAndMergeParams(instance, taskConfig);
                jsonSchemaValidationService.validate(taskTypeConfig.paramSchema(), params);

                String executor = taskTypeConfig.executor();
                if (executor == null || executor.isEmpty()) {
                    throw SchedulingExceptions.validation("任务类型未配置执行器: %s", taskConfig.taskId());
                }

                TaskExecutor executorBean = getExecutor(executor)
                        .orElseThrow(() -> SchedulingExceptions.notFound("找不到执行器: %s", executor));

                return executorBean.execute(executionContext, params);
            });

            logger.info("任务实例执行成功, instanceId: {}", instance.getId());
            return TaskExecutionResult.success(result);

        } catch (Exception e) {
            logger.error("任务实例执行失败, instanceId: {}", instance.getId(), e);
            return TaskExecutionResult.failure(SchedulingErrorSanitizer.sanitizeForPersistence(e.getMessage()), e);
        }
    }

    private SchedulingTask findTask(Long taskId, Long tenantId) {
        if (tenantId != null && tenantId > 0) {
            return taskRepository.findByIdAndTenantId(taskId, tenantId)
                    .orElseThrow(() -> SchedulingExceptions.notFound("任务不存在: %s", taskId));
        }
        return taskRepository.findById(taskId)
                .orElseThrow(() -> SchedulingExceptions.notFound("任务不存在: %s", taskId));
    }

    private SchedulingTaskType findTaskType(Long taskTypeId, Long tenantId) {
        if (tenantId != null && tenantId > 0) {
            return taskTypeRepository.findByIdAndTenantId(taskTypeId, tenantId)
                    .orElseThrow(() -> SchedulingExceptions.notFound("任务类型不存在: %s", taskTypeId));
        }
        return taskTypeRepository.findById(taskTypeId)
                .orElseThrow(() -> SchedulingExceptions.notFound("任务类型不存在: %s", taskTypeId));
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

    private ExecutionTaskConfig resolveTaskConfig(
            SchedulingTaskInstance instance,
            SchedulingTaskExecutionSnapshot snapshot,
            Long tenantId) {
        SchedulingTaskExecutionSnapshot.TaskSnapshot taskSnapshot = snapshot != null ? snapshot.getTask() : null;
        if (taskSnapshot != null) {
            return new ExecutionTaskConfig(
                    instance.getTaskId(),
                    taskSnapshot.getTaskTypeId(),
                    taskSnapshot.getParams(),
                    taskSnapshot.getTimeoutSec(),
                    taskSnapshot.getMaxRetry(),
                    taskSnapshot.getRetryPolicy());
        }

        SchedulingTask task = findTask(instance.getTaskId(), tenantId);
        if (Boolean.FALSE.equals(task.getEnabled())) {
            throw SchedulingExceptions.operationNotAllowed("任务已禁用，无法执行: %s", task.getId());
        }
        return new ExecutionTaskConfig(
                task.getId(),
                task.getTypeId(),
                task.getParams(),
                task.getTimeoutSec(),
                task.getMaxRetry(),
                task.getRetryPolicy());
    }

    private ExecutionTaskTypeConfig resolveTaskTypeConfig(
            SchedulingTaskExecutionSnapshot snapshot,
            ExecutionTaskConfig taskConfig,
            Long tenantId) {
        SchedulingTaskExecutionSnapshot.TaskTypeSnapshot taskTypeSnapshot = snapshot != null ? snapshot.getTaskType() : null;
        if (taskTypeSnapshot != null) {
            return new ExecutionTaskTypeConfig(
                    taskTypeSnapshot.getExecutor(),
                    taskTypeSnapshot.getParamSchema(),
                    taskTypeSnapshot.getDefaultTimeoutSec(),
                    taskTypeSnapshot.getDefaultMaxRetry());
        }

        SchedulingTaskType taskType = findTaskType(taskConfig.taskTypeId(), tenantId);
        if (Boolean.FALSE.equals(taskType.getEnabled())) {
            throw SchedulingExceptions.operationNotAllowed("任务类型已禁用，无法执行: %s", taskType.getId());
        }
        return new ExecutionTaskTypeConfig(
                taskType.getExecutor(),
                taskType.getParamSchema(),
                taskType.getDefaultTimeoutSec(),
                taskType.getDefaultMaxRetry());
    }

    private <T> T runWithTenantContext(SchedulingExecutionContext executionContext, Callable<T> callable) throws Exception {
        Long previousTenantId = TenantContext.getActiveTenantId();
        String previousTenantSource = TenantContext.getTenantSource();
        try {
            TenantContext.clear();
            if (executionContext != null && executionContext.getExecutionTenantId() != null) {
                TenantContext.setActiveTenantId(executionContext.getExecutionTenantId());
                TenantContext.setTenantSource(TenantContext.SOURCE_UNKNOWN);
            }
            return callable.call();
        } finally {
            TenantContext.clear();
            if (previousTenantId != null) {
                TenantContext.setActiveTenantId(previousTenantId);
            }
            if (previousTenantSource != null) {
                TenantContext.setTenantSource(previousTenantSource);
            }
        }
    }

    /**
     * 获取执行器 Bean：注册表（名称/类名） → Bean 名称 → 类名加载。
     */
    private Optional<TaskExecutor> getExecutor(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        // 1. 优先从注册表获取（含 Bean 名与类名）
        Optional<TaskExecutor> registered = taskExecutorRegistry.find(identifier);
        if (registered.isPresent()) {
            return registered;
        }
        // 2. 若标识符像 Bean 名（无包名），先按 Bean 名查找，避免误用 Class.forName 导致 ClassNotFoundException
        if (!identifier.contains(".")) {
            try {
                TaskExecutor byName = applicationContext.getBean(identifier, TaskExecutor.class);
                return Optional.of(byName);
            } catch (Exception ignored) {
                // 非 Bean 名，继续按类名加载
            }
        }
        // 3. 回退到按类名加载
        try {
            Class<?> clazz = ClassUtils.forName(identifier, this.getClass().getClassLoader());
            if (TaskExecutor.class.isAssignableFrom(clazz)) {
                @SuppressWarnings("unchecked")
                Class<? extends TaskExecutor> executorType = (Class<? extends TaskExecutor>) clazz;
                TaskExecutor executor = applicationContext.getBean(executorType);
                logger.warn("执行器 {} 未在注册表中，已通过类型 {} 自动装配。建议在执行器上添加 @Component 并在注册表中注册。", identifier, clazz.getName());
                return Optional.ofNullable(executor);
            }
            logger.warn("执行器 {} 不是 TaskExecutor 类型", identifier);
        } catch (ClassNotFoundException e) {
            logger.error("获取执行器失败: {}（未找到 Bean 或类）", identifier, e);
        } catch (Exception e) {
            logger.error("获取执行器失败: {}", identifier, e);
        }
        return Optional.empty();
    }

    /**
     * 解析并合并参数
     * 优先级：节点覆盖参数 > 任务默认参数
     */
    private Map<String, Object> parseAndMergeParams(
            SchedulingTaskInstance instance,
            ExecutionTaskConfig taskConfig) {

        Map<String, Object> mergedParams = new HashMap<>();

        // 1. 任务默认参数
        mergedParams.putAll(parseJsonToMap(taskConfig.params()));

        // 2. 节点定义覆盖参数（最高优先级）；无请求上下文时按租户过滤
        if (instance.getDagVersionId() != null && instance.getNodeCode() != null) {
            (instance.getTenantId() != null
                    ? dagTaskRepository.findByDagVersionIdAndNodeCodeAndTenantId(
                            instance.getDagVersionId(), instance.getNodeCode(), instance.getTenantId())
                    : dagTaskRepository.findByDagVersionIdAndNodeCode(instance.getDagVersionId(), instance.getNodeCode()))
                    .map(SchedulingDagTask::getOverrideParams)
                    .ifPresent(json -> mergedParams.putAll(parseJsonToMap(json)));
        }

        // 3. 运行时实例覆盖参数
        mergedParams.putAll(parseJsonToMap(instance.getParams()));

        return mergedParams;
    }

    private Map<String, Object> parseJsonToMap(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            logger.warn("解析 JSON 参数失败", e);
            throw SchedulingExceptions.validation("解析参数失败，请检查参数格式");
        }
    }

    /**
     * 任务执行结果
     */
    public static class TaskExecutionResult {
        private final boolean success;
        private final Object result;
        private final String errorMessage;
        private final Exception exception;

        private TaskExecutionResult(boolean success, Object result, String errorMessage, Exception exception) {
            this.success = success;
            this.result = result;
            this.errorMessage = errorMessage;
            this.exception = exception;
        }

        public static TaskExecutionResult success(Object result) {
            return new TaskExecutionResult(true, result, null, null);
        }

        public static TaskExecutionResult failure(String errorMessage, Exception exception) {
            return new TaskExecutionResult(false, null, errorMessage, exception);
        }

        public boolean isSuccess() {
            return success;
        }

        public Object getResult() {
            return result;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Exception getException() {
            return exception;
        }
    }

    /**
     * 任务执行器接口
     */
    public interface TaskExecutor {
        default Object execute(SchedulingExecutionContext executionContext, Map<String, Object> params) throws Exception {
            return execute(params);
        }

        /**
         * 执行任务
         * @param params 任务参数
         * @return 执行结果
         */
        default Object execute(Map<String, Object> params) throws Exception {
            throw new UnsupportedOperationException("TaskExecutor must implement execute(context, params) or execute(params)");
        }
    }

    private record ExecutionTaskConfig(
            Long taskId,
            Long taskTypeId,
            String params,
            Integer timeoutSec,
            Integer maxRetry,
            String retryPolicy) {}

    private record ExecutionTaskTypeConfig(
            String executor,
            String paramSchema,
            Integer defaultTimeoutSec,
            Integer defaultMaxRetry) {}
}
