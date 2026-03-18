package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.config.LoginProtectionProperties;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.LoginFailurePolicy;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomLoginFailureHandlerTest {

    private final AuthUserResolutionService authUserResolutionService = mock(AuthUserResolutionService.class);

    private static LoginFailurePolicy loginFailurePolicy() {
        LoginProtectionProperties properties = new LoginProtectionProperties();
        properties.setMaxFailedAttempts(5);
        properties.setLockMinutes(15);
        return new LoginFailurePolicy(properties);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldRedirectAndSkipAuditWhenUsernameBlank() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, authUserResolutionService, auditService, loginFailurePolicy());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("username", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true&message=bad");
        verify(authUserResolutionService, never()).resolveUserRecordInActiveTenant(any(), any());
        verify(auditService, never()).recordLoginFailure(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRecordFailedLoginForExistingTenantUserAndRedirect() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, authUserResolutionService, auditService, loginFailurePolicy());

        User user = new User();
        user.setId(99L);
        user.setUsername("alice");
        user.setFailedLoginCount(1);

        TenantContext.setActiveTenantId(10L);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("alice", 10L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.9");
        request.setParameter("username", "alice");
        request.setParameter("redirect", "/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad password"));

        assertThat(response.getRedirectedUrl()).startsWith("/login?error=true");
        assertThat(response.getRedirectedUrl()).contains("redirect=%2Fdashboard");
        assertThat(response.getRedirectedUrl()).contains("message=bad+password");
        assertThat(user.getFailedLoginCount()).isEqualTo(2);
        assertThat(user.getLastFailedLoginAt()).isNotNull();
        verify(userRepository).save(user);
        verify(auditService).recordLoginFailure(eq("alice"), eq(99L), eq("LOCAL"), eq("PASSWORD"), same(request));
    }

    @Test
    void shouldAuditFailureWithNullUserWhenTenantMissing() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, authUserResolutionService, auditService, loginFailurePolicy());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("username", "bob");
        request.setParameter("authenticationProvider", "LDAP");
        request.setParameter("authenticationType", "PASSWORD");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).startsWith("/login?error=true");
        assertThat(response.getRedirectedUrl()).contains("message=bad");
        verify(authUserResolutionService, never()).resolveUserRecordInActiveTenant(any(), any());
        verify(auditService).recordLoginFailure(eq("bob"), isNull(), eq("LDAP"), eq("PASSWORD"), same(request));
    }

    @Test
    void shouldFallbackToAuditWhenRepositoryThrowsAndIgnoreAuditErrors() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, authUserResolutionService, auditService, loginFailurePolicy());

        TenantContext.setActiveTenantId(20L);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("charlie", 20L))
                .thenThrow(new RuntimeException("db down"));
        doThrow(new RuntimeException("audit down"))
                .when(auditService)
                .recordLoginFailure(eq("charlie"), isNull(), eq("LOCAL"), eq("PASSWORD"), any(MockHttpServletRequest.class));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("username", "charlie");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).startsWith("/login?error=true");
        verify(auditService).recordLoginFailure(eq("charlie"), isNull(), eq("LOCAL"), eq("PASSWORD"), same(request));
    }

    @Test
    void shouldResolveTenantFromSessionActiveTenantWhenTenantContextMissing() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, authUserResolutionService, auditService, loginFailurePolicy());

        User user = new User();
        user.setId(12L);
        user.setUsername("grace");
        user.setFailedLoginCount(0);

        when(authUserResolutionService.resolveUserRecordInActiveTenant("grace", 66L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, 66L);
        request.setParameter("username", "grace");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).startsWith("/login?error=true");
        verify(authUserResolutionService).resolveUserRecordInActiveTenant("grace", 66L);
        verify(auditService).recordLoginFailure(eq("grace"), eq(12L), eq("LOCAL"), eq("PASSWORD"), same(request));
    }

    @Test
    void shouldFallbackToAuditWhenSaveFailedInsideRecordFailedLogin() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, authUserResolutionService, auditService, loginFailurePolicy());

        User user = new User();
        user.setId(7L);
        user.setUsername("dave");
        user.setFailedLoginCount(null);

        TenantContext.setActiveTenantId(88L);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("dave", 88L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("save failed"));
        doNothing().when(auditService).recordLoginFailure(eq("dave"), eq(7L), eq("LOCAL"), eq("PASSWORD"), any());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("username", "dave");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).startsWith("/login?error=true");
        // recordFailedLogin catch 不应中断审计流程
        verify(auditService).recordLoginFailure(eq("dave"), eq(7L), eq("LOCAL"), eq("PASSWORD"), same(request));
    }

    @Test
    void shouldResetExpiredFailureWindowBeforeIncrementingAgain() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, authUserResolutionService, auditService, loginFailurePolicy());

        User user = new User();
        user.setId(8L);
        user.setUsername("erin");
        user.setFailedLoginCount(5);
        user.setLastFailedLoginAt(java.time.LocalDateTime.now().minusMinutes(30));

        TenantContext.setActiveTenantId(88L);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("erin", 88L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("username", "erin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).startsWith("/login?error=true");
        assertThat(user.getFailedLoginCount()).isEqualTo(1);
        verify(auditService).recordLoginFailure(eq("erin"), eq(8L), eq("LOCAL"), eq("PASSWORD"), same(request));
    }

    @Test
    void shouldNotIncrementCounterWhenFailureIsLockedRejection() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, authUserResolutionService, auditService, loginFailurePolicy());

        User user = new User();
        user.setId(10L);
        user.setUsername("frank");
        user.setFailedLoginCount(5);

        TenantContext.setActiveTenantId(88L);
        when(authUserResolutionService.resolveUserRecordInActiveTenant("frank", 88L)).thenReturn(Optional.of(user));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("username", "frank");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new LockedException("locked"));

        assertThat(response.getRedirectedUrl()).startsWith("/login?error=true");
        assertThat(response.getRedirectedUrl()).contains("message=locked");
        assertThat(user.getFailedLoginCount()).isEqualTo(5);
        verify(userRepository, never()).save(any(User.class));
        verify(auditService).recordLoginFailure(eq("frank"), eq(10L), eq("LOCAL"), eq("PASSWORD"), same(request));
    }
}
