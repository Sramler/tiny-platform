package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagRun;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagEdge;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTask;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskHistory;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskType;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagEdgeRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagRunRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagTaskRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskHistoryRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskInstanceRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskTypeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskWorkerServiceCancellationTest {

    private SchedulingTaskInstanceRepository taskInstanceRepository;
    private SchedulingTaskHistoryRepository taskHistoryRepository;
    private SchedulingTaskRepository taskRepository;
    private SchedulingTaskTypeRepository taskTypeRepository;
    private SchedulingDagRunRepository dagRunRepository;
    private SchedulingDagTaskRepository dagTaskRepository;
    private SchedulingDagEdgeRepository dagEdgeRepository;
    private TaskExecutorService taskExecutorService;
    private DependencyCheckerService dependencyCheckerService;
    private ExecutorService taskExecutionExecutor;
    private ExecutorService dispatchExecutor;
    private TaskWorkerService taskWorkerService;

    @BeforeEach
    void setUp() {
        taskInstanceRepository = mock(SchedulingTaskInstanceRepository.class);
        taskHistoryRepository = mock(SchedulingTaskHistoryRepository.class);
        taskRepository = mock(SchedulingTaskRepository.class);
        taskTypeRepository = mock(SchedulingTaskTypeRepository.class);
        dagRunRepository = mock(SchedulingDagRunRepository.class);
        dagTaskRepository = mock(SchedulingDagTaskRepository.class);
        dagEdgeRepository = mock(SchedulingDagEdgeRepository.class);
        taskExecutorService = mock(TaskExecutorService.class);
        dependencyCheckerService = mock(DependencyCheckerService.class);
        taskExecutionExecutor = Executors.newSingleThreadExecutor();
        dispatchExecutor = Executors.newSingleThreadExecutor();
        taskWorkerService = new TaskWorkerService(
                taskInstanceRepository,
                taskHistoryRepository,
                taskRepository,
                taskTypeRepository,
                dagRunRepository,
                dagTaskRepository,
                dagEdgeRepository,
                taskExecutorService,
                dependencyCheckerService,
                new ObjectMapper(),
                taskExecutionExecutor,
                dispatchExecutor);
        ReflectionTestUtils.setField(taskWorkerService, "self", taskWorkerService);
        ReflectionTestUtils.setField(taskWorkerService, "lockTimeoutSec", 1);
        ReflectionTestUtils.setField(taskWorkerService, "maxTasksPerTenantPerCycle", 100);
    }

    @AfterEach
    void tearDown() {
        taskExecutionExecutor.shutdownNow();
        dispatchExecutor.shutdownNow();
    }

    @Test
    void executeTaskShouldKeepCancelledStatusWhenCancellationHappensDuringExecution() throws Exception {
        AtomicReference<SchedulingTaskInstance> storedInstance = new AtomicReference<>();
        SchedulingTaskInstance initial = new SchedulingTaskInstance();
        initial.setId(10L);
        initial.setTenantId(88L);
        initial.setDagId(5L);
        initial.setDagRunId(6L);
        initial.setDagVersionId(7L);
        initial.setTaskId(2L);
        initial.setAttemptNo(1);
        initial.setStatus("RESERVED");
        initial.setParams("{\"tenant\":88}");
        storedInstance.set(copyInstance(initial));

        AtomicReference<SchedulingTaskHistory> storedHistory = new AtomicReference<>();
        when(taskInstanceRepository.findByIdAndTenantId(10L, 88L)).thenAnswer(invocation ->
                Optional.of(copyInstance(storedInstance.get())));
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> {
            SchedulingTaskInstance saved = copyInstance(invocation.getArgument(0));
            storedInstance.set(saved);
            return copyInstance(saved);
        });
        when(taskHistoryRepository.save(any(SchedulingTaskHistory.class))).thenAnswer(invocation -> {
            SchedulingTaskHistory saved = copyHistory(invocation.getArgument(0));
            if (saved.getId() == null) {
                saved.setId(99L);
            }
            storedHistory.set(saved);
            return copyHistory(saved);
        });
        when(taskHistoryRepository.findById(99L)).thenAnswer(invocation ->
                Optional.ofNullable(copyHistory(storedHistory.get())));
        when(taskHistoryRepository.findByIdAndTenantId(99L, 88L)).thenAnswer(invocation ->
                Optional.ofNullable(copyHistory(storedHistory.get())));

        SchedulingDagRun dagRun = new SchedulingDagRun();
        dagRun.setId(6L);
        dagRun.setTriggerType("MANUAL");
        dagRun.setTriggeredBy("alice");
        when(dagRunRepository.findByIdAndTenantId(6L, 88L)).thenReturn(Optional.of(dagRun));

        SchedulingTask task = new SchedulingTask();
        task.setId(2L);
        task.setTenantId(88L);
        task.setTypeId(3L);
        task.setTimeoutSec(0);
        task.setMaxRetry(0);
        when(taskRepository.findByIdAndTenantId(2L, 88L)).thenReturn(Optional.of(task));

        SchedulingTaskType taskType = new SchedulingTaskType();
        taskType.setId(3L);
        taskType.setTenantId(88L);
        taskType.setDefaultTimeoutSec(0);
        taskType.setDefaultMaxRetry(0);
        when(taskTypeRepository.findByIdAndTenantId(3L, 88L)).thenReturn(Optional.of(taskType));

        when(taskExecutorService.execute(any(SchedulingExecutionContext.class), any(SchedulingTaskInstance.class)))
                .thenAnswer(invocation -> {
                    Thread.sleep(1500L);
                    return TaskExecutorService.TaskExecutionResult.success("ok");
                });

        Thread cancellationThread = new Thread(() -> {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            SchedulingTaskInstance cancelled = copyInstance(storedInstance.get());
            cancelled.setStatus("CANCELLED");
            cancelled.setLockedBy(null);
            cancelled.setLockTime(null);
            storedInstance.set(cancelled);
        });
        cancellationThread.start();

        taskWorkerService.executeTask(copyInstance(initial));
        cancellationThread.join();

        assertThat(storedInstance.get().getStatus()).isEqualTo("CANCELLED");
        assertThat(storedHistory.get()).isNotNull();
        assertThat(storedHistory.get().getStatus()).isEqualTo("CANCELLED");
        assertThat(storedHistory.get().getTenantId()).isEqualTo(88L);
        assertThat(storedHistory.get().getParams()).isEqualTo("{\"tenant\":88}");
    }

    @Test
    void recoverZombieTasksShouldRecoverRunningInstancesToo() {
        when(taskInstanceRepository.recoverZombies(anySet(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(2);

        taskWorkerService.recoverZombieTasks();

        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Set.class);
        verify(taskInstanceRepository).recoverZombies(captor.capture(), any(LocalDateTime.class), any(LocalDateTime.class));
        assertThat(captor.getValue()).contains("RESERVED", "RUNNING");
    }

    @Test
    void reserveTaskShouldUseAtomicSingletonReservation() {
        SchedulingTaskInstance instance = new SchedulingTaskInstance();
        instance.setId(20L);
        instance.setTaskId(9L);
        instance.setTenantId(88L);
        instance.setStatus("PENDING");

        SchedulingTask task = new SchedulingTask();
        task.setId(9L);
        task.setTenantId(88L);
        task.setConcurrencyPolicy("SINGLETON");
        when(taskRepository.findByIdAndTenantId(9L, 88L)).thenReturn(Optional.of(task));
        when(taskInstanceRepository.reserveSingletonTaskInstance(
                anyLong(), anyString(), anyString(), any(LocalDateTime.class), anyLong()))
                .thenReturn(1);

        boolean reserved = taskWorkerService.reserveTask(instance);

        assertThat(reserved).isTrue();
        verify(taskInstanceRepository).reserveSingletonTaskInstance(
                anyLong(), anyString(), anyString(), any(LocalDateTime.class), anyLong());
        verify(taskInstanceRepository, never()).existsByTaskIdAndStatusIn(anyLong(), anySet());
    }

    @Test
    void processPendingTasksShouldAdvanceToLaterPagesWhenFirstPageCannotBeReserved() {
        TaskWorkerService spyWorker = spy(taskWorkerService);
        ReflectionTestUtils.setField(spyWorker, "self", spyWorker);

        SchedulingTaskInstance blocked = new SchedulingTaskInstance();
        blocked.setId(30L);
        blocked.setTaskId(3L);
        blocked.setTenantId(88L);
        blocked.setStatus("PENDING");
        blocked.setScheduledAt(LocalDateTime.now());

        SchedulingTask blockedTask = new SchedulingTask();
        blockedTask.setId(3L);
        blockedTask.setTenantId(88L);
        blockedTask.setConcurrencyPolicy("SINGLETON");

        SchedulingTaskInstance ready = new SchedulingTaskInstance();
        ready.setId(31L);
        ready.setTaskId(4L);
        ready.setTenantId(88L);
        ready.setStatus("PENDING");
        ready.setScheduledAt(LocalDateTime.now());

        SchedulingTask readyTask = new SchedulingTask();
        readyTask.setId(4L);
        readyTask.setTenantId(88L);
        readyTask.setConcurrencyPolicy("PARALLEL");

        when(taskRepository.findByIdAndTenantId(3L, 88L)).thenReturn(Optional.of(blockedTask));
        when(taskRepository.findByIdAndTenantId(4L, 88L)).thenReturn(Optional.of(readyTask));
        when(dependencyCheckerService.checkDependencies(any(SchedulingTaskInstance.class))).thenReturn(true);
        when(taskInstanceRepository.findPendingReadyForExecution(
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                argThat(pageable -> pageable.getPageNumber() == 0)))
                .thenReturn(new PageImpl<>(List.of(blocked), PageRequest.of(0, 100), 101));
        when(taskInstanceRepository.findPendingReadyForExecution(
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                argThat(pageable -> pageable.getPageNumber() == 1)))
                .thenReturn(new PageImpl<>(List.of(ready), PageRequest.of(1, 100), 101));
        when(taskInstanceRepository.reserveSingletonTaskInstance(
                anyLong(), anyString(), anyString(), any(LocalDateTime.class), anyLong()))
                .thenReturn(0);
        when(taskInstanceRepository.reserveTaskInstance(
                eq(31L), anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);
        doNothing().when(spyWorker).executeTask(any(SchedulingTaskInstance.class));

        spyWorker.processPendingTasks();

        verify(taskInstanceRepository).findPendingReadyForExecution(
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                argThat(pageable -> pageable.getPageNumber() == 0));
        verify(taskInstanceRepository).findPendingReadyForExecution(
                anyString(),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                argThat(pageable -> pageable.getPageNumber() == 1));
        verify(taskInstanceRepository).reserveTaskInstance(
                eq(31L), anyString(), anyString(), any(LocalDateTime.class));
    }

    @Test
    void scheduleDownstreamTasksShouldNotReleaseMergeWhenOnlyOneParallelBranchCompletes() {
        SchedulingTaskInstance completed = new SchedulingTaskInstance();
        completed.setId(40L);
        completed.setDagRunId(77L);
        completed.setDagVersionId(3L);
        completed.setNodeCode("count-orders");

        SchedulingDagEdge edge = new SchedulingDagEdge();
        edge.setDagVersionId(3L);
        edge.setFromNodeCode("count-orders");
        edge.setToNodeCode("merge-report");
        when(dagEdgeRepository.findByDagVersionIdAndFromNodeCode(3L, "count-orders"))
                .thenReturn(List.of(edge));

        SchedulingTaskInstance merge = new SchedulingTaskInstance();
        merge.setId(41L);
        merge.setDagRunId(77L);
        merge.setDagVersionId(3L);
        merge.setNodeCode("merge-report");
        merge.setStatus("PENDING");
        when(taskInstanceRepository.findByDagRunIdAndNodeCodeInAndStatusAndScheduledAtIsNull(
                77L, List.of("merge-report"), "PENDING"))
                .thenReturn(List.of(merge));
        when(dependencyCheckerService.checkDependencies(merge)).thenReturn(false);

        taskWorkerService.scheduleDownstreamTasks(completed);

        verify(taskInstanceRepository, never()).save(argThat(instance ->
                "merge-report".equals(instance.getNodeCode()) && instance.getScheduledAt() != null));
    }

    @Test
    void scheduleDownstreamTasksShouldReleaseMergeAfterAllParallelBranchesComplete() {
        SchedulingTaskInstance completed = new SchedulingTaskInstance();
        completed.setId(42L);
        completed.setDagRunId(77L);
        completed.setDagVersionId(3L);
        completed.setNodeCode("count-users");

        SchedulingDagEdge edge = new SchedulingDagEdge();
        edge.setDagVersionId(3L);
        edge.setFromNodeCode("count-users");
        edge.setToNodeCode("merge-report");
        when(dagEdgeRepository.findByDagVersionIdAndFromNodeCode(3L, "count-users"))
                .thenReturn(List.of(edge));

        SchedulingTaskInstance merge = new SchedulingTaskInstance();
        merge.setId(43L);
        merge.setDagRunId(77L);
        merge.setDagVersionId(3L);
        merge.setNodeCode("merge-report");
        merge.setStatus("PENDING");
        when(taskInstanceRepository.findByDagRunIdAndNodeCodeInAndStatusAndScheduledAtIsNull(
                77L, List.of("merge-report"), "PENDING"))
                .thenReturn(List.of(merge));
        when(dependencyCheckerService.checkDependencies(merge)).thenReturn(true);
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        taskWorkerService.scheduleDownstreamTasks(completed);

        verify(taskInstanceRepository).save(argThat(instance ->
                "merge-report".equals(instance.getNodeCode()) && instance.getScheduledAt() != null));
    }

    @Test
    void scheduleDownstreamTasksShouldReleaseNextSerialStageImmediately() {
        SchedulingTaskInstance completed = new SchedulingTaskInstance();
        completed.setId(44L);
        completed.setDagRunId(88L);
        completed.setDagVersionId(4L);
        completed.setNodeCode("stage-1");

        SchedulingDagEdge edge = new SchedulingDagEdge();
        edge.setDagVersionId(4L);
        edge.setFromNodeCode("stage-1");
        edge.setToNodeCode("stage-2");
        when(dagEdgeRepository.findByDagVersionIdAndFromNodeCode(4L, "stage-1"))
                .thenReturn(List.of(edge));

        SchedulingTaskInstance nextStage = new SchedulingTaskInstance();
        nextStage.setId(45L);
        nextStage.setDagRunId(88L);
        nextStage.setDagVersionId(4L);
        nextStage.setNodeCode("stage-2");
        nextStage.setStatus("PENDING");
        when(taskInstanceRepository.findByDagRunIdAndNodeCodeInAndStatusAndScheduledAtIsNull(
                88L, List.of("stage-2"), "PENDING"))
                .thenReturn(List.of(nextStage));
        when(dependencyCheckerService.checkDependencies(nextStage)).thenReturn(true);
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        taskWorkerService.scheduleDownstreamTasks(completed);

        verify(taskInstanceRepository).save(argThat(instance ->
                "stage-2".equals(instance.getNodeCode()) && instance.getScheduledAt() != null));
    }

    @Test
    void markTaskRunningShouldSkipWhenStatusAlreadyChanged() {
        SchedulingTaskInstance latest = new SchedulingTaskInstance();
        latest.setId(91L);
        latest.setTenantId(88L);
        latest.setStatus("SUCCESS");
        when(taskInstanceRepository.findByIdAndTenantId(91L, 88L)).thenReturn(Optional.of(latest));

        TaskWorkerService.RunningTaskState state = taskWorkerService.markTaskRunning(latest);

        assertThat(state).isNull();
        verify(taskHistoryRepository, never()).save(any(SchedulingTaskHistory.class));
    }

    @Test
    void handleTaskFailureShouldScheduleRetryWhenAttemptsRemain() {
        AtomicReference<SchedulingTaskInstance> storedInstance = new AtomicReference<>();
        SchedulingTaskInstance latest = new SchedulingTaskInstance();
        latest.setId(100L);
        latest.setTenantId(88L);
        latest.setTaskId(2L);
        latest.setAttemptNo(1);
        latest.setStatus("RUNNING");
        storedInstance.set(copyInstance(latest));

        SchedulingTaskHistory history = new SchedulingTaskHistory();
        history.setId(501L);
        history.setTenantId(88L);
        history.setStatus("RUNNING");

        SchedulingTask task = new SchedulingTask();
        task.setId(2L);
        task.setTenantId(88L);
        task.setMaxRetry(2);
        task.setRetryPolicy("{\"delaySec\":5}");

        when(taskInstanceRepository.findByIdAndTenantId(100L, 88L))
                .thenAnswer(invocation -> Optional.of(copyInstance(storedInstance.get())));
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> {
            SchedulingTaskInstance saved = copyInstance(invocation.getArgument(0));
            storedInstance.set(saved);
            return copyInstance(saved);
        });
        when(taskHistoryRepository.findByIdAndTenantId(501L, 88L)).thenReturn(Optional.of(history));
        when(taskHistoryRepository.save(any(SchedulingTaskHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.findByIdAndTenantId(2L, 88L)).thenReturn(Optional.of(task));

        taskWorkerService.handleTaskFailure(
                latest,
                501L,
                TaskExecutorService.TaskExecutionResult.failure("boom", new IllegalStateException("boom")),
                LocalDateTime.now(),
                123L);

        SchedulingTaskInstance retried = storedInstance.get();
        assertThat(retried.getStatus()).isEqualTo("PENDING");
        assertThat(retried.getAttemptNo()).isEqualTo(2);
        assertThat(retried.getNextRetryAt()).isNotNull();
        assertThat(retried.getScheduledAt()).isEqualTo(retried.getNextRetryAt());
        assertThat(retried.getLockedBy()).isNull();
        assertThat(retried.getLockTime()).isNull();
        assertThat(retried.getErrorMessage()).isNull();
        assertThat(history.getStatus()).isEqualTo("FAILED");
        assertThat(history.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void handleTaskFailureShouldMarkFailedWhenRetryExhausted() {
        AtomicReference<SchedulingTaskInstance> storedInstance = new AtomicReference<>();
        SchedulingTaskInstance latest = new SchedulingTaskInstance();
        latest.setId(101L);
        latest.setTenantId(88L);
        latest.setTaskId(2L);
        latest.setAttemptNo(1);
        latest.setStatus("RUNNING");
        storedInstance.set(copyInstance(latest));

        SchedulingTaskHistory history = new SchedulingTaskHistory();
        history.setId(502L);
        history.setTenantId(88L);
        history.setStatus("RUNNING");

        SchedulingTask task = new SchedulingTask();
        task.setId(2L);
        task.setTenantId(88L);
        task.setMaxRetry(1);

        when(taskInstanceRepository.findByIdAndTenantId(101L, 88L))
                .thenAnswer(invocation -> Optional.of(copyInstance(storedInstance.get())));
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> {
            SchedulingTaskInstance saved = copyInstance(invocation.getArgument(0));
            storedInstance.set(saved);
            return copyInstance(saved);
        });
        when(taskHistoryRepository.findByIdAndTenantId(502L, 88L)).thenReturn(Optional.of(history));
        when(taskHistoryRepository.save(any(SchedulingTaskHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskRepository.findByIdAndTenantId(2L, 88L)).thenReturn(Optional.of(task));

        taskWorkerService.handleTaskFailure(
                latest,
                502L,
                TaskExecutorService.TaskExecutionResult.failure("fatal", new IllegalStateException("fatal-ex")),
                LocalDateTime.now(),
                456L);

        SchedulingTaskInstance failed = storedInstance.get();
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getErrorMessage()).isEqualTo("fatal");
        assertThat(failed.getLockedBy()).isNull();
        assertThat(history.getStatus()).isEqualTo("FAILED");
        assertThat(history.getErrorMessage()).isEqualTo("fatal");
        assertThat(history.getStackTrace()).contains("fatal-ex");
    }

    private SchedulingTaskInstance copyInstance(SchedulingTaskInstance source) {
        SchedulingTaskInstance copy = new SchedulingTaskInstance();
        copy.setId(source.getId());
        copy.setDagRunId(source.getDagRunId());
        copy.setDagId(source.getDagId());
        copy.setDagVersionId(source.getDagVersionId());
        copy.setNodeCode(source.getNodeCode());
        copy.setConcurrencyKey(source.getConcurrencyKey());
        copy.setTaskId(source.getTaskId());
        copy.setTenantId(source.getTenantId());
        copy.setAttemptNo(source.getAttemptNo());
        copy.setStatus(source.getStatus());
        copy.setScheduledAt(source.getScheduledAt());
        copy.setLockedBy(source.getLockedBy());
        copy.setLockTime(source.getLockTime());
        copy.setNextRetryAt(source.getNextRetryAt());
        copy.setParams(source.getParams());
        copy.setExecutionSnapshot(source.getExecutionSnapshot());
        copy.setResult(source.getResult());
        copy.setErrorMessage(source.getErrorMessage());
        return copy;
    }

    private SchedulingTaskHistory copyHistory(SchedulingTaskHistory source) {
        if (source == null) {
            return null;
        }
        SchedulingTaskHistory copy = new SchedulingTaskHistory();
        copy.setId(source.getId());
        copy.setTaskInstanceId(source.getTaskInstanceId());
        copy.setDagRunId(source.getDagRunId());
        copy.setDagId(source.getDagId());
        copy.setNodeCode(source.getNodeCode());
        copy.setTaskId(source.getTaskId());
        copy.setTenantId(source.getTenantId());
        copy.setAttemptNo(source.getAttemptNo());
        copy.setStatus(source.getStatus());
        copy.setStartTime(source.getStartTime());
        copy.setEndTime(source.getEndTime());
        copy.setDurationMs(source.getDurationMs());
        copy.setParams(source.getParams());
        copy.setResult(source.getResult());
        copy.setErrorMessage(source.getErrorMessage());
        copy.setStackTrace(source.getStackTrace());
        copy.setWorkerId(source.getWorkerId());
        return copy;
    }
}
