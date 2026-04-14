package com.tiny.platform.application.controller.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.data.web.SpringDataWebAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, classes = ResourceControllerRbacIntegrationTest.RbacTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class ResourceControllerRbacIntegrationTest {

    @SpringBootConfiguration
    @Import({
        ResourceControllerRbacTestConfig.class,
        ResourceController.class,
        SpringDataWebAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        WebMvcAutoConfiguration.class
    })
    static class RbacTestApp {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ResourceService resourceService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    

    @Nested
    @DisplayName("RESOURCE READ")
    class ReadAccess {

        @Test
        void list_allowsReadAuthority() throws Exception {
            when(resourceService.resources(any(), any())).thenReturn(new PageImpl<>(List.of(sampleResponse())));
            when(resourceService.evaluateApiEndpointRequirement(eq("GET"), eq("/sys/resources")))
                .thenReturn(com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision.ALLOWED);

            mockMvc.perform(get("/sys/resources")
                    .with(user("resource-reader").authorities(new SimpleGrantedAuthority("system:resource:list"))))
                .andExpect(status().isOk());
        }

        @Test
        void registeredEndpoint_deniesWhenRequirementNotSatisfied() throws Exception {
            when(resourceService.evaluateApiEndpointRequirement(eq("GET"), eq("/sys/resources")))
                .thenReturn(com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision.DENIED);
            mockMvc.perform(get("/sys/resources")
                    .with(user("resource-reader").authorities(new SimpleGrantedAuthority("system:resource:list"))))
                .andExpect(status().isForbidden());
        }

        @Test
        void unregisteredEndpoint_shouldFailClosedInUnifiedGuard() throws Exception {
            when(resourceService.getResourceTypes()).thenReturn(List.of(ResourceType.values()));
            when(resourceService.evaluateApiEndpointRequirement(eq("GET"), eq("/sys/resources/types")))
                .thenReturn(com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision.DENIED);
            mockMvc.perform(get("/sys/resources/types")
                    .with(user("resource-reader").authorities(new SimpleGrantedAuthority("system:resource:list"))))
                .andExpect(status().isForbidden());
        }

        @Test
        void tree_allowsResourceListAuthority() throws Exception {
            when(resourceService.findResourceTreeDtos()).thenReturn(List.of(sampleResponse()));
            when(resourceService.evaluateApiEndpointRequirement(eq("GET"), eq("/sys/resources/tree")))
                .thenReturn(com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision.ALLOWED);

            mockMvc.perform(get("/sys/resources/tree")
                    .with(user("admin").authorities(new SimpleGrantedAuthority("system:resource:list"))))
                .andExpect(status().isOk());
        }

        @Test
        void get_deniesWithoutReadAuthority() throws Exception {
            mockMvc.perform(get("/sys/resources/4")
                    .with(user("plain-user").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithAnonymousUser
        void list_deniesAnonymous() throws Exception {
            mockMvc.perform(get("/sys/resources"))
                .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("RESOURCE WRITE")
    class WriteAccess {

        @Test
        void create_allowsCreateAuthority() throws Exception {
            when(resourceService.createFromDto(any(ResourceCreateUpdateDto.class))).thenReturn(sampleResource(5L, "res"));
            when(resourceService.evaluateApiEndpointRequirement(eq("POST"), eq("/sys/resources")))
                .thenReturn(com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision.ALLOWED);

            mockMvc.perform(post("/sys/resources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validResourceDto()))
                    .with(user("resource-creator").authorities(new SimpleGrantedAuthority("system:resource:create"))))
                .andExpect(status().isOk());
        }

        @Test
        void updateSort_allowsEditAuthority() throws Exception {
            when(resourceService.updateSort(5L, 6)).thenReturn(sampleResource(5L, "res"));
            when(resourceService.evaluateApiEndpointRequirement(eq("PUT"), eq("/sys/resources/5/sort")))
                .thenReturn(com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision.ALLOWED);

            mockMvc.perform(put("/sys/resources/5/sort")
                    .param("sort", "6")
                    .with(user("resource-editor").authorities(new SimpleGrantedAuthority("system:resource:edit"))))
                .andExpect(status().isOk());
        }

        @Test
        void batchDelete_deniesReadOnlyAuthority() throws Exception {
            when(resourceService.evaluateApiEndpointRequirement(eq("POST"), eq("/sys/resources/batch/delete")))
                .thenReturn(com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision.ALLOWED);
            mockMvc.perform(post("/sys/resources/batch/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(List.of(1L, 2L)))
                    .with(user("resource-reader").authorities(new SimpleGrantedAuthority("system:resource:list"))))
                .andExpect(status().isForbidden());
        }
    }

    @Test
    void removedLegacyMenuEndpoint_shouldFailClosed() throws Exception {
        mockMvc.perform(get("/sys/resources/menus")
                .with(user("resource-reader").authorities(new SimpleGrantedAuthority("system:resource:list"))))
            .andExpect(status().isForbidden());
    }

    private static ResourceCreateUpdateDto validResourceDto() {
        ResourceCreateUpdateDto dto = new ResourceCreateUpdateDto();
        dto.setName("res");
        dto.setTitle("资源");
        dto.setType(ResourceType.API.getCode());
        return dto;
    }

    private static ResourceResponseDto sampleResponse() {
        ResourceResponseDto dto = new ResourceResponseDto();
        dto.setId(1L);
        dto.setName("res");
        dto.setTitle("资源");
        return dto;
    }

    private static Resource sampleResource(Long id, String name) {
        Resource resource = new Resource();
        resource.setId(id);
        resource.setName(name);
        resource.setTitle(name);
        resource.setType(ResourceType.API);
        return resource;
    }
}
