package com.tiny.platform.application.oauth.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ProcessController 单元测试：部署、列表、删除等端点委托给 ProcessEngineService，
 * 验证 200/400 及租户上下文传递。
 */
class ProcessControllerTest {

    private ProcessEngineService processEngineService;
    private BpmnValidationHelper bpmnValidationHelper;
    private ProcessController controller;

    @BeforeEach
    void setUp() {
        processEngineService = mock(ProcessEngineService.class);
        bpmnValidationHelper = mock(BpmnValidationHelper.class);
        controller = new ProcessController();
        ReflectionTestUtils.setField(controller, "processEngineService", processEngineService);
        ReflectionTestUtils.setField(controller, "bpmnValidationHelper", bpmnValidationHelper);
        TenantContext.setCurrentTenant("tenant-1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
            assertThat(response.getBody()).containsEntry("deploymentId", "dep-456");
            assertThat(response.getBody()).containsEntry("processName", "My Process");
            assertThat(response.getBody()).containsEntry("source", "modeler");
            verify(processEngineService).deployProcess("<bpmn/>", "tenant-1", "My Process", "alice", "modeler");
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
            when(processEngineService.listDeployments(null)).thenThrow(new RuntimeException("db error"));

            ResponseEntity<Object> response = controller.listDeployments(null);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("success", false);
            assertThat(body).containsEntry("error", "db error");
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

            ResponseEntity<Map<String, Object>> response =
                controller.start("processKey", Map.of("var", "value"));

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).containsEntry("success", true);
            assertThat(response.getBody()).containsEntry("instanceId", "inst-1");
            verify(processEngineService).startProcessInstance("processKey", "tenant-1", Map.of("var", "value"));
        }

        @Test
        void start_whenServiceThrows_returns400() {
            when(processEngineService.startProcessInstance(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("process key not found"));

            ResponseEntity<Map<String, Object>> response = controller.start("unknown", null);

            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(response.getBody()).containsEntry("success", false);
            assertThat(response.getBody()).containsEntry("error", "process key not found");
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
}
