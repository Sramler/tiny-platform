package com.tiny.platform.core.oauth.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 统一配置 @Async 默认执行器，并挂载 MDC 透传。
 */
@Configuration
public class AsyncExecutionConfig {

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor(
            @Qualifier("mdcTaskDecorator") TaskDecorator mdcTaskDecorator,
            @Value("${spring.task.execution.pool.core-size:8}") int corePoolSize,
            @Value("${spring.task.execution.pool.max-size:32}") int maxPoolSize,
            @Value("${spring.task.execution.pool.queue-capacity:1000}") int queueCapacity,
            @Value("${spring.task.execution.pool.keep-alive:60s}") java.time.Duration keepAlive,
            @Value("${spring.task.execution.thread-name-prefix:app-async-}") String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(Math.toIntExact(keepAlive.getSeconds()));
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setTaskDecorator(mdcTaskDecorator);
        // 队列满时抛拒绝异常，避免请求线程被迫执行耗时任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
