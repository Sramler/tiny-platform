package com.tiny.platform.application.oauth.workflow;

import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.workflow.service.PlatformWorkflowBusinessDataValidationService;
import com.tiny.platform.infrastructure.workflow.service.ProcessModelRuntimeBusinessAccessService;
import com.tiny.platform.infrastructure.workflow.service.WorkflowBusinessRequestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProcessController 单元测试：部署、列表、删除等端点委托给 ProcessEngineService，
 * 验证 200/400 及租户上下文传递。
 */
class ProcessControllerTest {

    private ProcessEngineService processEngineService;
    private BpmnValidationHelper bpmnValidationHelper;
    private ProcessModelRuntimeBusinessAccessService runtimeBusinessAccessService;
    private PlatformWorkflowBusinessDataValidationService platformWorkflowBusinessDataValidationService;
    private WorkflowBusinessRequestService workflowBusinessRequestService;
    private ProcessController controller;

    @BeforeEach
    void setUp() {
        processEngineService = mock(ProcessEngineService.class);
        bpmnValidationHelper = mock(BpmnValidationHelper.class);
        runtimeBusinessAccessService = mock(ProcessModelRuntimeBusinessAccessService.class);
        platformWorkflowBusinessDataValidationService = mock(PlatformWorkflowBusinessDataValidationService.class);
        workflowBusinessRequestService = mock(WorkflowBusinessRequestService.class);
        when(workflowBusinessRequestService.createSubmittedRequest(anyString(), any(), any(), any()))
            .thenReturn(Optional.empty());
        controller = new ProcessController();
        ReflectionTestUtils.setField(controller, "processEngineService", processEngineService);
        ReflectionTestUtils.setField(controller, "bpmnValidationHelper", bpmnValidationHelper);
        ReflectionTestUtils.setField(controller, "processModelRuntimeBusinessAccessService", runtimeBusinessAccessService);
        ReflectionTestUtils.setField(
            controller,
            "platformWorkflowBusinessDataValidationService",
            platformWorkflowBusinessDataValidationService
        );
        ReflectionTestUtils.setField(controller, "workflowBusinessRequestService", workflowBusinessRequestService);
        TenantContext.setCurrentTenant("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        com.tiny.platform.core.oauth.tenant.TenantContext.clear();
    }

    @Nested
    class Deploy {

        @Test
        void deploy_whenSuccess_returns200WithDeploymentId() throws Exception {
            when(processEngineService.deployProcess(anyString(), anyString())).thenReturn("dep-123");

            ResponseEntity<Map<String, Object>> response = controller.deploy("<bpmn/>");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("success", true);
            assertThat(response.getBody()).containsEntry("deploymentId", "dep-123");
            assertThat(response.getBody()).containsKey("message");
            verify(processEngineService).deployProcess("<bpmn/>", "tenant-1");
        }

        @Test
        void deploy_whenServiceThrows_returns400WithError() throws Exception {
            when(processEngineService.deployProcess(anyString(), anyString()))
                .thenThrow(new RuntimeException("invalid BPMN"));

            ResponseEntity<Map<String, Object>> response = controller.deploy("<bpmn/>");

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).containsEntry("success", false);
            assertThat(response.getBody()).containsEntry("error", "invalid BPMN");
        }

        @Test
        void deploy_whenPlatformScope_usesNoTenantRuntime() throws Exception {
            com.tiny.platform.core.oauth.tenant.TenantContext.setActiveScopeType(
                com.tiny.platform.core.oauth.tenant.TenantContextContract.SCOPE_TYPE_PLATFORM
            );
            when(processEngineService.deployProcess(eq("<bpmn/>"), isNull())).thenReturn("dep-platform");

            ResponseEntity<Map<String, Object>> response = controller.deploy("<bpmn/>");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("deploymentId", "dep-platform");
            verify(processEngineService).deployProcess("<bpmn/>", null);
        }
    }

    @Nested
    class DeployWithInfo {

