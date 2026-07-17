package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.application.oauth.workflow.BpmnValidationHelper;
import com.tiny.platform.application.oauth.workflow.CamundaProcessEngineServiceImpl;
import com.tiny.platform.application.oauth.workflow.WorkflowTaskContext;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelDeployResponse;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelDto;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelRequests;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelValidationResponse;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelEntity;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelStatus;
import com.tiny.platform.infrastructure.workflow.repository.ProcessModelRepository;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.camunda.bpm.engine.task.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessModelTemplateRuntimeIntegrationTest {

    private static final String TEMPLATE_KEY = "platform_tenant_onboarding";
    private static final String START_PERMISSION = "workflow:platform:tenant-onboarding:start";

    private ProcessEngine processEngine;
    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private CamundaProcessEngineServiceImpl processEngineService;
    private ProcessModelService processModelService;
    private ProcessModelRuntimeBusinessAccessService runtimeBusinessAccessService;
    private PlatformWorkflowBusinessDataValidationService businessDataValidationService;

    @BeforeEach
    void setUp() {
        processEngine = new StandaloneInMemProcessEngineConfiguration()
            .setProcessEngineName("template-runtime-" + System.nanoTime())
            .setJdbcUrl("jdbc:h2:mem:template-runtime-" + System.nanoTime() + ";DB_CLOSE_DELAY=1000")
            .setDatabaseSchemaUpdate("true")
            .setJobExecutorActivate(false)
            .buildProcessEngine();
        repositoryService = processEngine.getRepositoryService();
        runtimeService = processEngine.getRuntimeService();
        taskService = processEngine.getTaskService();

        BpmnValidationHelper bpmnValidationHelper = new BpmnValidationHelper();
        processEngineService = processEngineService(bpmnValidationHelper);
        ProcessModelRepository processModelRepository = processModelRepository();
        ProcessModelBusinessValidationService businessValidationService = businessValidationService();
        processModelService = new ProcessModelService(
            processModelRepository,
            bpmnValidationHelper,
            businessValidationService,
            processEngineService
        );
        runtimeBusinessAccessService = new ProcessModelRuntimeBusinessAccessService(
            processModelRepository,
            businessValidationService
        );
        businessDataValidationService = businessDataValidationService();

        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (processEngine != null) {
            processEngine.close();
        }
    }

    @Test
    void platformTemplateDraft_canValidateDeployAndStartWithBusinessPermissionGate() {
        ProcessModelDto draft = processModelService.create(
            new ProcessModelRequests.Create(
                TEMPLATE_KEY,
                "租户开通审批",
                "平台侧租户开通模板运行态闭环验证",
                1,
                platformTenantOnboardingBpmnXml(),
                null
            ),
            "platform-admin"
        );

        ProcessModelValidationResponse validation = processModelService.validate(draft.id(), "platform-admin");
        assertThat(validation.valid()).isTrue();
        assertThat(validation.warnings()).contains(
            "表单注册中心尚未接入当前校验链，本次仅校验 formKey 非空与格式",
            "连接器注册中心尚未接入当前校验链，本次仅校验 delegateExpression 格式"
        );

        ProcessModelDeployResponse deployment = processModelService.deploy(draft.id(), "platform-admin");
        assertThat(deployment.deploymentId()).isNotBlank();
        assertThat(deployment.processDefinitionKey()).isEqualTo(TEMPLATE_KEY);
        assertThat(repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey(TEMPLATE_KEY)
            .withoutTenantId()
            .count()).isEqualTo(1);

        assertThatThrownBy(() -> runtimeBusinessAccessService.assertCanStartProcess(
            TEMPLATE_KEY,
            auth("workflow:instance:control")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("缺少流程发起权限: " + START_PERMISSION);
        assertThat(runtimeService.createProcessInstanceQuery().processDefinitionKey(TEMPLATE_KEY).count())
            .isZero();

        runtimeBusinessAccessService.assertCanStartProcess(TEMPLATE_KEY, auth(START_PERMISSION));
        Map<String, Object> startVariables = businessDataValidationService.prepareStartVariables(
            TEMPLATE_KEY,
            Map.of(
                "requestId",
                "REQ-001",
                "requestTitle",
                "租户开通",
                "requestReason",
                "测试开通",
                "tenantCode",
                "acme",
                "tenantName",
                "Acme",
                "planCode",
                "standard"
            ),
            auth(START_PERMISSION)
        );
        String instanceId = processEngineService.startProcessInstance(
            TEMPLATE_KEY,
            null,
            startVariables
        );

        assertThat(instanceId).isNotBlank();
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).count())
            .isEqualTo(1);
        assertThat(runtimeService.getVariable(instanceId, "tpWorkflowStatus")).isEqualTo("SUBMITTED");
        assertThat(runtimeService.getVariable(instanceId, "tpStartedBy")).isEqualTo("workflow-runtime-smoke");
        assertThat(taskService.createTaskQuery()
            .processInstanceId(instanceId)
            .taskCandidateGroup("PLATFORM_PRODUCT")
            .count()).isEqualTo(1);

        Task firstTask = taskService.createTaskQuery()
            .processInstanceId(instanceId)
            .taskCandidateGroup("PLATFORM_PRODUCT")
            .singleResult();
        WorkflowTaskContext taskContext = processEngineService.getTaskContext(firstTask.getId());
        Map<String, Object> completeVariables = businessDataValidationService.prepareTaskCompleteVariables(
            taskContext,
            Map.of("decision", "APPROVE", "comment", "资料审核通过"),
            auth("workflow:platform:tenant-onboarding:approve")
        );
        processEngineService.completeTask(firstTask.getId(), completeVariables);

        assertThat(runtimeService.getVariable(instanceId, "tpWorkflowStatus")).isEqualTo("APPROVED_IN_STEP");
        assertThat(runtimeService.getVariable(instanceId, "tpLastDecision")).isEqualTo("APPROVE");
        assertThat(runtimeService.getVariable(instanceId, "tpLastTaskKey"))
            .isEqualTo("UserTask_1_TenantRequestReview");
        assertThat(taskService.createTaskQuery()
            .processInstanceId(instanceId)
            .taskCandidateGroup("PLATFORM_ADMIN")
            .count()).isEqualTo(1);
    }

    private ProcessModelRepository processModelRepository() {
        ProcessModelRepository repository = mock(ProcessModelRepository.class);
        AtomicLong ids = new AtomicLong(10_000L);
        AtomicReference<ProcessModelEntity> storedModel = new AtomicReference<>();

        when(repository.findMaxVersionInScope(ProcessModelScopeType.PLATFORM, null, TEMPLATE_KEY))
            .thenReturn(null);
        when(repository.existsByScopeAndModelKeyAndVersion(ProcessModelScopeType.PLATFORM, null, TEMPLATE_KEY, 1))
            .thenReturn(false);
        when(repository.save(any(ProcessModelEntity.class))).thenAnswer(invocation -> {
            ProcessModelEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(ids.incrementAndGet());
            }
            LocalDateTime now = LocalDateTime.now();
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(now);
            }
            entity.setUpdatedAt(now);
            if (entity.getLockVersion() == null) {
                entity.setLockVersion(0L);
            }
            storedModel.set(entity);
            return entity;
        });
        when(repository.findByIdInScope(anyLong(), eq(ProcessModelScopeType.PLATFORM), isNull()))
            .thenAnswer(invocation -> Optional.ofNullable(storedModel.get())
                .filter(model -> model.getId().equals(invocation.getArgument(0))));
        when(repository.findRuntimeCandidatesInScope(
            eq(ProcessModelScopeType.PLATFORM),
            isNull(),
            eq(TEMPLATE_KEY),
            eq(ProcessModelStatus.DEPLOYED)
        )).thenAnswer(invocation -> {
            ProcessModelEntity entity = storedModel.get();
            if (entity == null || entity.getDeploymentId() == null) {
                return List.of();
            }
            return List.of(entity);
        });
        return repository;
    }

    private ProcessModelBusinessValidationService businessValidationService() {
        RoleRepository roleRepository = mock(RoleRepository.class);
        when(roleRepository.findEnabledPermissionCodesByTenantIdAndCodes(isNull(), anyList()))
            .thenAnswer(invocation -> List.copyOf(invocation.getArgument(1)));
        when(roleRepository.findEnabledRoleCodesByTenantIdAndCodes(isNull(), anyList()))
            .thenAnswer(invocation -> List.copyOf(invocation.getArgument(1)));
        return new ProcessModelBusinessValidationService(roleRepository);
    }

    private PlatformWorkflowBusinessDataValidationService businessDataValidationService() {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        when(tenantRepository.findByCode("acme")).thenReturn(Optional.empty());
        return new PlatformWorkflowBusinessDataValidationService(tenantRepository, roleRepository);
    }

    private CamundaProcessEngineServiceImpl processEngineService(BpmnValidationHelper bpmnValidationHelper) {
        CamundaProcessEngineServiceImpl service = new CamundaProcessEngineServiceImpl();
        ReflectionTestUtils.setField(service, "repositoryService", repositoryService);
        ReflectionTestUtils.setField(service, "runtimeService", runtimeService);
        ReflectionTestUtils.setField(service, "taskService", taskService);
        ReflectionTestUtils.setField(service, "historyService", processEngine.getHistoryService());
        ReflectionTestUtils.setField(service, "managementService", processEngine.getManagementService());
        ReflectionTestUtils.setField(service, "identityService", processEngine.getIdentityService());
        ReflectionTestUtils.setField(service, "bpmnValidationHelper", bpmnValidationHelper);
        return service;
    }

    private static Authentication auth(String authority) {
        return new UsernamePasswordAuthenticationToken(
            "workflow-runtime-smoke",
            "n/a",
            List.of(new SimpleGrantedAuthority(authority))
        );
    }

    private static String platformTenantOnboardingBpmnXml() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                              xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                              xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                              xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                              id="Definitions_platform_tenant_onboarding"
                              targetNamespace="http://tiny-platform/workflow/templates/platform"
                              exporter="Tiny Platform"
                              exporterVersion="1.6">
              <bpmn:process id="platform_tenant_onboarding" name="租户开通审批" isExecutable="true" camunda:historyTimeToLive="180">
                <bpmn:documentation>新租户入驻、套餐确认、资源初始化与平台运营复核。</bpmn:documentation>
                <bpmn:extensionElements>
                  <camunda:properties>
                    <camunda:property name="tp:businessModule" value="platform.tenant.lifecycle"/>
                    <camunda:property name="tp:startPermission" value="workflow:platform:tenant-onboarding:start"/>
                    <camunda:property name="tp:approvePermission" value="workflow:platform:tenant-onboarding:approve"/>
                    <camunda:property name="tp:managePermission" value="workflow:platform:tenant-onboarding:manage"/>
                    <camunda:property name="tp:roleCodes" value="ROLE_PLATFORM_PRODUCT,ROLE_PLATFORM_ADMIN,ROLE_PLATFORM_OPS"/>
                  </camunda:properties>
                </bpmn:extensionElements>
                <bpmn:startEvent id="StartEvent_1" name="提交申请">
                  <bpmn:outgoing>Flow_1</bpmn:outgoing>
                </bpmn:startEvent>
                <bpmn:userTask id="UserTask_1_TenantRequestReview" name="入驻资料审核" camunda:candidateGroups="PLATFORM_PRODUCT" camunda:formKey="forms/platform/tenant-onboarding-review">
                  <bpmn:documentation>入驻资料审核，请按平台治理要求补充审批意见。</bpmn:documentation>
                  <bpmn:incoming>Flow_1</bpmn:incoming>
                  <bpmn:outgoing>Flow_2</bpmn:outgoing>
                </bpmn:userTask>
                <bpmn:userTask id="UserTask_2_TenantPlanApprove" name="套餐与额度审批" camunda:candidateGroups="PLATFORM_ADMIN" camunda:formKey="forms/platform/tenant-plan-approval">
                  <bpmn:documentation>套餐与额度审批，请按平台治理要求补充审批意见。</bpmn:documentation>
                  <bpmn:incoming>Flow_2</bpmn:incoming>
                  <bpmn:outgoing>Flow_3</bpmn:outgoing>
                </bpmn:userTask>
                <bpmn:serviceTask id="ServiceTask_3_TenantProvisioning" name="初始化租户资源" camunda:delegateExpression="${platformTenantProvisioningConnector}">
                  <bpmn:documentation>初始化租户资源。正式部署前必须确认连接器已在当前 scope 白名单内启用。</bpmn:documentation>
                  <bpmn:incoming>Flow_3</bpmn:incoming>
                  <bpmn:outgoing>Flow_4</bpmn:outgoing>
                </bpmn:serviceTask>
                <bpmn:userTask id="UserTask_4_TenantActivationReview" name="开通结果复核" camunda:candidateGroups="PLATFORM_OPS" camunda:formKey="forms/platform/tenant-activation-review">
                  <bpmn:documentation>开通结果复核，请按平台治理要求补充审批意见。</bpmn:documentation>
                  <bpmn:incoming>Flow_4</bpmn:incoming>
                  <bpmn:outgoing>Flow_5</bpmn:outgoing>
                </bpmn:userTask>
                <bpmn:endEvent id="EndEvent_1" name="流程完成">
                  <bpmn:incoming>Flow_5</bpmn:incoming>
                </bpmn:endEvent>
                <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="UserTask_1_TenantRequestReview"/>
                <bpmn:sequenceFlow id="Flow_2" sourceRef="UserTask_1_TenantRequestReview" targetRef="UserTask_2_TenantPlanApprove"/>
                <bpmn:sequenceFlow id="Flow_3" sourceRef="UserTask_2_TenantPlanApprove" targetRef="ServiceTask_3_TenantProvisioning"/>
                <bpmn:sequenceFlow id="Flow_4" sourceRef="ServiceTask_3_TenantProvisioning" targetRef="UserTask_4_TenantActivationReview"/>
                <bpmn:sequenceFlow id="Flow_5" sourceRef="UserTask_4_TenantActivationReview" targetRef="EndEvent_1"/>
              </bpmn:process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="platform_tenant_onboarding">
                  <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
                    <dc:Bounds x="152" y="102" width="36" height="36"/>
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNShape id="UserTask_1_TenantRequestReview_di" bpmnElement="UserTask_1_TenantRequestReview">
                    <dc:Bounds x="240" y="80" width="118" height="80"/>
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNShape id="UserTask_2_TenantPlanApprove_di" bpmnElement="UserTask_2_TenantPlanApprove">
                    <dc:Bounds x="410" y="80" width="118" height="80"/>
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNShape id="ServiceTask_3_TenantProvisioning_di" bpmnElement="ServiceTask_3_TenantProvisioning">
                    <dc:Bounds x="580" y="80" width="118" height="80"/>
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNShape id="UserTask_4_TenantActivationReview_di" bpmnElement="UserTask_4_TenantActivationReview">
                    <dc:Bounds x="750" y="80" width="118" height="80"/>
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
                    <dc:Bounds x="960" y="102" width="36" height="36"/>
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
                    <di:waypoint x="188" y="120"/>
                    <di:waypoint x="240" y="120"/>
                  </bpmndi:BPMNEdge>
                  <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2">
                    <di:waypoint x="358" y="120"/>
                    <di:waypoint x="410" y="120"/>
                  </bpmndi:BPMNEdge>
                  <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3">
                    <di:waypoint x="528" y="120"/>
                    <di:waypoint x="580" y="120"/>
                  </bpmndi:BPMNEdge>
                  <bpmndi:BPMNEdge id="Flow_4_di" bpmnElement="Flow_4">
                    <di:waypoint x="698" y="120"/>
                    <di:waypoint x="750" y="120"/>
                  </bpmndi:BPMNEdge>
                  <bpmndi:BPMNEdge id="Flow_5_di" bpmnElement="Flow_5">
                    <di:waypoint x="868" y="120"/>
                    <di:waypoint x="960" y="120"/>
                  </bpmndi:BPMNEdge>
                </bpmndi:BPMNPlane>
              </bpmndi:BPMNDiagram>
            </bpmn:definitions>
            """;
    }
}
