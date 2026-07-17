package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleResponseDto;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.role.service.RoleService;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformRoleBaselineServiceTest {

    @Mock
    private RoleService roleService;

    @Mock
    private RoleRepository roleRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void publishCreate_delegatesToRoleServiceWithGovernanceFieldsAndPermissions() {
        PlatformRoleBaselineService service = new PlatformRoleBaselineService(roleService, roleRepository);
        when(roleRepository.findByCodeAndTenantId("ROLE_PLATFORM_WORKFLOW_AUDITOR", null))
            .thenReturn(Optional.empty());
        RoleResponseDto response = new RoleResponseDto();
        response.setId(601L);
        response.setCode("ROLE_PLATFORM_WORKFLOW_AUDITOR");
        response.setName("平台流程审计员");
        response.setEnabled(true);
        response.setRiskLevel("HIGH");
        response.setApprovalMode("ONE_STEP");
        response.setPermissionIds(List.of(101L, 102L));
        when(roleService.create(org.mockito.ArgumentMatchers.any(RoleCreateUpdateDto.class)))
            .thenReturn(response);
        ArgumentCaptor<RoleCreateUpdateDto> dtoCaptor = ArgumentCaptor.forClass(RoleCreateUpdateDto.class);

        Map<String, Object> result = service.publish(
            Map.of(
                "roleCode",
                "ROLE_PLATFORM_WORKFLOW_AUDITOR",
                "roleName",
                "平台流程审计员",
                "changeType",
                "CREATE",
                "baselineReason",
                "新增流程审计职责",
                "riskLevel",
                "HIGH",
                "approvalMode",
                "ONE_STEP",
                "permissionIds",
                List.of(101L, 102L)
            ),
            null,
            ProcessModelScopeType.PLATFORM,
            null
        );

        verify(roleService).create(dtoCaptor.capture());
        RoleCreateUpdateDto dto = dtoCaptor.getValue();
        assertThat(dto.getCode()).isEqualTo("ROLE_PLATFORM_WORKFLOW_AUDITOR");
        assertThat(dto.getName()).isEqualTo("平台流程审计员");
        assertThat(dto.getRiskLevel()).isEqualTo("HIGH");
        assertThat(dto.getApprovalMode()).isEqualTo("ONE_STEP");
        assertThat(dto.getPermissionIds()).containsExactly(101L, 102L);
        assertThat(result)
            .containsEntry("roleId", 601L)
            .containsEntry("roleCode", "ROLE_PLATFORM_WORKFLOW_AUDITOR")
            .containsEntry("changeType", "CREATE")
            .containsEntry("enabled", true);
    }
}
