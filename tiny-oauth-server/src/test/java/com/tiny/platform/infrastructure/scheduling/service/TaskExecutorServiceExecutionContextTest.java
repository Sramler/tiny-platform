package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTask;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskExecutionSnapshot;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskType;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagTaskRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskTypeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskExecutorServiceExecutionContextTest {

    private SchedulingTaskRepository taskRepository;
    private SchedulingTaskTypeRepository taskTypeRepository;
    private SchedulingDagTaskRepository dagTaskRepository;
    private ApplicationContext applicationContext;
    private JsonSchemaValidationService jsonSchemaValidationService;
    private TaskExecutorRegistry taskExecutorRegistry;
    private TaskExecutorService taskExecutorService;

    @BeforeEach
    void setUp() {
        taskRepository = mock(SchedulingTaskRepository.class);
        taskTypeRepository = mock(SchedulingTaskTypeRepository.class);
        dagTaskRepository = mock(SchedulingDagTaskRepository.class);
        applicationContext = mock(ApplicationContext.class);
        jsonSchemaValidationService = mock(JsonSchemaValidationService.class);
        taskExecutorRegistry = mock(TaskExecutorRegistry.class);
        taskExecutorService = new TaskExecutorService(
                taskRepository,
                taskTypeRepository,
                dagTaskRepository,
                applicationContext,
                new ObjectMapper(),
                jsonSchemaValidationService,
                taskExecutorRegistry);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void executeShouldUseTenantScopedLookupsAndRestoreTenantContext() {
        SchedulingTaskInstance instance = new SchedulingTaskInstance();
        instance.setId(10L);
        instance.setTaskId(2L);
        instance.setTenantId(88L);
        instance.setDagId(5L);
        instance.setDagRunId(6L);
        instance.setDagVersionId(7L);
        instance.setParams("{\"name\":\"tenant\"}");

        SchedulingTask task = new SchedulingTask();
        task.setId(2L);
        task.setTenantId(88L);
        task.setTypeId(3L);
        task.setParams("{\"prefix\":\"hello\"}");

        SchedulingTaskType taskType = new SchedulingTaskType();
        taskType.setId(3L);
        taskType.setTenantId(88L);
        taskType.setExecutor("demoExecutor");

        when(taskRepository.findByIdAndTenantId(2L, 88L)).thenReturn(Optional.of(task));
        when(taskTypeRepository.findByIdAndTenantId(3L, 88L)).thenReturn(Optional.of(taskType));

        AtomicLong tenantInsideExecutor = new AtomicLong(-1L);
        AtomicReference<SchedulingExecutionContext> executionContextRef = new AtomicReference<>();
        AtomicReference<Map<String, Object>> paramsRef = new AtomicReference<>();

        TaskExecutorService.TaskExecutor executor = new TaskExecutorService.TaskExecutor() {
            @Override
            public Object execute(SchedulingExecutionContext executionContext, Map<String, Object> params) {
                tenantInsideExecutor.set(TenantContext.getTenantId());
                executionContextRef.set(executionContext);
                paramsRef.set(params);
                return Map.of(
                        "tenantId", TenantContext.getTenantId(),
                        "username", executionContext.getUsername(),
                        "prefix", params.get("prefix"),
                        "name", params.get("name"));
            }
        };
        when(taskExecutorRegistry.find("demoExecutor")).thenReturn(Optional.of(executor));

        TenantContext.setTenantId(999L);
        TenantContext.setTenantSource(TenantContext.SOURCE_SESSION);

        SchedulingExecutionContext executionContext = SchedulingExecutionContext.builder()
                .tenantId(88L)
                .userId("8")
                .username("alice")
                .dagId(5L)
                .dagRunId(6L)
                .dagVersionId(7L)
                .triggerType("MANUAL")
                .build();

        TaskExecutorService.TaskExecutionResult result = taskExecutorService.execute(executionContext, instance);

        assertThat(result.isSuccess()).isTrue();
        assertThat(tenantInsideExecutor.get()).isEqualTo(88L);
        assertThat(executionContextRef.get()).isNotNull();
        assertThat(executionContextRef.get().getTenantId()).isEqualTo(88L);
        assertThat(executionContextRef.get().getUsername()).isEqualTo("alice");
        assertThat(paramsRef.get()).containsEntry("prefix", "hello");
        assertThat(paramsRef.get()).containsEntry("name", "tenant");
        assertThat(result.getResult()).isEqualTo(Map.of(
                "tenantId", 88L,
                "username", "alice",
                "prefix", "hello",
                "name", "tenant"));
        assertThat(TenantContext.getTenantId()).isEqualTo(999L);
        assertThat(TenantContext.getTenantSource()).isEqualTo(TenantContext.SOURCE_SESSION);

        verify(taskRepository).findByIdAndTenantId(2L, 88L);
        verify(taskRepository, never()).findById(2L);
        verify(taskTypeRepository).findByIdAndTenantId(3L, 88L);
        verify(taskTypeRepository, never()).findById(3L);
        verify(jsonSchemaValidationService).validate(eq(null), anyMap());
    }

    @Test
    void executeShouldPreferExecutionSnapshotOverLiveDefinitions() throws Exception {
        SchedulingTaskInstance instance = new SchedulingTaskInstance();
        instance.setId(11L);
        instance.setTaskId(22L);
        instance.setTenantId(88L);
        instance.setParams("{\"name\":\"snapshot\"}");

        SchedulingTask task = new SchedulingTask();
        task.setId(22L);
        task.setTypeId(33L);
        task.setParams("{\"prefix\":\"live\"}");

        SchedulingTaskType taskType = new SchedulingTaskType();
        taskType.setId(33L);
        taskType.setExecutor("liveExecutor");

        SchedulingTaskExecutionSnapshot snapshot = SchedulingTaskExecutionSnapshot.from(task, taskType);
        snapshot.getTask().setParams("{\"prefix\":\"snap\"}");
        snapshot.getTaskType().setExecutor("demoExecutor");
        instance.setExecutionSnapshot(new ObjectMapper().writeValueAsString(snapshot));

        AtomicReference<Map<String, Object>> paramsRef = new AtomicReference<>();
        TaskExecutorService.TaskExecutor executor = new TaskExecutorService.TaskExecutor() {
            @Override
            public Object execute(SchedulingExecutionContext executionContext, Map<String, Object> params) {
                paramsRef.set(params);
                return params.get("prefix") + "-" + params.get("name");
            }
        };
        when(taskExecutorRegistry.find("demoExecutor")).thenReturn(Optional.of(executor));

        TaskExecutorService.TaskExecutionResult result = taskExecutorService.execute(
                SchedulingExecutionContext.builder().tenantId(88L).build(),
                instance);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getResult()).isEqualTo("snap-snapshot");
        assertThat(paramsRef.get()).containsEntry("prefix", "snap");
        assertThat(paramsRef.get()).containsEntry("name", "snapshot");
        verify(taskRepository, never()).findByIdAndTenantId(22L, 88L);
        verify(taskRepository, never()).findById(22L);
        verify(taskTypeRepository, never()).findByIdAndTenantId(33L, 88L);
        verify(taskTypeRepository, never()).findById(33L);
    }

    @Test
    void executeShouldRejectDisabledLiveTaskDefinition() {
        SchedulingTaskInstance instance = new SchedulingTaskInstance();
        instance.setId(12L);
        instance.setTaskId(22L);
        instance.setTenantId(88L);

        SchedulingTask task = new SchedulingTask();
        task.setId(22L);
        task.setTenantId(88L);
        task.setTypeId(33L);
        task.setEnabled(false);

        when(taskRepository.findByIdAndTenantId(22L, 88L)).thenReturn(Optional.of(task));

        TaskExecutorService.TaskExecutionResult result = taskExecutorService.execute(
                SchedulingExecutionContext.builder().tenantId(88L).build(),
                instance);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("任务已禁用");
        verify(taskRepository).findByIdAndTenantId(22L, 88L);
        verify(taskTypeRepository, never()).findByIdAndTenantId(33L, 88L);
        verify(taskExecutorRegistry, never()).find(any());
    }

    @Test
    void executeShouldRejectDisabledLiveTaskTypeDefinition() {
        SchedulingTaskInstance instance = new SchedulingTaskInstance();
        instance.setId(13L);
        instance.setTaskId(22L);
        instance.setTenantId(88L);

        SchedulingTask task = new SchedulingTask();
        task.setId(22L);
        task.setTenantId(88L);
        task.setTypeId(33L);
        task.setEnabled(true);

        SchedulingTaskType taskType = new SchedulingTaskType();
        taskType.setId(33L);
        taskType.setTenantId(88L);
        taskType.setEnabled(false);

        when(taskRepository.findByIdAndTenantId(22L, 88L)).thenReturn(Optional.of(task));
        when(taskTypeRepository.findByIdAndTenantId(33L, 88L)).thenReturn(Optional.of(taskType));

        TaskExecutorService.TaskExecutionResult result = taskExecutorService.execute(
                SchedulingExecutionContext.builder().tenantId(88L).build(),
                instance);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("任务类型已禁用");
        verify(taskRepository).findByIdAndTenantId(22L, 88L);
        verify(taskTypeRepository).findByIdAndTenantId(33L, 88L);
        verify(taskExecutorRegistry, never()).find(any());
    }
}