        @Test
        void deployWithInfo_whenSuccess_returns200() throws Exception {
            Map<String, Object> request = Map.of(
                "bpmnXml", "<bpmn/>",
                "deploymentName", "My Process",
                "source", "modeler"
            );
            Principal principal = () -> "alice";
            when(processEngineService.deployProcess(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("dep-456");

            ResponseEntity<Map<String, Object>> response = controller.deployWithInfo(request, principal);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("success", true);
            assertThat(response.getBody()).containsEntry("deploymentId", "dep-456");
            assertThat(response.getBody()).containsEntry("processName", "My Process");
            assertThat(response.getBody()).containsEntry("source", "modeler");
            verify(processEngineService).deployProcess("<bpmn/>", "tenant-1", "My Process", "alice", "modeler");
        }

        @Test
        void deployWithInfo_whenPlatformScope_usesNoTenantRuntime() throws Exception {
            com.tiny.platform.core.oauth.tenant.TenantContext.setActiveScopeType(
                com.tiny.platform.core.oauth.tenant.TenantContextContract.SCOPE_TYPE_PLATFORM
            );
            Map<String, Object> request = Map.of(
                "bpmnXml", "<bpmn/>",
                "deploymentName", "Platform Process",
                "source", "platform-modeler"
            );
            Principal principal = () -> "platform-admin";
            when(processEngineService.deployProcess(anyString(), any(), anyString(), anyString(), anyString()))
                .thenReturn("dep-platform-info");

            ResponseEntity<Map<String, Object>> response = controller.deployWithInfo(request, principal);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("success", true);
            assertThat(response.getBody()).containsEntry("deploymentId", "dep-platform-info");
            verify(processEngineService).deployProcess(
                "<bpmn/>",
                null,
                "Platform Process",
                "platform-admin",
                "platform-modeler"
            );
        }

        @Test
        void deployWithInfo_whenServiceThrows_returns400() throws Exception {
            Map<String, Object> request = Map.of("bpmnXml", "<bpmn/>", "deploymentName", "X");
            when(processEngineService.deployProcess(anyString(), anyString(), anyString(), any(), any()))
                .thenThrow(new IllegalArgumentException("deploy failed"));

            ResponseEntity<Map<String, Object>> response = controller.deployWithInfo(request, null);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).containsEntry("success", false);
            assertThat(response.getBody()).containsEntry("error", "deploy failed");
        }
    }

    @Nested
    class ListDeployments {

        @Test
        void listDeployments_whenSuccess_returns200() {
            Object result = Map.of("deployments", java.util.List.of());
            when(processEngineService.listDeployments("tenant-1")).thenReturn(result);

            ResponseEntity<Object> response = controller.listDeployments("tenant-1");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo(result);
            verify(processEngineService).listDeployments("tenant-1");
        }

        @Test
        void listDeployments_whenServiceThrows_returns400() {
            when(processEngineService.listDeployments("tenant-1")).thenThrow(new RuntimeException("db error"));

            ResponseEntity<Object> response = controller.listDeployments(null);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("success", false);
            assertThat(body).containsEntry("error", "db error");
        }

        @Test
        void listDeployments_whenPlatformScope_usesNoTenantRuntime() {
            com.tiny.platform.core.oauth.tenant.TenantContext.setActiveScopeType(
                com.tiny.platform.core.oauth.tenant.TenantContextContract.SCOPE_TYPE_PLATFORM
            );
            Object result = Map.of("deployments", java.util.List.of());
            when(processEngineService.listDeployments(null)).thenReturn(result);

            ResponseEntity<Object> response = controller.listDeployments(null);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isEqualTo(result);
            verify(processEngineService).listDeployments(null);
        }

        @Test
        void listDeployments_whenPlatformScopeRejectsTenantFilter() {
            com.tiny.platform.core.oauth.tenant.TenantContext.setActiveScopeType(
                com.tiny.platform.core.oauth.tenant.TenantContextContract.SCOPE_TYPE_PLATFORM
            );

            ResponseEntity<Object> response = controller.listDeployments("9");

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("success", false);
            assertThat(body).containsEntry("error", "平台流程管理不支持按租户过滤");
        }
    }

    @Nested
    class DeleteDeployment {

        @Test
        void deleteDeployment_whenSuccess_returns200() {
            ResponseEntity<Map<String, Object>> response = controller.deleteDeployment("dep-789");

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("success", true);
            assertThat(response.getBody()).containsKey("message");
            verify(processEngineService).deleteDeployment("dep-789", true);
        }

        @Test
        void deleteDeployment_whenServiceThrows_returns400() {
            doThrow(new RuntimeException("not found"))
                .when(processEngineService).deleteDeployment(anyString(), any(Boolean.class));

            ResponseEntity<Map<String, Object>> response = controller.deleteDeployment("dep-999");

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).containsEntry("success", false);
            assertThat(response.getBody()).containsEntry("error", "not found");
        }
    }

    @Nested
    class StartProcess {

