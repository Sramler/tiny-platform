package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 校验 Worker 在高负载租户场景下的基础多租户公平性：
 * 同一轮扫描内不会为某个租户拾取超过配置上限的任务。
 */
public class TaskWorkerServiceFairnessTest {

    @Test
    void shouldRespectPerTenantCapWithinSingleCycle() throws Exception {
        SchedulingTaskInstanceRepository taskInstanceRepository = mock(SchedulingTaskInstanceRepository.class);
        SchedulingTaskHistoryRepository taskHistoryRepository = mock(SchedulingTaskHistoryRepository.class);
        SchedulingTaskRepository taskRepository = mock(SchedulingTaskRepository.class);
        SchedulingTaskTypeRepository taskTypeRepository = mock(SchedulingTaskTypeRepository.class);
        SchedulingDagRunRepository dagRunRepository = mock(SchedulingDagRunRepository.class);
        SchedulingDagTaskRepository dagTaskRepository = mock(SchedulingDagTaskRepository.class);
        SchedulingDagEdgeRepository dagEdgeRepository = mock(SchedulingDagEdgeRepository.class);
        TaskExecutorService taskExecutorService = mock(TaskExecutorService.class);
        DependencyCheckerService dependencyCheckerService = mock(DependencyCheckerService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ExecutorService taskExecutionExecutor = Executors.newSingleThreadExecutor();
        ExecutorService dispatchExecutor = Executors.newSingleThreadExecutor();

        TaskWorkerService worker = Mockito.spy(new TaskWorkerService(
                taskInstanceRepository,
                taskHistoryRepository,
                taskRepository,
                taskTypeRepository,
                dagRunRepository,
                dagTaskRepository,
                dagEdgeRepository,
                taskExecutorService,
                dependencyCheckerService,
                objectMapper,
                taskExecutionExecutor,
                dispatchExecutor));

        // 直接让 self 指向 spy 实例本身，便于统计 reserveTask 调用
        java.lang.reflect.Field selfField = TaskWorkerService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(worker, worker);

        // 将每租户上限设置得很小，便于断言
        java.lang.reflect.Field capField = TaskWorkerService.class.getDeclaredField("maxTasksPerTenantPerCycle");
        capField.setAccessible(true);
        capField.setInt(worker, 3);

        // 构造 2 个租户、各 10 个 PENDING 实例，模拟高负载租户
        List<SchedulingTaskInstance> instances = new ArrayList<>();
        for (long tenantId = 1L; tenantId <= 2L; tenantId++) {
            for (int i = 0; i < 10; i++) {
                SchedulingTaskInstance instance = new SchedulingTaskInstance();
                instance.setId(tenantId * 100 + i);
                instance.setTenantId(tenantId);
                instance.setStatus("PENDING");
                instance.setScheduledAt(LocalDateTime.now().minusSeconds(10));
                instances.add(instance);
            }
        }
        Page<SchedulingTaskInstance> page =
                new PageImpl<>(instances, PageRequest.of(0, instances.size()), instances.size());

        when(taskInstanceRepository.findPendingReadyForExecution(
                anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(page);
        when(dependencyCheckerService.checkDependencies(any())).thenReturn(true);

        java.util.Map<Long, Integer> reservedPerTenant = new java.util.HashMap<>();
        doAnswer(invocation -> {
            SchedulingTaskInstance inst = invocation.getArgument(0);
            reservedPerTenant.merge(inst.getTenantId(), 1, Integer::sum);
            return true;
        }).when(worker).reserveTask(any(SchedulingTaskInstance.class));

        doNothing().when(worker).executeTask(any());

        worker.processPendingTasks();

        assertThat(reservedPerTenant.getOrDefault(1L, 0))
                .isLessThanOrEqualTo(3);
        assertThat(reservedPerTenant.getOrDefault(2L, 0))
                .isLessThanOrEqualTo(3);
    }

    @Test
    void shouldNotStarveOtherTenantsWithinSameCycleWhenHighLoadTenantFirst() throws Exception {
        SchedulingTaskInstanceRepository taskInstanceRepository = mock(SchedulingTaskInstanceRepository.class);
        SchedulingTaskHistoryRepository taskHistoryRepository = mock(SchedulingTaskHistoryRepository.class);
        SchedulingTaskRepository taskRepository = mock(SchedulingTaskRepository.class);
        SchedulingTaskTypeRepository taskTypeRepository = mock(SchedulingTaskTypeRepository.class);
        SchedulingDagRunRepository dagRunRepository = mock(SchedulingDagRunRepository.class);
        SchedulingDagTaskRepository dagTaskRepository = mock(SchedulingDagTaskRepository.class);
        SchedulingDagEdgeRepository dagEdgeRepository = mock(SchedulingDagEdgeRepository.class);
        TaskExecutorService taskExecutorService = mock(TaskExecutorService.class);
        DependencyCheckerService dependencyCheckerService = mock(DependencyCheckerService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        ExecutorService taskExecutionExecutor = Executors.newSingleThreadExecutor();
        ExecutorService dispatchExecutor = Executors.newSingleThreadExecutor();

        TaskWorkerService worker = Mockito.spy(new TaskWorkerService(
                taskInstanceRepository,
                taskHistoryRepository,
                taskRepository,
                taskTypeRepository,
                dagRunRepository,
                dagTaskRepository,
                dagEdgeRepository,
                taskExecutorService,
                dependencyCheckerService,
                objectMapper,
                taskExecutionExecutor,
                dispatchExecutor));

        java.lang.reflect.Field selfField = TaskWorkerService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(worker, worker);

        java.lang.reflect.Field capField = TaskWorkerService.class.getDeclaredField("maxTasksPerTenantPerCycle");
        capField.setAccessible(true);
        capField.setInt(worker, 3);

        // 构造：先放入大量 tenant=1 的任务，再放入少量 tenant=2 的任务，模拟“高负载租户排在前面”
        List<SchedulingTaskInstance> instances = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            SchedulingTaskInstance instance = new SchedulingTaskInstance();
            instance.setId(100L + i);
            instance.setTenantId(1L);
            instance.setStatus("PENDING");
            instance.setScheduledAt(LocalDateTime.now().minusSeconds(10));
            instances.add(instance);
        }
        for (int i = 0; i < 2; i++) {
            SchedulingTaskInstance instance = new SchedulingTaskInstance();
            instance.setId(200L + i);
            instance.setTenantId(2L);
            instance.setStatus("PENDING");
            instance.setScheduledAt(LocalDateTime.now().minusSeconds(10));
            instances.add(instance);
        }

        Page<SchedulingTaskInstance> page =
                new PageImpl<>(instances, PageRequest.of(0, instances.size()), instances.size());

        when(taskInstanceRepository.findPendingReadyForExecution(
                anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                .thenReturn(page);
        when(dependencyCheckerService.checkDependencies(any())).thenReturn(true);

        java.util.Map<Long, Integer> reservedPerTenant = new java.util.HashMap<>();
        doAnswer(invocation -> {
            SchedulingTaskInstance inst = invocation.getArgument(0);
            reservedPerTenant.merge(inst.getTenantId(), 1, Integer::sum);
            return true;
        }).when(worker).reserveTask(any(SchedulingTaskInstance.class));

        doNothing().when(worker).executeTask(any());

        worker.processPendingTasks();

        // 高负载租户被 cap 限制
        assertThat(reservedPerTenant.getOrDefault(1L, 0)).isEqualTo(3);
        // 低负载租户在同一轮扫描内仍能被拾取（否则仍可能被“排序偏好”饿死）
        assertThat(reservedPerTenant.getOrDefault(2L, 0)).isEqualTo(2);
    }
}

