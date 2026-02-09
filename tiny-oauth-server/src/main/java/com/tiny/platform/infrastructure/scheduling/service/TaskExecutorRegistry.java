package com.tiny.platform.infrastructure.scheduling.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务执行器注册表，负责管理和发现 TaskExecutor 实例。
 */
@Component
public class TaskExecutorRegistry {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecutorRegistry.class);

    private final Map<String, TaskExecutorService.TaskExecutor> executorsByName = new ConcurrentHashMap<>();
    private final Map<String, TaskExecutorService.TaskExecutor> executorsByClass = new ConcurrentHashMap<>();

    public TaskExecutorRegistry(ApplicationContext applicationContext) {
        Map<String, TaskExecutorService.TaskExecutor> beans = applicationContext.getBeansOfType(TaskExecutorService.TaskExecutor.class);
        beans.forEach((name, executor) -> {
            executorsByName.put(name, executor);
            executorsByClass.put(executor.getClass().getName(), executor);
            logger.info("注册任务执行器: name={}, class={}", name, executor.getClass().getName());
        });
    }

    /**
     * 返回当前已注册的执行器标识列表（bean 名称，排序），供前端下拉选择。
     */
    public List<String> getExecutorIdentifiers() {
        List<String> names = new ArrayList<>(executorsByName.keySet());
        names.sort(String::compareTo);
        return names;
    }

    public Optional<TaskExecutorService.TaskExecutor> find(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        TaskExecutorService.TaskExecutor byName = executorsByName.get(identifier);
        if (byName != null) {
            return Optional.of(byName);
        }
        TaskExecutorService.TaskExecutor byClass = executorsByClass.get(identifier);
        return Optional.ofNullable(byClass);
    }
}

