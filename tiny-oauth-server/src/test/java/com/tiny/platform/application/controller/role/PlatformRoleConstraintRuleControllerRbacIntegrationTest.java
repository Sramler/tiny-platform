package com.tiny.platform.application.controller.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.application.controller.role.security.RoleManagementAccessGuard;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.role.dto.RoleHierarchyEdgeUpsertDto;
import com.tiny.platform.infrastructure.auth.role.service.RoleConstraintRuleAdminService;
import com.tiny.platform.infrastructure.auth.role.service.RoleConstraintViolationLogQueryService;
import com.tiny.platform.infrastructure.core.exception.handler.OAuthServerExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.data.autoconfigure.web.DataWebAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, classes = PlatformRoleConstraintRuleControllerRbacIntegrationTest.RbacTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
@SuppressWarnings({"null", "removal"})
class PlatformRoleConstraintRuleControllerRbacIntegrationTest {

    @SpringBootConfiguration
    @Import({
        RbacTestConfig.class,
        PlatformRoleConstraintRuleController.class,
        OAuthServerExceptionHandler.class,
        DataWebAutoConfiguration.class,
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

    @MockitoBean
    private RoleConstraintRuleAdminService adminService;

    @MockitoBean
    private RoleConstraintViolationLogQueryService violationLogQueryService;

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TenantContext.setActiveTenantId(null);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        TenantContext.setTenantSource(TenantContext.SOURCE_UNKNOWN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void upsertHierarchy_allowsConstraintEditAuthority_inPlatformScope() throws Exception {
        doNothing().when(adminService).upsertPlatformRoleHierarchyEdge(any(), any());
        RoleHierarchyEdgeUpsertDto dto = new RoleHierarchyEdgeUpsertDto();
        dto.setChildRoleId(10L);
        dto.setParentRoleId(11L);

        mockMvc.perform(post("/platform/role-constraints/hierarchy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
                .with(user("platform-editor").authorities(new SimpleGrantedAuthority("system:role:constraint:edit"))))
            .andExpect(status().isOk());
    }

    @Test
    void upsertHierarchy_deniesWithoutConstraintEditAuthority() throws Exception {
        RoleHierarchyEdgeUpsertDto dto = new RoleHierarchyEdgeUpsertDto();
        dto.setChildRoleId(10L);
        dto.setParentRoleId(11L);

        mockMvc.perform(post("/platform/role-constraints/hierarchy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
                .with(user("platform-reader").authorities(new SimpleGrantedAuthority("system:role:constraint:view"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void listHierarchy_deniesWhenNotPlatformScope() throws Exception {
        TenantContext.setActiveTenantId(9L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

        mockMvc.perform(get("/platform/role-constraints/hierarchy")
                .with(user("platform-viewer").authorities(new SimpleGrantedAuthority("system:role:constraint:view"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void listViolations_allowsConstraintViolationViewAuthority() throws Exception {
        when(violationLogQueryService.queryInPlatform(any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/platform/role-constraints/violations")
                .with(user("platform-auditor").authorities(new SimpleGrantedAuthority("system:role:constraint:violation:view"))))
            .andExpect(status().isOk());
    }
}
