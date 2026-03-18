package com.tiny.platform.infrastructure.scheduling.config;

import com.tiny.platform.core.oauth.tenant.TenantContext;
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

    @Value("${scheduling.worker.dispatch.pool-size:4}")
    private int dispatchPoolSize;

    @Bean(name = "schedulingTaskExecutor")
    public ExecutorService schedulingTaskExecutor(@Qualifier("mdcTaskDecorator") TaskDecorator mdcTaskDecorator) {
        return createTenantAwareExecutor(
                "scheduling-worker-",
                coreSize,
                maxSize,
                queueCapacity,
                mdcTaskDecorator);
    }

    @Bean(name = "schedulingDispatchExecutor")
    public ExecutorService schedulingDispatchExecutor(@Qualifier("mdcTaskDecorator") TaskDecorator mdcTaskDecorator) {
        return createTenantAwareExecutor(
                "scheduling-dispatch-",
                dispatchPoolSize,
                dispatchPoolSize,
                queueCapacity,
                mdcTaskDecorator);
    }

    private ExecutorService createTenantAwareExecutor(
            String threadNamePrefix,
            int corePoolSize,
            int maximumPoolSize,
            int taskQueueCapacity,
            TaskDecorator mdcTaskDecorator) {
        TaskDecorator tenantAwareTaskDecorator = command -> {
            Long capturedTenantId = TenantContext.getActiveTenantId();
            String capturedTenantSource = TenantContext.getTenantSource();
            Runnable mdcDecorated = mdcTaskDecorator.decorate(command);
            return () -> {
                Long previousTenantId = TenantContext.getActiveTenantId();
                String previousTenantSource = TenantContext.getTenantSource();
                try {
                    TenantContext.clear();
                    if (capturedTenantId != null) {
                        TenantContext.setActiveTenantId(capturedTenantId);
                    }
                    if (capturedTenantSource != null) {
                        TenantContext.setTenantSource(capturedTenantSource);
                    }
                    mdcDecorated.run();
                } finally {
                    TenantContext.clear();
                    if (previousTenantId != null) {
                        TenantContext.setActiveTenantId(previousTenantId);
                    }
                    if (previousTenantSource != null) {
                        TenantContext.setTenantSource(previousTenantSource);
                    }
                }
            };
        };
        AtomicInteger counter = new AtomicInteger(0);
        return new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(taskQueueCapacity),
                r -> {
                    Thread t = new Thread(r, threadNamePrefix + counter.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()) {
            @Override
            public void execute(Runnable command) {
                super.execute(tenantAwareTaskDecorator.decorate(command));
            }
        };
    }
}
