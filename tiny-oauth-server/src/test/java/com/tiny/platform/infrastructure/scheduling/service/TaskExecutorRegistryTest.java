package com.tiny.platform.infrastructure.scheduling.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskExecutorRegistryTest {

    @Test
    void shouldExposeSortedExecutorIdentifiersAndFindByNameOrClass() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        AlphaExecutor alphaExecutor = new AlphaExecutor();
        BetaExecutor betaExecutor = new BetaExecutor();
        when(applicationContext.getBeansOfType(TaskExecutorService.TaskExecutor.class)).thenReturn(Map.of(
                "betaExecutor", betaExecutor,
                "alphaExecutor", alphaExecutor
        ));

        TaskExecutorRegistry registry = new TaskExecutorRegistry(applicationContext);

        assertThat(registry.getExecutorIdentifiers()).isEqualTo(List.of("alphaExecutor", "betaExecutor"));
        assertThat(registry.find("alphaExecutor")).containsSame(alphaExecutor);
        assertThat(registry.find(betaExecutor.getClass().getName())).containsSame(betaExecutor);
        assertThat(registry.find(" ")).isEmpty();
        assertThat(registry.find("missing")).isEmpty();
    }

    private static final class AlphaExecutor implements TaskExecutorService.TaskExecutor {}

    private static final class BetaExecutor implements TaskExecutorService.TaskExecutor {}
}
