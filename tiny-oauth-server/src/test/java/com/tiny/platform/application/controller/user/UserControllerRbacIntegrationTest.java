package com.tiny.platform.application.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.dto.UserCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationAuditRepository;
import com.tiny.platform.infrastructure.auth.user.service.AvatarService;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.infrastructure.auth.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.data.autoconfigure.web.DataWebAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK, classes = UserControllerRbacIntegrationTest.RbacTestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("rbac-test")
class UserControllerRbacIntegrationTest {

    @SpringBootConfiguration
    @Import({
        UserControllerRbacTestConfig.class,
        UserController.class,
        DataWebAutoConfiguration.class,
        org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration.class,
        org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration.class
    })
    static class RbacTestApp {}

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAuthenticationAuditRepository auditRepository;

    @MockitoBean
    private AvatarService avatarService;

    @MockitoBean
    private OrganizationUnitRepository organizationUnitRepository;

    @MockitoBean
    private UserUnitRepository userUnitRepository;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private AuthUserResolutionService authUserResolutionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("READ")
    class ReadAccess {

        @Test
        void list_allowsReadAuthority() throws Exception {
            UserResponseDto responseDto = new UserResponseDto(
                1L, "alice", "Alice", true, true, true, true,
                LocalDateTime.of(2026, 3, 1, 10, 0), 0, null, false, null
            );
            when(userService.users(any(), any())).thenReturn(new PageImpl<>(List.of(responseDto)));

            mockMvc.perform(get("/sys/users")
                    .with(user("reader").authorities(new SimpleGrantedAuthority("system:user:list"))))
                .andExpect(status().isOk());
        }

        @Test
        void get_allowsReadAuthority() throws Exception {
            UserResponseDto detail = new UserResponseDto(
                9L, "bob", "bob", true, true, true, true, null, 0, null, false, null);
            when(userService.findUserDtoById(9L)).thenReturn(Optional.of(detail));

            mockMvc.perform(get("/sys/users/9")
                    .with(user("reader").authorities(new SimpleGrantedAuthority("system:user:list"))))
                .andExpect(status().isOk());
        }

        @Test
        void list_deniesWithoutReadAuthority() throws Exception {
            mockMvc.perform(get("/sys/users")
                    .with(user("plain-user").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithAnonymousUser
        void list_deniesAnonymous() throws Exception {
            mockMvc.perform(get("/sys/users"))
                .andExpect(status().is4xxClientError());
        }

        @Test
        void current_allowsAuthenticatedUserWithoutManagementAuthority() throws Exception {
            when(userService.findByUsername("plain-user")).thenReturn(Optional.of(sampleUser(7L, "plain-user")));

            mockMvc.perform(get("/sys/users/current")
                    .with(user("plain-user").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("CREATE")
    class CreateAccess {

        @Test
        void create_allowsCreateAuthority() throws Exception {
            when(userService.createFromDto(any(UserCreateUpdateDto.class))).thenReturn(sampleUser(3L, "carol"));

            mockMvc.perform(post("/sys/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validUserDto()))
                    .with(user("creator").authorities(new SimpleGrantedAuthority("system:user:create"))))
                .andExpect(status().isOk());
        }

        @Test
        void create_deniesReadOnlyAuthority() throws Exception {
            mockMvc.perform(post("/sys/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validUserDto()))
                    .with(user("reader").authorities(new SimpleGrantedAuthority("system:user:list"))))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("UPDATE")
    class UpdateAccess {

        @Test
        void update_allowsEditAuthority() throws Exception {
            when(userService.updateFromDto(any(UserCreateUpdateDto.class))).thenReturn(sampleUser(3L, "carol"));

            mockMvc.perform(put("/sys/users/3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validUserDto()))
                    .with(user("editor").authorities(new SimpleGrantedAuthority("system:user:edit"))))
                .andExpect(status().isOk());
        }

        @Test
        void assignRoles_allowsEditAuthority() throws Exception {
            mockMvc.perform(post("/sys/users/3/roles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(List.of(1L, 2L)))
                    .with(user("editor").authorities(new SimpleGrantedAuthority("system:user:edit"))))
                .andExpect(status().isOk());
        }

        @Test
        void assignRoles_deniesReadOnlyAuthority() throws Exception {
            mockMvc.perform(post("/sys/users/3/roles")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(List.of(1L, 2L)))
                    .with(user("reader").authorities(new SimpleGrantedAuthority("system:user:list"))))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE")
    class DeleteAccess {

        @Test
        void delete_allowsDeleteAuthority() throws Exception {
            mockMvc.perform(delete("/sys/users/9")
                    .with(user("deleter").authorities(new SimpleGrantedAuthority("system:user:delete"))))
                .andExpect(status().isNoContent());
        }

        @Test
        void batchDelete_allowsBatchDeleteAuthority() throws Exception {
            mockMvc.perform(post("/sys/users/batch/delete")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(List.of(1L, 2L)))
                    .with(user("deleter").authorities(new SimpleGrantedAuthority("system:user:batch-delete"))))
                .andExpect(status().isOk());
        }

        @Test
        void delete_deniesReadOnlyAuthority() throws Exception {
            mockMvc.perform(delete("/sys/users/9")
                    .with(user("reader").authorities(new SimpleGrantedAuthority("system:user:list"))))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("ENABLE DISABLE")
    class EnableDisableAccess {

        @Test
        void batchEnable_allowsEnableAuthority() throws Exception {
            mockMvc.perform(post("/sys/users/batch/enable")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(List.of(1L, 2L)))
                    .with(user("enabler").authorities(new SimpleGrantedAuthority("system:user:batch-enable"))))
                .andExpect(status().isOk());
        }

        @Test
        void batchDisable_allowsDisableAuthority() throws Exception {
            mockMvc.perform(post("/sys/users/batch/disable")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(List.of(1L, 2L)))
                    .with(user("disabler").authorities(new SimpleGrantedAuthority("system:user:batch-disable"))))
                .andExpect(status().isOk());
        }

        @Test
        void batchEnable_deniesReadOnlyAuthority() throws Exception {
            mockMvc.perform(post("/sys/users/batch/enable")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(List.of(1L, 2L)))
                    .with(user("reader").authorities(new SimpleGrantedAuthority("system:user:list"))))
                .andExpect(status().isForbidden());
        }
    }

    private UserCreateUpdateDto validUserDto() {
        UserCreateUpdateDto dto = new UserCreateUpdateDto();
        dto.setUsername("carol");
        dto.setNickname("Carol");
        dto.setPassword("secret123");
        dto.setConfirmPassword("secret123");
        dto.setEnabled(true);
        dto.setAccountNonExpired(true);
        dto.setAccountNonLocked(true);
        dto.setCredentialsNonExpired(true);
        return dto;
    }

    private User sampleUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username);
        user.setEmail(username + "@example.com");
        user.setPhone("13800000000");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        user.setFailedLoginCount(0);
        return user;
    }
}
