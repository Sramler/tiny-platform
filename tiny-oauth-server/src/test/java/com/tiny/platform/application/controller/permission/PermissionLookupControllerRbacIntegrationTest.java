package com.tiny.platform.application.controller.permission;

import com.tiny.platform.application.controller.resource.ResourceControllerRbacTestConfig;
import com.tiny.platform.infrastructure.auth.permission.service.PermissionLookupService;
import com.tiny.platform.infrastructure.auth.resource.dto.PermissionOptionDto;
import com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, classes = PermissionLookupControllerRbacIntegrationTest.RbacTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class PermissionLookupControllerRbacIntegrationTest {

    @SpringBootConfiguration
    @Import({
        ResourceControllerRbacTestConfig.class,
        PermissionLookupController.class,
        HttpMessageConvertersAutoConfiguration.class,
        WebMvcAutoConfiguration.class
    })
    static class RbacTestApp {}

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PermissionLookupService permissionLookupService;

    @MockitoBean
    private ResourceService resourceService;

    @BeforeEach
    void setUp() {
        when(resourceService.evaluateApiEndpointRequirement(eq("GET"), eq("/sys/permissions/options")))
            .thenReturn(ApiEndpointRequirementDecision.ALLOWED);
        when(permissionLookupService.findPermissionOptions("menu", 10))
            .thenReturn(List.of(new PermissionOptionDto(7001L, "system:menu:list", "菜单读取")));
    }

    @Nested
    @DisplayName("READ ACCESS")
    class ReadAccess {

        @Test
        void options_allowMenuReadAuthority() throws Exception {
            mockMvc.perform(get("/sys/permissions/options")
                    .param("keyword", "menu")
                    .param("limit", "10")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(user("menu-reader").authorities(new SimpleGrantedAuthority("system:menu:list"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                    [{"id":7001,"permissionCode":"system:menu:list","permissionName":"菜单读取"}]
                    """));
        }

        @Test
        void options_allowResourceReadAuthority() throws Exception {
            mockMvc.perform(get("/sys/permissions/options")
                    .param("keyword", "menu")
                    .param("limit", "10")
                    .accept(MediaType.APPLICATION_JSON)
                    .with(user("resource-reader").authorities(new SimpleGrantedAuthority("system:resource:list"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }

        @Test
        void options_denyWithoutMenuOrResourceReadAuthority() throws Exception {
            mockMvc.perform(get("/sys/permissions/options")
                    .param("keyword", "menu")
                    .param("limit", "10")
                    .with(user("plain-user").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithAnonymousUser
        void options_denyAnonymous() throws Exception {
            mockMvc.perform(get("/sys/permissions/options")
                    .param("keyword", "menu")
                    .param("limit", "10"))
                .andExpect(status().is4xxClientError());
        }
    }
}
