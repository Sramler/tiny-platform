package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

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

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldRedirectAndSkipAuditWhenUsernameBlank() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, auditService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("username", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
        verify(userRepository, never()).findUserByUsernameAndTenantId(any(), any());
        verify(auditService, never()).recordLoginFailure(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRecordFailedLoginForExistingTenantUserAndRedirect() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, auditService);

        User user = new User();
        user.setId(99L);
        user.setUsername("alice");
        user.setFailedLoginCount(1);

        TenantContext.setTenantId(10L);
        when(userRepository.findUserByUsernameAndTenantId("alice", 10L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.9");
        request.setParameter("username", "alice");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad password"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
        assertThat(user.getFailedLoginCount()).isEqualTo(2);
        assertThat(user.getLastFailedLoginAt()).isNotNull();
        verify(userRepository).save(user);
        verify(auditService).recordLoginFailure(eq("alice"), eq(99L), eq("LOCAL"), eq("PASSWORD"), same(request));
    }

    @Test
    void shouldAuditFailureWithNullUserWhenTenantMissing() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, auditService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("username", "bob");
        request.setParameter("authenticationProvider", "LDAP");
        request.setParameter("authenticationType", "PASSWORD");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
        verify(userRepository, never()).findUserByUsernameAndTenantId(any(), any());
        verify(auditService).recordLoginFailure(eq("bob"), isNull(), eq("LDAP"), eq("PASSWORD"), same(request));
    }

    @Test
    void shouldFallbackToAuditWhenRepositoryThrowsAndIgnoreAuditErrors() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, auditService);

        TenantContext.setTenantId(20L);
        when(userRepository.findUserByUsernameAndTenantId("charlie", 20L))
                .thenThrow(new RuntimeException("db down"));
        doThrow(new RuntimeException("audit down"))
                .when(auditService)
                .recordLoginFailure(eq("charlie"), isNull(), eq("LOCAL"), eq("PASSWORD"), any(MockHttpServletRequest.class));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("username", "charlie");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
        verify(auditService).recordLoginFailure(eq("charlie"), isNull(), eq("LOCAL"), eq("PASSWORD"), same(request));
    }

    @Test
    void shouldFallbackToAuditWhenSaveFailedInsideRecordFailedLogin() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        CustomLoginFailureHandler handler = new CustomLoginFailureHandler(userRepository, auditService);

        User user = new User();
        user.setId(7L);
        user.setUsername("dave");
        user.setFailedLoginCount(null);

        TenantContext.setTenantId(88L);
        when(userRepository.findUserByUsernameAndTenantId("dave", 88L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("save failed"));
        doNothing().when(auditService).recordLoginFailure(eq("dave"), eq(7L), eq("LOCAL"), eq("PASSWORD"), any());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("username", "dave");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=true");
        // recordFailedLogin catch 不应中断审计流程
        verify(auditService).recordLoginFailure(eq("dave"), eq(7L), eq("LOCAL"), eq("PASSWORD"), same(request));
    }
}
