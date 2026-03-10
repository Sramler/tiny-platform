package com.tiny.platform.infrastructure.scheduling.controller;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagEdgeCreateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagStatsDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagTaskCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagVersionCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingTaskCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingTaskTypeCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingAudit;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDag;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagEdge;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagRun;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagTask;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagVersion;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTask;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskHistory;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskType;
import com.tiny.platform.infrastructure.scheduling.service.QuartzSchedulerService;
import com.tiny.platform.infrastructure.scheduling.service.SchedulingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 调度控制器单元测试（参照 ExportControllerTest 结构）。
 * 验证 HTTP 层委托给 Service、返回 200/404 等。
 */
class SchedulingControllerTest {

    private SchedulingService schedulingService;
    private QuartzSchedulerService quartzSchedulerService;
    private SchedulingController controller;

    @BeforeEach
    void setUp() {
        schedulingService = mock(SchedulingService.class);
        quartzSchedulerService = mock(QuartzSchedulerService.class);
        controller = new SchedulingController(schedulingService, quartzSchedulerService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void authenticate(Long userId, Long tenantId, String username) {
        TenantContext.setTenantId(tenantId);
        SecurityUser user = new SecurityUser(userId, tenantId, username, "", List.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, "N/A", List.of()));
    }

    @Nested
    class TaskType {

        @Test
        void getTaskTypeShouldReturn200WhenExists() {
            authenticate(1L, 10L, "alice");
            SchedulingTaskType taskType = new SchedulingTaskType();
            taskType.setId(1L);
            taskType.setTenantId(10L);
            taskType.setCode("demo");
            when(schedulingService.getTaskType(1L)).thenReturn(Optional.of(taskType));

            ResponseEntity<SchedulingTaskType> response = controller.getTaskType(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(1L);
            verify(schedulingService).getTaskType(1L);
        }

        @Test
        void getTaskTypeShouldReturn404WhenNotExists() {
            authenticate(1L, 10L, "alice");
            when(schedulingService.getTaskType(999L)).thenReturn(Optional.empty());

            ResponseEntity<SchedulingTaskType> response = controller.getTaskType(999L);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody()).isNull();
        }

        @Test
        void deleteTaskTypeShouldCallServiceAndReturn200() {
            authenticate(1L, 10L, "alice");
            controller.deleteTaskType(1L);

            verify(schedulingService).deleteTaskType(1L);
        }

        @Test
        void listTaskTypesShouldDelegateToServiceAndReturnPage() {
            authenticate(1L, 10L, "alice");
            SchedulingTaskType tt = new SchedulingTaskType();
            tt.setId(1L);
            when(schedulingService.listTaskTypes(eq(null), eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tt)));

            ResponseEntity<?> response = controller.listTaskTypes(null, null, PageRequest.of(0, 10));

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
        }
    }

    @Nested
    class Task {

        @Test
        void getTaskShouldReturn200WhenExists() {
            authenticate(1L, 10L, "alice");
            com.tiny.platform.infrastructure.scheduling.model.SchedulingTask task =
                new com.tiny.platform.infrastructure.scheduling.model.SchedulingTask();
            task.setId(1L);
            task.setTenantId(10L);
            when(schedulingService.getTask(1L)).thenReturn(Optional.of(task));

            ResponseEntity<com.tiny.platform.infrastructure.scheduling.model.SchedulingTask> response =
                controller.getTask(1L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(1L);
        }

        @Test
        void getTaskShouldReturn404WhenNotExists() {
            authenticate(1L, 10L, "alice");
            when(schedulingService.getTask(999L)).thenReturn(Optional.empty());

            ResponseEntity<com.tiny.platform.infrastructure.scheduling.model.SchedulingTask> response =
                controller.getTask(999L);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody()).isNull();
        }

