package com.tiny.platform.infrastructure.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.application.oauth.workflow.WorkflowTaskContext;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import com.tiny.platform.infrastructure.tenant.service.TenantService;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import com.tiny.platform.infrastructure.workflow.model.WorkflowBusinessRequestEntity;
import com.tiny.platform.infrastructure.workflow.model.WorkflowBusinessRequestStatus;
import com.tiny.platform.infrastructure.workflow.repository.WorkflowBusinessRequestRepository;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowBusinessRequestServiceTest {

    @Mock
    private WorkflowBusinessRequestRepository repository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantService tenantService;

    @Mock
    private DelegateExecution execution;

    @Mock
    private PlatformPermissionPublishService platformPermissionPublishService;

    @Mock
    private PlatformRoleBaselineService platformRoleBaselineService;

    @Mock
    private WorkflowGovernanceAssetService governanceAssetService;

    private WorkflowBusinessRequestService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowBusinessRequestService(
            repository,
            tenantRepository,
            tenantService,
            new ObjectMapper(),
            platformPermissionPublishService,
            platformRoleBaselineService,
            governanceAssetService
        );
        AtomicLong ids = new AtomicLong(100L);
        when(repository.save(any(WorkflowBusinessRequestEntity.class))).thenAnswer(invocation -> {
            WorkflowBusinessRequestEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(ids.incrementAndGet());
            }
            return entity;
        });
    }

    @Test
    void createSubmittedRequest_persistsMaskedBusinessPayload() {
        when(repository.findByScopeAndRequestId(ProcessModelScopeType.PLATFORM, null, "REQ-001"))
            .thenReturn(Optional.empty());
        ArgumentCaptor<WorkflowBusinessRequestEntity> captor =
            ArgumentCaptor.forClass(WorkflowBusinessRequestEntity.class);

        Optional<Long> requestPk = service.createSubmittedRequest(
            "platform_tenant_onboarding",
            null,
            Map.of(
                "requestId",
                "REQ-001",
                "requestTitle",
                "租户开通",
                "requestReason",
                "客户入驻",
                "tenantCode",
                "acme",
                "initialAdminPassword",
                "plain-secret"
            ),
            auth("platform-admin")
        );

        assertThat(requestPk).contains(101L);
        verify(repository).save(captor.capture());
        WorkflowBusinessRequestEntity entity = captor.getValue();
        assertThat(entity.getScopeType()).isEqualTo(ProcessModelScopeType.PLATFORM);
        assertThat(entity.getStatus()).isEqualTo(WorkflowBusinessRequestStatus.SUBMITTED);
        assertThat(entity.getPayloadJson()).contains("\"initialAdminPassword\":\"***\"");
        assertThat(entity.getPayloadJson()).doesNotContain("plain-secret");
    }

    @Test
    void applyConnector_whenTenantPlanChange_updatesTenantPlanAndMarksRequestApplied() {
        WorkflowBusinessRequestEntity entity = request("platform_tenant_plan_change");
        when(repository.findByProcessInstanceId("inst-1")).thenReturn(Optional.of(entity));
        TenantResponseDto response = new TenantResponseDto();
        response.setId(9L);
        response.setCode("acme");
        response.setPlanCode("enterprise");
        when(tenantService.update(any(), any(TenantCreateUpdateDto.class))).thenReturn(response);
        when(execution.getProcessInstanceId()).thenReturn("inst-1");
        when(execution.getVariables()).thenReturn(Map.of(
            "tenantId",
            9L,
            "targetPlanCode",
            "enterprise"
        ));
        ArgumentCaptor<TenantCreateUpdateDto> dtoCaptor = ArgumentCaptor.forClass(TenantCreateUpdateDto.class);

        service.applyConnector(execution, "platformTenantPlanChangeConnector");

        verify(tenantService).update(any(), dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getPlanCode()).isEqualTo("enterprise");
        assertThat(entity.getStatus()).isEqualTo(WorkflowBusinessRequestStatus.APPLIED);
        assertThat(entity.getDomainResourceType()).isEqualTo("TENANT");
        assertThat(entity.getDomainResourceId()).isEqualTo("9");
        assertThat(entity.getDomainStatus()).isEqualTo("TENANT_PLAN_UPDATED");
    }

    @Test
    void finishTask_whenProcessEnded_marksBusinessRequestCompleted() {
        WorkflowBusinessRequestEntity entity = request("platform_tenant_plan_change");
        entity.setStatus(WorkflowBusinessRequestStatus.APPLIED);
        when(repository.findByProcessInstanceId("inst-1")).thenReturn(Optional.of(entity));
        WorkflowTaskContext taskContext = new WorkflowTaskContext(
            "task-1",
            "结果确认",
            "UserTask_Confirm",
            "inst-1",
            "platform_tenant_plan_change:1:1",
            "platform_tenant_plan_change",
            null
        );

        service.finishTask(
            taskContext,
            Map.of("decision", "APPROVE", "comment", "确认完成"),
            auth("platform-admin"),
            false
        );

        assertThat(entity.getStatus()).isEqualTo(WorkflowBusinessRequestStatus.COMPLETED);
        assertThat(entity.getCompletedAt()).isNotNull();
        assertThat(entity.getLastDecision()).isEqualTo("APPROVE");
        assertThat(entity.getLastTaskKey()).isEqualTo("UserTask_Confirm");
    }

    @Test
    void applyConnector_whenPermissionPublish_writesPermissionDomainClosure() {
        WorkflowBusinessRequestEntity entity = request("platform_permission_publish");
        Map<String, Object> variables = Map.of(
            "permissionCode",
            "workflow:platform:demo:start",
            "changeType",
            "CREATE"
        );
        when(repository.findByProcessInstanceId("inst-1")).thenReturn(Optional.of(entity));
        when(execution.getProcessInstanceId()).thenReturn("inst-1");
        when(execution.getVariables()).thenReturn(variables);
        when(platformPermissionPublishService.publish(variables, null, ProcessModelScopeType.PLATFORM, null))
            .thenReturn(Map.of(
                "permissionId",
                501L,
                "permissionCode",
                "workflow:platform:demo:start"
            ));

        service.applyConnector(execution, "platformPermissionPublishConnector");

        verify(platformPermissionPublishService).publish(variables, null, ProcessModelScopeType.PLATFORM, null);
        assertThat(entity.getStatus()).isEqualTo(WorkflowBusinessRequestStatus.APPLIED);
        assertThat(entity.getDomainResourceType()).isEqualTo("PERMISSION");
        assertThat(entity.getDomainResourceId()).isEqualTo("501");
        assertThat(entity.getDomainStatus()).isEqualTo("PERMISSION_PUBLISHED");
        assertThat(entity.getResultJson()).contains("workflow:platform:demo:start", "PERMISSION_PUBLISHED");
    }

    @Test
    void applyConnector_whenRoleBaselineChange_writesRoleDomainClosure() {
        WorkflowBusinessRequestEntity entity = request("platform_role_baseline_change");
        Map<String, Object> variables = Map.of(
            "roleCode",
            "ROLE_PLATFORM_WORKFLOW_AUDITOR",
            "changeType",
            "CREATE"
        );
        when(repository.findByProcessInstanceId("inst-1")).thenReturn(Optional.of(entity));
        when(execution.getProcessInstanceId()).thenReturn("inst-1");
        when(execution.getVariables()).thenReturn(variables);
        when(platformRoleBaselineService.publish(variables, null, ProcessModelScopeType.PLATFORM, null))
            .thenReturn(Map.of(
                "roleId",
                601L,
                "roleCode",
                "ROLE_PLATFORM_WORKFLOW_AUDITOR"
            ));

        service.applyConnector(execution, "platformRoleBaselineConnector");

        verify(platformRoleBaselineService).publish(variables, null, ProcessModelScopeType.PLATFORM, null);
        assertThat(entity.getStatus()).isEqualTo(WorkflowBusinessRequestStatus.APPLIED);
        assertThat(entity.getDomainResourceType()).isEqualTo("ROLE");
        assertThat(entity.getDomainResourceId()).isEqualTo("601");
        assertThat(entity.getDomainStatus()).isEqualTo("ROLE_BASELINE_APPLIED");
        assertThat(entity.getResultJson()).contains("ROLE_PLATFORM_WORKFLOW_AUDITOR", "ROLE_BASELINE_APPLIED");
    }

    @Test
    void applyConnector_whenConnectorPublish_writesGovernanceAssetClosure() {
        WorkflowBusinessRequestEntity entity = request("platform_connector_publish");
        Map<String, Object> variables = Map.of(
            "connectorKey",
            "platformDemoConnector",
            "connectorAction",
            "PUBLISH"
        );
        when(repository.findByProcessInstanceId("inst-1")).thenReturn(Optional.of(entity));
        when(execution.getProcessInstanceId()).thenReturn("inst-1");
        when(execution.getVariables()).thenReturn(variables);
        when(governanceAssetService.publishConnector(variables, null, ProcessModelScopeType.PLATFORM, null))
            .thenReturn(Map.of(
                "governanceAssetId",
                701L,
                "assetKey",
                "platformDemoConnector",
                "status",
                "PUBLISHED"
            ));

        service.applyConnector(execution, "platformConnectorPublishConnector");

        verify(governanceAssetService).publishConnector(variables, null, ProcessModelScopeType.PLATFORM, null);
        assertThat(entity.getDomainResourceType()).isEqualTo("CONNECTOR");
        assertThat(entity.getDomainResourceId()).isEqualTo("701");
        assertThat(entity.getDomainStatus()).isEqualTo("CONNECTOR_PUBLISHED");
        assertThat(entity.getResultJson()).contains("platformDemoConnector", "CONNECTOR_PUBLISHED");
    }

    @Test
    void applyConnector_whenTemplatePublish_writesGovernanceAssetClosure() {
        WorkflowBusinessRequestEntity entity = request("platform_template_publish");
        Map<String, Object> variables = Map.of(
            "templateKey",
            "platform_demo_template",
            "publishAction",
            "PUBLISH"
        );
        when(repository.findByProcessInstanceId("inst-1")).thenReturn(Optional.of(entity));
        when(execution.getProcessInstanceId()).thenReturn("inst-1");
        when(execution.getVariables()).thenReturn(variables);
        when(governanceAssetService.publishTemplate(variables, null, ProcessModelScopeType.PLATFORM, null))
            .thenReturn(Map.of(
                "governanceAssetId",
                801L,
                "assetKey",
                "platform_demo_template",
                "status",
                "PUBLISHED"
            ));

        service.applyConnector(execution, "platformTemplatePublishConnector");

        verify(governanceAssetService).publishTemplate(variables, null, ProcessModelScopeType.PLATFORM, null);
        assertThat(entity.getDomainResourceType()).isEqualTo("TEMPLATE");
        assertThat(entity.getDomainResourceId()).isEqualTo("801");
        assertThat(entity.getDomainStatus()).isEqualTo("TEMPLATE_PUBLISHED");
        assertThat(entity.getResultJson()).contains("platform_demo_template", "TEMPLATE_PUBLISHED");
    }

    @Test
    void applyConnector_whenConfigChange_writesGovernanceAssetClosure() {
        WorkflowBusinessRequestEntity entity = request("platform_config_change");
        Map<String, Object> variables = Map.of(
            "configKey",
            "workflow.approval.window",
            "changeType",
            "UPDATE"
        );
        when(repository.findByProcessInstanceId("inst-1")).thenReturn(Optional.of(entity));
        when(execution.getProcessInstanceId()).thenReturn("inst-1");
        when(execution.getVariables()).thenReturn(variables);
        when(governanceAssetService.publishConfig(variables, null, ProcessModelScopeType.PLATFORM, null))
            .thenReturn(Map.of(
                "governanceAssetId",
                901L,
                "assetKey",
                "workflow.approval.window",
                "status",
                "APPLIED"
            ));

        service.applyConnector(execution, "platformConfigChangeConnector");

        verify(governanceAssetService).publishConfig(variables, null, ProcessModelScopeType.PLATFORM, null);
        assertThat(entity.getDomainResourceType()).isEqualTo("CONFIG");
        assertThat(entity.getDomainResourceId()).isEqualTo("901");
        assertThat(entity.getDomainStatus()).isEqualTo("CONFIG_APPLIED");
        assertThat(entity.getResultJson()).contains("workflow.approval.window", "CONFIG_APPLIED");
    }

    private static WorkflowBusinessRequestEntity request(String processKey) {
        WorkflowBusinessRequestEntity entity = new WorkflowBusinessRequestEntity();
        entity.setId(10L);
        entity.setScopeType(ProcessModelScopeType.PLATFORM);
        entity.setRequestId("REQ-001");
        entity.setRequestTitle("测试申请");
        entity.setRequestReason("测试");
        entity.setProcessKey(processKey);
        entity.setProcessInstanceId("inst-1");
        entity.setPayloadJson("{}");
        entity.setStatus(WorkflowBusinessRequestStatus.SUBMITTED);
        return entity;
    }

    private static Authentication auth(String username) {
        return new UsernamePasswordAuthenticationToken(
            username,
            "n/a",
            List.of(new SimpleGrantedAuthority("workflow:*"))
        );
    }
}
