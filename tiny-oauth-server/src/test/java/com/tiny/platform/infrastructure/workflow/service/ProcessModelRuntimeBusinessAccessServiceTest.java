package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelEntity;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelStatus;
import com.tiny.platform.infrastructure.workflow.repository.ProcessModelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessModelRuntimeBusinessAccessServiceTest {

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
          </bpmn:process>
        </bpmn:definitions>
        """;

    @Mock
    private ProcessModelRepository repository;

    private ProcessModelRuntimeBusinessAccessService service;

    @BeforeEach
    void setUp() {
        service = new ProcessModelRuntimeBusinessAccessService(
            repository,
            new ProcessModelBusinessValidationService(null)
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void assertCanStartProcess_whenPlatformPermissionExists_allowsStart() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        when(repository.findRuntimeCandidatesInScope(
            ProcessModelScopeType.PLATFORM,
            null,
            "platform_tenant_onboarding",
            ProcessModelStatus.DEPLOYED
        )).thenReturn(List.of(deployedModel(ProcessModelScopeType.PLATFORM, null)));

        assertThatCode(() -> service.assertCanStartProcess(
            "platform_tenant_onboarding",
            auth("workflow:platform:tenant-onboarding:start")
        )).doesNotThrowAnyException();
    }

    @Test
    void assertCanStartProcess_whenPermissionMissing_blocksBusinessStart() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        when(repository.findRuntimeCandidatesInScope(
            ProcessModelScopeType.PLATFORM,
            null,
            "platform_tenant_onboarding",
            ProcessModelStatus.DEPLOYED
        )).thenReturn(List.of(deployedModel(ProcessModelScopeType.PLATFORM, null)));

        assertThatThrownBy(() -> service.assertCanStartProcess(
            "platform_tenant_onboarding",
            auth("workflow:instance:control")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("缺少流程发起权限: workflow:platform:tenant-onboarding:start");
    }

    @Test
    void assertCanStartProcess_whenWorkflowWildcardExists_allowsStart() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        when(repository.findRuntimeCandidatesInScope(
            ProcessModelScopeType.PLATFORM,
            null,
            "platform_tenant_onboarding",
            ProcessModelStatus.DEPLOYED
        )).thenReturn(List.of(deployedModel(ProcessModelScopeType.PLATFORM, null)));

        assertThatCode(() -> service.assertCanStartProcess(
            "platform_tenant_onboarding",
            auth("workflow:*")
        )).doesNotThrowAnyException();
    }

    @Test
    void assertCanStartProcess_whenNoDesignModelExists_keepsRuntimeCompatible() {
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
        TenantContext.setActiveTenantId(9L);
        when(repository.findRuntimeCandidatesInScope(
            ProcessModelScopeType.TENANT,
            9L,
            "legacy_process",
            ProcessModelStatus.DEPLOYED
        )).thenReturn(List.of());

        assertThatCode(() -> service.assertCanStartProcess(
            "legacy_process",
            auth("workflow:instance:control")
        )).doesNotThrowAnyException();
    }

    @Test
    void assertCanStartProcess_whenLegacyTenantContextOnly_skipsBusinessGate() {
        assertThatCode(() -> service.assertCanStartProcess(
            "legacy_process",
            auth("workflow:instance:control")
        )).doesNotThrowAnyException();

        verify(repository, never()).findRuntimeCandidatesInScope(
            ProcessModelScopeType.TENANT,
            null,
            "legacy_process",
            ProcessModelStatus.DEPLOYED
        );
    }

    private static ProcessModelEntity deployedModel(ProcessModelScopeType scopeType, Long tenantId) {
        ProcessModelEntity entity = new ProcessModelEntity();
        entity.setId(10L);
        entity.setModelKey("platform_tenant_onboarding");
        entity.setName("租户开通审批");
        entity.setScopeType(scopeType);
        entity.setTenantId(tenantId);
        entity.setStatus(ProcessModelStatus.DEPLOYED);
        entity.setDeploymentId("dep-1");
        entity.setBpmnXml(BUSINESS_BPMN);
        return entity;
    }

    private static Authentication auth(String authority) {
        return new UsernamePasswordAuthenticationToken(
            "alice",
            "n/a",
            List.of(new SimpleGrantedAuthority(authority))
        );
    }
}