        @Test
        void createTaskTypeShouldDelegateAndReturn200() {
            authenticate(1L, 10L, "alice");
            SchedulingTaskTypeCreateUpdateDto dto = new SchedulingTaskTypeCreateUpdateDto();
            dto.setCode("billing");
            dto.setName("Billing");
            SchedulingTaskType saved = new SchedulingTaskType();
            saved.setId(1L);
            saved.setTenantId(10L);
            when(schedulingService.createTaskType(dto)).thenReturn(saved);

            ResponseEntity<SchedulingTaskType> response = controller.createTaskType(dto);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().getId()).isEqualTo(1L);
            verify(schedulingService).createTaskType(dto);
        }
    }

    @Nested
    class DagAndTrigger {

        @Test
        void getDagShouldReturn404WhenNotExists() {
            authenticate(1L, 10L, "alice");
            when(schedulingService.getDag(999L)).thenReturn(Optional.empty());

            ResponseEntity<SchedulingDag> response = controller.getDag(999L);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
        }

        @Test
        void triggerDagShouldDelegateAndReturn200() throws Exception {
            authenticate(1L, 10L, "alice");
            SchedulingDag dag = new SchedulingDag();
            dag.setId(10L);
            dag.setTenantId(10L);
            SchedulingDagRun run = new SchedulingDagRun();
            run.setId(1L);
            when(schedulingService.triggerDag(10L)).thenReturn(run);

            ResponseEntity<SchedulingDagRun> response = controller.triggerDag(10L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
            verify(schedulingService).triggerDag(10L);
        }

        @Test
        void retryDagRunShouldDelegateAndReturn200() {
            authenticate(1L, 10L, "alice");

            ResponseEntity<Void> response = controller.retryDagRun(10L, 77L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(schedulingService).retryDagRun(10L, 77L);
        }

        @Test
        void stopDagRunShouldDelegateAndReturn200() {
            authenticate(1L, 10L, "alice");

            ResponseEntity<Void> response = controller.stopDagRun(10L, 77L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(schedulingService).stopDagRun(10L, 77L);
        }

        @Test
        void triggerNodeInRunShouldDelegateAndReturn200() {
            authenticate(1L, 10L, "alice");

            ResponseEntity<Void> response = controller.triggerNodeInRun(10L, 77L, 11L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(schedulingService).triggerNode(10L, 77L, 11L);
        }

        @Test
        void retryNodeInRunShouldDelegateAndReturn200() {
            authenticate(1L, 10L, "alice");

            ResponseEntity<Void> response = controller.retryNodeInRun(10L, 77L, 11L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(schedulingService).retryNode(10L, 77L, 11L);
        }

        @Test
        void pauseNodeInRunShouldDelegateAndReturn200() {
            authenticate(1L, 10L, "alice");

            ResponseEntity<Void> response = controller.pauseNodeInRun(10L, 77L, 11L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(schedulingService).pauseNode(10L, 77L, 11L);
        }

        @Test
        void resumeNodeInRunShouldDelegateAndReturn200() {
            authenticate(1L, 10L, "alice");

            ResponseEntity<Void> response = controller.resumeNodeInRun(10L, 77L, 11L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(schedulingService).resumeNode(10L, 77L, 11L);
        }

        @Test
        void pauseResumeStopRetryDagAndNodeShouldDelegate() {
            authenticate(1L, 10L, "alice");

            assertThat(controller.pauseDag(10L).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.resumeDag(10L).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.stopDag(10L).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.retryDag(10L).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.triggerNode(10L, 11L).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.retryNode(10L, 11L).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.pauseNode(10L, 11L).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.resumeNode(10L, 11L).getStatusCode().value()).isEqualTo(200);

            verify(schedulingService).pauseDag(10L);
            verify(schedulingService).resumeDag(10L);
            verify(schedulingService).stopDag(10L);
            verify(schedulingService).retryDag(10L);
            verify(schedulingService).triggerNode(10L, 11L);
            verify(schedulingService).retryNode(10L, 11L);
            verify(schedulingService).pauseNode(10L, 11L);
            verify(schedulingService).resumeNode(10L, 11L);
        }
    }

    @Nested
    class Executors {

        @Test
        void listExecutorsShouldReturn200AndDelegateToService() throws Exception {
            when(schedulingService.listExecutors()).thenReturn(List.of("logging", "shell"));

            ResponseEntity<List<String>> response = controller.listExecutors();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsExactly("logging", "shell");
            verify(schedulingService).listExecutors();
            verifyNoInteractions(quartzSchedulerService);
        }
    }

    @Nested
    class TaskLog {

        @Test
        void getTaskInstanceLogShouldReturn404WhenNotExists() {
            authenticate(1L, 10L, "alice");
            when(schedulingService.getTaskInstanceLog(999L)).thenReturn(Optional.empty());

            ResponseEntity<String> response = controller.getTaskInstanceLog(999L);

            assertThat(response.getStatusCode().value()).isEqualTo(404);
            assertThat(response.getBody()).isNull();
        }

        @Test
        void getTaskInstanceLogShouldReturn200WhenExists() {
            authenticate(1L, 10L, "alice");
            when(schedulingService.getTaskInstanceLog(100L)).thenReturn(Optional.of("执行成功"));

            ResponseEntity<String> response = controller.getTaskInstanceLog(100L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo("执行成功");
        }
    }

    @Nested
    class VersionsNodesAndEdges {

        @Test
        void createDagVersionShouldSetDagIdAndDelegate() {
            authenticate(1L, 10L, "alice");
            SchedulingDagVersionCreateUpdateDto dto = new SchedulingDagVersionCreateUpdateDto();
            SchedulingDagVersion version = new SchedulingDagVersion();
            version.setId(5L);
            when(schedulingService.createDagVersion(10L, dto)).thenReturn(version);

            ResponseEntity<SchedulingDagVersion> response = controller.createDagVersion(10L, dto);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(dto.getDagId()).isEqualTo(10L);
            verify(schedulingService).createDagVersion(10L, dto);
        }

        @Test
        void getDagVersionShouldReturn200Or404() {
            authenticate(1L, 10L, "alice");
            SchedulingDagVersion version = new SchedulingDagVersion();
            version.setId(5L);
            when(schedulingService.getDagVersion(10L, 5L)).thenReturn(Optional.of(version));
            when(schedulingService.getDagVersion(10L, 9L)).thenReturn(Optional.empty());

            assertThat(controller.getDagVersion(10L, 5L).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.getDagVersion(10L, 9L).getStatusCode().value()).isEqualTo(404);
        }

        @Test
        void listDagVersionsShouldReturnData() {
            authenticate(1L, 10L, "alice");
            SchedulingDagVersion version = new SchedulingDagVersion();
            version.setId(5L);
            when(schedulingService.listDagVersions(10L)).thenReturn(List.of(version));

            ResponseEntity<List<SchedulingDagVersion>> response = controller.listDagVersions(10L);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).hasSize(1);
        }

        @Test
        void createDagNodeShouldSetVersionIdAndDelegate() {
            authenticate(1L, 10L, "alice");
            SchedulingDagTaskCreateUpdateDto dto = new SchedulingDagTaskCreateUpdateDto();
            SchedulingDagTask node = new SchedulingDagTask();
            node.setId(11L);
            when(schedulingService.createDagNode(10L, 3L, dto)).thenReturn(node);

            ResponseEntity<SchedulingDagTask> response = controller.createDagNode(10L, 3L, dto);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(dto.getDagVersionId()).isEqualTo(3L);
            verify(schedulingService).createDagNode(10L, 3L, dto);
        }

        @Test
        void getDagNodeShouldReturn200Or404() {
            authenticate(1L, 10L, "alice");
            SchedulingDagTask node = new SchedulingDagTask();
            node.setId(11L);
            when(schedulingService.getDagNode(10L, 3L, 11L)).thenReturn(Optional.of(node));
            when(schedulingService.getDagNode(10L, 3L, 12L)).thenReturn(Optional.empty());

            assertThat(controller.getDagNode(10L, 3L, 11L).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.getDagNode(10L, 3L, 12L).getStatusCode().value()).isEqualTo(404);
        }

        @Test
        void listNodeRelationsShouldDelegate() {
            authenticate(1L, 10L, "alice");
            SchedulingDagTask node = new SchedulingDagTask();
            node.setId(11L);
            when(schedulingService.getDagNodes(10L, 3L)).thenReturn(List.of(node));
            when(schedulingService.getUpstreamNodes(10L, 3L, 11L)).thenReturn(List.of(node));
            when(schedulingService.getDownstreamNodes(10L, 3L, 11L)).thenReturn(List.of(node));

            assertThat(controller.getDagNodes(10L, 3L).getBody()).hasSize(1);
            assertThat(controller.getUpstreamNodes(10L, 3L, 11L).getBody()).hasSize(1);
            assertThat(controller.getDownstreamNodes(10L, 3L, 11L).getBody()).hasSize(1);
        }

        @Test
        void createAndDeleteDagEdgeShouldDelegate() {
            authenticate(1L, 10L, "alice");
            SchedulingDagEdgeCreateDto dto = new SchedulingDagEdgeCreateDto();
            SchedulingDagEdge edge = new SchedulingDagEdge();
            edge.setId(21L);
            when(schedulingService.createDagEdge(10L, 3L, dto)).thenReturn(edge);
            when(schedulingService.getDagEdges(10L, 3L)).thenReturn(List.of(edge));

            ResponseEntity<SchedulingDagEdge> createResponse = controller.createDagEdge(10L, 3L, dto);
            ResponseEntity<Void> deleteResponse = controller.deleteDagEdge(10L, 3L, 21L);

            assertThat(createResponse.getStatusCode().value()).isEqualTo(200);
            assertThat(dto.getDagVersionId()).isEqualTo(3L);
            assertThat(deleteResponse.getStatusCode().value()).isEqualTo(200);
            assertThat(controller.getDagEdges(10L, 3L).getBody()).hasSize(1);
            verify(schedulingService).deleteDagEdge(10L, 3L, 21L);
        }
    }

    @Nested
    class HistoryAndAudit {

        @Test
        void getDagRunsShouldParseDateFilters() {
            authenticate(1L, 10L, "alice");
            when(schedulingService.getDagRuns(eq(10L), any(Pageable.class), eq("FAILED"), eq("MANUAL"), eq("run-1"), any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            ResponseEntity<?> response = controller.getDagRuns(
                    10L,
                    PageRequest.of(0, 10),
                    "FAILED",
                    "MANUAL",
                    "run-1",
                    "2026-03-01",
                    "2026-03-02");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            verify(schedulingService).getDagRuns(
                    eq(10L),
                    any(Pageable.class),
                    eq("FAILED"),
                    eq("MANUAL"),
                    eq("run-1"),
                    eq(LocalDateTime.of(2026, 3, 1, 0, 0)),
                    eq(LocalDateTime.of(2026, 3, 2, 23, 59, 59, 999_999_999)));
        }

        @Test
        void getDagRunsShouldPassNullForInvalidDateFilters() {
            authenticate(1L, 10L, "alice");
            when(schedulingService.getDagRuns(eq(10L), any(Pageable.class), eq(null), eq(null), eq(null), eq(null), eq(null)))
                    .thenReturn(new PageImpl<>(List.of()));

            controller.getDagRuns(10L, PageRequest.of(0, 10), null, null, null, "bad-date", "bad-date");

            verify(schedulingService).getDagRuns(eq(10L), any(Pageable.class), eq(null), eq(null), eq(null), eq(null), eq(null));
        }

        @Test
        void getDagRunRunNodeHistoryAndAuditShouldDelegate() {
            authenticate(1L, 10L, "alice");
            SchedulingDagRun run = new SchedulingDagRun();
            run.setId(77L);
            SchedulingTaskInstance instance = new SchedulingTaskInstance();
            instance.setId(88L);
            SchedulingTaskHistory history = new SchedulingTaskHistory();
            history.setId(99L);
            SchedulingAudit audit = new SchedulingAudit();
            audit.setId(100L);
            SchedulingDagStatsDto statsDto = new SchedulingDagStatsDto();
            statsDto.setTotal(3);

            when(schedulingService.getDagRun(10L, 77L)).thenReturn(Optional.of(run));
            when(schedulingService.getDagRun(10L, 78L)).thenReturn(Optional.empty());
            when(schedulingService.getDagRunNodes(10L, 77L)).thenReturn(List.of(instance));
            when(schedulingService.getDagRunNode(10L, 77L, 88L)).thenReturn(Optional.of(instance));
            when(schedulingService.getDagRunNode(10L, 77L, 89L)).thenReturn(Optional.empty());
            when(schedulingService.getTaskHistory(99L)).thenReturn(Optional.of(history));
            when(schedulingService.getTaskHistory(100L)).thenReturn(Optional.empty());
            when(schedulingService.listAudits(eq("dag"), eq("TRIGGER"), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(audit)));
            when(schedulingService.getDagStats(10L)).thenReturn(statsDto);

            assertThat(controller.getDagRun(10L, 77L).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.getDagRun(10L, 78L).getStatusCode().value()).isEqualTo(404);
            assertThat(controller.getDagRunNodes(10L, 77L).getBody()).hasSize(1);
            assertThat(controller.getDagRunNode(10L, 77L, 88L).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.getDagRunNode(10L, 77L, 89L).getStatusCode().value()).isEqualTo(404);
            assertThat(controller.getTaskHistory(99L).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.getTaskHistory(100L).getStatusCode().value()).isEqualTo(404);
            assertThat(controller.listAudits("dag", "TRIGGER", PageRequest.of(0, 10)).getStatusCode().value()).isEqualTo(200);
            assertThat(controller.getDagStats(10L).getBody().getTotal()).isEqualTo(3);
        }

        @Test
        void getTaskParamShouldReturnTaskParamsOrDefaultOr404() {
            authenticate(1L, 10L, "alice");
            SchedulingTask withParams = new SchedulingTask();
            withParams.setId(1L);
            withParams.setParams("{\"foo\":\"bar\"}");
            SchedulingTask emptyParams = new SchedulingTask();
            emptyParams.setId(2L);
            emptyParams.setParams(null);
            when(schedulingService.getTask(1L)).thenReturn(Optional.of(withParams));
            when(schedulingService.getTask(2L)).thenReturn(Optional.of(emptyParams));
            when(schedulingService.getTask(3L)).thenReturn(Optional.empty());

            assertThat(controller.getTaskParam(1L).getBody()).isEqualTo("{\"foo\":\"bar\"}");
            assertThat(controller.getTaskParam(2L).getBody()).isEqualTo("{}");
            assertThat(controller.getTaskParam(3L).getStatusCode().value()).isEqualTo(404);
        }
    }

    @Nested
    class ClusterStatus {

        @Test
        void getQuartzClusterStatusShouldMapFields() {
            QuartzSchedulerService.ClusterStatusInfo statusInfo =
                    new QuartzSchedulerService.ClusterStatusInfo("main", "node-1", true, 12, 123456L, true, false);
            when(quartzSchedulerService.getClusterStatus()).thenReturn(statusInfo);

            ResponseEntity<Map<String, Object>> response = controller.getQuartzClusterStatus();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody())
                    .containsEntry("schedulerName", "main")
                    .containsEntry("schedulerInstanceId", "node-1")
                    .containsEntry("isClustered", true)
                    .containsEntry("numberOfJobsExecuted", 12)
                    .containsEntry("schedulerStarted", 123456L)
                    .containsEntry("clusterMode", "集群模式")
                    .containsEntry("status", "运行中");
        }
    }
}
