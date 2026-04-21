package com.tiny.platform.application.controller.tenant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantInitializationSummaryDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantPrecheckResponseDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantPermissionSummaryDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import com.tiny.platform.infrastructure.tenant.service.PlatformTemplateDiffResult;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import com.tiny.platform.infrastructure.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, classes = TenantControllerRbacIntegrationTest.RbacTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class TenantControllerRbacIntegrationTest {

    @SpringBootConfiguration
    @Import({
        TenantControllerRbacTestConfig.class,
        TenantController.class,
        org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration.class,
        org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class
    })
    static class RbacTestApp {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantService tenantService;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private TenantLifecycleAccessGuard tenantLifecycleAccessGuard;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("platform admin access")
    class PlatformAdminAccess {

        @BeforeEach
        void setPlatformTenant() {
            TenantContext.setActiveTenantId(1L);
            TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        }

        @Test
        void list_allowsPlatformAdmin() throws Exception {
            when(tenantService.list(any(), any())).thenReturn(new PageImpl<>(List.of(new TenantResponseDto())));

            mockMvc.perform(get("/sys/tenants")
                    .param("page", "0")
                    .param("size", "10")
                    .with(user("platform-admin").authorities(new SimpleGrantedAuthority("system:tenant:list"))))
                .andExpect(status().isOk());
        }

        @Test
        void create_allowsPlatformAdmin() throws Exception {
            TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
            dto.setCode("tenant-z");
            dto.setName("Tenant Z");
            TenantResponseDto response = new TenantResponseDto();
            response.setId(9L);
            when(tenantService.create(any(TenantCreateUpdateDto.class))).thenReturn(response);

            mockMvc.perform(post("/sys/tenants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("platform-admin").authorities(new SimpleGrantedAuthority("system:tenant:create"))))
                .andExpect(status().isOk());
        }

        @Test
        void precheck_allowsPlatformAdmin() throws Exception {
            TenantPrecheckResponseDto precheck = new TenantPrecheckResponseDto();
            TenantInitializationSummaryDto summary = new TenantInitializationSummaryDto();
            summary.setTenantCode("tenant-z");
            precheck.setOk(true);
            precheck.setInitializationSummary(summary);
            when(tenantService.precheckCreate(any(TenantCreateUpdateDto.class))).thenReturn(precheck);

            mockMvc.perform(post("/sys/tenants/precheck")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new TenantCreateUpdateDto()))
                    .with(user("platform-admin").authorities(new SimpleGrantedAuthority("system:tenant:create"))))
                .andExpect(status().isOk());
        }

        @Test
        void freeze_allowsPlatformAdminWithLifecycleAuthority() throws Exception {
            TenantResponseDto response = new TenantResponseDto();
            response.setId(9L);
            response.setLifecycleStatus("FROZEN");
            when(tenantService.freeze(eq(9L))).thenReturn(response);

            mockMvc.perform(post("/sys/tenants/9/freeze")
                    .with(user("platform-admin").authorities(new SimpleGrantedAuthority("system:tenant:freeze"))))
                .andExpect(status().isOk());
        }

        @Test
        void decommission_allowsPlatformAdminWithLifecycleAuthority() throws Exception {
            TenantResponseDto response = new TenantResponseDto();
            response.setId(9L);
            response.setLifecycleStatus("DECOMMISSIONED");
            when(tenantService.decommission(eq(9L))).thenReturn(response);

            mockMvc.perform(post("/sys/tenants/9/decommission")
                    .with(user("platform-admin").authorities(new SimpleGrantedAuthority("system:tenant:decommission"))))
                .andExpect(status().isOk());
        }

        @Test
        void initializePlatformTemplate_allowsPlatformAdminWithDedicatedAuthority() throws Exception {
            when(tenantService.initializePlatformTemplates()).thenReturn(true);

            mockMvc.perform(post("/sys/tenants/platform-template/initialize")
                    .with(user("platform-admin").authorities(new SimpleGrantedAuthority("system:tenant:template:initialize"))))
                .andExpect(status().isOk());
        }

        @Test
        void permissionSummary_allowsPlatformAdmin() throws Exception {
            when(tenantService.summarizeTenantPermissions(9L))
                .thenReturn(new TenantPermissionSummaryDto(9L, 1L, 1L, 2L, 2L, 3L, 3L, 1L, 1L, 1L));

            mockMvc.perform(get("/sys/tenants/9/permission-summary")
                    .with(user("platform-admin").authorities(new SimpleGrantedAuthority("system:tenant:view"))))
                .andExpect(status().isOk());
        }

        @Test
        void diffPlatformTemplate_allowsPlatformAdmin_andLifecycleGuardIsInvoked() throws Exception {
            when(tenantService.diffPlatformTemplate(9L))
                .thenReturn(new PlatformTemplateDiffResult(
                    9L,
                    new PlatformTemplateDiffResult.Summary(1, 1, 0, 0, 0),
                    List.of()
                ));

            mockMvc.perform(get("/sys/tenants/9/platform-template/diff")
                    .with(user("platform-admin").authorities(new SimpleGrantedAuthority("system:tenant:view"))))
                .andExpect(status().isOk());

            verify(tenantLifecycleAccessGuard).assertPlatformTargetTenantReadable(9L, "system:tenant:view");
        }
    }

    @Test
    void list_deniesNonPlatformTenantAdmin() throws Exception {
        TenantContext.setActiveTenantId(2L);

        mockMvc.perform(get("/sys/tenants")
                .with(user("tenant-admin").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_deniesPlatformTenantWithoutAdminRole() throws Exception {
        TenantContext.setActiveTenantId(1L);
        TenantCreateUpdateDto dto = new TenantCreateUpdateDto();
        dto.setCode("tenant-z");
        dto.setName("Tenant Z");

        mockMvc.perform(post("/sys/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))
                .with(user("viewer").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void precheck_deniesWithoutCreateAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        mockMvc.perform(post("/sys/tenants/precheck")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TenantCreateUpdateDto()))
                .with(user("viewer").authorities(new SimpleGrantedAuthority("system:tenant:view"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void freeze_deniesWithoutDedicatedAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        mockMvc.perform(post("/sys/tenants/9/freeze")
                .with(user("editor").authorities(new SimpleGrantedAuthority("system:tenant:edit"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void initializePlatformTemplate_deniesWithoutDedicatedAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        mockMvc.perform(post("/sys/tenants/platform-template/initialize")
                .with(user("editor").authorities(new SimpleGrantedAuthority("system:tenant:edit"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void permissionSummary_deniesWithoutReadAuthority() throws Exception {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);

        mockMvc.perform(get("/sys/tenants/9/permission-summary")
                .with(user("editor").authorities(new SimpleGrantedAuthority("system:tenant:edit"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void diffPlatformTemplate_deniesWhenLifecycleGuardBlocksFrozenOrDecommissionedTarget() throws Exception {
        TenantContext.setActiveTenantId(1L);
        TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
        doThrow(new org.springframework.security.access.AccessDeniedException("lifecycle allowlist denied"))
            .when(tenantLifecycleAccessGuard).assertPlatformTargetTenantReadable(9L, "system:tenant:view");

        mockMvc.perform(get("/sys/tenants/9/platform-template/diff")
                .with(user("platform-admin").authorities(new SimpleGrantedAuthority("system:tenant:view"))))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void list_deniesAnonymous() throws Exception {
        TenantContext.setActiveTenantId(1L);

        mockMvc.perform(get("/sys/tenants"))
            .andExpect(status().is4xxClientError());
    }
}
