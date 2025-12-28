package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.scheduling.exception.SchedulingExceptions;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
        logger.info("开始执行任务实例, instanceId: {}, taskId: {}, nodeCode: {}", 
                instance.getId(), instance.getTaskId(), instance.getNodeCode());

        try {
            // 1. 获取任务定义
            SchedulingTask task = taskRepository.findById(instance.getTaskId())
                    .orElseThrow(() -> SchedulingExceptions.notFound("任务不存在: %s", instance.getTaskId()));

            // 2. 获取任务类型
            SchedulingTaskType taskType = taskTypeRepository.findById(task.getTypeId())
                    .orElseThrow(() -> SchedulingExceptions.notFound("任务类型不存在: %s", task.getTypeId()));

            // 3. 验证参数（如果任务类型有 paramSchema）
            Map<String, Object> params = parseAndMergeParams(instance, task);
            jsonSchemaValidationService.validate(taskType.getParamSchema(), params);

            // 4. 获取执行器
            String executor = taskType.getExecutor();
            if (executor == null || executor.isEmpty()) {
                throw SchedulingExceptions.validation("任务类型未配置执行器: %s", taskType.getCode());
            }

            // 5. 根据执行器类型执行任务
            TaskExecutor executorBean = getExecutor(executor)
                    .orElseThrow(() -> SchedulingExceptions.notFound("找不到执行器: %s", executor));

            // 6. 执行任务
            Object result = executorBean.execute(params);

            logger.info("任务实例执行成功, instanceId: {}", instance.getId());
            return TaskExecutionResult.success(result);

        } catch (Exception e) {
            logger.error("任务实例执行失败, instanceId: {}", instance.getId(), e);
            return TaskExecutionResult.failure(e.getMessage(), e);
        }
    }

    /**
     * 获取执行器 Bean，优先从注册表中查找，支持名称或类名。
     */
    private Optional<TaskExecutor> getExecutor(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        // 优先从注册表获取
        Optional<TaskExecutor> registered = taskExecutorRegistry.find(identifier);
        if (registered.isPresent()) {
            return registered;
        }
        // 回退到按类名加载
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
            SchedulingTask task) {

        Map<String, Object> mergedParams = new HashMap<>();

        // 1. 任务默认参数
        mergedParams.putAll(parseJsonToMap(task.getParams()));

        // 2. 节点定义覆盖参数（最高优先级）
        if (instance.getDagVersionId() != null && instance.getNodeCode() != null) {
            dagTaskRepository.findByDagVersionIdAndNodeCode(instance.getDagVersionId(), instance.getNodeCode())
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
            logger.warn("解析 JSON 参数失败: {}", json, e);
            throw SchedulingExceptions.validation("解析参数失败: %s", e.getMessage());
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
        /**
         * 执行任务
         * @param params 任务参数
         * @return 执行结果
         */
        Object execute(Map<String, Object> params) throws Exception;
    }
}

