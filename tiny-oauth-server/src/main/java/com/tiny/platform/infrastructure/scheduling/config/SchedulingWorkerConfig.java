package com.tiny.platform.infrastructure.scheduling.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 调度 Worker 线程池配置：替代无界 newCachedThreadPool，使用可配置 core/max/queue。
 */
@Configuration
public class SchedulingWorkerConfig {

    @Value("${scheduling.worker.pool.core-size:4}")
    private int coreSize;

    @Value("${scheduling.worker.pool.max-size:16}")
    private int maxSize;

    @Value("${scheduling.worker.pool.queue-capacity:200}")
    private int queueCapacity;

    @Bean(name = "schedulingTaskExecutor")
    public ExecutorService schedulingTaskExecutor(@Qualifier("mdcTaskDecorator") TaskDecorator mdcTaskDecorator) {
        AtomicInteger counter = new AtomicInteger(0);
        return new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "scheduling-worker-" + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()) {
            @Override
            public void execute(Runnable command) {
                super.execute(mdcTaskDecorator.decorate(command));
            }
        };
    }
}
