package com.tiny.platform.core.oauth.session;

import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserSessionServiceImplTest {

    private UserSessionRepository userSessionRepository;
    private AuthenticationAuditService authenticationAuditService;
    private TenantRepository tenantRepository;
    private UserSessionServiceImpl userSessionService;

    @BeforeEach
    void setUp() {
        userSessionRepository = mock(UserSessionRepository.class);
        authenticationAuditService = mock(AuthenticationAuditService.class);
        tenantRepository = mock(TenantRepository.class);
        userSessionService = new UserSessionServiceImpl(userSessionRepository, authenticationAuditService, tenantRepository);
    }

    @Test
    void registerOrTouch_shouldCreateNewActiveSession() {
        when(tenantRepository.findLoginBlockedLifecycleStatus(9L)).thenReturn(Optional.empty());
        when(userSessionRepository.findBySessionId("sid-1")).thenReturn(Optional.empty());
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSessionState state = userSessionService.registerOrTouch(new SessionTouchRequest(
            "sid-1",
            1L,
            9L,
            "alice",
            "LOCAL",
            "PASSWORD",
            "127.0.0.1",
            "Chrome",
            LocalDateTime.now(),
            LocalDateTime.now().plusMinutes(30)
        ));

        assertThat(state).isEqualTo(UserSessionState.ACTIVE);
        verify(userSessionRepository).save(argThat(session ->
            "sid-1".equals(session.getSessionId())
                && Long.valueOf(1L).equals(session.getUserId())
                && session.getStatus() == UserSessionState.ACTIVE
        ));
    }

    @Test
    void registerOrTouch_shouldRejectRevokedSession() {
        when(tenantRepository.findLoginBlockedLifecycleStatus(9L)).thenReturn(Optional.empty());
        UserSession session = new UserSession();
        session.setSessionId("sid-1");
        session.setUserId(1L);
        session.setTenantId(9L);
        session.setStatus(UserSessionState.REVOKED);
        when(userSessionRepository.findBySessionId("sid-1")).thenReturn(Optional.of(session));

        UserSessionState state = userSessionService.registerOrTouch(new SessionTouchRequest(
            "sid-1",
            1L,
            9L,
            "alice",
            "LOCAL",
            "PASSWORD",
            "127.0.0.1",
            "Chrome",
            LocalDateTime.now(),
            LocalDateTime.now().plusMinutes(30)
        ));

        assertThat(state).isEqualTo(UserSessionState.REVOKED);
        verify(userSessionRepository, never()).save(any(UserSession.class));
    }

    @Test
    void registerOrTouch_shouldNotCreateSessionWhenTenantLifecycleBlocked() {
        when(tenantRepository.findLoginBlockedLifecycleStatus(9L)).thenReturn(Optional.of("FROZEN"));
        when(userSessionRepository.findBySessionId("sid-1")).thenReturn(Optional.empty());

        UserSessionState state = userSessionService.registerOrTouch(new SessionTouchRequest(
            "sid-1",
            1L,
            9L,
            "alice",
            "LOCAL",
            "PASSWORD",
            "127.0.0.1",
            "Chrome",
            LocalDateTime.now(),
            LocalDateTime.now().plusMinutes(30)
        ));

        assertThat(state).isEqualTo(UserSessionState.ACTIVE);
        verify(userSessionRepository, never()).save(any(UserSession.class));
    }

    @Test
    void revokeOtherSessions_shouldSkipCurrentSessionAndAuditOthers() {
        UserSession current = new UserSession();
        current.setSessionId("sid-current");
        current.setUserId(1L);
        current.setTenantId(9L);
        current.setStatus(UserSessionState.ACTIVE);

        UserSession other = new UserSession();
        other.setSessionId("sid-other");
        other.setUserId(1L);
        other.setTenantId(9L);
        other.setStatus(UserSessionState.ACTIVE);

        when(userSessionRepository.findByUserIdAndTenantIdAndStatusOrderByActivityDesc(1L, 9L, UserSessionState.ACTIVE))
            .thenReturn(List.of(current, other));
        when(userSessionRepository.save(any(UserSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int revokedCount = userSessionService.revokeOtherSessions(1L, 9L, "sid-current", "alice", null);

        assertThat(revokedCount).isEqualTo(1);
        verify(authenticationAuditService).recordSessionRevoke(eq("alice"), eq(1L), eq("sid-other"), eq(null));
        verify(authenticationAuditService, never()).recordSessionRevoke(eq("alice"), eq(1L), eq("sid-current"), eq(null));
    }
}
