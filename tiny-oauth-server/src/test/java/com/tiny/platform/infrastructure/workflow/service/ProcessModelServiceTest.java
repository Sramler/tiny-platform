package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.application.oauth.workflow.BpmnValidationHelper;
import com.tiny.platform.application.oauth.workflow.ProcessEngineService;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelDeployResponse;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelDto;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelRequests;
import com.tiny.platform.application.oauth.workflow.model.ProcessModelValidationResponse;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelEntity;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelStatus;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelValidationStatus;
import com.tiny.platform.infrastructure.workflow.repository.ProcessModelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessModelServiceTest {

    private static final String BPMN_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
          <bpmn:process id="leave_process" name="Leave Process" isExecutable="true" />
        </bpmn:definitions>
        """;

    @Mock
    private ProcessModelRepository repository;

    @Mock
    private BpmnValidationHelper bpmnValidationHelper;

    @Mock
    private ProcessModelBusinessValidationService businessValidationService;

    @Mock
    private ProcessEngineService processEngineService;

    private ProcessModelService service;

    @BeforeEach
    void setUp() {
        service = new ProcessModelService(repository, bpmnValidationHelper, businessValidationService, processEngineService);
        lenient().when(repository.save(any(ProcessModelEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(businessValidationService.validate(any(), any(), any()))
            .thenReturn(ProcessModelBusinessValidationService.BusinessValidationResult.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_whenPlatformScope_savesPlatformModelWithoutTenant() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        when(repository.findMaxVersionInScope(ProcessModelScopeType.PLATFORM, null, "leave_process"))
            .thenReturn(null);

        ProcessModelDto dto = service.create(
            new ProcessModelRequests.Create(null, null, "desc", null, BPMN_XML, "<svg/>"),
            "alice"
        );

        assertThat(dto.modelKey()).isEqualTo("leave_process");
        assertThat(dto.name()).isEqualTo("Leave Process");
        assertThat(dto.scopeType()).isEqualTo("PLATFORM");
        assertThat(dto.recordTenantId()).isNull();
        assertThat(dto.status()).isEqualTo("DRAFT");
        assertThat(dto.validationStatus()).isEqualTo("NOT_VALIDATED");
        verify(repository).existsByScopeAndModelKeyAndVersion(ProcessModelScopeType.PLATFORM, null, "leave_process", 1);
    }

    @Test
    void create_whenTenantScope_savesTenantModel() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        TenantContext.setActiveTenantId(7L);
        when(repository.findMaxVersionInScope(ProcessModelScopeType.TENANT, 7L, "leave_process"))
            .thenReturn(2);

        ProcessModelDto dto = service.create(
            new ProcessModelRequests.Create(null, "Custom Name", null, null, BPMN_XML, null),
            "bob"
        );

        assertThat(dto.scopeType()).isEqualTo("TENANT");
        assertThat(dto.recordTenantId()).isEqualTo(7L);
        assertThat(dto.version()).isEqualTo(3);
        verify(repository).existsByScopeAndModelKeyAndVersion(ProcessModelScopeType.TENANT, 7L, "leave_process", 3);
    }

    @Test
    void create_whenTenantScopeWithoutTenant_rejectsRequest() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        assertThatThrownBy(() -> service.create(
            new ProcessModelRequests.Create(null, null, null, null, BPMN_XML, null),
            "bob"
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("当前请求未解析到有效租户上下文");
    }

    @Test
    void listGroupsCurrentScope_groupsVersionsByModelKey() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        TenantContext.setActiveTenantId(9L);
        ProcessModelEntity leaveV1 = draftEntity(ProcessModelScopeType.TENANT, 9L);
        leaveV1.setId(10L);
        leaveV1.setVersion(1);
        leaveV1.setStatus(ProcessModelStatus.DEPLOYED);
        leaveV1.setDeploymentId("dep-old");
        leaveV1.setDeployedAt(LocalDateTime.parse("2026-05-09T10:00:00"));
        leaveV1.setUpdatedAt(LocalDateTime.parse("2026-05-09T10:00:00"));
        ProcessModelEntity leaveV2 = draftEntity(ProcessModelScopeType.TENANT, 9L);
        leaveV2.setId(11L);
        leaveV2.setVersion(2);
        leaveV2.setStatus(ProcessModelStatus.DEPLOYED);
        leaveV2.setDeploymentId("dep-current");
        leaveV2.setDeployedAt(LocalDateTime.parse("2026-05-10T10:00:00"));
        leaveV2.setUpdatedAt(LocalDateTime.parse("2026-05-10T10:00:00"));
        ProcessModelEntity leaveV3 = draftEntity(ProcessModelScopeType.TENANT, 9L);
        leaveV3.setId(13L);
        leaveV3.setVersion(3);
        leaveV3.setUpdatedAt(LocalDateTime.parse("2026-05-11T10:00:00"));
        ProcessModelEntity expense = draftEntity(ProcessModelScopeType.TENANT, 9L);
        expense.setId(12L);
        expense.setModelKey("expense_process");
        expense.setName("Expense Process");
        expense.setVersion(1);
        expense.setUpdatedAt(LocalDateTime.parse("2026-05-08T10:00:00"));
        when(repository.findAllInScope(ProcessModelScopeType.TENANT, 9L))
            .thenReturn(List.of(leaveV3, leaveV2, leaveV1, expense));

        var groups = service.listGroupsCurrentScope();

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).modelKey()).isEqualTo("leave_process");
        assertThat(groups.get(0).versionCount()).isEqualTo(3);
        assertThat(groups.get(0).latestModel().id()).isEqualTo(13L);
        assertThat(groups.get(0).latestVersion()).isEqualTo(3);
        assertThat(groups.get(0).latestDesignVersion()).isEqualTo(3);
        assertThat(groups.get(0).latestStatus()).isEqualTo("DRAFT");
        assertThat(groups.get(0).currentRuntimeVersion()).isEqualTo(2);
        assertThat(groups.get(0).currentDeploymentId()).isEqualTo("dep-current");
        assertThat(groups.get(0).hasUndeployedChanges()).isTrue();
        assertThat(groups.get(0).updatedAt()).isEqualTo(LocalDateTime.parse("2026-05-11T10:00:00"));
        assertThat(groups.get(0).versions()).extracting(ProcessModelDto::version).containsExactly(3, 2, 1);
        assertThat(groups.get(0).versions()).extracting(ProcessModelDto::runtimeState)
            .containsExactly("NOT_DEPLOYED", "CURRENT_RUNTIME", "HISTORICAL_DEPLOYED");
        assertThat(groups.get(1).modelKey()).isEqualTo("expense_process");
    }

    @Test
    void deploy_whenTenantModel_passesActiveTenantToRuntimeDeployment() throws Exception {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        TenantContext.setActiveTenantId(9L);
        ProcessModelEntity entity = draftEntity(ProcessModelScopeType.TENANT, 9L);
        when(repository.findByIdInScope(10L, ProcessModelScopeType.TENANT, 9L)).thenReturn(Optional.of(entity));
        when(bpmnValidationHelper.validateBpmnXml(BPMN_XML)).thenReturn(validationResult(true));
        when(processEngineService.deployProcess(eq(BPMN_XML), eq("9"), eq("Leave Process"), eq("bob"), eq("process-modeler")))
            .thenReturn("dep-1");

        ProcessModelDeployResponse response = service.deploy(10L, "bob");

        assertThat(response.deploymentId()).isEqualTo("dep-1");
        assertThat(response.status()).isEqualTo("DEPLOYED");
        assertThat(entity.getStatus()).isEqualTo(ProcessModelStatus.DEPLOYED);
        assertThat(entity.getValidationStatus()).isEqualTo(ProcessModelValidationStatus.PASSED);
        verify(processEngineService).deployProcess(BPMN_XML, "9", "Leave Process", "bob", "process-modeler");
    }

    @Test
    void deploy_whenPlatformModel_passesNullTenantToRuntimeDeployment() throws Exception {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        ProcessModelEntity entity = draftEntity(ProcessModelScopeType.PLATFORM, null);
        when(repository.findByIdInScope(10L, ProcessModelScopeType.PLATFORM, null)).thenReturn(Optional.of(entity));
        when(bpmnValidationHelper.validateBpmnXml(BPMN_XML)).thenReturn(validationResult(true));
        when(processEngineService.deployProcess(eq(BPMN_XML), isNull(), eq("Leave Process"), eq("alice"), eq("process-modeler")))
            .thenReturn("dep-platform");

        ProcessModelDeployResponse response = service.deploy(10L, "alice");

        assertThat(response.deploymentId()).isEqualTo("dep-platform");
        verify(processEngineService).deployProcess(BPMN_XML, null, "Leave Process", "alice", "process-modeler");
    }

    @Test
    void deploy_whenValidationFails_doesNotDeploy() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        TenantContext.setActiveTenantId(9L);
        ProcessModelEntity entity = draftEntity(ProcessModelScopeType.TENANT, 9L);
        when(repository.findByIdInScope(10L, ProcessModelScopeType.TENANT, 9L)).thenReturn(Optional.of(entity));
        when(bpmnValidationHelper.validateBpmnXml(BPMN_XML)).thenReturn(validationResult(false));

        assertThatThrownBy(() -> service.deploy(10L, "bob"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("BPMN XML 验证失败");

        assertThat(entity.getValidationStatus()).isEqualTo(ProcessModelValidationStatus.FAILED);
    }

    @Test
    void validate_whenBusinessReferenceFails_marksModelFailed() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        ProcessModelEntity entity = draftEntity(ProcessModelScopeType.PLATFORM, null);
        when(repository.findByIdInScope(10L, ProcessModelScopeType.PLATFORM, null)).thenReturn(Optional.of(entity));
        when(bpmnValidationHelper.validateBpmnXml(BPMN_XML)).thenReturn(validationResult(true));
        when(businessValidationService.validate(BPMN_XML, ProcessModelScopeType.PLATFORM, null))
            .thenReturn(new ProcessModelBusinessValidationService.BusinessValidationResult(
                List.of("权限码不存在、未启用或不属于当前 scope: workflow:platform:demo:start"),
                List.of("表单注册中心尚未接入当前校验链，本次仅校验 formKey 非空与格式")
            ));

        ProcessModelValidationResponse response = service.validate(10L, "alice");

        assertThat(response.valid()).isFalse();
        assertThat(response.message()).contains("流程模型业务校验失败");
        assertThat(response.warnings()).contains("表单注册中心尚未接入当前校验链，本次仅校验 formKey 非空与格式");
        assertThat(entity.getStatus()).isEqualTo(ProcessModelStatus.DRAFT);
        assertThat(entity.getValidationStatus()).isEqualTo(ProcessModelValidationStatus.FAILED);
        assertThat(entity.getValidationSummary()).contains("Errors:");
    }

    @Test
    void deploy_whenBusinessReferenceFails_doesNotDeploy() throws Exception {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        ProcessModelEntity entity = draftEntity(ProcessModelScopeType.PLATFORM, null);
        when(repository.findByIdInScope(10L, ProcessModelScopeType.PLATFORM, null)).thenReturn(Optional.of(entity));
        when(bpmnValidationHelper.validateBpmnXml(BPMN_XML)).thenReturn(validationResult(true));
        when(businessValidationService.validate(BPMN_XML, ProcessModelScopeType.PLATFORM, null))
            .thenReturn(new ProcessModelBusinessValidationService.BusinessValidationResult(
                List.of("角色不存在、未启用或不属于当前 scope: ROLE_PLATFORM_PRODUCT"),
                List.of()
            ));

        assertThatThrownBy(() -> service.deploy(10L, "alice"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("流程模型业务校验失败");

        assertThat(entity.getValidationStatus()).isEqualTo(ProcessModelValidationStatus.FAILED);
        verify(processEngineService, never()).deployProcess(any(), any(), any(), any(), any());
    }

    @Test
    void delete_whenDraftNotDeployed_deletesModel() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        TenantContext.setActiveTenantId(9L);
        ProcessModelEntity entity = draftEntity(ProcessModelScopeType.TENANT, 9L);
        when(repository.findByIdInScope(10L, ProcessModelScopeType.TENANT, 9L)).thenReturn(Optional.of(entity));

        service.delete(10L);

        verify(repository).delete(entity);
    }

    @Test
    void delete_whenValidatedNotDeployed_deletesModel() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        ProcessModelEntity entity = draftEntity(ProcessModelScopeType.PLATFORM, null);
        entity.setStatus(ProcessModelStatus.VALIDATED);
        entity.setValidationStatus(ProcessModelValidationStatus.PASSED);
        when(repository.findByIdInScope(10L, ProcessModelScopeType.PLATFORM, null)).thenReturn(Optional.of(entity));

        service.delete(10L);

        verify(repository).delete(entity);
    }

    @Test
    void delete_whenModelWasDeployed_rejectsAndKeepsRecord() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        TenantContext.setActiveTenantId(9L);
        ProcessModelEntity entity = draftEntity(ProcessModelScopeType.TENANT, 9L);
        entity.setStatus(ProcessModelStatus.DEPLOYED);
        entity.setDeploymentId("dep-1");
        when(repository.findByIdInScope(10L, ProcessModelScopeType.TENANT, 9L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.delete(10L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅允许删除未部署的草稿或已校验草稿");

        verify(repository, never()).delete(any());
    }

    @Test
    void delete_whenModelHasRuntimeReference_rejectsEvenIfStatusIsDraft() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        TenantContext.setActiveTenantId(9L);
        ProcessModelEntity entity = draftEntity(ProcessModelScopeType.TENANT, 9L);
        entity.setProcessDefinitionId("def-1");
        when(repository.findByIdInScope(10L, ProcessModelScopeType.TENANT, 9L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.delete(10L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("仅允许删除未部署的草稿或已校验草稿");

        verify(repository, never()).delete(any());
        verifyNoInteractions(processEngineService);
    }

    private static ProcessModelEntity draftEntity(ProcessModelScopeType scopeType, Long tenantId) {
        ProcessModelEntity entity = new ProcessModelEntity();
        entity.setId(10L);
        entity.setModelKey("leave_process");
        entity.setName("Leave Process");
        entity.setScopeType(scopeType);
        entity.setTenantId(tenantId);
        entity.setVersion(1);
        entity.setBpmnXml(BPMN_XML);
        entity.setStatus(ProcessModelStatus.DRAFT);
        entity.setValidationStatus(ProcessModelValidationStatus.NOT_VALIDATED);
        entity.setLockVersion(0L);
        return entity;
    }

    private static BpmnValidationHelper.BpmnValidationResult validationResult(boolean valid) {
        BpmnValidationHelper.BpmnValidationResult result = new BpmnValidationHelper.BpmnValidationResult();
        result.setValid(valid);
        result.setMessage(valid ? "BPMN XML 验证通过" : "BPMN XML 验证失败");
        if (!valid) {
            result.addWarning("invalid gateway");
        }
        return result;
    }
}
