package com.tiny.platform.core.oauth.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPropagationConfigTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldPropagateCapturedMdcAndRestorePreviousContext() {
        TaskDecorator decorator = new ContextPropagationConfig().contextPropagatingTaskDecorator();
        assertThat(decorator).isInstanceOf(ContextPropagatingTaskDecorator.class);

        MDC.put("traceId", "captured");
        Runnable decorated = decorator.decorate(() -> {
            assertThat(MDC.get("traceId")).isEqualTo("captured");
            MDC.put("inside", "1");
        });

        MDC.clear();
        MDC.put("pre", "existing");
        decorated.run();

        assertThat(MDC.get("pre")).isEqualTo("existing");
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("inside")).isNull();
    }

    @Test
    void shouldClearMdcInsideTaskWhenNoCapturedContextAndRestoreEmpty() {
        TaskDecorator decorator = new ContextPropagationConfig().contextPropagatingTaskDecorator();

        MDC.clear();
        Runnable decorated = decorator.decorate(() -> {
            assertThat(MDC.getCopyOfContextMap()).isNull();
            MDC.put("x", "y");
        });

        decorated.run();
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }
}
