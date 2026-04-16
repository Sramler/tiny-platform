package com.tiny.platform.application.controller.audit;

import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationAudit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.data.autoconfigure.web.DataWebAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, classes = AuthenticationAuditControllerRbacIntegrationTest.RbacTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class AuthenticationAuditControllerRbacIntegrationTest {

    @SpringBootConfiguration
    @Import({
        AuthenticationAuditControllerRbacTestConfig.class,
        AuthenticationAuditController.class,
        DataWebAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        WebMvcAutoConfiguration.class
    })
    static class RbacTestApp {}

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationAuditService authenticationAuditService;

    @MockitoBean
    private TenantLifecycleAccessGuard tenantLifecycleAccessGuard;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("VIEW")
    class ViewAccess {

        @Test
        void list_allowsViewAuthorityInTenantScope() throws Exception {
            TenantContext.setActiveTenantId(1L);
            TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
            when(authenticationAuditService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleAudit()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/sys/audit/authentication")
                    .with(user("auth-auditor").authorities(
                        new SimpleGrantedAuthority("system:audit:authentication:view"))))
                .andExpect(status().isOk());
        }

        @Test
        void list_allowsPlatformScopeTenantFilter() throws Exception {
            TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
            when(authenticationAuditService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleAudit()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/sys/audit/authentication")
                    .param("tenantId", "2")
                    .with(user("platform-auditor").authorities(
                        new SimpleGrantedAuthority("system:audit:authentication:view"))))
                .andExpect(status().isOk());
        }

        @Test
        void export_allowsExportAuthority() throws Exception {
            TenantContext.setActiveTenantId(1L);
            TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);
            when(authenticationAuditService.search(any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleAudit()), PageRequest.of(0, 10001), 1));

            mockMvc.perform(get("/sys/audit/authentication/export")
                    .with(user("auth-auditor").authorities(
                        new SimpleGrantedAuthority("system:audit:authentication:export"))))
                .andExpect(status().isOk());
        }

        @Test
        void list_deniesWithoutViewAuthority() throws Exception {
            TenantContext.setActiveTenantId(1L);
            TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

            mockMvc.perform(get("/sys/audit/authentication")
                    .with(user("plain-user").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        }

        @Test
        void export_deniesWithoutExportAuthority() throws Exception {
            TenantContext.setActiveTenantId(1L);
            TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_TENANT);

            mockMvc.perform(get("/sys/audit/authentication/export")
                    .with(user("plain-user").authorities(
                        new SimpleGrantedAuthority("system:audit:authentication:view"))))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithAnonymousUser
        void list_deniesAnonymous() throws Exception {
            mockMvc.perform(get("/sys/audit/authentication"))
                .andExpect(status().is4xxClientError());
        }
    }

    private static UserAuthenticationAudit sampleAudit() {
        UserAuthenticationAudit audit = new UserAuthenticationAudit();
        audit.setId(1L);
        audit.setTenantId(1L);
        audit.setUserId(1L);
        audit.setUsername("alice");
        audit.setEventType("LOGIN");
        audit.setSuccess(true);
        audit.setCreatedAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        return audit;
    }
}
