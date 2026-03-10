package com.tiny.platform.infrastructure.scheduling.config;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskDecorator;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingWorkerConfigTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void schedulingTaskExecutorShouldPropagateAndRestoreTenantContext() throws Exception {
        SchedulingWorkerConfig config = new SchedulingWorkerConfig();
        ReflectionTestUtils.setField(config, "coreSize", 1);
        ReflectionTestUtils.setField(config, "maxSize", 1);
        ReflectionTestUtils.setField(config, "queueCapacity", 8);
        ReflectionTestUtils.setField(config, "dispatchPoolSize", 1);

        TaskDecorator decorator = runnable -> runnable;
        ExecutorService executor = config.schedulingTaskExecutor(decorator);
        try {
            TenantContext.setTenantId(88L);
            TenantContext.setTenantSource("request");
            AtomicLong childTenantId = new AtomicLong(-1L);
            AtomicReference<String> childSource = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            executor.execute(() -> {
                childTenantId.set(TenantContext.getTenantId());
                childSource.set(TenantContext.getTenantSource());
                latch.countDown();
            });

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(childTenantId.get()).isEqualTo(88L);
            assertThat(childSource.get()).isEqualTo("request");
            assertThat(TenantContext.getTenantId()).isEqualTo(88L);
            assertThat(TenantContext.getTenantSource()).isEqualTo("request");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void schedulingDispatchExecutorShouldRestorePreviousThreadTenantContext() throws Exception {
        SchedulingWorkerConfig config = new SchedulingWorkerConfig();
        ReflectionTestUtils.setField(config, "coreSize", 1);
        ReflectionTestUtils.setField(config, "maxSize", 1);
        ReflectionTestUtils.setField(config, "queueCapacity", 8);
        ReflectionTestUtils.setField(config, "dispatchPoolSize", 1);

        ExecutorService executor = config.schedulingDispatchExecutor(runnable -> runnable);
        try {
            TenantContext.setTenantId(99L);
            TenantContext.setTenantSource("dispatch");
            AtomicLong childTenantId = new AtomicLong(-1L);
            CountDownLatch latch = new CountDownLatch(1);

            executor.execute(() -> {
                childTenantId.set(TenantContext.getTenantId());
                latch.countDown();
            });

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(childTenantId.get()).isEqualTo(99L);
            assertThat(TenantContext.getTenantId()).isEqualTo(99L);
            assertThat(TenantContext.getTenantSource()).isEqualTo("dispatch");
        } finally {
            executor.shutdownNow();
        }
    }
}
