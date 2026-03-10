package com.tiny.platform.core.oauth.controller;

import com.tiny.platform.core.oauth.config.FrontendProperties;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
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
    private SecurityController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        securityService = mock(SecurityService.class);
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
            auditService
        );
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void status_whenNotLoggedIn_shouldReturn401() {
        ResponseEntity<Map<String, Object>> resp = controller.status();

        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals(Boolean.FALSE, resp.getBody().get("success"));
    }

    @Test
    void status_whenLoggedIn_shouldDelegateToService() {
        TenantContext.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("alice", "n/a", List.of())
        );
        User user = mock(User.class);
        when(user.getId()).thenReturn(10L);
        when(user.getUsername()).thenReturn("alice");
        when(userRepository.findUserByUsernameAndTenantId("alice", 1L)).thenReturn(Optional.of(user));
        when(securityService.getSecurityStatus(user)).thenReturn(Map.of("success", true));

        ResponseEntity<Map<String, Object>> resp = controller.status();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(Boolean.TRUE, resp.getBody().get("success"));
        verify(securityService).getSecurityStatus(user);
    }
}

