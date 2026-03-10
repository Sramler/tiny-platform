package com.tiny.platform.infrastructure.scheduling.service;

import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagEdge;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagEdgeRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DependencyCheckerServiceTest {

    private SchedulingTaskInstanceRepository taskInstanceRepository;
    private SchedulingDagEdgeRepository dagEdgeRepository;
    private DependencyCheckerService dependencyCheckerService;

    @BeforeEach
    void setUp() {
        taskInstanceRepository = mock(SchedulingTaskInstanceRepository.class);
        dagEdgeRepository = mock(SchedulingDagEdgeRepository.class);
        dependencyCheckerService = new DependencyCheckerService(taskInstanceRepository, dagEdgeRepository);
    }

    @Test
    void checkDependenciesShouldWaitForAllParallelUpstreamsBeforeMerge() {
        SchedulingTaskInstance mergeInstance = new SchedulingTaskInstance();
        mergeInstance.setId(99L);
        mergeInstance.setDagRunId(77L);
        mergeInstance.setDagVersionId(3L);
        mergeInstance.setNodeCode("merge-report");

        SchedulingDagEdge edgeA = new SchedulingDagEdge();
        edgeA.setDagVersionId(3L);
        edgeA.setFromNodeCode("count-orders");
        edgeA.setToNodeCode("merge-report");
        SchedulingDagEdge edgeB = new SchedulingDagEdge();
        edgeB.setDagVersionId(3L);
        edgeB.setFromNodeCode("count-users");
        edgeB.setToNodeCode("merge-report");
        when(dagEdgeRepository.findByDagVersionIdAndToNodeCode(3L, "merge-report"))
                .thenReturn(List.of(edgeA, edgeB));
        when(taskInstanceRepository.countDistinctNodeCodesByDagRunIdAndNodeCodeInAndStatus(
                77L, List.of("count-orders", "count-users"), "SUCCESS"))
                .thenReturn(1L);

        boolean ready = dependencyCheckerService.checkDependencies(mergeInstance);

        assertThat(ready).isFalse();
    }

    @Test
    void checkDependenciesShouldAllowSerialSuccessorWhenSingleUpstreamSucceeds() {
        SchedulingTaskInstance childInstance = new SchedulingTaskInstance();
        childInstance.setId(100L);
        childInstance.setDagRunId(88L);
        childInstance.setDagVersionId(4L);
        childInstance.setNodeCode("stage-2");

        SchedulingDagEdge edge = new SchedulingDagEdge();
        edge.setDagVersionId(4L);
        edge.setFromNodeCode("stage-1");
        edge.setToNodeCode("stage-2");
        when(dagEdgeRepository.findByDagVersionIdAndToNodeCode(4L, "stage-2"))
                .thenReturn(List.of(edge));
        when(taskInstanceRepository.countDistinctNodeCodesByDagRunIdAndNodeCodeInAndStatus(
                88L, List.of("stage-1"), "SUCCESS"))
                .thenReturn(1L);

        boolean ready = dependencyCheckerService.checkDependencies(childInstance);

        assertThat(ready).isTrue();
    }

    @Test
    void hasAnyUpstreamInTerminalFailOrSkippedShouldIgnoreOlderFailedAttemptWhenLatestRetryPending() {
        SchedulingTaskInstance mergeInstance = new SchedulingTaskInstance();
        mergeInstance.setDagVersionId(5L);
        mergeInstance.setNodeCode("merge");

        SchedulingDagEdge edge = new SchedulingDagEdge();
        edge.setDagVersionId(5L);
        edge.setFromNodeCode("count-orders");
        edge.setToNodeCode("merge");
        when(dagEdgeRepository.findByDagVersionIdAndToNodeCode(5L, "merge"))
                .thenReturn(List.of(edge));

        SchedulingTaskInstance olderFailed = new SchedulingTaskInstance();
        olderFailed.setId(10L);
        olderFailed.setNodeCode("count-orders");
        olderFailed.setStatus("FAILED");

        SchedulingTaskInstance latestRetryPending = new SchedulingTaskInstance();
        latestRetryPending.setId(11L);
        latestRetryPending.setNodeCode("count-orders");
        latestRetryPending.setStatus("PENDING");

        boolean unreachable = dependencyCheckerService.hasAnyUpstreamInTerminalFailOrSkipped(
                mergeInstance,
                List.of(olderFailed, latestRetryPending));

        assertThat(unreachable).isFalse();
    }
}
