package com.tiny.platform.application.controller.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.application.controller.role.security.RoleManagementAccessGuard;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCardinalityRuleUpsertDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleHierarchyEdgeUpsertDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleMutexRuleUpsertDto;
import com.tiny.platform.infrastructure.auth.role.dto.RolePrerequisiteRuleUpsertDto;
import com.tiny.platform.infrastructure.auth.role.service.RoleConstraintRuleAdminService;
import com.tiny.platform.infrastructure.auth.role.service.RoleConstraintViolationLogQueryService;
import com.tiny.platform.infrastructure.core.exception.handler.OAuthServerExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, classes = RoleConstraintRuleControllerRbacIntegrationTest.RbacTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
@SuppressWarnings({"null", "removal"})
class RoleConstraintRuleControllerRbacIntegrationTest {

    @SpringBootConfiguration
    @Import({
        RbacTestConfig.class,
        RoleConstraintRuleController.class,
        OAuthServerExceptionHandler.class,
        SpringDataWebAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        WebMvcAutoConfiguration.class
    })
    static class RbacTestApp {}

    @TestConfiguration
    @Profile("rbac-test")
    @EnableWebSecurity
    @EnableMethodSecurity
    static class RbacTestConfig {
        @Bean
        public RoleManagementAccessGuard roleManagementAccessGuard() {
            return new RoleManagementAccessGuard();
        }

