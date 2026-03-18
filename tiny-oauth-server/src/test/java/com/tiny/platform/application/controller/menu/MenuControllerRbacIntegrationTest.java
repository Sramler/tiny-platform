package com.tiny.platform.application.controller.menu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.menu.service.MenuService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, classes = MenuControllerRbacIntegrationTest.RbacTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class MenuControllerRbacIntegrationTest {

    @SpringBootConfiguration
    @Import({
        MenuControllerRbacTestConfig.class,
        MenuController.class,
        org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class
    })
    static class RbacTestApp {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MenuService menuService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("READ")
    class ReadAccess {

        @Test
        void list_allowsReadAuthority() throws Exception {
            when(menuService.list(any())).thenReturn(List.of());

            mockMvc.perform(get("/sys/menus")
                    .with(user("menu-reader").authorities(new SimpleGrantedAuthority("system:menu:list"))))
                .andExpect(status().isOk());
        }

        @Test
        void fullTree_allowsLegacyViewAuthority() throws Exception {
            when(menuService.menuTreeAll()).thenReturn(List.of());

            mockMvc.perform(get("/sys/menus/tree/all")
                    .with(user("menu-viewer").authorities(new SimpleGrantedAuthority("system:menu:list"))))
                .andExpect(status().isOk());
        }

        @Test
        void list_deniesWithoutReadAuthority() throws Exception {
            mockMvc.perform(get("/sys/menus")
                    .with(user("plain-user").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithAnonymousUser
        void list_deniesAnonymous() throws Exception {
            mockMvc.perform(get("/sys/menus"))
                .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("CREATE")
    class CreateAccess {

        @Test
        void create_allowsCreateAuthority() throws Exception {
            when(menuService.createMenu(any(ResourceCreateUpdateDto.class))).thenReturn(new Resource());

            ResourceCreateUpdateDto dto = new ResourceCreateUpdateDto();
            dto.setName("menu");
            dto.setTitle("菜单");
            dto.setType(1);

            mockMvc.perform(post("/sys/menus")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("menu-editor").authorities(new SimpleGrantedAuthority("system:menu:create"))))
                .andExpect(status().isOk());
        }

        @Test
        void create_deniesReadOnlyAuthority() throws Exception {
            ResourceCreateUpdateDto dto = new ResourceCreateUpdateDto();
            dto.setName("menu");
            dto.setTitle("菜单");
            dto.setType(1);

            mockMvc.perform(post("/sys/menus")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(dto))
                    .with(user("menu-reader").authorities(new SimpleGrantedAuthority("system:menu:list"))))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE")
    class DeleteAccess {

        @Test
        void delete_allowsDeleteAuthority() throws Exception {
            mockMvc.perform(delete("/sys/menus/9")
                    .with(user("menu-deleter").authorities(new SimpleGrantedAuthority("system:menu:delete"))))
                .andExpect(status().isNoContent());
        }

        @Test
        void delete_deniesReadOnlyAuthority() throws Exception {
            mockMvc.perform(delete("/sys/menus/9")
                    .with(user("menu-reader").authorities(new SimpleGrantedAuthority("system:menu:list"))))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("UPDATE")
    class UpdateAccess {

        @Test
        void updateSort_allowsUpdateAuthority() throws Exception {
            when(menuService.updateMenuSort(9L, 8)).thenReturn(new Resource());

            mockMvc.perform(put("/sys/menus/9/sort")
                    .param("sort", "8")
                    .with(user("menu-editor").authorities(new SimpleGrantedAuthority("system:menu:edit"))))
                .andExpect(status().isOk());
        }

        @Test
        void updateSort_deniesReadOnlyAuthority() throws Exception {
            mockMvc.perform(put("/sys/menus/9/sort")
                    .param("sort", "8")
                    .with(user("menu-reader").authorities(new SimpleGrantedAuthority("system:menu:list"))))
                .andExpect(status().isForbidden());
        }
    }
}
