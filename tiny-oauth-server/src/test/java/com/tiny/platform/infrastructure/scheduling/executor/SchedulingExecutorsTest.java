package com.tiny.platform.infrastructure.scheduling.executor;

import com.tiny.platform.infrastructure.scheduling.service.SchedulingExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulingExecutorsTest {

    private static final SchedulingExecutionContext EXECUTION_CONTEXT = SchedulingExecutionContext.builder()
            .executionTenantId(88L)
            .dagRunId(99L)
            .build();

    @Test
    void delayTaskExecutorShouldReturnDelayMetadata() throws Exception {
        DelayTaskExecutor executor = new DelayTaskExecutor();

        Object result = executor.execute(EXECUTION_CONTEXT, Map.of("delayMs", 1));

        assertThat(result)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "OK")
                .containsEntry("delayMs", 1L)
                .containsKey("message");
    }

    @Test
    void delayTaskExecutorShouldFailWhenRequested() {
        DelayTaskExecutor executor = new DelayTaskExecutor();

        assertThatThrownBy(() -> executor.execute(EXECUTION_CONTEXT, Map.of("delayMs", 1, "fail", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模拟执行失败");
    }

    @Test
    void loggingTaskExecutorShouldEchoParams() {
        LoggingTaskExecutor executor = new LoggingTaskExecutor();

        Object result = executor.execute(EXECUTION_CONTEXT, Map.of("message", "hello"));

        assertThat(result)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "OK")
                .containsEntry("echo", Map.of("message", "hello"));
    }

    @Test
    void shellTaskExecutorShouldReturnPlaceholderMessage() {
        ShellTaskExecutor executor = new ShellTaskExecutor();

        Object result = executor.execute(EXECUTION_CONTEXT, Map.of("command", "echo hi"));

        assertThat(result)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "OK")
                .containsEntry("executor", "shellExecutor")
                .containsKey("message");
    }

    @Test
    void upperCaseExecutorShouldTransformMessage() {
        UpperCaseExecutor executor = new UpperCaseExecutor();

        Object result = executor.execute(EXECUTION_CONTEXT, Map.of("message", "hello world"));

        assertThat(result)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("status", "OK")
                .containsEntry("original", "hello world")
                .containsEntry("result", "HELLO WORLD");
    }
}