        @Bean
        public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(c -> c.anyRequest().permitAll());
            return http.build();
        }
    }

    @MockBean
    private RoleConstraintRuleAdminService adminService;

    @MockBean
    private RoleConstraintViolationLogQueryService violationLogQueryService;

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void upsertHierarchy_deniesWithoutPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            RoleHierarchyEdgeUpsertDto dto = new RoleHierarchyEdgeUpsertDto();
            dto.setChildRoleId(10L);
            dto.setParentRoleId(11L);

            mockMvc.perform(post("/sys/role-constraints/hierarchy")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("role-editor").authorities(new SimpleGrantedAuthority("system:role:edit"))))
                .andExpect(status().isForbidden());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void upsertHierarchy_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            doNothing().when(adminService).upsertRoleHierarchyEdge(any(), any(), any());

            RoleHierarchyEdgeUpsertDto dto = new RoleHierarchyEdgeUpsertDto();
            dto.setChildRoleId(10L);
            dto.setParentRoleId(11L);

            mockMvc.perform(post("/sys/role-constraints/hierarchy")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isOk());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listHierarchy_deniesWithoutPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            mockMvc.perform(get("/sys/role-constraints/hierarchy")
                    .with(user("role-reader").authorities(new SimpleGrantedAuthority("system:role:list"))))
                .andExpect(status().isForbidden());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listHierarchy_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            when(adminService.listRoleHierarchyEdges(any())).thenReturn(java.util.List.of());

            mockMvc.perform(get("/sys/role-constraints/hierarchy")
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isOk());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void deleteHierarchy_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            doNothing().when(adminService).deleteRoleHierarchyEdge(any(), any(), any());

            mockMvc.perform(delete("/sys/role-constraints/hierarchy")
                    .param("childRoleId", "10")
                    .param("parentRoleId", "11")
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isNoContent());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listHierarchy_returnsBadRequest_whenTenantMissing() throws Exception {
        TenantContext.clear();
        mockMvc.perform(get("/sys/role-constraints/hierarchy")
                .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void upsertMutex_deniesWithoutPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            RoleMutexRuleUpsertDto dto = new RoleMutexRuleUpsertDto();
            dto.setRoleIdA(10L);
            dto.setRoleIdB(11L);

            mockMvc.perform(post("/sys/role-constraints/mutex")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("role-editor").authorities(new SimpleGrantedAuthority("system:role:edit"))))
                .andExpect(status().isForbidden());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void upsertMutex_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            doNothing().when(adminService).upsertRoleMutex(any(), any(), any());

            RoleMutexRuleUpsertDto dto = new RoleMutexRuleUpsertDto();
            dto.setRoleIdA(10L);
            dto.setRoleIdB(11L);

            mockMvc.perform(post("/sys/role-constraints/mutex")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isOk());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listMutex_deniesWithoutPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            mockMvc.perform(get("/sys/role-constraints/mutex")
                    .with(user("role-reader").authorities(new SimpleGrantedAuthority("system:role:list"))))
                .andExpect(status().isForbidden());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listMutex_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            when(adminService.listRoleMutexRules(any())).thenReturn(java.util.List.of());

            mockMvc.perform(get("/sys/role-constraints/mutex")
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isOk());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void deleteMutex_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            doNothing().when(adminService).deleteRoleMutex(any(), any(), any());

            mockMvc.perform(delete("/sys/role-constraints/mutex")
                    .param("roleIdA", "10")
                    .param("roleIdB", "11")
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isNoContent());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listMutex_returnsBadRequest_whenTenantMissing() throws Exception {
        TenantContext.clear();
        mockMvc.perform(get("/sys/role-constraints/mutex")
                .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void upsertPrerequisite_deniesWithoutPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            RolePrerequisiteRuleUpsertDto dto = new RolePrerequisiteRuleUpsertDto();
            dto.setRoleId(10L);
            dto.setRequiredRoleId(11L);

            mockMvc.perform(post("/sys/role-constraints/prerequisite")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("role-editor").authorities(new SimpleGrantedAuthority("system:role:edit"))))
                .andExpect(status().isForbidden());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void upsertPrerequisite_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            doNothing().when(adminService).upsertRolePrerequisite(any(), any(), any());

            RolePrerequisiteRuleUpsertDto dto = new RolePrerequisiteRuleUpsertDto();
            dto.setRoleId(10L);
            dto.setRequiredRoleId(11L);

            mockMvc.perform(post("/sys/role-constraints/prerequisite")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isOk());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listPrerequisite_deniesWithoutPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            mockMvc.perform(get("/sys/role-constraints/prerequisite")
                    .with(user("role-reader").authorities(new SimpleGrantedAuthority("system:role:list"))))
                .andExpect(status().isForbidden());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listPrerequisite_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            when(adminService.listRolePrerequisiteRules(any())).thenReturn(java.util.List.of());

            mockMvc.perform(get("/sys/role-constraints/prerequisite")
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isOk());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void deletePrerequisite_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            doNothing().when(adminService).deleteRolePrerequisite(any(), any(), any());

            mockMvc.perform(delete("/sys/role-constraints/prerequisite")
                    .param("roleId", "10")
                    .param("requiredRoleId", "11")
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isNoContent());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listPrerequisite_returnsBadRequest_whenTenantMissing() throws Exception {
        TenantContext.clear();
        mockMvc.perform(get("/sys/role-constraints/prerequisite")
                .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void upsertCardinality_deniesWithoutPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            RoleCardinalityRuleUpsertDto dto = new RoleCardinalityRuleUpsertDto();
            dto.setRoleId(10L);
            dto.setScopeType("TENANT");
            dto.setMaxAssignments(1);

            mockMvc.perform(post("/sys/role-constraints/cardinality")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("role-editor").authorities(new SimpleGrantedAuthority("system:role:edit"))))
                .andExpect(status().isForbidden());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void upsertCardinality_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            doNothing().when(adminService).upsertRoleCardinality(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());

            RoleCardinalityRuleUpsertDto dto = new RoleCardinalityRuleUpsertDto();
            dto.setRoleId(10L);
            dto.setScopeType("TENANT");
            dto.setMaxAssignments(1);

            mockMvc.perform(post("/sys/role-constraints/cardinality")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isOk());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listCardinality_deniesWithoutPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            mockMvc.perform(get("/sys/role-constraints/cardinality")
                    .with(user("role-reader").authorities(new SimpleGrantedAuthority("system:role:list"))))
                .andExpect(status().isForbidden());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listCardinality_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            when(adminService.listRoleCardinalityRules(any())).thenReturn(java.util.List.of());

            mockMvc.perform(get("/sys/role-constraints/cardinality")
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isOk());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void deleteCardinality_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            doNothing().when(adminService).deleteRoleCardinality(any(), any(), any());

            mockMvc.perform(delete("/sys/role-constraints/cardinality")
                    .param("roleId", "10")
                    .param("scopeType", "TENANT")
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isNoContent());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listCardinality_returnsBadRequest_whenTenantMissing() throws Exception {
        TenantContext.clear();
        mockMvc.perform(get("/sys/role-constraints/cardinality")
                .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listViolations_deniesWithoutPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            mockMvc.perform(get("/sys/role-constraints/violations")
                    .with(user("role-reader").authorities(new SimpleGrantedAuthority("system:role:list"))))
                .andExpect(status().isForbidden());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listViolations_allowsPermissionAssignAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            when(violationLogQueryService.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

            mockMvc.perform(get("/sys/role-constraints/violations")
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isOk());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listViolations_allowsNewConstraintViolationViewAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        try {
            when(violationLogQueryService.query(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

            mockMvc.perform(get("/sys/role-constraints/violations")
                    .with(user("role-constraint-violation-viewer")
                        .authorities(new SimpleGrantedAuthority("system:role:constraint:violation:view"))))
                .andExpect(status().isOk());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void listViolations_returnsBadRequest_whenTenantMissing() throws Exception {
        TenantContext.clear();
        mockMvc.perform(get("/sys/role-constraints/violations")
                .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
            .andExpect(status().isBadRequest());
    }
}

