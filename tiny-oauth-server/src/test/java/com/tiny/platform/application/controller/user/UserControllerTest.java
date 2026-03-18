package com.tiny.platform.application.controller.user;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationAudit;
import com.tiny.platform.infrastructure.auth.user.dto.UserCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserRequestDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationAuditRepository;
import com.tiny.platform.infrastructure.auth.user.service.AvatarService;
import com.tiny.platform.infrastructure.auth.user.service.UserService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private static void assertActiveTenantResponse(Map<String, Object> payload, long expectedTenantId) {
        assertThat(payload).containsEntry("activeTenantId", expectedTenantId);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void should_cover_basic_crud_batch_and_role_endpoints() {
        UserService userService = mock(UserService.class);
        UserAuthenticationAuditRepository auditRepository = mock(UserAuthenticationAuditRepository.class);
        AvatarService avatarService = mock(AvatarService.class);
        UserController controller = new UserController(userService, auditRepository, avatarService);

        UserRequestDto query = new UserRequestDto();
        Pageable pageable = PageRequest.of(0, 10);
        UserResponseDto responseDto = new UserResponseDto(1L, "alice", "Alice", true, true, true, true,
            LocalDateTime.of(2026, 3, 1, 12, 0), 2, LocalDateTime.of(2026, 3, 1, 9, 0), true, 12);
        User user = user(2L, "bob");
        UserCreateUpdateDto dto = new UserCreateUpdateDto();
        dto.setUsername("bob");
        dto.setNickname("Bob");

        when(userService.users(query, pageable)).thenReturn(new PageImpl<>(List.of(responseDto), pageable, 1));
        when(userService.findById(2L)).thenReturn(Optional.of(user));
        when(userService.findById(99L)).thenReturn(Optional.empty());
        when(userService.createFromDto(dto)).thenReturn(user);
        when(userService.updateFromDto(dto)).thenReturn(user);

        PageResponse<UserResponseDto> listBody = controller.getUsers(query, pageable).getBody();
        assertThat(listBody).isNotNull();
        assertThat(listBody.getContent()).containsExactly(responseDto);
        assertThat(listBody.getContent().getFirst().getFailedLoginCount()).isEqualTo(2);
        assertThat(listBody.getContent().getFirst().getLastFailedLoginAt()).isEqualTo(LocalDateTime.of(2026, 3, 1, 9, 0));
        assertThat(listBody.getContent().getFirst().isTemporarilyLocked()).isTrue();
        assertThat(listBody.getContent().getFirst().getLockRemainingMinutes()).isEqualTo(12);

        assertThat(controller.getUser(2L).getBody()).isEqualTo(user);
        assertThat(controller.getUser(99L).getStatusCode().value()).isEqualTo(404);
        assertThat(controller.create(dto).getBody()).isEqualTo(user);
        assertThat(controller.update(2L, dto).getBody()).isEqualTo(user);
        assertThat(dto.getId()).isEqualTo(2L);

        assertThat(controller.delete(3L).getStatusCode().value()).isEqualTo(204);
        verify(userService).delete(3L);

        assertThat(controller.batchEnable(List.of(1L, 2L)).getBody())
            .containsEntry("success", true)
            .containsEntry("message", "批量启用成功");
        verify(userService).batchEnable(List.of(1L, 2L));

        assertThat(controller.batchDisable(List.of(1L, 2L)).getBody())
            .containsEntry("success", true)
            .containsEntry("message", "批量禁用成功");
        verify(userService).batchDisable(List.of(1L, 2L));

        assertThat(controller.batchDelete(List.of(1L, 2L)).getBody())
            .containsEntry("success", true)
            .containsEntry("message", "批量删除成功");
        verify(userService).batchDelete(List.of(1L, 2L));

        when(userService.getRoleIdsByUserId(2L)).thenReturn(List.of(10L, 11L));
        when(userService.getRoleIdsByUserId(99L)).thenThrow(new RuntimeException("missing"));
        assertThat(controller.getUserRoles(2L).getBody()).containsExactlyInAnyOrder(10L, 11L);
        assertThat(controller.getUserRoles(99L).getStatusCode().value()).isEqualTo(404);

        assertThat(controller.updateUserRoles(2L, List.of(10L)).getStatusCode().value()).isEqualTo(200);
        verify(userService).updateUserRoles(2L, List.of(10L));

        doThrow(new RuntimeException("role-error")).when(userService).updateUserRoles(3L, List.of(11L));
        assertThat(asMap(controller.updateUserRoles(3L, List.of(11L)).getBody()))
            .containsEntry("success", false)
            .containsEntry("message", "role-error");
    }

    @Test
    void should_cover_getCurrentUser_branches() {
        UserService userService = mock(UserService.class);
        UserController controller = new UserController(userService, mock(UserAuthenticationAuditRepository.class), mock(AvatarService.class));

        SecurityContextHolder.clearContext();
        ResponseEntity<Map<String, Object>> unauthenticated = controller.getCurrentUser();
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthenticated.getBody()).containsEntry("success", false).containsEntry("error", "用户未认证");

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("alice");
        SecurityContextHolder.getContext().setAuthentication(auth);
        TenantContext.setActiveTenantId(9L);

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice")));
        Map<String, Object> successBody = controller.getCurrentUser().getBody();
        assertActiveTenantResponse(successBody, 9L);
        assertThat(successBody)
            .containsEntry("id", "1")
            .containsEntry("username", "alice")
            .containsEntry("nickname", "Alice")
            .containsEntry("enabled", true)
            .containsEntry("accountNonExpired", true)
            .containsEntry("accountNonLocked", true)
            .containsEntry("credentialsNonExpired", true)
            .containsEntry("email", "alice@example.com")
            .containsEntry("phone", "13800000000")
            .containsEntry("failedLoginCount", 2);
        assertThat(successBody.get("lastLoginAt")).isEqualTo("2026-03-01T10:00");
        assertThat(successBody.get("lastFailedLoginAt")).isEqualTo("2026-03-01T09:00");

        when(userService.findByUsername("alice")).thenReturn(Optional.empty());
        ResponseEntity<Map<String, Object>> notFound = controller.getCurrentUser();
        assertThat(notFound.getStatusCode().value()).isEqualTo(404);
        assertThat(notFound.getBody()).containsEntry("success", false).containsEntry("error", "用户不存在");

        when(userService.findByUsername("alice")).thenThrow(new RuntimeException("boom"));
        ResponseEntity<Map<String, Object>> error = controller.getCurrentUser();
        assertThat(error.getStatusCode().value()).isEqualTo(500);
        assertThat(error.getBody()).containsEntry("success", false).containsEntry("error", "boom");
    }

    @Test
    void should_prefer_active_tenant_from_authentication_when_context_is_absent() {
        UserService userService = mock(UserService.class);
        UserController controller = new UserController(userService, mock(UserAuthenticationAuditRepository.class), mock(AvatarService.class));

        User user = user(1L, "alice");
        user.setTenantId(3L);
        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));

        SecurityUser securityUser = new SecurityUser(
            1L,
            12L,
            "alice",
            "",
            List.of(),
            true,
            true,
            true,
            true
        );
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(securityUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Map<String, Object> body = controller.getCurrentUser().getBody();
        assertActiveTenantResponse(body, 12L);
    }

    @Test
    void should_cover_login_history_branches() {
        UserService userService = mock(UserService.class);
        UserAuthenticationAuditRepository auditRepository = mock(UserAuthenticationAuditRepository.class);
        UserController controller = new UserController(userService, auditRepository, mock(AvatarService.class));
        Pageable pageable = PageRequest.of(0, 20);

        SecurityContextHolder.clearContext();
        assertThat(controller.getCurrentUserLoginHistory(pageable).getStatusCode().value()).isEqualTo(401);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("alice");
        SecurityContextHolder.getContext().setAuthentication(auth);

        User user = user(1L, "alice");
        UserAuthenticationAudit audit = new UserAuthenticationAudit();
        audit.setId(5L);
        audit.setEventType("LOGIN");
        audit.setSuccess(true);
        audit.setAuthenticationProvider("LOCAL");
        audit.setAuthenticationFactor("PASSWORD");
        audit.setIpAddress("127.0.0.1");
        audit.setUserAgent("JUnit");
        audit.setCreatedAt(LocalDateTime.of(2026, 3, 1, 10, 0));

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        when(auditRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(1L, "LOGIN", pageable))
            .thenReturn(new PageImpl<>(List.of(audit), pageable, 1));

        Map<String, Object> body = controller.getCurrentUserLoginHistory(pageable).getBody();
        assertThat(body).containsEntry("success", true);
        assertThat(body).containsEntry("totalElements", 1L);
        assertThat(body).containsEntry("totalPages", 1);
        assertThat(body).containsEntry("number", 0);
        assertThat(body).containsEntry("size", 20);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.getFirst())
            .containsEntry("id", 5L)
            .containsEntry("eventType", "LOGIN")
            .containsEntry("success", true)
            .containsEntry("authenticationProvider", "LOCAL")
            .containsEntry("authenticationFactor", "PASSWORD")
            .containsEntry("ipAddress", "127.0.0.1")
            .containsEntry("userAgent", "JUnit")
            .containsEntry("createdAt", "2026-03-01T10:00");

        when(userService.findByUsername("alice")).thenReturn(Optional.empty());
        assertThat(controller.getCurrentUserLoginHistory(pageable).getStatusCode().value()).isEqualTo(404);

        when(userService.findByUsername("alice")).thenThrow(new RuntimeException("audit-boom"));
        ResponseEntity<Map<String, Object>> error = controller.getCurrentUserLoginHistory(pageable);
        assertThat(error.getStatusCode().value()).isEqualTo(500);
        assertThat(error.getBody()).containsEntry("success", false).containsEntry("error", "audit-boom");
    }

    @Test
    void should_cover_upload_avatar_branches() throws Exception {
        UserService userService = mock(UserService.class);
        AvatarService avatarService = mock(AvatarService.class);
        UserController controller = new UserController(userService, mock(UserAuthenticationAuditRepository.class), avatarService);
        MultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});

        SecurityContextHolder.clearContext();
        assertThat(controller.uploadCurrentUserAvatar(file).getStatusCode().value()).isEqualTo(401);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("alice");
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userService.findByUsername("alice")).thenReturn(Optional.empty());
        assertThat(controller.uploadCurrentUserAvatar(file).getStatusCode().value()).isEqualTo(404);

        User user = user(1L, "alice");
        when(userService.findByUsername("alice")).thenReturn(Optional.of(user));
        MultipartFile emptyFile = new MockMultipartFile("file", "a.png", "image/png", new byte[]{});
        ResponseEntity<Map<String, Object>> empty = controller.uploadCurrentUserAvatar(emptyFile);
        assertThat(empty.getStatusCode().value()).isEqualTo(400);
        assertThat(empty.getBody()).containsEntry("success", false).containsEntry("error", "文件不能为空");

        when(avatarService.uploadAvatar(anyLong(), any(), anyString(), anyString(), eq(3L))).thenReturn(true);
        ResponseEntity<Map<String, Object>> success = controller.uploadCurrentUserAvatar(file);
        assertThat(success.getBody()).containsEntry("success", true).containsEntry("message", "头像上传成功");

        when(avatarService.uploadAvatar(anyLong(), any(), anyString(), anyString(), eq(3L))).thenReturn(false);
        ResponseEntity<Map<String, Object>> failed = controller.uploadCurrentUserAvatar(file);
        assertThat(failed.getStatusCode().value()).isEqualTo(400);
        assertThat(failed.getBody()).containsEntry("success", false).containsEntry("error", "头像上传失败");

        when(avatarService.uploadAvatar(anyLong(), any(), anyString(), anyString(), eq(3L)))
            .thenThrow(new IllegalArgumentException("bad-file"));
        ResponseEntity<Map<String, Object>> badRequest = controller.uploadCurrentUserAvatar(file);
        assertThat(badRequest.getStatusCode().value()).isEqualTo(400);
        assertThat(badRequest.getBody()).containsEntry("success", false).containsEntry("error", "bad-file");

        when(avatarService.uploadAvatar(anyLong(), any(), anyString(), anyString(), eq(3L)))
            .thenThrow(new RuntimeException("disk-down"));
        ResponseEntity<Map<String, Object>> serverError = controller.uploadCurrentUserAvatar(file);
        assertThat(serverError.getStatusCode().value()).isEqualTo(500);
        assertThat(serverError.getBody()).containsEntry("success", false).containsEntry("error", "头像上传失败: disk-down");

        when(userService.findByUsername("alice")).thenThrow(new RuntimeException("outer"));
        ResponseEntity<Map<String, Object>> outerError = controller.uploadCurrentUserAvatar(file);
        assertThat(outerError.getStatusCode().value()).isEqualTo(500);
        assertThat(outerError.getBody()).containsEntry("success", false).containsEntry("error", "头像上传失败: outer");
    }

    @Test
    void should_cover_get_user_avatar_and_current_user_avatar() throws IOException {
        UserService userService = mock(UserService.class);
        AvatarService avatarService = mock(AvatarService.class);
        UserController controller = new UserController(userService, mock(UserAuthenticationAuditRepository.class), avatarService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(avatarService.getAvatarMetadata(1L)).thenReturn(null);
        assertThat(controller.getUserAvatar(1L, request, response).getStatusCode().value()).isEqualTo(404);

        AvatarService.AvatarMetadata metadata = new AvatarService.AvatarMetadata("image/png", "a.png", 3, "hash1");
        when(avatarService.getAvatarMetadata(2L)).thenReturn(metadata);
        when(avatarService.getAvatarData(2L)).thenReturn(null);
        assertThat(controller.getUserAvatar(2L, request, response).getStatusCode().value()).isEqualTo(404);

        when(avatarService.getAvatarMetadata(3L)).thenReturn(metadata);
        when(avatarService.getAvatarData(3L)).thenReturn(new byte[]{1, 2, 3});
        request.addHeader("If-None-Match", "\"hash1\"");
        ResponseEntity<StreamingResponseBody> notModified = controller.getUserAvatar(3L, request, response);
        assertThat(notModified.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);

        MockHttpServletRequest okRequest = new MockHttpServletRequest();
        ResponseEntity<StreamingResponseBody> ok = controller.getUserAvatar(3L, okRequest, response);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getHeaders().getETag()).isEqualTo("\"hash1\"");
        assertThat(ok.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(ok.getHeaders().getContentLength()).isEqualTo(3);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ok.getBody().writeTo(outputStream);
        assertThat(outputStream.toByteArray()).containsExactly(1, 2, 3);

        when(avatarService.getAvatarMetadata(4L)).thenThrow(new RuntimeException("avatar-error"));
        assertThat(controller.getUserAvatar(4L, new MockHttpServletRequest(), response).getStatusCode().value()).isEqualTo(500);

        SecurityContextHolder.clearContext();
        assertThat(controller.getCurrentUserAvatar(new MockHttpServletRequest(), response).getStatusCode().value()).isEqualTo(401);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("alice");
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userService.findByUsername("alice")).thenReturn(Optional.empty());
        assertThat(controller.getCurrentUserAvatar(new MockHttpServletRequest(), response).getStatusCode().value()).isEqualTo(404);

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user(3L, "alice")));
        ResponseEntity<StreamingResponseBody> currentOk = controller.getCurrentUserAvatar(new MockHttpServletRequest(), response);
        assertThat(currentOk.getStatusCode()).isEqualTo(HttpStatus.OK);

        when(userService.findByUsername("alice")).thenThrow(new RuntimeException("current-avatar-error"));
        assertThat(controller.getCurrentUserAvatar(new MockHttpServletRequest(), response).getStatusCode().value()).isEqualTo(500);
    }

    @Test
    void should_cover_delete_current_user_avatar_branches() {
        UserService userService = mock(UserService.class);
        AvatarService avatarService = mock(AvatarService.class);
        UserController controller = new UserController(userService, mock(UserAuthenticationAuditRepository.class), avatarService);

        SecurityContextHolder.clearContext();
        assertThat(controller.deleteCurrentUserAvatar().getStatusCode().value()).isEqualTo(401);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("alice");
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userService.findByUsername("alice")).thenReturn(Optional.empty());
        assertThat(controller.deleteCurrentUserAvatar().getStatusCode().value()).isEqualTo(404);

        when(userService.findByUsername("alice")).thenReturn(Optional.of(user(1L, "alice")));
        when(avatarService.deleteAvatar(1L)).thenReturn(true, false);

        assertThat(controller.deleteCurrentUserAvatar().getBody())
            .containsEntry("success", true)
            .containsEntry("message", "头像删除成功");

        ResponseEntity<Map<String, Object>> missing = controller.deleteCurrentUserAvatar();
        assertThat(missing.getStatusCode().value()).isEqualTo(400);
        assertThat(missing.getBody()).containsEntry("success", false).containsEntry("error", "头像不存在");

        when(userService.findByUsername("alice")).thenThrow(new RuntimeException("delete-error"));
        ResponseEntity<Map<String, Object>> error = controller.deleteCurrentUserAvatar();
        assertThat(error.getStatusCode().value()).isEqualTo(500);
        assertThat(error.getBody()).containsEntry("success", false).containsEntry("error", "头像删除失败: delete-error");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname("Alice");
        user.setEnabled(true);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        user.setEmail("alice@example.com");
        user.setPhone("13800000000");
        user.setLastLoginAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        user.setLastLoginIp("127.0.0.1");
        user.setLastLoginDevice("Chrome");
        user.setFailedLoginCount(2);
        user.setLastFailedLoginAt(LocalDateTime.of(2026, 3, 1, 9, 0));
        return user;
    }
}
