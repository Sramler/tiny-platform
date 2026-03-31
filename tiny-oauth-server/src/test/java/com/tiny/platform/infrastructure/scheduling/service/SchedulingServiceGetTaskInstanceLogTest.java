package com.tiny.platform.infrastructure.scheduling.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskHistory;
import com.tiny.platform.infrastructure.scheduling.model.SchedulingTaskInstance;
import com.tiny.platform.infrastructure.scheduling.repository.*;
import com.tiny.platform.infrastructure.tenant.guard.TenantLifecycleGuard;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 getTaskInstanceLog 不向前端暴露 logPath、stackTrace、未脱敏异常信息。
 */
class SchedulingServiceGetTaskInstanceLogTest {

    private SchedulingTaskInstanceRepository taskInstanceRepository;
    private SchedulingTaskHistoryRepository taskHistoryRepository;
    private SchedulingService schedulingService;
    private static final Long TENANT_ID = 88L;
    private static final Long INSTANCE_ID = 100L;

    @BeforeEach
    void setUp() {
        taskInstanceRepository = mock(SchedulingTaskInstanceRepository.class);
        taskHistoryRepository = mock(SchedulingTaskHistoryRepository.class);
        SchedulingTaskTypeRepository taskTypeRepository = mock(SchedulingTaskTypeRepository.class);
        SchedulingTaskRepository taskRepository = mock(SchedulingTaskRepository.class);
        SchedulingDagRepository dagRepository = mock(SchedulingDagRepository.class);
        SchedulingDagVersionRepository dagVersionRepository = mock(SchedulingDagVersionRepository.class);
        SchedulingDagTaskRepository dagTaskRepository = mock(SchedulingDagTaskRepository.class);
        SchedulingDagEdgeRepository dagEdgeRepository = mock(SchedulingDagEdgeRepository.class);
        SchedulingDagRunRepository dagRunRepository = mock(SchedulingDagRunRepository.class);
        SchedulingAuditRepository auditRepository = mock(SchedulingAuditRepository.class);
        TenantUserRepository tenantUserRepository = mock(TenantUserRepository.class);
        UserUnitRepository userUnitRepository = mock(UserUnitRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        QuartzSchedulerService quartzSchedulerService = mock(QuartzSchedulerService.class);
        TaskExecutorRegistry taskExecutorRegistry = mock(TaskExecutorRegistry.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        TenantLifecycleGuard tenantLifecycleGuard = new TenantLifecycleGuard(tenantRepository);
        schedulingService = new SchedulingService(
                taskTypeRepository, taskRepository, dagRepository, dagVersionRepository,
                dagTaskRepository, dagEdgeRepository, dagRunRepository, taskInstanceRepository,
                taskHistoryRepository, auditRepository, tenantUserRepository, userUnitRepository, userRepository,
                quartzSchedulerService, taskExecutorRegistry,
                new JsonSchemaValidationService(new ObjectMapper()), new ObjectMapper(), tenantLifecycleGuard);
        TenantContext.setActiveTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getTaskInstanceLogMustNotReturnRawLogPath() {
        SchedulingTaskInstance instance = new SchedulingTaskInstance();
        instance.setId(INSTANCE_ID);
        instance.setTenantId(TENANT_ID);
        when(taskInstanceRepository.findByIdAndTenantId(eq(INSTANCE_ID), eq(TENANT_ID)))
                .thenReturn(Optional.of(instance));

        SchedulingTaskHistory history = new SchedulingTaskHistory();
        history.setLogPath("/opt/app/logs/task-100.log");
        history.setStatus("RUNNING");
        when(taskHistoryRepository.findTopByTaskInstanceIdAndTenantIdOrderByIdDesc(eq(INSTANCE_ID), eq(TENANT_ID)))
                .thenReturn(Optional.of(history));

        Optional<String> result = schedulingService.getTaskInstanceLog(INSTANCE_ID);

        assertThat(result).isPresent();
        String body = result.get();
        assertThat(body).doesNotContain("/opt/app/logs");
        assertThat(body).doesNotContain("日志路径:");
    }

    @Test
    void getTaskInstanceLogMustNotReturnStackTrace() {
        SchedulingTaskInstance instance = new SchedulingTaskInstance();
        instance.setId(INSTANCE_ID);
        instance.setTenantId(TENANT_ID);
        when(taskInstanceRepository.findByIdAndTenantId(eq(INSTANCE_ID), eq(TENANT_ID)))
                .thenReturn(Optional.of(instance));

        SchedulingTaskHistory history = new SchedulingTaskHistory();
        history.setStatus("FAILED");
        history.setErrorMessage("java.lang.NullPointerException");
        history.setStackTrace("at com.example.Foo.bar(Foo.java:42)\n\tat org.springframework...");
        when(taskHistoryRepository.findTopByTaskInstanceIdAndTenantIdOrderByIdDesc(eq(INSTANCE_ID), eq(TENANT_ID)))
                .thenReturn(Optional.of(history));

        Optional<String> result = schedulingService.getTaskInstanceLog(INSTANCE_ID);

        assertThat(result).isPresent();
        String body = result.get();
        assertThat(body).doesNotContain("at com.");
        assertThat(body).doesNotContain("at org.");
        assertThat(body).doesNotContain("Foo.java");
    }

    @Test
    void getTaskInstanceLogSanitizesFailureMessage() {
        SchedulingTaskInstance instance = new SchedulingTaskInstance();
        instance.setId(INSTANCE_ID);
        instance.setTenantId(TENANT_ID);
        when(taskInstanceRepository.findByIdAndTenantId(eq(INSTANCE_ID), eq(TENANT_ID)))
                .thenReturn(Optional.of(instance));

        SchedulingTaskHistory history = new SchedulingTaskHistory();
        history.setStatus("FAILED");
        history.setErrorMessage("com.mysql.cj.jdbc.exceptions.CommunicationException: Internal path /var/lib/mysql");
        history.setStackTrace("at com.mysql...");
        when(taskHistoryRepository.findTopByTaskInstanceIdAndTenantIdOrderByIdDesc(eq(INSTANCE_ID), eq(TENANT_ID)))
                .thenReturn(Optional.of(history));

        Optional<String> result = schedulingService.getTaskInstanceLog(INSTANCE_ID);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo("任务执行失败");
    }
}