        @Test
        void start_whenSuccess_returns200WithInstanceId() {
            when(processEngineService.startProcessInstance(anyString(), anyString(), any()))
                .thenReturn("inst-1");
            Authentication authentication = auth("workflow:platform:tenant-onboarding:start");
            Map<String, Object> requestVariables = Map.of("var", "value");
            Map<String, Object> startVariables = Map.of("var", "value", "tpWorkflowStatus", "SUBMITTED");
            when(platformWorkflowBusinessDataValidationService.prepareStartVariables(
                "processKey",
                requestVariables,
                authentication
            )).thenReturn(startVariables);

            ResponseEntity<Map<String, Object>> response =
                controller.start("processKey", requestVariables, authentication);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("success", true);
            assertThat(response.getBody()).containsEntry("instanceId", "inst-1");
            verify(runtimeBusinessAccessService).assertCanStartProcess("processKey", authentication);
            verify(platformWorkflowBusinessDataValidationService)
                .prepareStartVariables("processKey", requestVariables, authentication);
            verify(workflowBusinessRequestService).createSubmittedRequest(
                "processKey",
                "tenant-1",
                startVariables,
                authentication
            );
            verify(processEngineService).startProcessInstance("processKey", "tenant-1", startVariables);
        }

        @Test
        void start_whenBusinessRequestCreated_attachesProcessInstance() {
            when(processEngineService.startProcessInstance(anyString(), anyString(), any()))
                .thenReturn("inst-1");
            Authentication authentication = auth("workflow:*");
            Map<String, Object> requestVariables = new java.util.LinkedHashMap<>();
            requestVariables.put("requestId", "REQ-001");
            requestVariables.put("requestTitle", "流程申请");
            requestVariables.put("requestReason", "测试");
            when(platformWorkflowBusinessDataValidationService.prepareStartVariables(
                "platform_tenant_plan_change",
                requestVariables,
                authentication
            )).thenReturn(requestVariables);
            when(workflowBusinessRequestService.createSubmittedRequest(
                "platform_tenant_plan_change",
                "tenant-1",
                requestVariables,
                authentication
            )).thenReturn(Optional.of(100L));

            ResponseEntity<Map<String, Object>> response =
                controller.start("platform_tenant_plan_change", requestVariables, authentication);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(requestVariables).containsEntry("tpBusinessRequestId", 100L);
            verify(processEngineService).startProcessInstance("platform_tenant_plan_change", "tenant-1", requestVariables);
            verify(workflowBusinessRequestService).attachProcessInstance(100L, "inst-1", null, authentication);
        }

        @Test
        void start_whenServiceThrows_returns400() {
            when(processEngineService.startProcessInstance(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("process key not found"));
            Authentication authentication = auth("workflow:*");
            when(platformWorkflowBusinessDataValidationService.prepareStartVariables("unknown", null, authentication))
                .thenReturn(Map.of());

            ResponseEntity<Map<String, Object>> response = controller.start("unknown", null, authentication);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).containsEntry("success", false);
            assertThat(response.getBody()).containsEntry("error", "process key not found");
        }

        @Test
        void start_whenBusinessPermissionFails_propagatesForbiddenAndDoesNotStartEngine() {
            Authentication authentication = auth("workflow:instance:control");
            doThrow(BusinessException.forbidden("缺少流程发起权限: workflow:platform:tenant-onboarding:start"))
                .when(runtimeBusinessAccessService)
                .assertCanStartProcess("platform_tenant_onboarding", authentication);

            assertThatThrownBy(() -> controller.start("platform_tenant_onboarding", null, authentication))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少流程发起权限");

            verify(processEngineService, never()).startProcessInstance(anyString(), any(), any());
            verify(platformWorkflowBusinessDataValidationService, never()).prepareStartVariables(anyString(), any(), any());
        }

        @Test
        void start_whenPlatformScope_usesNoTenantRuntime() {
            com.tiny.platform.core.oauth.tenant.TenantContext.setActiveScopeType(
                com.tiny.platform.core.oauth.tenant.TenantContextContract.SCOPE_TYPE_PLATFORM
            );
            when(processEngineService.startProcessInstance(eq("processKey"), isNull(), any()))
                .thenReturn("inst-platform");
            Authentication authentication = auth("workflow:*");
            Map<String, Object> requestVariables = Map.of("var", "value");
            when(platformWorkflowBusinessDataValidationService.prepareStartVariables(
                "processKey",
                requestVariables,
                authentication
            )).thenReturn(requestVariables);

            ResponseEntity<Map<String, Object>> response =
                controller.start("processKey", requestVariables, authentication);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("instanceId", "inst-platform");
            verify(runtimeBusinessAccessService).assertCanStartProcess("processKey", authentication);
            verify(processEngineService).startProcessInstance("processKey", null, requestVariables);
        }
    }

    @Nested
    class CompleteTask {

