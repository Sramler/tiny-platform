package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagEdgeCreateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagTaskCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagVersionCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagStatsDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingTaskCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingTaskTypeCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.exception.SchedulingException;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingAudit;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTask;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskExecutionSnapshot;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDag;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagEdge;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagTask;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagRun;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingDagVersion;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskType;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingAuditRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagEdgeRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagRunRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagTaskRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingDagVersionRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskHistoryRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskInstanceRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskRepository;
import com.tiny.platform.infrastructure.scheduling.repository.SchedulingTaskTypeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulingServiceTenantScopeTest {

    private SchedulingTaskTypeRepository taskTypeRepository;
    private SchedulingTaskRepository taskRepository;
    private SchedulingDagRepository dagRepository;
    private SchedulingDagVersionRepository dagVersionRepository;
    private SchedulingDagTaskRepository dagTaskRepository;
    private SchedulingDagEdgeRepository dagEdgeRepository;
    private SchedulingDagRunRepository dagRunRepository;
    private SchedulingTaskInstanceRepository taskInstanceRepository;
    private SchedulingTaskHistoryRepository taskHistoryRepository;
    private SchedulingAuditRepository auditRepository;
    private QuartzSchedulerService quartzSchedulerService;
    private TaskExecutorRegistry taskExecutorRegistry;
    private SchedulingService schedulingService;

    @BeforeEach
    void setUp() {
        taskTypeRepository = mock(SchedulingTaskTypeRepository.class);
        taskRepository = mock(SchedulingTaskRepository.class);
        dagRepository = mock(SchedulingDagRepository.class);
        dagVersionRepository = mock(SchedulingDagVersionRepository.class);
        dagTaskRepository = mock(SchedulingDagTaskRepository.class);
        dagEdgeRepository = mock(SchedulingDagEdgeRepository.class);
        dagRunRepository = mock(SchedulingDagRunRepository.class);
        taskInstanceRepository = mock(SchedulingTaskInstanceRepository.class);
        taskHistoryRepository = mock(SchedulingTaskHistoryRepository.class);
        auditRepository = mock(SchedulingAuditRepository.class);
        quartzSchedulerService = mock(QuartzSchedulerService.class);
        taskExecutorRegistry = mock(TaskExecutorRegistry.class);
        schedulingService = new SchedulingService(
                taskTypeRepository,
                taskRepository,
                dagRepository,
                dagVersionRepository,
                dagTaskRepository,
                dagEdgeRepository,
                dagRunRepository,
                taskInstanceRepository,
                taskHistoryRepository,
                auditRepository,
                quartzSchedulerService,
                taskExecutorRegistry,
                new JsonSchemaValidationService(new ObjectMapper()),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void createTaskTypeShouldUseCurrentTenantAndActor() {
        authenticate(8L, 88L, "alice");
        when(taskTypeRepository.findByTenantIdAndCode(88L, "billing")).thenReturn(Optional.empty());
        when(taskExecutorRegistry.find("loggingTaskExecutor")).thenReturn(Optional.of(mock(TaskExecutorService.TaskExecutor.class)));
        when(taskTypeRepository.save(any(SchedulingTaskType.class))).thenAnswer(invocation -> {
            SchedulingTaskType entity = invocation.getArgument(0);
            entity.setId(11L);
            return entity;
        });

        SchedulingTaskTypeCreateUpdateDto dto = new SchedulingTaskTypeCreateUpdateDto();
        dto.setTenantId(999L);
        dto.setCode("billing");
        dto.setName("Billing");
        dto.setExecutor("loggingTaskExecutor");
        dto.setCreatedBy("mallory");

        SchedulingTaskType saved = schedulingService.createTaskType(dto);

        assertThat(saved.getTenantId()).isEqualTo(88L);
        assertThat(saved.getCreatedBy()).isEqualTo("alice");

        ArgumentCaptor<SchedulingAudit> auditCaptor = ArgumentCaptor.forClass(SchedulingAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getTenantId()).isEqualTo(88L);
        assertThat(auditCaptor.getValue().getPerformedBy()).isEqualTo("alice");
    }

    @Test
    void getTaskTypeShouldUseTenantScopedLookup() {
        authenticate(8L, 88L, "alice");
        SchedulingTaskType taskType = new SchedulingTaskType();
        taskType.setId(1L);
        taskType.setTenantId(88L);
        taskType.setCode("demo");
        when(taskTypeRepository.findByIdAndTenantId(1L, 88L)).thenReturn(Optional.of(taskType));

        Optional<SchedulingTaskType> found = schedulingService.getTaskType(1L);

        assertThat(found).contains(taskType);
        verify(taskTypeRepository).findByIdAndTenantId(1L, 88L);
        verify(taskTypeRepository, never()).findById(1L);
    }

    @Test
    void getDagShouldPopulateCurrentVersionIdFromActiveVersion() {
        authenticate(8L, 88L, "alice");
        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagVersion activeVersion = new SchedulingDagVersion();
        activeVersion.setId(101L);
        activeVersion.setDagId(10L);
        activeVersion.setStatus("ACTIVE");
        when(dagVersionRepository.findByDagIdAndStatus(10L, "ACTIVE")).thenReturn(Optional.of(activeVersion));
        SchedulingDagRun runningRun = new SchedulingDagRun();
        runningRun.setDagId(10L);
        runningRun.setStatus("RUNNING");
        when(dagRunRepository.findByDagIdInAndStatusInOrderByIdDesc(List.of(10L), List.of("RUNNING", "FAILED", "PARTIAL_FAILED")))
                .thenReturn(List.of(runningRun));

        Optional<SchedulingDag> found = schedulingService.getDag(10L);

        assertThat(found).isPresent();
        assertThat(found.get().getCurrentVersionId()).isEqualTo(101L);
        assertThat(found.get().getHasRunningRun()).isTrue();
        assertThat(found.get().getHasRetryableRun()).isFalse();
    }

    @Test
    void listDagsShouldPopulateCurrentVersionIdForEachDag() {
        authenticate(8L, 88L, "alice");
        SchedulingDag dagOne = new SchedulingDag();
        dagOne.setId(10L);
        dagOne.setTenantId(88L);
        SchedulingDag dagTwo = new SchedulingDag();
        dagTwo.setId(20L);
        dagTwo.setTenantId(88L);
        when(dagRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dagOne, dagTwo), PageRequest.of(0, 10), 2));

        SchedulingDagVersion activeVersion = new SchedulingDagVersion();
        activeVersion.setId(201L);
        activeVersion.setDagId(20L);
        activeVersion.setStatus("ACTIVE");
        when(dagVersionRepository.findByDagIdInAndStatus(List.of(10L, 20L), "ACTIVE"))
                .thenReturn(List.of(activeVersion));
        SchedulingDagRun runningRun = new SchedulingDagRun();
        runningRun.setDagId(10L);
        runningRun.setStatus("RUNNING");
        SchedulingDagRun retryableRun = new SchedulingDagRun();
        retryableRun.setDagId(20L);
        retryableRun.setStatus("FAILED");
        when(dagRunRepository.findByDagIdInAndStatusInOrderByIdDesc(List.of(10L, 20L), List.of("RUNNING", "FAILED", "PARTIAL_FAILED")))
                .thenReturn(List.of(retryableRun, runningRun));

        List<SchedulingDag> dags = schedulingService.listDags(null, null, PageRequest.of(0, 10)).getContent();

        assertThat(dags).hasSize(2);
        assertThat(dags.get(0).getCurrentVersionId()).isNull();
        assertThat(dags.get(0).getHasRunningRun()).isTrue();
        assertThat(dags.get(0).getHasRetryableRun()).isFalse();
        assertThat(dags.get(1).getCurrentVersionId()).isEqualTo(201L);
        assertThat(dags.get(1).getHasRunningRun()).isFalse();
        assertThat(dags.get(1).getHasRetryableRun()).isTrue();
    }

    @Test
    void getDagStatsShouldMapAggregationAndPercentiles() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));
        when(dagRunRepository.getDagRunStatsAggregation(10L))
                .thenReturn(new Object[]{5L, 3L, 2L, 4L, 1250.5D});
        when(dagRunRepository.getDurationMsAtOffset(10L, 3)).thenReturn(2100D);

        SchedulingDagStatsDto stats = schedulingService.getDagStats(10L);

        assertThat(stats.getTotal()).isEqualTo(5L);
        assertThat(stats.getSuccess()).isEqualTo(3L);
        assertThat(stats.getFailed()).isEqualTo(2L);
        assertThat(stats.getAvgDurationMs()).isEqualTo(1250L);
        assertThat(stats.getP95DurationMs()).isEqualTo(2100L);
        assertThat(stats.getP99DurationMs()).isEqualTo(2100L);
    }

    @Test
    void createDagNodeShouldRejectCrossTenantTaskReference() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));
        when(dagTaskRepository.findByDagVersionIdAndNodeCode(any(), any())).thenReturn(Optional.empty());
        when(taskRepository.findByIdAndTenantId(501L, 88L)).thenReturn(Optional.empty());

        SchedulingDagTaskCreateUpdateDto dto = new SchedulingDagTaskCreateUpdateDto();
        dto.setNodeCode("node-a");
        dto.setTaskId(501L);

        assertThatThrownBy(() -> schedulingService.createDagNode(10L, 100L, dto))
                .isInstanceOf(SchedulingException.class)
                .hasMessage("任务不存在: 501");
    }

    @Test
    void updateDagNodeShouldRewriteEdgeReferencesWhenNodeCodeChanges() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        version.setStatus("DRAFT");
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));

        SchedulingDagTask node = new SchedulingDagTask();
        node.setId(11L);
        node.setDagVersionId(100L);
        node.setNodeCode("node-a");
        node.setTaskId(501L);
        when(dagTaskRepository.findById(11L)).thenReturn(Optional.of(node));
        when(dagTaskRepository.findByDagVersionIdAndNodeCode(100L, "node-b")).thenReturn(Optional.empty());
        when(dagTaskRepository.save(any(SchedulingDagTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SchedulingDagTaskCreateUpdateDto dto = new SchedulingDagTaskCreateUpdateDto();
        dto.setNodeCode("node-b");

        SchedulingDagTask saved = schedulingService.updateDagNode(10L, 100L, 11L, dto);

        assertThat(saved.getNodeCode()).isEqualTo("node-b");
        verify(dagEdgeRepository).updateFromNodeCode(100L, "node-a", "node-b");
        verify(dagEdgeRepository).updateToNodeCode(100L, "node-a", "node-b");
    }

    @Test
    void createTaskTypeShouldRejectUnknownExecutor() {
        authenticate(8L, 88L, "alice");
        when(taskTypeRepository.findByTenantIdAndCode(88L, "billing")).thenReturn(Optional.empty());
        when(taskExecutorRegistry.find("missingExecutor")).thenReturn(Optional.empty());

        SchedulingTaskTypeCreateUpdateDto dto = new SchedulingTaskTypeCreateUpdateDto();
        dto.setCode("billing");
        dto.setName("Billing");
        dto.setExecutor("missingExecutor");

        assertThatThrownBy(() -> schedulingService.createTaskType(dto))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("执行器不存在");
    }

    @Test
    void createTaskTypeShouldRejectInvalidSchema() {
        authenticate(8L, 88L, "alice");
        when(taskTypeRepository.findByTenantIdAndCode(88L, "billing")).thenReturn(Optional.empty());
        when(taskExecutorRegistry.find("loggingTaskExecutor")).thenReturn(Optional.of(mock(TaskExecutorService.TaskExecutor.class)));

        SchedulingTaskTypeCreateUpdateDto dto = new SchedulingTaskTypeCreateUpdateDto();
        dto.setCode("billing");
        dto.setName("Billing");
        dto.setExecutor("loggingTaskExecutor");
        dto.setParamSchema("{invalid-json");

        assertThatThrownBy(() -> schedulingService.createTaskType(dto))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("JSON Schema");
    }

    @Test
    void triggerDagShouldIgnoreRequestedTriggeredByAndUseCurrentActor() throws Exception {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        dag.setEnabled(true);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(3L);
        version.setDagId(10L);
        when(dagVersionRepository.findByDagIdAndStatus(10L, "ACTIVE")).thenReturn(Optional.of(version));
        when(dagRunRepository.save(any(SchedulingDagRun.class))).thenAnswer(invocation -> {
            SchedulingDagRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(77L);
            }
            return run;
        });

        TransactionSynchronizationManager.initSynchronization();
        SchedulingDagRun run = schedulingService.triggerDag(10L);
        verify(quartzSchedulerService, never()).triggerDagNow(eq(dag), any(SchedulingExecutionContext.class));
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        TransactionSynchronizationManager.clearSynchronization();

        assertThat(run.getTriggeredBy()).isEqualTo("alice");
        ArgumentCaptor<SchedulingExecutionContext> executionContextCaptor =
                ArgumentCaptor.forClass(SchedulingExecutionContext.class);
        verify(quartzSchedulerService).triggerDagNow(eq(dag), executionContextCaptor.capture());
        SchedulingExecutionContext executionContext = executionContextCaptor.getValue();
        assertThat(executionContext.getTenantId()).isEqualTo(88L);
        assertThat(executionContext.getUserId()).isEqualTo("8");
        assertThat(executionContext.getUsername()).isEqualTo("alice");
        assertThat(executionContext.getDagId()).isEqualTo(10L);
        assertThat(executionContext.getDagRunId()).isEqualTo(77L);
        assertThat(executionContext.getDagVersionId()).isEqualTo(3L);
        assertThat(executionContext.getTriggerType()).isEqualTo("MANUAL");

        ArgumentCaptor<SchedulingAudit> auditCaptor = ArgumentCaptor.forClass(SchedulingAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getPerformedBy()).isEqualTo("alice");
        assertThat(auditCaptor.getValue().getDetail()).contains("alice");
    }

    @Test
    void createDagShouldRejectCronSchedulingWhenNoActiveVersion() throws Exception {
        authenticate(8L, 88L, "alice");
        when(dagRepository.findByTenantIdAndCode(88L, "dag-a")).thenReturn(Optional.empty());

        SchedulingDagCreateUpdateDto dto = new SchedulingDagCreateUpdateDto();
        dto.setCode("dag-a");
        dto.setName("Dag A");
        dto.setCronEnabled(true);
        dto.setCronExpression("0 0 12 * * ?");
        dto.setCronTimezone("Asia/Shanghai");

        assertThatThrownBy(() -> schedulingService.createDag(dto))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("ACTIVE版本");
        verify(dagRepository, never()).save(any(SchedulingDag.class));
        verify(quartzSchedulerService, never()).createOrUpdateDagJob(any(SchedulingDag.class), any(), any());
    }

    @Test
    void updateDagShouldRejectCronSchedulingWhenNoActiveVersion() throws Exception {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        dag.setEnabled(true);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagCreateUpdateDto dto = new SchedulingDagCreateUpdateDto();
        dto.setCronEnabled(true);
        dto.setCronExpression("0 0 12 * * ?");
        dto.setCronTimezone("Asia/Shanghai");

        assertThatThrownBy(() -> schedulingService.updateDag(10L, dto))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("ACTIVE版本");

        verify(dagRepository, never()).save(any(SchedulingDag.class));
        verify(quartzSchedulerService, never()).createOrUpdateDagJob(any(SchedulingDag.class), any(), any());
    }

    @Test
    void updateDagShouldDeferQuartzSyncUntilAfterCommitWhenActiveVersionExists() throws Exception {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        dag.setCode("dag-a");
        dag.setEnabled(true);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));
        when(dagRepository.save(any(SchedulingDag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SchedulingDagVersion activeVersion = new SchedulingDagVersion();
        activeVersion.setId(3L);
        activeVersion.setDagId(10L);
        activeVersion.setStatus("ACTIVE");
        when(dagVersionRepository.findByDagIdAndStatus(10L, "ACTIVE")).thenReturn(Optional.of(activeVersion));

        SchedulingDagCreateUpdateDto dto = new SchedulingDagCreateUpdateDto();
        dto.setCronEnabled(true);
        dto.setCronExpression("0 0 12 * * ?");
        dto.setCronTimezone("Asia/Shanghai");

        TransactionSynchronizationManager.initSynchronization();
        SchedulingDag saved = schedulingService.updateDag(10L, dto);
        verify(quartzSchedulerService, never()).createOrUpdateDagJob(any(SchedulingDag.class), any(), any());

        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        TransactionSynchronizationManager.clearSynchronization();

        verify(quartzSchedulerService).createOrUpdateDagJob(saved, "0 0 12 * * ?", "Asia/Shanghai");
    }

    @Test
    void retryDagShouldOnlyRetryLatestFailedRun() throws Exception {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        dag.setEnabled(true);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagRun failedRun = new SchedulingDagRun();
        failedRun.setId(202L);
        failedRun.setDagId(10L);
        failedRun.setDagVersionId(3L);
        failedRun.setTenantId(88L);
        failedRun.setTriggeredBy("legacy-user");
        failedRun.setStatus("FAILED");
        when(dagRunRepository.findTopByDagIdAndStatusInOrderByIdDesc(eq(10L), eq(List.of("FAILED", "PARTIAL_FAILED"))))
                .thenReturn(Optional.of(failedRun));
        when(dagRunRepository.save(any(SchedulingDagRun.class))).thenAnswer(invocation -> {
            SchedulingDagRun run = invocation.getArgument(0);
            run.setId(303L);
            return run;
        });

        TransactionSynchronizationManager.initSynchronization();
        schedulingService.retryDag(10L);
        verify(quartzSchedulerService, never()).triggerDagNow(eq(dag), any(SchedulingExecutionContext.class));
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        TransactionSynchronizationManager.clearSynchronization();

        ArgumentCaptor<SchedulingExecutionContext> executionContextCaptor =
                ArgumentCaptor.forClass(SchedulingExecutionContext.class);
        verify(quartzSchedulerService).triggerDagNow(eq(dag), executionContextCaptor.capture());
        SchedulingExecutionContext executionContext = executionContextCaptor.getValue();
        assertThat(executionContext.getDagRunId()).isEqualTo(303L);
        assertThat(executionContext.getDagVersionId()).isEqualTo(3L);
        assertThat(executionContext.getTriggerType()).isEqualTo("RETRY");
        assertThat(executionContext.getUsername()).isEqualTo("alice");

        ArgumentCaptor<SchedulingDagRun> runCaptor = ArgumentCaptor.forClass(SchedulingDagRun.class);
        verify(dagRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getTriggeredBy()).isEqualTo("alice");

        ArgumentCaptor<SchedulingAudit> auditCaptor = ArgumentCaptor.forClass(SchedulingAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("RETRY");
        assertThat(auditCaptor.getValue().getDetail()).contains("202");
        assertThat(auditCaptor.getValue().getDetail()).contains("303");
    }

    @Test
    void retryDagRunShouldRejectNonFailedRun() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        dag.setEnabled(true);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setDagId(10L);
        run.setTenantId(88L);
        run.setStatus("SUCCESS");
        when(dagRunRepository.findByIdAndTenantId(77L, 88L)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> schedulingService.retryDagRun(10L, 77L))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("仅支持重试失败的运行实例");

        verify(dagRunRepository, never()).save(any(SchedulingDagRun.class));
    }

    @Test
    void stopDagRunShouldOnlyCancelSpecifiedRun() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setDagId(10L);
        run.setTenantId(88L);
        run.setStatus("RUNNING");
        when(dagRunRepository.findByIdAndTenantId(77L, 88L)).thenReturn(Optional.of(run));
        when(dagRunRepository.save(any(SchedulingDagRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SchedulingTaskInstance running = new SchedulingTaskInstance();
        running.setId(501L);
        running.setStatus("RUNNING");
        running.setLockedBy("worker-a");
        running.setScheduledAt(java.time.LocalDateTime.now());

        SchedulingTaskInstance success = new SchedulingTaskInstance();
        success.setId(502L);
        success.setStatus("SUCCESS");

        when(taskInstanceRepository.findByDagRunId(77L)).thenReturn(List.of(running, success));

        schedulingService.stopDagRun(10L, 77L);

        assertThat(run.getStatus()).isEqualTo("CANCELLED");
        assertThat(run.getEndTime()).isNotNull();
        assertThat(running.getStatus()).isEqualTo("CANCELLED");
        assertThat(running.getLockedBy()).isNull();
        assertThat(running.getLockTime()).isNull();
        assertThat(running.getScheduledAt()).isNull();
        assertThat(running.getNextRetryAt()).isNull();
        assertThat(success.getStatus()).isEqualTo("SUCCESS");
        verify(taskInstanceRepository).saveAll(List.of(running, success));

        ArgumentCaptor<SchedulingAudit> auditCaptor = ArgumentCaptor.forClass(SchedulingAudit.class);
        verify(auditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getObjectType()).isEqualTo("dag_run");
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("STOP");
        assertThat(auditCaptor.getValue().getDetail()).contains("77");
    }

    @Test
    void stopDagRunShouldRejectNonRunningRun() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setDagId(10L);
        run.setTenantId(88L);
        run.setStatus("FAILED");
        when(dagRunRepository.findByIdAndTenantId(77L, 88L)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> schedulingService.stopDagRun(10L, 77L))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("RUNNING");

        verify(taskInstanceRepository, never()).findByDagRunId(any());
    }

    @Test
    void executeDagShouldUseTenantScopedRuntimeLookup() {
        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        dag.setEnabled(true);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setDagId(10L);
        run.setDagVersionId(3L);
        run.setTenantId(88L);
        when(dagRunRepository.findByIdAndTenantId(77L, 88L)).thenReturn(Optional.of(run));
        when(dagRunRepository.save(any(SchedulingDagRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(3L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(3L)).thenReturn(Optional.of(version));
        when(dagTaskRepository.findByDagVersionId(3L)).thenReturn(List.of());
        when(dagEdgeRepository.findByDagVersionId(3L)).thenReturn(List.of());

        schedulingService.executeDag(SchedulingExecutionContext.builder()
                .tenantId(88L)
                .dagId(10L)
                .dagRunId(77L)
                .dagVersionId(3L)
                .triggerType("MANUAL")
                .username("alice")
                .build());

        verify(dagRepository, atLeastOnce()).findByIdAndTenantId(10L, 88L);
        verify(dagRepository, never()).findById(10L);
        verify(dagRunRepository).findByIdAndTenantId(77L, 88L);
        verify(dagRunRepository, never()).findById(77L);
        assertThat(run.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void executeDagShouldPersistExecutionSnapshotOnTaskInstances() throws Exception {
        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        dag.setEnabled(true);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setDagId(10L);
        run.setDagVersionId(3L);
        run.setTenantId(88L);
        when(dagRunRepository.findByIdAndTenantId(77L, 88L)).thenReturn(Optional.of(run));
        when(dagRunRepository.save(any(SchedulingDagRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(3L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(3L)).thenReturn(Optional.of(version));

        SchedulingDagTask node = new SchedulingDagTask();
        node.setDagVersionId(3L);
        node.setNodeCode("node-a");
        node.setTaskId(501L);
        node.setOverrideParams("{\"fromNode\":\"yes\"}");
        when(dagTaskRepository.findByDagVersionId(3L)).thenReturn(List.of(node));
        when(dagEdgeRepository.findByDagVersionId(3L)).thenReturn(List.of());

        SchedulingTask task = new SchedulingTask();
        task.setId(501L);
        task.setTenantId(88L);
        task.setTypeId(601L);
        task.setParams("{\"fromTask\":\"yes\"}");
        when(taskRepository.findByIdAndTenantId(501L, 88L)).thenReturn(Optional.of(task));

        SchedulingTaskType taskType = new SchedulingTaskType();
        taskType.setId(601L);
        taskType.setTenantId(88L);
        taskType.setExecutor("loggingTaskExecutor");
        taskType.setParamSchema("{\"type\":\"object\"}");
        when(taskTypeRepository.findByIdAndTenantId(601L, 88L)).thenReturn(Optional.of(taskType));

        when(taskInstanceRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        schedulingService.executeDag(SchedulingExecutionContext.builder()
                .tenantId(88L)
                .dagId(10L)
                .dagRunId(77L)
                .dagVersionId(3L)
                .build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SchedulingTaskInstance>> instancesCaptor = ArgumentCaptor.forClass(List.class);
        verify(taskInstanceRepository).saveAll(instancesCaptor.capture());
        assertThat(instancesCaptor.getValue()).hasSize(1);
        SchedulingTaskInstance savedInstance = instancesCaptor.getValue().get(0);
        assertThat(savedInstance.getExecutionSnapshot()).isNotBlank();

        SchedulingTaskExecutionSnapshot snapshot = new ObjectMapper()
                .readValue(savedInstance.getExecutionSnapshot(), SchedulingTaskExecutionSnapshot.class);
        assertThat(snapshot.getTask().getTaskId()).isEqualTo(501L);
        assertThat(snapshot.getTask().getParams()).isEqualTo("{\"fromTask\":\"yes\"}");
        assertThat(snapshot.getTaskType().getTaskTypeId()).isEqualTo(601L);
        assertThat(snapshot.getTaskType().getExecutor()).isEqualTo("loggingTaskExecutor");
    }

    @Test
    void executeDagShouldScheduleRootNodesInParallelAndBlockDependentNodes() {
        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        dag.setEnabled(true);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setDagId(10L);
        run.setDagVersionId(3L);
        run.setTenantId(88L);
        when(dagRunRepository.findByIdAndTenantId(77L, 88L)).thenReturn(Optional.of(run));
        when(dagRunRepository.save(any(SchedulingDagRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(3L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(3L)).thenReturn(Optional.of(version));

        SchedulingDagTask rootA = new SchedulingDagTask();
        rootA.setDagVersionId(3L);
        rootA.setNodeCode("extract-a");
        rootA.setTaskId(501L);
        SchedulingDagTask rootB = new SchedulingDagTask();
        rootB.setDagVersionId(3L);
        rootB.setNodeCode("extract-b");
        rootB.setTaskId(502L);
        SchedulingDagTask join = new SchedulingDagTask();
        join.setDagVersionId(3L);
        join.setNodeCode("join");
        join.setTaskId(503L);
        when(dagTaskRepository.findByDagVersionId(3L)).thenReturn(List.of(rootA, rootB, join));

        SchedulingDagEdge edgeA = new SchedulingDagEdge();
        edgeA.setDagVersionId(3L);
        edgeA.setFromNodeCode("extract-a");
        edgeA.setToNodeCode("join");
        SchedulingDagEdge edgeB = new SchedulingDagEdge();
        edgeB.setDagVersionId(3L);
        edgeB.setFromNodeCode("extract-b");
        edgeB.setToNodeCode("join");
        when(dagEdgeRepository.findByDagVersionId(3L)).thenReturn(List.of(edgeA, edgeB));

        SchedulingTask taskA = new SchedulingTask();
        taskA.setId(501L);
        taskA.setTenantId(88L);
        taskA.setTypeId(601L);
        SchedulingTask taskB = new SchedulingTask();
        taskB.setId(502L);
        taskB.setTenantId(88L);
        taskB.setTypeId(601L);
        SchedulingTask taskJoin = new SchedulingTask();
        taskJoin.setId(503L);
        taskJoin.setTenantId(88L);
        taskJoin.setTypeId(601L);
        when(taskRepository.findByIdAndTenantId(501L, 88L)).thenReturn(Optional.of(taskA));
        when(taskRepository.findByIdAndTenantId(502L, 88L)).thenReturn(Optional.of(taskB));
        when(taskRepository.findByIdAndTenantId(503L, 88L)).thenReturn(Optional.of(taskJoin));

        SchedulingTaskType taskType = new SchedulingTaskType();
        taskType.setId(601L);
        taskType.setTenantId(88L);
        when(taskTypeRepository.findByIdAndTenantId(601L, 88L)).thenReturn(Optional.of(taskType));
        when(taskInstanceRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        schedulingService.executeDag(SchedulingExecutionContext.builder()
                .tenantId(88L)
                .dagId(10L)
                .dagRunId(77L)
                .dagVersionId(3L)
                .triggerType("MANUAL")
                .build());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SchedulingTaskInstance>> instancesCaptor = ArgumentCaptor.forClass(List.class);
        verify(taskInstanceRepository).saveAll(instancesCaptor.capture());
        assertThat(instancesCaptor.getValue()).hasSize(3);

        SchedulingTaskInstance savedRootA = instancesCaptor.getValue().stream()
                .filter(instance -> "extract-a".equals(instance.getNodeCode()))
                .findFirst()
                .orElseThrow();
        SchedulingTaskInstance savedRootB = instancesCaptor.getValue().stream()
                .filter(instance -> "extract-b".equals(instance.getNodeCode()))
                .findFirst()
                .orElseThrow();
        SchedulingTaskInstance savedJoin = instancesCaptor.getValue().stream()
                .filter(instance -> "join".equals(instance.getNodeCode()))
                .findFirst()
                .orElseThrow();

        assertThat(savedRootA.getScheduledAt()).isNotNull();
        assertThat(savedRootB.getScheduledAt()).isNotNull();
        assertThat(savedJoin.getScheduledAt()).isNull();
        assertThat(savedRootA.getStatus()).isEqualTo("PENDING");
        assertThat(savedRootB.getStatus()).isEqualTo("PENDING");
        assertThat(savedJoin.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void executeDagShouldGenerateUuidRunNoForScheduledRun() throws Exception {
        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        dag.setEnabled(true);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(3L);
        version.setDagId(10L);
        when(dagVersionRepository.findByDagIdAndStatus(10L, "ACTIVE")).thenReturn(Optional.of(version));
        when(dagTaskRepository.findByDagVersionId(3L)).thenReturn(List.of());
        when(dagEdgeRepository.findByDagVersionId(3L)).thenReturn(List.of());
        when(dagRunRepository.save(any(SchedulingDagRun.class))).thenAnswer(invocation -> {
            SchedulingDagRun saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(77L);
            }
            return saved;
        });

        schedulingService.executeDag(SchedulingExecutionContext.builder()
                .tenantId(88L)
                .dagId(10L)
                .triggerType("SCHEDULE")
                .username("Quartz Scheduler")
                .build());

        ArgumentCaptor<SchedulingDagRun> runCaptor = ArgumentCaptor.forClass(SchedulingDagRun.class);
        verify(dagRunRepository).save(runCaptor.capture());
        assertThat(runCaptor.getValue().getRunNo()).isNotBlank();
        assertThatCode(() -> UUID.fromString(runCaptor.getValue().getRunNo())).doesNotThrowAnyException();
    }

    @Test
    void triggerNodeShouldReuseLatestInstanceFromOrderedLookup() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagTask node = new SchedulingDagTask();
        node.setId(11L);
        node.setDagVersionId(100L);
        node.setNodeCode("node-a");
        node.setTaskId(501L);
        when(dagTaskRepository.findById(11L)).thenReturn(Optional.of(node));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setDagId(10L);
        run.setTenantId(88L);
        run.setStatus("RUNNING");
        when(dagRunRepository.findTopByDagIdAndStatusOrderByIdDesc(10L, "RUNNING")).thenReturn(Optional.of(run));

        SchedulingTaskInstance latest = new SchedulingTaskInstance();
        latest.setId(900L);
        latest.setDagRunId(77L);
        latest.setNodeCode("node-a");
        latest.setStatus("FAILED");
        when(taskInstanceRepository.findTopByDagRunIdAndNodeCodeOrderByIdDesc(77L, "node-a"))
                .thenReturn(Optional.of(latest));
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        schedulingService.triggerNode(10L, 11L);

        verify(taskInstanceRepository).findTopByDagRunIdAndNodeCodeOrderByIdDesc(77L, "node-a");
        assertThat(latest.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void triggerNodeInRunShouldRejectNonRunningRun() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagTask node = new SchedulingDagTask();
        node.setId(11L);
        node.setDagVersionId(100L);
        node.setNodeCode("node-a");
        node.setTaskId(501L);
        when(dagTaskRepository.findById(11L)).thenReturn(Optional.of(node));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setDagId(10L);
        run.setTenantId(88L);
        run.setStatus("FAILED");
        when(dagRunRepository.findByIdAndTenantId(77L, 88L)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> schedulingService.triggerNode(10L, 77L, 11L))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("RUNNING");
    }

    @Test
    void retryNodeShouldUseLatestRunWithFailedInstance() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagTask node = new SchedulingDagTask();
        node.setId(11L);
        node.setDagVersionId(100L);
        node.setNodeCode("node-a");
        node.setTaskId(501L);
        when(dagTaskRepository.findById(11L)).thenReturn(Optional.of(node));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));

        SchedulingDagRun latestRun = new SchedulingDagRun();
        latestRun.setId(88L);
        latestRun.setDagId(10L);
        latestRun.setTenantId(88L);
        latestRun.setStatus("FAILED");
        SchedulingDagRun olderRun = new SchedulingDagRun();
        olderRun.setId(77L);
        olderRun.setDagId(10L);
        olderRun.setTenantId(88L);
        when(dagRunRepository.findByDagIdOrderByIdDesc(10L)).thenReturn(List.of(latestRun, olderRun));

        SchedulingTaskInstance failedInstance = new SchedulingTaskInstance();
        failedInstance.setId(700L);
        failedInstance.setDagRunId(88L);
        failedInstance.setNodeCode("node-a");
        failedInstance.setStatus("FAILED");
        failedInstance.setAttemptNo(2);
        failedInstance.setExecutionSnapshot("{\"task\":{}}");
        when(taskInstanceRepository.findTopByDagRunIdAndNodeCodeOrderByIdDesc(88L, "node-a"))
                .thenReturn(Optional.of(failedInstance));
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> {
            SchedulingTaskInstance saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(701L);
            }
            return saved;
        });
        when(dagEdgeRepository.findByDagVersionIdAndFromNodeCode(100L, "node-a")).thenReturn(List.of());

        schedulingService.retryNode(10L, 11L);

        verify(taskInstanceRepository, atLeastOnce())
                .findTopByDagRunIdAndNodeCodeOrderByIdDesc(88L, "node-a");
        verify(taskInstanceRepository, never()).findTopByDagRunIdAndNodeCodeOrderByIdDesc(77L, "node-a");
        ArgumentCaptor<SchedulingTaskInstance> retryCaptor = ArgumentCaptor.forClass(SchedulingTaskInstance.class);
        verify(taskInstanceRepository).save(retryCaptor.capture());
        assertThat(retryCaptor.getValue().getDagRunId()).isEqualTo(88L);
        assertThat(retryCaptor.getValue().getAttemptNo()).isEqualTo(3);
        assertThat(latestRun.getStatus()).isEqualTo("RUNNING");
        assertThat(latestRun.getEndTime()).isNull();
        verify(dagRunRepository).save(latestRun);
    }

    @Test
    void retryNodeInRunShouldUseSpecifiedRun() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagTask node = new SchedulingDagTask();
        node.setId(11L);
        node.setDagVersionId(100L);
        node.setNodeCode("node-a");
        node.setTaskId(501L);
        when(dagTaskRepository.findById(11L)).thenReturn(Optional.of(node));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setDagId(10L);
        run.setTenantId(88L);
        run.setStatus("PARTIAL_FAILED");
        run.setEndTime(LocalDateTime.now());
        when(dagRunRepository.findByIdAndTenantId(77L, 88L)).thenReturn(Optional.of(run));

        SchedulingTaskInstance failedInstance = new SchedulingTaskInstance();
        failedInstance.setId(700L);
        failedInstance.setDagRunId(77L);
        failedInstance.setNodeCode("node-a");
        failedInstance.setStatus("FAILED");
        failedInstance.setAttemptNo(1);
        when(taskInstanceRepository.findTopByDagRunIdAndNodeCodeOrderByIdDesc(77L, "node-a"))
                .thenReturn(Optional.of(failedInstance));
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dagEdgeRepository.findByDagVersionIdAndFromNodeCode(100L, "node-a")).thenReturn(List.of());

        schedulingService.retryNode(10L, 77L, 11L);

        verify(taskInstanceRepository).findTopByDagRunIdAndNodeCodeOrderByIdDesc(77L, "node-a");
        assertThat(run.getStatus()).isEqualTo("RUNNING");
        assertThat(run.getEndTime()).isNull();
        verify(dagRunRepository).save(run);
    }

    @Test
    void pauseNodeShouldKeepPausedInstancePaused() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagTask node = new SchedulingDagTask();
        node.setId(11L);
        node.setDagVersionId(100L);
        node.setNodeCode("node-a");
        when(dagTaskRepository.findById(11L)).thenReturn(Optional.of(node));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setTenantId(88L);
        when(dagRunRepository.findByDagIdOrderByIdDesc(10L)).thenReturn(List.of(run));

        SchedulingTaskInstance paused = new SchedulingTaskInstance();
        paused.setId(501L);
        paused.setStatus("PAUSED");
        paused.setNodeCode("node-a");
        when(taskInstanceRepository.findTopByDagRunIdAndNodeCodeAndStatusInOrderByIdDesc(
                77L,
                "node-a",
                List.of("PENDING", "RESERVED", "PAUSED")))
                .thenReturn(Optional.of(paused));

        schedulingService.pauseNode(10L, 11L);

        assertThat(paused.getStatus()).isEqualTo("PAUSED");
        verify(taskInstanceRepository, never()).save(paused);
        verify(auditRepository).save(any(SchedulingAudit.class));
    }

    @Test
    void pauseNodeShouldUseLatestRunWithPauseableInstance() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagTask node = new SchedulingDagTask();
        node.setId(11L);
        node.setDagVersionId(100L);
        node.setNodeCode("node-a");
        when(dagTaskRepository.findById(11L)).thenReturn(Optional.of(node));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));

        SchedulingDagRun latestRun = new SchedulingDagRun();
        latestRun.setId(88L);
        latestRun.setTenantId(88L);
        SchedulingDagRun olderRun = new SchedulingDagRun();
        olderRun.setId(77L);
        olderRun.setTenantId(88L);
        when(dagRunRepository.findByDagIdOrderByIdDesc(10L)).thenReturn(List.of(latestRun, olderRun));

        SchedulingTaskInstance pending = new SchedulingTaskInstance();
        pending.setId(501L);
        pending.setStatus("PENDING");
        pending.setNodeCode("node-a");
        when(taskInstanceRepository.findTopByDagRunIdAndNodeCodeAndStatusInOrderByIdDesc(
                88L,
                "node-a",
                List.of("PENDING", "RESERVED", "PAUSED")))
                .thenReturn(Optional.of(pending));
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        schedulingService.pauseNode(10L, 11L);

        assertThat(pending.getStatus()).isEqualTo("PAUSED");
        verify(taskInstanceRepository, never()).findTopByDagRunIdAndNodeCodeAndStatusInOrderByIdDesc(
                77L,
                "node-a",
                List.of("PENDING", "RESERVED", "PAUSED"));
    }

    @Test
    void resumeNodeShouldOnlyResumePausedInstance() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagTask node = new SchedulingDagTask();
        node.setId(11L);
        node.setDagVersionId(100L);
        node.setNodeCode("node-a");
        when(dagTaskRepository.findById(11L)).thenReturn(Optional.of(node));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setTenantId(88L);
        when(dagRunRepository.findByDagIdOrderByIdDesc(10L)).thenReturn(List.of(run));

        SchedulingTaskInstance paused = new SchedulingTaskInstance();
        paused.setId(501L);
        paused.setStatus("PAUSED");
        paused.setNodeCode("node-a");
        when(taskInstanceRepository.findTopByDagRunIdAndNodeCodeAndStatusOrderByIdDesc(77L, "node-a", "PAUSED"))
                .thenReturn(Optional.of(paused));
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        schedulingService.resumeNode(10L, 11L);

        assertThat(paused.getStatus()).isEqualTo("PENDING");
        verify(taskInstanceRepository, times(1)).save(paused);
    }

    @Test
    void resumeNodeShouldUseLatestRunWithPausedInstance() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagTask node = new SchedulingDagTask();
        node.setId(11L);
        node.setDagVersionId(100L);
        node.setNodeCode("node-a");
        when(dagTaskRepository.findById(11L)).thenReturn(Optional.of(node));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));

        SchedulingDagRun latestRun = new SchedulingDagRun();
        latestRun.setId(88L);
        latestRun.setTenantId(88L);
        SchedulingDagRun olderRun = new SchedulingDagRun();
        olderRun.setId(77L);
        olderRun.setTenantId(88L);
        when(dagRunRepository.findByDagIdOrderByIdDesc(10L)).thenReturn(List.of(latestRun, olderRun));

        SchedulingTaskInstance paused = new SchedulingTaskInstance();
        paused.setId(501L);
        paused.setStatus("PAUSED");
        paused.setNodeCode("node-a");
        when(taskInstanceRepository.findTopByDagRunIdAndNodeCodeAndStatusOrderByIdDesc(88L, "node-a", "PAUSED"))
                .thenReturn(Optional.of(paused));
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        schedulingService.resumeNode(10L, 11L);

        assertThat(paused.getStatus()).isEqualTo("PENDING");
        verify(taskInstanceRepository, never()).findTopByDagRunIdAndNodeCodeAndStatusOrderByIdDesc(77L, "node-a", "PAUSED");
    }

    @Test
    void pauseNodeInRunShouldOnlyPauseLatestPauseableInstance() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagTask node = new SchedulingDagTask();
        node.setId(11L);
        node.setDagVersionId(100L);
        node.setNodeCode("node-a");
        when(dagTaskRepository.findById(11L)).thenReturn(Optional.of(node));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setDagId(10L);
        run.setTenantId(88L);
        when(dagRunRepository.findByIdAndTenantId(77L, 88L)).thenReturn(Optional.of(run));

        SchedulingTaskInstance pending = new SchedulingTaskInstance();
        pending.setId(501L);
        pending.setStatus("PENDING");
        pending.setNodeCode("node-a");
        pending.setLockedBy("worker-a");
        when(taskInstanceRepository.findTopByDagRunIdAndNodeCodeAndStatusInOrderByIdDesc(
                77L,
                "node-a",
                List.of("PENDING", "RESERVED", "PAUSED")))
                .thenReturn(Optional.of(pending));
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        schedulingService.pauseNode(10L, 77L, 11L);

        assertThat(pending.getStatus()).isEqualTo("PAUSED");
        assertThat(pending.getLockedBy()).isNull();
        assertThat(pending.getLockTime()).isNull();
        verify(taskInstanceRepository).save(pending);
    }

    @Test
    void resumeNodeInRunShouldOnlyResumeLatestPausedInstance() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagTask node = new SchedulingDagTask();
        node.setId(11L);
        node.setDagVersionId(100L);
        node.setNodeCode("node-a");
        when(dagTaskRepository.findById(11L)).thenReturn(Optional.of(node));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));

        SchedulingDagRun run = new SchedulingDagRun();
        run.setId(77L);
        run.setDagId(10L);
        run.setTenantId(88L);
        when(dagRunRepository.findByIdAndTenantId(77L, 88L)).thenReturn(Optional.of(run));

        SchedulingTaskInstance paused = new SchedulingTaskInstance();
        paused.setId(501L);
        paused.setStatus("PAUSED");
        paused.setNodeCode("node-a");
        when(taskInstanceRepository.findTopByDagRunIdAndNodeCodeAndStatusOrderByIdDesc(77L, "node-a", "PAUSED"))
                .thenReturn(Optional.of(paused));
        when(taskInstanceRepository.save(any(SchedulingTaskInstance.class))).thenAnswer(invocation -> invocation.getArgument(0));

        schedulingService.resumeNode(10L, 77L, 11L);

        assertThat(paused.getStatus()).isEqualTo("PENDING");
        assertThat(paused.getScheduledAt()).isNotNull();
        verify(taskInstanceRepository).save(paused);
    }

    @Test
    void createDagVersionShouldArchiveExistingActiveVersionWhenNewVersionIsActive() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));
        when(dagVersionRepository.findMaxVersionNoByDagId(10L)).thenReturn(2);

        SchedulingDagVersion existingActive = new SchedulingDagVersion();
        existingActive.setId(100L);
        existingActive.setDagId(10L);
        existingActive.setStatus("ACTIVE");
        when(dagVersionRepository.findByDagId(10L)).thenReturn(List.of(existingActive));
        when(dagVersionRepository.save(any(SchedulingDagVersion.class))).thenAnswer(invocation -> {
            SchedulingDagVersion saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(101L);
            }
            return saved;
        });

        SchedulingDagVersionCreateUpdateDto dto = new SchedulingDagVersionCreateUpdateDto();
        dto.setStatus(" active ");
        dto.setDefinition("{\"nodes\":[]}");

        SchedulingDagVersion created = schedulingService.createDagVersion(10L, dto);

        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getVersionNo()).isEqualTo(3);
        assertThat(created.getActivatedAt()).isNotNull();
        assertThat(existingActive.getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    void createDagVersionShouldRejectInvalidStatus() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));
        when(dagVersionRepository.findMaxVersionNoByDagId(10L)).thenReturn(2);

        SchedulingDagVersionCreateUpdateDto dto = new SchedulingDagVersionCreateUpdateDto();
        dto.setStatus("BROKEN");

        assertThatThrownBy(() -> schedulingService.createDagVersion(10L, dto))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("DAG版本状态无效");
    }

    @Test
    void createDagShouldRejectInvalidTimezone() {
        authenticate(8L, 88L, "alice");

        SchedulingDagCreateUpdateDto dto = new SchedulingDagCreateUpdateDto();
        dto.setCode("dag-a");
        dto.setName("Dag A");
        dto.setCronEnabled(true);
        dto.setCronExpression("0 0 12 * * ?");
        dto.setCronTimezone("Invalid/Timezone");

        assertThatThrownBy(() -> schedulingService.createDag(dto))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("Cron 时区无效");
    }

    @Test
    void createDagNodeShouldRejectMutatingActiveVersion() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagVersion activeVersion = new SchedulingDagVersion();
        activeVersion.setId(100L);
        activeVersion.setDagId(10L);
        activeVersion.setStatus("ACTIVE");
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(activeVersion));

        SchedulingDagTaskCreateUpdateDto dto = new SchedulingDagTaskCreateUpdateDto();
        dto.setNodeCode("node-a");
        dto.setTaskId(501L);

        assertThatThrownBy(() -> schedulingService.createDagNode(10L, 100L, dto))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("ACTIVE版本不可直接修改");
    }

    @Test
    void createDagEdgeShouldRejectInvalidConditionJson() {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));

        SchedulingDagVersion version = new SchedulingDagVersion();
        version.setId(100L);
        version.setDagId(10L);
        version.setStatus("DRAFT");
        when(dagVersionRepository.findById(100L)).thenReturn(Optional.of(version));

        SchedulingDagEdgeCreateDto dto = new SchedulingDagEdgeCreateDto();
        dto.setFromNodeCode("node-a");
        dto.setToNodeCode("node-b");
        dto.setCondition("{invalid-json");

        assertThatThrownBy(() -> schedulingService.createDagEdge(10L, 100L, dto))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("边条件 不是合法 JSON");
    }

    @Test
    void getTaskShouldReturnEmptyWhenResourceInDifferentTenant() {
        authenticate(8L, 88L, "alice");
        when(taskRepository.findByIdAndTenantId(100L, 88L)).thenReturn(Optional.empty());

        Optional<SchedulingTask> found = schedulingService.getTask(100L);

        assertThat(found).isEmpty();
        verify(taskRepository).findByIdAndTenantId(100L, 88L);
        verify(taskRepository, never()).findById(100L);
    }

    @Test
    void deleteTaskShouldRejectWhenActiveInstancesExist() {
        authenticate(8L, 88L, "alice");

        SchedulingTask task = new SchedulingTask();
        task.setId(501L);
        task.setTenantId(88L);
        task.setCode("billing-task");
        when(taskRepository.findByIdAndTenantId(501L, 88L)).thenReturn(Optional.of(task));
        when(dagTaskRepository.findByTaskId(501L)).thenReturn(List.of());
        when(taskInstanceRepository.existsByTaskIdAndStatusIn(eq(501L), any())).thenReturn(true);

        assertThatThrownBy(() -> schedulingService.deleteTask(501L))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("待执行或运行中的任务实例");

        verify(taskRepository, never()).delete(task);
    }

    @Test
    void deleteTaskShouldRejectWhenExecutionHistoryExists() {
        authenticate(8L, 88L, "alice");

        SchedulingTask task = new SchedulingTask();
        task.setId(501L);
        task.setTenantId(88L);
        task.setCode("billing-task");
        when(taskRepository.findByIdAndTenantId(501L, 88L)).thenReturn(Optional.of(task));
        when(dagTaskRepository.findByTaskId(501L)).thenReturn(List.of());
        when(taskHistoryRepository.existsByTaskId(501L)).thenReturn(true);

        assertThatThrownBy(() -> schedulingService.deleteTask(501L))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("已有执行历史");

        verify(taskRepository, never()).delete(task);
    }

    @Test
    void deleteDagShouldRejectWhenRunHistoryExists() throws Exception {
        authenticate(8L, 88L, "alice");

        SchedulingDag dag = new SchedulingDag();
        dag.setId(10L);
        dag.setTenantId(88L);
        dag.setCode("billing-dag");
        when(dagRepository.findByIdAndTenantId(10L, 88L)).thenReturn(Optional.of(dag));
        when(dagRunRepository.countByDagId(10L)).thenReturn(1L);

        assertThatThrownBy(() -> schedulingService.deleteDag(10L))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("已有运行历史");

        verify(quartzSchedulerService, never()).deleteDagJob(10L);
        verify(dagRepository, never()).delete(dag);
    }

    @Test
    void deleteTaskTypeShouldUseTenantScopedLookupAndThrowWhenNotInTenant() {
        authenticate(8L, 88L, "alice");
        when(taskTypeRepository.findByIdAndTenantId(1L, 88L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> schedulingService.deleteTaskType(1L))
                .isInstanceOf(SchedulingException.class)
                .hasMessageContaining("任务类型不存在");

        verify(taskTypeRepository).findByIdAndTenantId(1L, 88L);
        verify(taskTypeRepository, never()).delete(isA(SchedulingTaskType.class));
    }

    @Test
    void createTaskShouldNormalizeBlankCodeToNull() {
        authenticate(8L, 88L, "alice");

        SchedulingTaskType taskType = new SchedulingTaskType();
        taskType.setId(77L);
        taskType.setTenantId(88L);
        when(taskTypeRepository.findByIdAndTenantId(77L, 88L)).thenReturn(Optional.of(taskType));
        when(taskRepository.save(any(SchedulingTask.class))).thenAnswer(invocation -> {
            SchedulingTask saved = invocation.getArgument(0);
            saved.setId(501L);
            return saved;
        });

        SchedulingTaskCreateUpdateDto dto = new SchedulingTaskCreateUpdateDto();
        dto.setTypeId(77L);
        dto.setName("Billing Task");
        dto.setCode("   ");

        SchedulingTask saved = schedulingService.createTask(dto);

        assertThat(saved.getCode()).isNull();
        verify(taskRepository, never()).findByTenantIdAndCode(eq(88L), any());
    }

    @Test
    void listTaskTypesShouldFilterByCurrentTenant() {
        authenticate(8L, 88L, "alice");
        SchedulingTaskType tt = new SchedulingTaskType();
        tt.setId(1L);
        tt.setTenantId(88L);
        tt.setCode("billing");
        when(taskTypeRepository.findAll(isA(Specification.class), isA(Pageable.class))).thenReturn(new PageImpl<>(List.of(tt)));

        var page = schedulingService.listTaskTypes(null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTenantId()).isEqualTo(88L);
    }

    private void authenticate(Long userId, Long tenantId, String username) {
        TenantContext.setTenantId(tenantId);
        SecurityUser securityUser = new SecurityUser(userId, tenantId, username, "", List.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(securityUser, "N/A", List.of()));
    }
}
