package com.tiny.platform.infrastructure.scheduling.service;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagRun;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagRunRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DagRunMonitorServiceTest {

    private SchedulingDagRunRepository dagRunRepository;
    private SchedulingTaskInstanceRepository taskInstanceRepository;
    private DependencyCheckerService dependencyCheckerService;
    private DagRunMonitorService dagRunMonitorService;

    @BeforeEach
    void setUp() {
        dagRunRepository = mock(SchedulingDagRunRepository.class);
        taskInstanceRepository = mock(SchedulingTaskInstanceRepository.class);
        dependencyCheckerService = mock(DependencyCheckerService.class);
        dagRunMonitorService = new DagRunMonitorService(
                dagRunRepository,
                taskInstanceRepository,
                dependencyCheckerService);
    }

    @Test
    void updateDagRunStatusShouldKeepRunRunningWhenOnlyPausedInstancesRemain() {
        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setStatus("RUNNING");

        SchedulingTaskInstance pausedA = new SchedulingTaskInstance();
        pausedA.setStatus("PAUSED");
        SchedulingTaskInstance pausedB = new SchedulingTaskInstance();
        pausedB.setStatus("PAUSED");
        when(taskInstanceRepository.findByDagRunId(77L)).thenReturn(List.of(pausedA, pausedB));

        dagRunMonitorService.updateDagRunStatus(run);

        assertThat(run.getStatus()).isEqualTo("RUNNING");
        verify(dagRunRepository, never()).save(any(SchedulingDagRun.class));
    }

    @Test
    void updateDagRunStatusShouldSkipWhenRunAlreadyTerminal() {
        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setStatus("CANCELLED");

        dagRunMonitorService.updateDagRunStatus(run);

        verify(taskInstanceRepository, never()).findByDagRunId(any());
        verify(dagRunRepository, never()).save(any());
    }

    @Test
    void updateDagRunStatusShouldReturnWhenNoInstancesExist() {
        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(88L);
        run.setStatus("RUNNING");
        when(taskInstanceRepository.findByDagRunId(88L)).thenReturn(List.of());

        dagRunMonitorService.updateDagRunStatus(run);

        verify(dagRunRepository, never()).save(any());
    }

    @Test
    void updateDagRunStatusShouldCollapseUnreachablePendingNodeToSkippedAndFinishSuccess() {
        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(99L);
        run.setStatus("RUNNING");

        SchedulingTaskInstance pending = new SchedulingTaskInstance();
        pending.setId(11L);
        pending.setStatus("PENDING");

        SchedulingTaskInstance skipped = new SchedulingTaskInstance();
        skipped.setId(11L);
        skipped.setStatus("SKIPPED");

        when(taskInstanceRepository.findByDagRunId(99L))
                .thenReturn(List.of(pending))
                .thenReturn(List.of(skipped));
        when(dependencyCheckerService.hasAnyUpstreamInTerminalFailOrSkipped(eq(pending), any())).thenReturn(true);

        dagRunMonitorService.updateDagRunStatus(run);

        assertThat(run.getStatus()).isEqualTo("SUCCESS");
        assertThat(run.getEndTime()).isNotNull();
        verify(taskInstanceRepository).save(pending);
        verify(dagRunRepository).save(run);
    }

    @Test
    void updateDagRunStatusShouldMarkRunFailedWhenAllInstancesFailed() {
        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(100L);
        run.setStatus("RUNNING");

        SchedulingTaskInstance failed = new SchedulingTaskInstance();
        failed.setStatus("FAILED");
        when(taskInstanceRepository.findByDagRunId(100L)).thenReturn(List.of(failed));

        dagRunMonitorService.updateDagRunStatus(run);

        assertThat(run.getStatus()).isEqualTo("FAILED");
        assertThat(run.getEndTime()).isNotNull();
        verify(dagRunRepository).save(run);
    }

    @Test
    void updateDagRunStatusShouldMarkRunPartialFailedWhenSuccessAndFailureCoexist() {
        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(101L);
        run.setStatus("RUNNING");

        SchedulingTaskInstance success = new SchedulingTaskInstance();
        success.setStatus("SUCCESS");
        SchedulingTaskInstance failed = new SchedulingTaskInstance();
        failed.setStatus("FAILED");
        when(taskInstanceRepository.findByDagRunId(101L)).thenReturn(List.of(success, failed));

        dagRunMonitorService.updateDagRunStatus(run);

        assertThat(run.getStatus()).isEqualTo("PARTIAL_FAILED");
        assertThat(run.getEndTime()).isNotNull();
    }

    @Test
    void updateDagRunStatusShouldMarkRunCancelledWhenAllInstancesCancelled() {
        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(102L);
        run.setStatus("RUNNING");

        SchedulingTaskInstance cancelledA = new SchedulingTaskInstance();
        cancelledA.setStatus("CANCELLED");
        SchedulingTaskInstance cancelledB = new SchedulingTaskInstance();
        cancelledB.setStatus("CANCELLED");
        when(taskInstanceRepository.findByDagRunId(102L)).thenReturn(List.of(cancelledA, cancelledB));

        dagRunMonitorService.updateDagRunStatus(run);

        assertThat(run.getStatus()).isEqualTo("CANCELLED");
        assertThat(run.getEndTime()).isNotNull();
    }

    @Test
    void updateDagRunStatusShouldKeepRunRunningWhenPendingStillExists() {
        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(103L);
        run.setStatus("RUNNING");

        SchedulingTaskInstance pending = new SchedulingTaskInstance();
        pending.setStatus("PENDING");
        when(taskInstanceRepository.findByDagRunId(103L)).thenReturn(List.of(pending));
        when(dependencyCheckerService.hasAnyUpstreamInTerminalFailOrSkipped(eq(pending), any())).thenReturn(false);

        dagRunMonitorService.updateDagRunStatus(run);

        assertThat(run.getStatus()).isEqualTo("RUNNING");
        verify(dagRunRepository, never()).save(any());
    }

    @Test
    void monitorDagRunsShouldProcessRunningPagesUntilExhausted() {
        SchedulingDagRun runA = new SchedulingDagRun();
        runA.setId(201L);
        runA.setStatus("RUNNING");
        SchedulingDagRun runB = new SchedulingDagRun();
        runB.setId(202L);
        runB.setStatus("RUNNING");
        when(dagRunRepository.findByStatus(eq("RUNNING"), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(runA, runB)));
        when(taskInstanceRepository.findByDagRunId(201L)).thenReturn(List.of(successInstance()));
        when(taskInstanceRepository.findByDagRunId(202L)).thenReturn(List.of(successInstance()));

        dagRunMonitorService.monitorDagRuns();

        verify(taskInstanceRepository).findByDagRunId(201L);
        verify(taskInstanceRepository).findByDagRunId(202L);
        verify(dagRunRepository, times(2)).save(any(SchedulingDagRun.class));
    }

    private SchedulingTaskInstance successInstance() {
        SchedulingTaskInstance instance = new SchedulingTaskInstance();
        instance.setStatus("SUCCESS");
        return instance;
    }
}
