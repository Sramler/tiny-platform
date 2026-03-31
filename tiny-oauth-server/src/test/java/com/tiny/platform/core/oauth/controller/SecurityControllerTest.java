package com.tiny.platform.core.oauth.controller;

import com.tiny.platform.core.oauth.config.FrontendProperties;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.session.UserSessionService;
import com.tiny.platform.core.oauth.session.UserSessionView;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityControllerTest {

    private UserRepository userRepository;
    private SecurityService securityService;
    private AuthUserResolutionService authUserResolutionService;
    private UserSessionService userSessionService;
    private SecurityController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        securityService = mock(SecurityService.class);
        authUserResolutionService = mock(AuthUserResolutionService.class);
        userSessionService = mock(UserSessionService.class);
        UserAuthenticationMethodRepository authMethodRepo = mock(UserAuthenticationMethodRepository.class);
        FrontendProperties frontendProperties = mock(FrontendProperties.class);
        MultiFactorAuthenticationSessionManager sessionManager = mock(MultiFactorAuthenticationSessionManager.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        controller = new SecurityController(
            userRepository,
            securityService,
            authMethodRepo,
            frontendProperties,
            sessionManager,
            authUserResolutionService,
            auditService,
            userSessionService
        );
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private static void assertActiveTenantResponse(Map<String, Object> payload, long expectedTenantId) {
        assertEquals(expectedTenantId, payload.get("activeTenantId"));
    }

    @Test
    void status_whenNotLoggedIn_shouldReturn401() {
        ResponseEntity<Map<String, Object>> resp = controller.status();

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals(Boolean.FALSE, resp.getBody().get("success"));
    }

    @Test
    void status_whenLoggedIn_shouldDelegateToService() {
        TenantContext.setActiveTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("alice", "n/a", List.of())
        );
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);
        when(user.getUsername()).thenReturn("alice");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of("success", true));

        ResponseEntity<Map<String, Object>> resp = controller.status();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().get("success"));
        assertActiveTenantResponse(resp.getBody(), 1L);
        verify(securityService).getSecurityStatus(user);
    }

    @Test
    void status_should_support_membership_user_resolution() {
        TenantContext.setActiveTenantId(9L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("shared.alice", "n/a", List.of())
        );
        User user = mock(User.class);
        when(user.getId()).thenReturn(21L);
        when(user.getUsername()).thenReturn("shared.alice");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("shared.alice", 9L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of("success", true));

        ResponseEntity<Map<String, Object>> resp = controller.status();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().get("success"));
        assertActiveTenantResponse(resp.getBody(), 9L);
        verify(authUserResolutionService).resolveUserRecordInActiveTenant("shared.alice", 9L);
    }

    @Test
    void status_should_resolve_user_from_authentication_active_tenant_when_tenant_context_missing() {
        UserAuthenticationMethodRepository authMethodRepo = mock(UserAuthenticationMethodRepository.class);
        FrontendProperties frontendProperties = mock(FrontendProperties.class);
        MultiFactorAuthenticationSessionManager sessionManager = mock(MultiFactorAuthenticationSessionManager.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);
        controller = new SecurityController(
                userRepository,
                securityService,
                authMethodRepo,
                frontendProperties,
                sessionManager,
                authUserResolutionService,
                auditService,
                userSessionService
        );

        SecurityUser principal = new SecurityUser(21L, 7L, "shared.alice", "", List.of(), true, true, true, true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of())
        );
        User user = mock(User.class);
        when(user.getId()).thenReturn(21L);
        when(user.getUsername()).thenReturn("shared.alice");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("shared.alice", 7L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of("success", true));

        ResponseEntity<Map<String, Object>> resp = controller.status();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().get("success"));
        assertActiveTenantResponse(resp.getBody(), 7L);
        verify(authUserResolutionService).resolveUserRecordInActiveTenant("shared.alice", 7L);
    }

    @Test
    void preBindTotp_should_include_active_tenant_fields() {
        TenantContext.setActiveTenantId(9L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "n/a", List.of())
        );
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);
        when(user.getUsername()).thenReturn("alice");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 9L)).thenReturn(Optional.of(user));
        when(securityService.preBindTotp(user)).thenReturn(Map.of("success", true, "otpauthUri", "otpauth://totp/demo"));

        ResponseEntity<Map<String, Object>> resp = controller.preBindTotp();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().get("success"));
        assertActiveTenantResponse(resp.getBody(), 9L);
    }

    @Test
    void bindTotp_should_include_active_tenant_fields_on_success() {
        TenantContext.setActiveTenantId(9L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "n/a", List.of())
        );
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);
        when(user.getUsername()).thenReturn("alice");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 9L)).thenReturn(Optional.of(user));
        when(securityService.bindTotp(user, null, "123456")).thenReturn(Map.of("success", true));

        ResponseEntity<Map<String, Object>> resp = controller.bindTotp(Map.of("totpCode", "123456"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().get("success"));
        assertActiveTenantResponse(resp.getBody(), 9L);
    }

    @Test
    void bindTotp_should_include_active_tenant_fields_on_validation_error() {
        TenantContext.setActiveTenantId(9L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "n/a", List.of())
        );
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);
        when(user.getUsername()).thenReturn("alice");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 9L)).thenReturn(Optional.of(user));

        ResponseEntity<Map<String, Object>> resp = controller.bindTotp(Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(Boolean.FALSE, resp.getBody().get("success"));
        assertActiveTenantResponse(resp.getBody(), 9L);
    }

    @Test
    void listSessions_shouldReturnCurrentUserSessions() {
        TenantContext.setActiveTenantId(9L);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setSession(new org.springframework.mock.web.MockHttpSession(null, "sid-current"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("alice", "n/a", List.of())
        );
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);
        when(user.getUsername()).thenReturn("alice");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 9L)).thenReturn(Optional.of(user));
        when(userSessionService.listCurrentUserSessions(10L, 9L, "sid-current")).thenReturn(List.of(
            new UserSessionView("sid-current", true, "LOCAL", "PASSWORD", "127.0.0.1", "Chrome", null, null, null)
        ));

        ResponseEntity<Map<String, Object>> resp = controller.listSessions(request);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().get("success"));
        assertEquals("sid-current", resp.getBody().get("currentSessionId"));
        assertActiveTenantResponse(resp.getBody(), 9L);
    }

    @Test
    void revokeSession_shouldRejectCurrentSession() {
        TenantContext.setActiveTenantId(9L);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setSession(new org.springframework.mock.web.MockHttpSession(null, "sid-current"));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("alice", "n/a", List.of())
        );
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);
        when(user.getUsername()).thenReturn("alice");
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 9L)).thenReturn(Optional.of(user));

        ResponseEntity<Map<String, Object>> resp = controller.revokeSession("sid-current", request);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals(Boolean.FALSE, resp.getBody().get("success"));
        verify(userSessionService, never()).revokeSession(any(), any(), any(), any(), any());
    }
}
