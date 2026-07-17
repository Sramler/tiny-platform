package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessModelBusinessValidationServiceTest {

    private static final String BUSINESS_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          xmlns:camunda="http://camunda.org/schema/1.0/bpmn">
          <bpmn:process id="platform_tenant_onboarding" name="租户开通审批" isExecutable="true">
            <bpmn:extensionElements>
              <camunda:properties>
                <camunda:property name="tp:businessModule" value="platform.tenant.lifecycle"/>
                <camunda:property name="tp:startPermission" value="workflow:platform:tenant-onboarding:start"/>
                <camunda:property name="tp:approvePermission" value="workflow:platform:tenant-onboarding:approve"/>
                <camunda:property name="tp:managePermission" value="workflow:platform:tenant-onboarding:manage"/>
                <camunda:property name="tp:roleCodes" value="ROLE_PLATFORM_PRODUCT,ROLE_PLATFORM_ADMIN"/>
              </camunda:properties>
            </bpmn:extensionElements>
            <bpmn:startEvent id="StartEvent_1"/>
            <bpmn:userTask id="Task_Review" name="资料审核"
                           camunda:candidateGroups="PLATFORM_PRODUCT"
                           camunda:formKey="forms/platform/tenant-onboarding-review"/>
            <bpmn:serviceTask id="Task_Provisioning" name="初始化租户"
                              camunda:delegateExpression="${platformTenantProvisioningConnector}"/>
            <bpmn:endEvent id="EndEvent_1"/>
          </bpmn:process>
        </bpmn:definitions>
        """;

    @Mock
    private RoleRepository roleRepository;

    private ProcessModelBusinessValidationService service;

    @BeforeEach
    void setUp() {
        service = new ProcessModelBusinessValidationService(roleRepository);
    }

    @Test
    void validate_whenBusinessReferencesExist_passesWithRegistryWarnings() {
        when(roleRepository.findEnabledPermissionCodesByTenantIdAndCodes(isNull(), anyList()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        when(roleRepository.findEnabledRoleCodesByTenantIdAndCodes(isNull(), anyList()))
            .thenAnswer(invocation -> invocation.getArgument(1));

        ProcessModelBusinessValidationService.BusinessValidationResult result =
            service.validate(BUSINESS_BPMN, ProcessModelScopeType.PLATFORM, null);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).contains(
            "表单注册中心尚未接入当前校验链，本次仅校验 formKey 非空与格式",
            "连接器注册中心尚未接入当前校验链，本次仅校验 delegateExpression 格式"
        );
    }

    @Test
    void validate_whenPermissionOrRoleMissing_returnsBusinessErrors() {
        when(roleRepository.findEnabledPermissionCodesByTenantIdAndCodes(isNull(), anyList()))
            .thenReturn(List.of("workflow:platform:tenant-onboarding:start"));
        when(roleRepository.findEnabledRoleCodesByTenantIdAndCodes(isNull(), anyList()))
            .thenReturn(List.of("ROLE_PLATFORM_ADMIN"));

        ProcessModelBusinessValidationService.BusinessValidationResult result =
            service.validate(BUSINESS_BPMN, ProcessModelScopeType.PLATFORM, null);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
            .contains("权限码不存在、未启用或不属于当前 scope: workflow:platform:tenant-onboarding:approve")
            .contains("权限码不存在、未启用或不属于当前 scope: workflow:platform:tenant-onboarding:manage")
            .contains("角色不存在、未启用或不属于当前 scope: ROLE_PLATFORM_PRODUCT");
    }

    @Test
    void validate_whenCandidateGroupNotDeclaredInTpRoleCodes_returnsError() {
        when(roleRepository.findEnabledPermissionCodesByTenantIdAndCodes(isNull(), anyList()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        when(roleRepository.findEnabledRoleCodesByTenantIdAndCodes(isNull(), anyList()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        String xml = BUSINESS_BPMN.replace("ROLE_PLATFORM_PRODUCT,ROLE_PLATFORM_ADMIN", "ROLE_PLATFORM_ADMIN");

        ProcessModelBusinessValidationService.BusinessValidationResult result =
            service.validate(xml, ProcessModelScopeType.PLATFORM, null);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
            .contains("用户任务 资料审核(Task_Review) 的候选组 PLATFORM_PRODUCT 未包含在 tp:roleCodes 中");
    }

    @Test
    void validate_whenNoTpMetadata_keepsLegacyProcessAsWarningOnly() {
        String xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
              <bpmn:process id="legacy_process" name="Legacy Process" isExecutable="true">
                <bpmn:userTask id="Task_1" name="人工处理"/>
              </bpmn:process>
            </bpmn:definitions>
            """;

        ProcessModelBusinessValidationService.BusinessValidationResult result =
            service.validate(xml, ProcessModelScopeType.TENANT, 9L);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings()).contains("流程未声明 tp:* 业务元数据，当前仅完成 BPMN 结构校验与节点基础检查");
    }

    @Test
    void readBusinessMetadata_extractsRuntimePermissionReferences() {
        ProcessModelBusinessValidationService.BusinessMetadata metadata =
            service.readBusinessMetadata(BUSINESS_BPMN);

        assertThat(metadata.hasAnyBusinessMetadata()).isTrue();
        assertThat(metadata.businessModule()).isEqualTo("platform.tenant.lifecycle");
        assertThat(metadata.startPermission()).isEqualTo("workflow:platform:tenant-onboarding:start");
        assertThat(metadata.approvePermission()).isEqualTo("workflow:platform:tenant-onboarding:approve");
        assertThat(metadata.managePermission()).isEqualTo("workflow:platform:tenant-onboarding:manage");
        assertThat(metadata.roleCodes()).containsExactly("ROLE_PLATFORM_PRODUCT", "ROLE_PLATFORM_ADMIN");
    }
}