        @Test
        void completeTask_whenSuccess_passesValidatedVariablesToEngine() {
            Authentication authentication = auth("workflow:platform:tenant-onboarding:approve");
            WorkflowTaskContext taskContext = new WorkflowTaskContext(
                "task-1",
                "资料审核",
                "UserTask_Review",
                "inst-1",
                "platform_tenant_onboarding:1:1",
                "platform_tenant_onboarding",
                null
            );
            Map<String, Object> requestVariables = Map.of(
                "decision",
                "APPROVE",
                "comment",
                "资料完整"
            );
            Map<String, Object> completeVariables = Map.of(
                "decision",
                "APPROVE",
                "comment",
                "资料完整",
                "tpWorkflowStatus",
                "APPROVED_IN_STEP"
            );
            when(processEngineService.getTaskContext("task-1")).thenReturn(taskContext);
            when(platformWorkflowBusinessDataValidationService.prepareTaskCompleteVariables(
                taskContext,
                requestVariables,
                authentication
            )).thenReturn(completeVariables);

            ResponseEntity<Map<String, Object>> response =
                controller.completeTask("task-1", requestVariables, authentication);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("success", true);
            verify(processEngineService).getTaskContext("task-1");
            verify(platformWorkflowBusinessDataValidationService)
                .prepareTaskCompleteVariables(taskContext, requestVariables, authentication);
            verify(processEngineService).completeTask("task-1", completeVariables);
            verify(processEngineService).hasOpenTasks("inst-1");
            verify(workflowBusinessRequestService).finishTask(taskContext, completeVariables, authentication, false);
        }

        @Test
        void completeTask_whenPlatformDecisionRejected_terminatesProcessAndMarksRequestRejected() {
            Authentication authentication = auth("workflow:platform:tenant-onboarding:approve");
            WorkflowTaskContext taskContext = new WorkflowTaskContext(
                "task-1",
                "资料审核",
                "UserTask_Review",
                "inst-1",
                "platform_tenant_onboarding:1:1",
                "platform_tenant_onboarding",
                null
            );
            Map<String, Object> requestVariables = Map.of("decision", "REJECT", "comment", "资料不完整");
            Map<String, Object> completeVariables = Map.of(
                "decision",
                "REJECT",
                "comment",
                "资料不完整",
                "tpWorkflowStatus",
                "REJECTED"
            );
            when(processEngineService.getTaskContext("task-1")).thenReturn(taskContext);
            when(platformWorkflowBusinessDataValidationService.prepareTaskCompleteVariables(
                taskContext,
                requestVariables,
                authentication
            )).thenReturn(completeVariables);
            when(workflowBusinessRequestService.isRejectDecision(completeVariables)).thenReturn(true);
            when(workflowBusinessRequestService.isPlatformWorkflow("platform_tenant_onboarding")).thenReturn(true);

            ResponseEntity<Map<String, Object>> response =
                controller.completeTask("task-1", requestVariables, authentication);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("message", "任务已拒绝，流程实例已终止");
            verify(workflowBusinessRequestService).rejectTask(taskContext, completeVariables, authentication);
            verify(processEngineService).deleteInstance("inst-1");
            verify(processEngineService, never()).completeTask(anyString(), any());
        }

        @Test
        void completeTask_whenBusinessValidationFails_propagatesAndDoesNotComplete() {
            Authentication authentication = auth("workflow:platform:tenant-onboarding:approve");
            WorkflowTaskContext taskContext = new WorkflowTaskContext(
                "task-1",
                "资料审核",
                "UserTask_Review",
                "inst-1",
                "platform_tenant_onboarding:1:1",
                "platform_tenant_onboarding",
                null
            );
            Map<String, Object> requestVariables = Map.of("decision", "APPROVE");
            when(processEngineService.getTaskContext("task-1")).thenReturn(taskContext);
            when(platformWorkflowBusinessDataValidationService.prepareTaskCompleteVariables(
                taskContext,
                requestVariables,
                authentication
            )).thenThrow(BusinessException.validationError("审批任务必须提交审批意见"));

            assertThatThrownBy(() -> controller.completeTask("task-1", requestVariables, authentication))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审批任务必须提交审批意见");

            verify(processEngineService, never()).completeTask(anyString(), any());
        }
    }

    @Nested
    class CreateTenant {

        @Test
        void createTenant_whenSuccess_returnsCreatedTenantId() {
            when(processEngineService.createTenant(Map.of("id", "acme", "name", "Acme")))
                .thenReturn("acme");

            ResponseEntity<Map<String, Object>> response =
                controller.createTenant(Map.of("id", "acme", "name", "Acme"));

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("success", true);
            assertThat(response.getBody()).containsEntry("createdTenantId", "acme");
            assertThat(response.getBody()).doesNotContainKey("tenantId");
            verify(processEngineService).createTenant(Map.of("id", "acme", "name", "Acme"));
        }
    }

    private static Authentication auth(String authority) {
        return new UsernamePasswordAuthenticationToken(
            "alice",
            "n/a",
            List.of(new SimpleGrantedAuthority(authority))
        );
    }
}
