package com.tiny.platform.application.controller.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleResponseDto;
import com.tiny.platform.infrastructure.auth.role.service.RoleService;
import com.tiny.platform.infrastructure.core.exception.handler.OAuthServerExceptionHandler;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, classes = RoleControllerRbacIntegrationTest.RbacTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class RoleControllerRbacIntegrationTest {

    @SpringBootConfiguration
    @Import({
        RoleControllerRbacTestConfig.class,
        RoleController.class,
        OAuthServerExceptionHandler.class,
        DataWebAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        WebMvcAutoConfiguration.class
    })
    static class RbacTestApp {}

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoleService roleService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("READ")
    class ReadAccess {

        @Test
        void list_allowsReadAuthority() throws Exception {
            when(roleService.roles(any(), any())).thenReturn(new PageImpl<>(List.of(sampleRoleResponse())));

            mockMvc.perform(get("/sys/roles")
                    .with(user("role-reader").authorities(new SimpleGrantedAuthority("system:role:list"))))
                .andExpect(status().isOk());
        }

        @Test
        void getAll_allowsRoleListAuthority() throws Exception {
            when(roleService.roles(any(), any())).thenReturn(new PageImpl<>(List.of(sampleRoleResponse())));

            mockMvc.perform(get("/sys/roles/all")
                    .with(user("admin").authorities(new SimpleGrantedAuthority("system:role:list"))))
                .andExpect(status().isOk());
        }

        @Test
        void get_deniesWithoutReadAuthority() throws Exception {
            mockMvc.perform(get("/sys/roles/9")
                    .with(user("plain-user").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithAnonymousUser
        void list_deniesAnonymous() throws Exception {
            mockMvc.perform(get("/sys/roles"))
                .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("WRITE")
    class WriteAccess {

        @Test
        void create_allowsCreateAuthority() throws Exception {
            when(roleService.create(any(RoleCreateUpdateDto.class))).thenReturn(sampleRoleResponse());

            mockMvc.perform(post("/sys/roles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRoleDto()))
                    .with(user("role-creator").authorities(new SimpleGrantedAuthority("system:role:create"))))
                .andExpect(status().isOk());
        }

        @Test
        void update_allowsEditAuthority() throws Exception {
            when(roleService.update(anyLong(), any(RoleCreateUpdateDto.class))).thenReturn(sampleRoleResponse());

            mockMvc.perform(put("/sys/roles/8")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRoleDto()))
                    .with(user("role-editor").authorities(new SimpleGrantedAuthority("system:role:edit"))))
                .andExpect(status().isOk());
        }

        @Test
        void delete_deniesReadOnlyAuthority() throws Exception {
            mockMvc.perform(delete("/sys/roles/8")
                    .with(user("role-reader").authorities(new SimpleGrantedAuthority("system:role:list"))))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("ASSIGN")
    class AssignAccess {

        @Test
        void getRoleUsers_allowsUserRoleAssignAuthority() throws Exception {
            when(roleService.getUserIdsByRoleId(6L)).thenReturn(List.of(1L, 2L));

            mockMvc.perform(get("/sys/roles/6/users")
                    .with(user("role-user-assigner").authorities(new SimpleGrantedAuthority("system:user:role:assign"))))
                .andExpect(status().isOk());
        }

        @Test
        void updateRoleUsers_deniesRoleEditAuthorityOnly() throws Exception {
            mockMvc.perform(post("/sys/roles/6/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "userIds", List.of(2L, 3L),
                        "scopeType", "TENANT")))
                    .with(user("role-editor").authorities(new SimpleGrantedAuthority("system:role:edit"))))
                .andExpect(status().isForbidden());
        }

        @Test
        void getRoleResources_allowsPermissionAssignAuthority() throws Exception {
            when(roleService.getResourceIdsByRoleId(6L)).thenReturn(List.of(9L));

            mockMvc.perform(get("/sys/roles/6/resources")
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isOk());
        }

        @Test
        void getRolePermissions_allowsPermissionAssignAuthority() throws Exception {
            when(roleService.getPermissionIdsByRoleId(6L)).thenReturn(List.of(9001L));

            mockMvc.perform(get("/sys/roles/6/permissions")
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isOk());
        }

        @Test
        void updateRoleResources_deniesReadAuthority() throws Exception {
            mockMvc.perform(post("/sys/roles/6/resources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("permissionIds", List.of(4L, 5L))))
                    .with(user("role-reader").authorities(new SimpleGrantedAuthority("system:role:list"))))
                .andExpect(status().isForbidden());
        }

        @Test
        void updateRolePermissions_deniesReadAuthority() throws Exception {
            mockMvc.perform(post("/sys/roles/6/permissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("permissionIds", List.of(4L, 5L))))
                    .with(user("role-reader").authorities(new SimpleGrantedAuthority("system:role:list"))))
                .andExpect(status().isForbidden());
        }

        @Test
        void updateRoleResources_returnsBadRequest_whenPermissionIdsMissing() throws Exception {
            mockMvc.perform(post("/sys/roles/6/resources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of()))
                    .with(user("role-permission-assigner").authorities(new SimpleGrantedAuthority("system:role:permission:assign"))))
                .andExpect(status().isBadRequest());
        }
    }

    private static RoleCreateUpdateDto validRoleDto() {
        RoleCreateUpdateDto dto = new RoleCreateUpdateDto();
        dto.setName("Operator");
        dto.setCode("ROLE_OPERATOR");
        dto.setDescription("operator");
        dto.setEnabled(true);
        return dto;
    }

    private static RoleResponseDto sampleRoleResponse() {
        return new RoleResponseDto(6L, "Role", "ROLE_SAMPLE", "desc", false, true, null, null);
    }

    @SuppressWarnings("unused")
    private static Role sampleRole() {
        Role role = new Role();
        role.setId(6L);
        role.setName("Role");
        role.setCode("ROLE_SAMPLE");
        role.setDescription("desc");
        role.setEnabled(true);
        return role;
    }
}
