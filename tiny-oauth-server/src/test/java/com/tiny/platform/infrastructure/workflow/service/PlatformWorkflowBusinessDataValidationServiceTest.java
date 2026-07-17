package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.application.oauth.workflow.WorkflowTaskContext;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformWorkflowBusinessDataValidationServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private RoleRepository roleRepository;

    private PlatformWorkflowBusinessDataValidationService service;

    @BeforeEach
    void setUp() {
        service = new PlatformWorkflowBusinessDataValidationService(tenantRepository, roleRepository);
    }

    @Test
    void prepareStartVariables_whenTenantOnboardingValid_addsRuntimeBusinessState() {
        when(tenantRepository.findByCode("acme-01")).thenReturn(Optional.empty());

        Map<String, Object> result = service.prepareStartVariables(
            "platform_tenant_onboarding",
            Map.of(
                "requestId",
                "REQ-001",
                "requestTitle",
                "租户开通",
                "requestReason",
                "新客户入驻",
                "tenantCode",
                "acme-01",
                "tenantName",
                "Acme",
                "planCode",
                "standard"
            ),
            auth("platform-admin")
        );

        assertThat(result)
            .containsEntry("tenantCode", "acme-01")
            .containsEntry("tpProcessKey", "platform_tenant_onboarding")
            .containsEntry("tpWorkflowStatus", "SUBMITTED")
            .containsEntry("tpStartedBy", "platform-admin");
    }

    @Test
    void prepareStartVariables_whenTenantCodeAlreadyExists_rejectsDuplicateApplication() {
        when(tenantRepository.findByCode("acme-01")).thenReturn(Optional.of(tenant(1L, "acme-01", "standard", "ACTIVE")));

        assertThatThrownBy(() -> service.prepareStartVariables(
            "platform_tenant_onboarding",
            Map.of(
                "requestId",
                "REQ-001",
                "requestTitle",
                "租户开通",
                "requestReason",
                "重复开通",
                "tenantCode",
                "acme-01",
                "tenantName",
                "Acme",
                "planCode",
                "standard"
            ),
            auth("platform-admin")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("租户编码已存在");
    }

    @Test
    void prepareStartVariables_whenPlanChangeTargetEqualsCurrent_rejectsNoopChange() {
        when(tenantRepository.findByCode("acme-01")).thenReturn(Optional.of(tenant(1L, "acme-01", "standard", "ACTIVE")));

        assertThatThrownBy(() -> service.prepareStartVariables(
            "platform_tenant_plan_change",
            Map.of(
                "requestId",
                "REQ-002",
                "requestTitle",
                "套餐变更",
                "requestReason",
                "客户续约",
                "tenantCode",
                "acme-01",
                "targetPlanCode",
                "standard",
                "changeReason",
                "维持当前套餐"
            ),
            auth("platform-admin")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("目标套餐与当前套餐一致");
    }

    @Test
    void prepareStartVariables_whenPermissionCreateAlreadyExists_rejectsDuplicatePermission() {
        when(roleRepository.findEnabledPermissionCodesByTenantIdAndCodes(isNull(), anyList()))
            .thenReturn(List.of("workflow:platform:demo:start"));

        assertThatThrownBy(() -> service.prepareStartVariables(
            "platform_permission_publish",
            Map.of(
                "requestId",
                "REQ-003",
                "requestTitle",
                "权限发布",
                "requestReason",
                "新增审批入口",
                "permissionCode",
                "workflow:platform:demo:start",
                "changeType",
                "CREATE",
                "impactScope",
                "platform workflow"
            ),
            auth("platform-admin")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("权限码已存在");
    }

    @Test
    void prepareTaskCompleteVariables_whenDecisionOrCommentMissing_rejectsIncompleteApproval() {
        WorkflowTaskContext taskContext = taskContext();

        assertThatThrownBy(() -> service.prepareTaskCompleteVariables(
            taskContext,
            Map.of("decision", "APPROVE"),
            auth("approver")
        ))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("审批任务必须提交 comment/approvalComment");
    }

    @Test
    void prepareTaskCompleteVariables_whenApprove_addsStateFlowVariables() {
        WorkflowTaskContext taskContext = taskContext();

        Map<String, Object> result = service.prepareTaskCompleteVariables(
            taskContext,
            Map.of("decision", "APPROVE", "comment", "资料完整"),
            auth("approver")
        );

        assertThat(result)
            .containsEntry("tpLastTaskId", "task-1")
            .containsEntry("tpLastTaskKey", "UserTask_Review")
            .containsEntry("tpLastDecision", "APPROVE")
            .containsEntry("tpLastActionBy", "approver")
            .containsEntry("tpWorkflowStatus", "APPROVED_IN_STEP");
    }

    private static WorkflowTaskContext taskContext() {
        return new WorkflowTaskContext(
            "task-1",
            "资料审核",
            "UserTask_Review",
            "inst-1",
            "platform_tenant_onboarding:1:1",
            "platform_tenant_onboarding",
            null
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

    private static Authentication auth(String username) {
        return new UsernamePasswordAuthenticationToken(
            username,
            "n/a",
            List.of(new SimpleGrantedAuthority("workflow:*"))
        );
    }
}
