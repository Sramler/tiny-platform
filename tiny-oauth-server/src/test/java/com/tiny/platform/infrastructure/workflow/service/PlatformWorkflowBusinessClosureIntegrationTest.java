package com.tiny.platform.infrastructure.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.application.oauth.workflow.BpmnValidationHelper;
import com.tiny.platform.application.oauth.workflow.CamundaProcessEngineServiceImpl;
import com.tiny.platform.application.oauth.workflow.ProcessController;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import com.tiny.platform.infrastructure.tenant.service.TenantService;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import com.tiny.platform.infrastructure.workflow.model.WorkflowBusinessRequestEntity;
import com.tiny.platform.infrastructure.workflow.model.WorkflowBusinessRequestStatus;
import com.tiny.platform.infrastructure.workflow.repository.WorkflowBusinessRequestRepository;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlatformWorkflowBusinessClosureIntegrationTest {

    private final Map<Long, WorkflowBusinessRequestEntity> businessRequestsById = new LinkedHashMap<>();
    private final AtomicLong businessRequestIds = new AtomicLong(10_000L);

    private WorkflowBusinessRequestRepository businessRequestRepository;
    private TenantRepository tenantRepository;
    private TenantService tenantService;
    private RoleRepository roleRepository;
    private PlatformPermissionPublishService platformPermissionPublishService;
    private PlatformRoleBaselineService platformRoleBaselineService;
    private WorkflowGovernanceAssetService governanceAssetService;
    private ProcessEngine processEngine;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private ProcessController controller;

    @BeforeEach
    void setUp() {
        businessRequestRepository = mock(WorkflowBusinessRequestRepository.class);
        tenantRepository = mock(TenantRepository.class);
        tenantService = mock(TenantService.class);
        roleRepository = mock(RoleRepository.class);
        platformPermissionPublishService = mock(PlatformPermissionPublishService.class);
        platformRoleBaselineService = mock(PlatformRoleBaselineService.class);
        governanceAssetService = mock(WorkflowGovernanceAssetService.class);
        configureBusinessRequestRepository();
        when(roleRepository.findEnabledPermissionCodesByTenantIdAndCodes(nullable(Long.class), any()))
            .thenReturn(List.of());
        when(roleRepository.findEnabledRoleCodesByTenantIdAndCodes(nullable(Long.class), any()))
            .thenReturn(List.of());

        WorkflowBusinessRequestService workflowBusinessRequestService = new WorkflowBusinessRequestService(
            businessRequestRepository,
            tenantRepository,
            tenantService,
            new ObjectMapper(),
            platformPermissionPublishService,
            platformRoleBaselineService,
            governanceAssetService
        );

        StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
        configuration.setProcessEngineName("workflow-business-closure-" + System.nanoTime());
        configuration.setJdbcUrl("jdbc:h2:mem:workflow-business-closure-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000");
        configuration.setDatabaseSchemaUpdate("true");
        configuration.setJobExecutorActivate(false);
        configuration.setBeans(Map.of(
            "platformTenantProvisioningConnector",
            (JavaDelegate) execution -> workflowBusinessRequestService.applyConnector(
                execution,
                "platformTenantProvisioningConnector"
            ),
            "platformTenantPlanChangeConnector",
            (JavaDelegate) execution -> workflowBusinessRequestService.applyConnector(
                execution,
                "platformTenantPlanChangeConnector"
            ),
            "platformTenantLifecycleConnector",
            (JavaDelegate) execution -> workflowBusinessRequestService.applyConnector(
                execution,
                "platformTenantLifecycleConnector"
            ),
            "platformPermissionPublishConnector",
            (JavaDelegate) execution -> workflowBusinessRequestService.applyConnector(
                execution,
                "platformPermissionPublishConnector"
            ),
            "platformRoleBaselineConnector",
            (JavaDelegate) execution -> workflowBusinessRequestService.applyConnector(
                execution,
                "platformRoleBaselineConnector"
            ),
            "platformConnectorPublishConnector",
            (JavaDelegate) execution -> workflowBusinessRequestService.applyConnector(
                execution,
                "platformConnectorPublishConnector"
            ),
            "platformTemplatePublishConnector",
            (JavaDelegate) execution -> workflowBusinessRequestService.applyConnector(
                execution,
                "platformTemplatePublishConnector"
            ),
            "platformConfigChangeConnector",
            (JavaDelegate) execution -> workflowBusinessRequestService.applyConnector(
                execution,
                "platformConfigChangeConnector"
            )
        ));
        processEngine = configuration.buildProcessEngine();
        runtimeService = processEngine.getRuntimeService();
        taskService = processEngine.getTaskService();

        CamundaProcessEngineServiceImpl processEngineService = processEngineService(processEngine);
        PlatformWorkflowBusinessDataValidationService businessDataValidationService =
            new PlatformWorkflowBusinessDataValidationService(tenantRepository, roleRepository);

        controller = new ProcessController();
        ReflectionTestUtils.setField(controller, "processEngineService", processEngineService);
        ReflectionTestUtils.setField(controller, "bpmnValidationHelper", new BpmnValidationHelper());
        ReflectionTestUtils.setField(
            controller,
            "processModelRuntimeBusinessAccessService",
            mock(ProcessModelRuntimeBusinessAccessService.class)
        );
        ReflectionTestUtils.setField(
            controller,
            "platformWorkflowBusinessDataValidationService",
            businessDataValidationService
        );
        ReflectionTestUtils.setField(controller, "workflowBusinessRequestService", workflowBusinessRequestService);

        com.tiny.platform.core.oauth.tenant.TenantContext.setActiveScopeType(
            com.tiny.platform.core.oauth.tenant.TenantContextContract.SCOPE_TYPE_PLATFORM
        );
    }

    @AfterEach
    void tearDown() {
        com.tiny.platform.application.oauth.workflow.TenantContext.clear();
        com.tiny.platform.core.oauth.tenant.TenantContext.clear();
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @Test
    void tenantPlanChangeTemplate_runsToBusinessClosureAndUpdatesTenantPlan() throws Exception {
        repositoryService().createDeployment()
            .name("tenant-plan-change-proof")
            .addString("platform_tenant_plan_change.bpmn", platformWorkflowXml(
                "platform_tenant_plan_change",
                "租户套餐变更审批",
                "platform.tenant.plan",
                "platformTenantPlanChangeConnector",
                List.of(
                    userTask("PlanChangeIntake", "PLATFORM_PRODUCT", "forms/platform/tenant-plan-change"),
                    userTask("QuotaRiskReview", "PLATFORM_OPS", "forms/platform/quota-risk-review"),
                    serviceTask("ApplyPlanChange"),
                    userTask("PlanChangeConfirm", "PLATFORM_ADMIN", "forms/platform/tenant-plan-change-confirm")
                )
            ))
            .deploy();
        when(tenantRepository.findById(9L)).thenReturn(Optional.of(tenant(9L, "acme", "standard", "ACTIVE")));
        TenantResponseDto updatedTenant = tenantResponse(9L, "acme", "enterprise", "ACTIVE");
        when(tenantService.update(eq(9L), any(TenantCreateUpdateDto.class))).thenReturn(updatedTenant);

        String instanceId = start("platform_tenant_plan_change", Map.of(
            "requestId",
            "REQ-PLAN-001",
            "requestTitle",
            "Acme 套餐升级",
            "requestReason",
            "客户升级到企业版",
            "tenantId",
            9L,
            "targetPlanCode",
            "enterprise",
            "changeReason",
            "年度升级"
        ));

        completeOnlyOpenTask(instanceId, "申请核对通过");
        assertThat(onlyBusinessRequest().getStatus()).isEqualTo(WorkflowBusinessRequestStatus.APPROVED_IN_STEP);

        completeOnlyOpenTask(instanceId, "成本复核通过");
        WorkflowBusinessRequestEntity appliedRequest = onlyBusinessRequest();
        assertThat(appliedRequest.getStatus()).isEqualTo(WorkflowBusinessRequestStatus.APPLIED);
        assertThat(appliedRequest.getDomainResourceType()).isEqualTo("TENANT");
        assertThat(appliedRequest.getDomainResourceId()).isEqualTo("9");
        assertThat(appliedRequest.getDomainStatus()).isEqualTo("TENANT_PLAN_UPDATED");
        assertThat(appliedRequest.getResultJson()).contains("TENANT_PLAN_UPDATED", "enterprise");

        ArgumentCaptor<TenantCreateUpdateDto> updateDto = ArgumentCaptor.forClass(TenantCreateUpdateDto.class);
        verify(tenantService).update(eq(9L), updateDto.capture());
        assertThat(updateDto.getValue().getPlanCode()).isEqualTo("enterprise");

        completeOnlyOpenTask(instanceId, "变更结果确认");
        assertThat(onlyBusinessRequest().getStatus()).isEqualTo(WorkflowBusinessRequestStatus.COMPLETED);
        assertThat(onlyBusinessRequest().getCompletedAt()).isNotNull();
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count()).isZero();
    }

    @Test
    void tenantSuspendRestoreTemplate_runsToBusinessClosureAndFreezesTenant() throws Exception {
        repositoryService().createDeployment()
            .name("tenant-suspend-restore-proof")
            .addString("platform_tenant_suspend_restore.bpmn", platformWorkflowXml(
                "platform_tenant_suspend_restore",
                "租户停用恢复审批",
                "platform.tenant.lifecycle",
                "platformTenantLifecycleConnector",
                List.of(
                    userTask("SuspendRiskReview", "PLATFORM_SECURITY", "forms/platform/tenant-suspend-risk"),
                    userTask("BusinessApproval", "PLATFORM_ADMIN", "forms/platform/high-risk-approval"),
                    serviceTask("ApplySuspendRestore"),
                    userTask("NotifyAndArchive", "PLATFORM_OPS", "forms/platform/operation-archive")
                )
            ))
            .deploy();
        when(tenantRepository.findById(9L)).thenReturn(Optional.of(tenant(9L, "acme", "standard", "ACTIVE")));
        when(tenantService.freeze(9L)).thenReturn(tenantResponse(9L, "acme", "standard", "FROZEN"));

        String instanceId = start("platform_tenant_suspend_restore", Map.of(
            "requestId",
            "REQ-SUSPEND-001",
            "requestTitle",
            "Acme 风控停用",
            "requestReason",
            "触发高风险策略",
            "tenantId",
            9L,
            "lifecycleAction",
            "SUSPEND",
            "riskReason",
            "异常访问告警"
        ));

        completeOnlyOpenTask(instanceId, "风险复核通过");
        completeOnlyOpenTask(instanceId, "业务审批通过");

        WorkflowBusinessRequestEntity appliedRequest = onlyBusinessRequest();
        assertThat(appliedRequest.getStatus()).isEqualTo(WorkflowBusinessRequestStatus.APPLIED);
        assertThat(appliedRequest.getDomainResourceType()).isEqualTo("TENANT");
        assertThat(appliedRequest.getDomainStatus()).isEqualTo("TENANT_SUSPENDED");
        assertThat(appliedRequest.getResultJson()).contains("TENANT_SUSPENDED", "FROZEN");
        verify(tenantService).freeze(9L);

        completeOnlyOpenTask(instanceId, "通知归档完成");
        assertThat(onlyBusinessRequest().getStatus()).isEqualTo(WorkflowBusinessRequestStatus.COMPLETED);
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count()).isZero();
    }

    @Test
    void rejectingBeforeConnector_marksRequestRejectedAndDoesNotMutateTenant() {
        repositoryService().createDeployment()
            .name("tenant-plan-change-reject-proof")
            .addString("platform_tenant_plan_change.bpmn", platformWorkflowXml(
                "platform_tenant_plan_change",
                "租户套餐变更审批",
                "platform.tenant.plan",
                "platformTenantPlanChangeConnector",
                List.of(
                    userTask("PlanChangeIntake", "PLATFORM_PRODUCT", "forms/platform/tenant-plan-change"),
                    userTask("QuotaRiskReview", "PLATFORM_OPS", "forms/platform/quota-risk-review"),
                    serviceTask("ApplyPlanChange"),
                    userTask("PlanChangeConfirm", "PLATFORM_ADMIN", "forms/platform/tenant-plan-change-confirm")
                )
            ))
            .deploy();
        when(tenantRepository.findById(9L)).thenReturn(Optional.of(tenant(9L, "acme", "standard", "ACTIVE")));

        String instanceId = start("platform_tenant_plan_change", Map.of(
            "requestId",
            "REQ-PLAN-REJECT-001",
            "requestTitle",
            "Acme 套餐升级",
            "requestReason",
            "客户升级到企业版",
            "tenantId",
            9L,
            "targetPlanCode",
            "enterprise",
            "changeReason",
            "年度升级"
        ));

        Task task = onlyOpenTask(instanceId);
        ResponseEntity<Map<String, Object>> response = controller.completeTask(
            task.getId(),
            Map.of("decision", "REJECT", "comment", "资料不完整"),
            auth()
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(onlyBusinessRequest().getStatus()).isEqualTo(WorkflowBusinessRequestStatus.REJECTED);
        assertThat(onlyBusinessRequest().getDomainStatus()).isEqualTo("REJECTED");
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count()).isZero();
        verify(tenantService, never()).update(any(), any(TenantCreateUpdateDto.class));
        verify(tenantService, never()).freeze(any());
    }

    @Test
    void permissionPublishTemplate_runsToBusinessClosureAndPublishesPermission() throws Exception {
        repositoryService().createDeployment()
            .name("permission-publish-proof")
            .addString("platform_permission_publish.bpmn", platformWorkflowXml(
                "platform_permission_publish",
                "平台权限发布审批",
                "platform.permission.publish",
                "platformPermissionPublishConnector",
                List.of(
                    userTask("PermissionReview", "PLATFORM_SECURITY", "forms/platform/permission-review"),
                    serviceTask("ApplyPermissionPublish"),
                    userTask("PermissionPublishConfirm", "PLATFORM_ADMIN", "forms/platform/publish-confirm")
                )
            ))
            .deploy();
        when(platformPermissionPublishService.publish(any(), eq(null), eq(ProcessModelScopeType.PLATFORM), nullable(Long.class)))
            .thenReturn(Map.of(
                "permissionId",
                501L,
                "permissionCode",
                "workflow:platform:demo:start"
            ));

        String instanceId = start("platform_permission_publish", Map.of(
            "requestId",
            "REQ-PERM-001",
            "requestTitle",
            "发布演示权限",
            "requestReason",
            "新增平台流程启动权限",
            "permissionCode",
            "workflow:platform:demo:start",
            "permissionName",
            "演示流程启动",
            "changeType",
            "CREATE",
            "impactScope",
            "平台流程权限矩阵"
        ));

        completeOnlyOpenTask(instanceId, "权限评审通过");
        WorkflowBusinessRequestEntity appliedRequest = onlyBusinessRequest();
        assertThat(appliedRequest.getStatus()).isEqualTo(WorkflowBusinessRequestStatus.APPLIED);
        assertThat(appliedRequest.getDomainResourceType()).isEqualTo("PERMISSION");
        assertThat(appliedRequest.getDomainResourceId()).isEqualTo("501");
        assertThat(appliedRequest.getDomainStatus()).isEqualTo("PERMISSION_PUBLISHED");
        assertThat(appliedRequest.getResultJson()).contains("workflow:platform:demo:start", "PERMISSION_PUBLISHED");
        verify(platformPermissionPublishService).publish(any(), eq(null), eq(ProcessModelScopeType.PLATFORM), nullable(Long.class));

        completeOnlyOpenTask(instanceId, "发布结果确认");
        assertThat(onlyBusinessRequest().getStatus()).isEqualTo(WorkflowBusinessRequestStatus.COMPLETED);
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count()).isZero();
    }

    @Test
    void roleBaselineTemplate_runsToBusinessClosureAndAppliesRoleBaseline() throws Exception {
        repositoryService().createDeployment()
            .name("role-baseline-proof")
            .addString("platform_role_baseline_change.bpmn", platformWorkflowXml(
                "platform_role_baseline_change",
                "平台角色基线变更审批",
                "platform.role.baseline",
                "platformRoleBaselineConnector",
                List.of(
                    userTask("RoleBaselineReview", "PLATFORM_SECURITY", "forms/platform/role-baseline-review"),
                    serviceTask("ApplyRoleBaseline"),
                    userTask("RoleBaselineConfirm", "PLATFORM_ADMIN", "forms/platform/publish-confirm")
                )
            ))
            .deploy();
        when(platformRoleBaselineService.publish(any(), eq(null), eq(ProcessModelScopeType.PLATFORM), nullable(Long.class)))
            .thenReturn(Map.of(
                "roleId",
                601L,
                "roleCode",
                "ROLE_PLATFORM_WORKFLOW_AUDITOR"
            ));

        String instanceId = start("platform_role_baseline_change", Map.of(
            "requestId",
            "REQ-ROLE-001",
            "requestTitle",
            "发布流程审计角色",
            "requestReason",
            "补齐平台流程审计职责",
            "roleCode",
            "ROLE_PLATFORM_WORKFLOW_AUDITOR",
            "roleName",
            "平台流程审计员",
            "changeType",
            "CREATE",
            "baselineReason",
            "新增审计职责基线"
        ));

        completeOnlyOpenTask(instanceId, "角色基线评审通过");
        WorkflowBusinessRequestEntity appliedRequest = onlyBusinessRequest();
        assertThat(appliedRequest.getStatus()).isEqualTo(WorkflowBusinessRequestStatus.APPLIED);
        assertThat(appliedRequest.getDomainResourceType()).isEqualTo("ROLE");
        assertThat(appliedRequest.getDomainResourceId()).isEqualTo("601");
        assertThat(appliedRequest.getDomainStatus()).isEqualTo("ROLE_BASELINE_APPLIED");
        assertThat(appliedRequest.getResultJson()).contains("ROLE_PLATFORM_WORKFLOW_AUDITOR", "ROLE_BASELINE_APPLIED");
        verify(platformRoleBaselineService).publish(any(), eq(null), eq(ProcessModelScopeType.PLATFORM), nullable(Long.class));

        completeOnlyOpenTask(instanceId, "角色基线发布确认");
        assertThat(onlyBusinessRequest().getStatus()).isEqualTo(WorkflowBusinessRequestStatus.COMPLETED);
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count()).isZero();
    }

    @Test
    void connectorPublishTemplate_runsToBusinessClosureAndPublishesGovernanceAsset() throws Exception {
        repositoryService().createDeployment()
            .name("connector-publish-proof")
            .addString("platform_connector_publish.bpmn", platformWorkflowXml(
                "platform_connector_publish",
                "平台连接器发布审批",
                "platform.connector.publish",
                "platformConnectorPublishConnector",
                List.of(
                    userTask("ConnectorSecurityReview", "PLATFORM_SECURITY", "forms/platform/connector-security-review"),
                    serviceTask("ApplyConnectorPublish"),
                    userTask("ConnectorPublishConfirm", "PLATFORM_ADMIN", "forms/platform/publish-confirm")
                )
            ))
            .deploy();
        when(governanceAssetService.publishConnector(any(), eq(null), eq(ProcessModelScopeType.PLATFORM), nullable(Long.class)))
            .thenReturn(Map.of(
                "governanceAssetId",
                701L,
                "assetKey",
                "platformDemoConnector",
                "status",
                "PUBLISHED"
            ));

        String instanceId = start("platform_connector_publish", Map.of(
            "requestId",
            "REQ-CONNECTOR-001",
            "requestTitle",
            "发布演示连接器",
            "requestReason",
            "开放平台连接器上线",
            "connectorKey",
            "platformDemoConnector",
            "connectorName",
            "演示连接器",
            "connectorAction",
            "PUBLISH",
            "ownerTeam",
            "platform-integration",
            "securityReviewNo",
            "SEC-20260522-001"
        ));

        completeOnlyOpenTask(instanceId, "连接器安全评审通过");
        WorkflowBusinessRequestEntity appliedRequest = onlyBusinessRequest();
        assertThat(appliedRequest.getStatus()).isEqualTo(WorkflowBusinessRequestStatus.APPLIED);
        assertThat(appliedRequest.getDomainResourceType()).isEqualTo("CONNECTOR");
        assertThat(appliedRequest.getDomainResourceId()).isEqualTo("701");
        assertThat(appliedRequest.getDomainStatus()).isEqualTo("CONNECTOR_PUBLISHED");
        assertThat(appliedRequest.getResultJson()).contains("platformDemoConnector", "CONNECTOR_PUBLISHED");
        verify(governanceAssetService).publishConnector(any(), eq(null), eq(ProcessModelScopeType.PLATFORM), nullable(Long.class));

        completeOnlyOpenTask(instanceId, "连接器发布确认");
        assertThat(onlyBusinessRequest().getStatus()).isEqualTo(WorkflowBusinessRequestStatus.COMPLETED);
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count()).isZero();
    }

    @Test
    void templatePublishTemplate_runsToBusinessClosureAndPublishesGovernanceAsset() throws Exception {
        repositoryService().createDeployment()
            .name("template-publish-proof")
            .addString("platform_template_publish.bpmn", platformWorkflowXml(
                "platform_template_publish",
                "平台模板发布审批",
                "platform.template.publish",
                "platformTemplatePublishConnector",
                List.of(
                    userTask("TemplateReview", "PLATFORM_PRODUCT", "forms/platform/template-review"),
                    serviceTask("ApplyTemplatePublish"),
                    userTask("TemplatePublishConfirm", "PLATFORM_ADMIN", "forms/platform/publish-confirm")
                )
            ))
            .deploy();
        when(governanceAssetService.publishTemplate(any(), eq(null), eq(ProcessModelScopeType.PLATFORM), nullable(Long.class)))
            .thenReturn(Map.of(
                "governanceAssetId",
                801L,
                "assetKey",
                "platform_demo_template",
                "status",
                "PUBLISHED"
            ));

        String instanceId = start("platform_template_publish", Map.of(
            "requestId",
            "REQ-TEMPLATE-001",
            "requestTitle",
            "发布演示流程模板",
            "requestReason",
            "模板市场上线",
            "templateKey",
            "platform_demo_template",
            "templateName",
            "演示流程模板",
            "templateVersion",
            "1.0.0",
            "publishAction",
            "PUBLISH"
        ));

        completeOnlyOpenTask(instanceId, "模板评审通过");
        WorkflowBusinessRequestEntity appliedRequest = onlyBusinessRequest();
        assertThat(appliedRequest.getStatus()).isEqualTo(WorkflowBusinessRequestStatus.APPLIED);
        assertThat(appliedRequest.getDomainResourceType()).isEqualTo("TEMPLATE");
        assertThat(appliedRequest.getDomainResourceId()).isEqualTo("801");
        assertThat(appliedRequest.getDomainStatus()).isEqualTo("TEMPLATE_PUBLISHED");
        assertThat(appliedRequest.getResultJson()).contains("platform_demo_template", "TEMPLATE_PUBLISHED");
        verify(governanceAssetService).publishTemplate(any(), eq(null), eq(ProcessModelScopeType.PLATFORM), nullable(Long.class));

        completeOnlyOpenTask(instanceId, "模板发布确认");
        assertThat(onlyBusinessRequest().getStatus()).isEqualTo(WorkflowBusinessRequestStatus.COMPLETED);
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count()).isZero();
    }

    @Test
    void configChangeTemplate_runsToBusinessClosureAndPublishesGovernanceAsset() throws Exception {
        repositoryService().createDeployment()
            .name("config-change-proof")
            .addString("platform_config_change.bpmn", platformWorkflowXml(
                "platform_config_change",
                "平台配置变更审批",
                "platform.config.change",
                "platformConfigChangeConnector",
                List.of(
                    userTask("ConfigRiskReview", "PLATFORM_SECURITY", "forms/platform/config-risk-review"),
                    serviceTask("ApplyConfigChange"),
                    userTask("ConfigChangeConfirm", "PLATFORM_ADMIN", "forms/platform/publish-confirm")
                )
            ))
            .deploy();
        when(governanceAssetService.publishConfig(any(), eq(null), eq(ProcessModelScopeType.PLATFORM), nullable(Long.class)))
            .thenReturn(Map.of(
                "governanceAssetId",
                901L,
                "assetKey",
                "workflow.approval.window",
                "status",
                "APPLIED"
            ));

        String instanceId = start("platform_config_change", Map.of(
            "requestId",
            "REQ-CONFIG-001",
            "requestTitle",
            "调整审批超时窗口",
            "requestReason",
            "统一平台审批 SLA",
            "configKey",
            "workflow.approval.window",
            "configName",
            "审批超时窗口",
            "changeType",
            "UPDATE",
            "rollbackPlan",
            "恢复到上一版本配置"
        ));

        completeOnlyOpenTask(instanceId, "配置风险评审通过");
        WorkflowBusinessRequestEntity appliedRequest = onlyBusinessRequest();
        assertThat(appliedRequest.getStatus()).isEqualTo(WorkflowBusinessRequestStatus.APPLIED);
        assertThat(appliedRequest.getDomainResourceType()).isEqualTo("CONFIG");
        assertThat(appliedRequest.getDomainResourceId()).isEqualTo("901");
        assertThat(appliedRequest.getDomainStatus()).isEqualTo("CONFIG_APPLIED");
        assertThat(appliedRequest.getResultJson()).contains("workflow.approval.window", "CONFIG_APPLIED");
        verify(governanceAssetService).publishConfig(any(), eq(null), eq(ProcessModelScopeType.PLATFORM), nullable(Long.class));

        completeOnlyOpenTask(instanceId, "配置变更确认");
        assertThat(onlyBusinessRequest().getStatus()).isEqualTo(WorkflowBusinessRequestStatus.COMPLETED);
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count()).isZero();
    }

    private void configureBusinessRequestRepository() {
        when(businessRequestRepository.save(any(WorkflowBusinessRequestEntity.class))).thenAnswer(invocation -> {
            WorkflowBusinessRequestEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(businessRequestIds.incrementAndGet());
            }
            businessRequestsById.put(entity.getId(), entity);
            return entity;
        });
        when(businessRequestRepository.findById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return Optional.ofNullable(businessRequestsById.get(id));
        });
        when(businessRequestRepository.findByProcessInstanceId(anyString())).thenAnswer(invocation -> {
            String processInstanceId = invocation.getArgument(0);
            return businessRequestsById.values().stream()
                .filter(request -> processInstanceId.equals(request.getProcessInstanceId()))
                .findFirst();
        });
        when(businessRequestRepository.findByScopeAndRequestId(
            any(ProcessModelScopeType.class),
            nullable(Long.class),
            anyString()
        )).thenAnswer(invocation -> {
            ProcessModelScopeType scopeType = invocation.getArgument(0);
            Long tenantId = invocation.getArgument(1);
            String requestId = invocation.getArgument(2);
            return businessRequestsById.values().stream()
                .filter(request -> request.getScopeType() == scopeType)
                .filter(request -> (tenantId == null && request.getTenantId() == null)
                    || (tenantId != null && tenantId.equals(request.getTenantId())))
                .filter(request -> requestId.equals(request.getRequestId()))
                .findFirst();
        });
    }

    private String start(String processKey, Map<String, Object> variables) {
        ResponseEntity<Map<String, Object>> response = controller.start(processKey, variables, auth());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsKey("instanceId");
        return String.valueOf(response.getBody().get("instanceId"));
    }

    private void completeOnlyOpenTask(String instanceId, String comment) {
        Task task = onlyOpenTask(instanceId);
        ResponseEntity<Map<String, Object>> response = controller.completeTask(
            task.getId(),
            Map.of("decision", "APPROVE", "comment", comment),
            auth()
        );
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).containsEntry("success", true);
    }

    private Task onlyOpenTask(String instanceId) {
        List<Task> tasks = taskService.createTaskQuery()
            .processInstanceId(instanceId)
            .list();
        assertThat(tasks).hasSize(1);
        return tasks.get(0);
    }

    private WorkflowBusinessRequestEntity onlyBusinessRequest() {
        assertThat(businessRequestsById).hasSize(1);
        return businessRequestsById.values().iterator().next();
    }

    private RepositoryService repositoryService() {
        return processEngine.getRepositoryService();
    }

    private CamundaProcessEngineServiceImpl processEngineService(ProcessEngine engine) {
        CamundaProcessEngineServiceImpl service = new CamundaProcessEngineServiceImpl();
        ReflectionTestUtils.setField(service, "repositoryService", engine.getRepositoryService());
        ReflectionTestUtils.setField(service, "runtimeService", engine.getRuntimeService());
        ReflectionTestUtils.setField(service, "taskService", engine.getTaskService());
        ReflectionTestUtils.setField(service, "historyService", engine.getHistoryService());
        ReflectionTestUtils.setField(service, "managementService", engine.getManagementService());
        ReflectionTestUtils.setField(service, "identityService", engine.getIdentityService());
        ReflectionTestUtils.setField(service, "bpmnValidationHelper", new BpmnValidationHelper());
        return service;
    }

    private static Authentication auth() {
        return new UsernamePasswordAuthenticationToken(
            "workflow-business-closure",
            "n/a",
            List.of(new SimpleGrantedAuthority("workflow:*"))
        );
    }

    private static Tenant tenant(Long id, String code, String planCode, String lifecycleStatus) {
        Tenant tenant = new Tenant();
        tenant.setId(id);
        tenant.setCode(code);
        tenant.setName(code);
        tenant.setPlanCode(planCode);
        tenant.setLifecycleStatus(lifecycleStatus);
        tenant.setEnabled(true);
        return tenant;
    }

    private static TenantResponseDto tenantResponse(Long id, String code, String planCode, String lifecycleStatus) {
        TenantResponseDto response = new TenantResponseDto();
        response.setId(id);
        response.setCode(code);
        response.setName(code);
        response.setPlanCode(planCode);
        response.setLifecycleStatus(lifecycleStatus);
        response.setEnabled(true);
        return response;
    }

    private static WorkflowNode userTask(String id, String candidateGroup, String formKey) {
        return new WorkflowNode("userTask", id, candidateGroup, formKey);
    }

    private static WorkflowNode serviceTask(String id) {
        return new WorkflowNode("serviceTask", id, null, null);
    }

    private static String platformWorkflowXml(
        String processKey,
        String processName,
        String businessModule,
        String connectorKey,
        List<WorkflowNode> nodes
    ) {
        String segment = processKey.replaceFirst("^platform_", "").replace('_', '-');
        StringBuilder taskXml = new StringBuilder();
        StringBuilder flowXml = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            WorkflowNode node = nodes.get(i);
            int sequence = i + 1;
            String elementId = node.elementId(sequence);
            String incoming = "Flow_" + sequence;
            String outgoing = "Flow_" + (sequence + 1);
            if ("serviceTask".equals(node.type())) {
                taskXml.append("""
                        <bpmn:serviceTask id="%s" name="%s" camunda:delegateExpression="${%s}">
                          <bpmn:incoming>%s</bpmn:incoming>
                          <bpmn:outgoing>%s</bpmn:outgoing>
                        </bpmn:serviceTask>
                    """.formatted(elementId, node.id(), connectorKey, incoming, outgoing));
            } else {
                taskXml.append("""
                        <bpmn:userTask id="%s" name="%s" camunda:candidateGroups="%s" camunda:formKey="%s">
                          <bpmn:incoming>%s</bpmn:incoming>
                          <bpmn:outgoing>%s</bpmn:outgoing>
                        </bpmn:userTask>
                    """.formatted(elementId, node.id(), node.candidateGroup(), node.formKey(), incoming, outgoing));
            }
            String sourceRef = sequence == 1 ? "StartEvent_1" : nodes.get(i - 1).elementId(sequence - 1);
            String targetRef = sequence == nodes.size() ? elementId : elementId;
            flowXml.append("""
                    <bpmn:sequenceFlow id="%s" sourceRef="%s" targetRef="%s"/>
                """.formatted(incoming, sourceRef, targetRef));
        }
        flowXml.append("""
                <bpmn:sequenceFlow id="Flow_%d" sourceRef="%s" targetRef="EndEvent_1"/>
            """.formatted(nodes.size() + 1, nodes.get(nodes.size() - 1).elementId(nodes.size())));

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                              id="Definitions_%s"
                              targetNamespace="http://tiny-platform/workflow/templates/platform">
              <bpmn:process id="%s" name="%s" isExecutable="true" camunda:historyTimeToLive="180">
                <bpmn:extensionElements>
                  <camunda:properties>
                    <camunda:property name="tp:businessModule" value="%s"/>
                    <camunda:property name="tp:startPermission" value="workflow:platform:%s:start"/>
                    <camunda:property name="tp:approvePermission" value="workflow:platform:%s:approve"/>
                    <camunda:property name="tp:managePermission" value="workflow:platform:%s:manage"/>
                    <camunda:property name="tp:roleCodes" value="ROLE_PLATFORM_PRODUCT,ROLE_PLATFORM_OPS,ROLE_PLATFORM_SECURITY,ROLE_PLATFORM_ADMIN"/>
                  </camunda:properties>
                </bpmn:extensionElements>
                <bpmn:startEvent id="StartEvent_1" name="提交申请">
                  <bpmn:outgoing>Flow_1</bpmn:outgoing>
                </bpmn:startEvent>
            %s
                <bpmn:endEvent id="EndEvent_1" name="流程完成">
                  <bpmn:incoming>Flow_%d</bpmn:incoming>
                </bpmn:endEvent>
            %s
              </bpmn:process>
            </bpmn:definitions>
            """.formatted(
            processKey,
            processKey,
            processName,
            businessModule,
            segment,
            segment,
            segment,
            taskXml,
            nodes.size() + 1,
            flowXml
        );
    }

    private record WorkflowNode(String type, String id, String candidateGroup, String formKey) {
        private String elementId(int sequence) {
            return ("serviceTask".equals(type) ? "ServiceTask_" : "UserTask_") + sequence + "_" + id;
        }
    }
}
