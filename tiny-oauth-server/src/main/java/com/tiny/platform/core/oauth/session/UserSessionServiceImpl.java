package com.tiny.platform.core.oauth.session;

import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserSessionServiceImpl implements UserSessionService {

    private static final long TOUCH_INTERVAL_SECONDS = 30L;

    private final UserSessionRepository userSessionRepository;
    private final AuthenticationAuditService authenticationAuditService;
    private final TenantRepository tenantRepository;

    public UserSessionServiceImpl(UserSessionRepository userSessionRepository,
                                  AuthenticationAuditService authenticationAuditService,
                                  TenantRepository tenantRepository) {
        this.userSessionRepository = userSessionRepository;
        this.authenticationAuditService = authenticationAuditService;
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional
    public UserSessionState registerOrTouch(SessionTouchRequest request) {
        if (request == null
            || !StringUtils.hasText(request.sessionId())
            || request.userId() == null
            || !StringUtils.hasText(request.username())) {
            return UserSessionState.ACTIVE;
        }

        if (request.tenantId() != null && tenantRepository != null) {
            Optional<String> blockedLifecycleStatus = tenantRepository.findLoginBlockedLifecycleStatus(request.tenantId());
            if (blockedLifecycleStatus != null && blockedLifecycleStatus.isPresent()) {
                return UserSessionState.ACTIVE;
            }
        }

        LocalDateTime now = request.touchAt() != null ? request.touchAt() : LocalDateTime.now();
        return userSessionRepository.findBySessionId(request.sessionId())
            .map(existing -> handleExistingSession(existing, request, now))
            .orElseGet(() -> createActiveSession(request, now));
    }

    @Override
    @Transactional
    public List<UserSessionView> listCurrentUserSessions(Long userId, Long tenantId, String currentSessionId) {
        if (userId == null || tenantId == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<UserSessionView> results = new ArrayList<>();
        for (UserSession session : userSessionRepository.findByUserIdAndTenantIdAndStatusOrderByActivityDesc(
            userId,
            tenantId,
            UserSessionState.ACTIVE
        )) {
            if (isExpired(session, now)) {
                expire(session, now);
                continue;
            }
            results.add(toView(session, currentSessionId));
        }
        return results;
    }

    @Override
    @Transactional
    public boolean revokeSession(Long userId,
                                 Long tenantId,
                                 String sessionId,
                                 String actorUsername,
                                 HttpServletRequest request) {
        if (userId == null || tenantId == null || !StringUtils.hasText(sessionId)) {
            return false;
        }
        return userSessionRepository.findBySessionId(sessionId)
            .filter(session -> session.getUserId().equals(userId))
            .filter(session -> tenantId.equals(session.getTenantId()))
            .filter(session -> session.getStatus() == UserSessionState.ACTIVE)
            .map(session -> {
                revoke(session, LocalDateTime.now());
                authenticationAuditService.recordSessionRevoke(actorUsername, userId, session.getSessionId(), request);
                return true;
            })
            .orElse(false);
    }

    @Override
    @Transactional
    public int revokeOtherSessions(Long userId,
                                   Long tenantId,
                                   String currentSessionId,
                                   String actorUsername,
                                   HttpServletRequest request) {
        if (userId == null || tenantId == null) {
            return 0;
        }
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        for (UserSession session : userSessionRepository.findByUserIdAndTenantIdAndStatusOrderByActivityDesc(
            userId,
            tenantId,
            UserSessionState.ACTIVE
        )) {
            if (session.getSessionId().equals(currentSessionId)) {
                continue;
            }
            revoke(session, now);
            authenticationAuditService.recordSessionRevoke(actorUsername, userId, session.getSessionId(), request);
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public void markLoggedOut(String sessionId) {
        markEnded(sessionId, UserSessionState.LOGGED_OUT);
    }

    @Override
    @Transactional
    public void markExpired(String sessionId) {
        markEnded(sessionId, UserSessionState.EXPIRED);
    }

    private UserSessionState handleExistingSession(UserSession existing,
                                                   SessionTouchRequest request,
                                                   LocalDateTime now) {
        if (existing.getStatus() != UserSessionState.ACTIVE) {
            return existing.getStatus();
        }
        if (isExpired(existing, now)) {
            expire(existing, now);
            return UserSessionState.EXPIRED;
        }
        if (shouldTouch(existing, now)) {
            existing.setLastSeenAt(now);
            existing.setExpiresAt(request.expiresAt());
            updateSessionSnapshot(existing, request);
            userSessionRepository.save(existing);
        }
        return UserSessionState.ACTIVE;
    }

    private UserSessionState createActiveSession(SessionTouchRequest request, LocalDateTime now) {
        UserSession session = new UserSession();
        session.setSessionId(request.sessionId());
        session.setUserId(request.userId());
        session.setTenantId(request.tenantId());
        session.setUsername(request.username());
        session.setStatus(UserSessionState.ACTIVE);
        session.setLastSeenAt(now);
        session.setExpiresAt(request.expiresAt());
        updateSessionSnapshot(session, request);
        userSessionRepository.save(session);
        return UserSessionState.ACTIVE;
    }

    private void updateSessionSnapshot(UserSession session, SessionTouchRequest request) {
        session.setAuthenticationProvider(blankToNull(request.authenticationProvider()));
        session.setAuthenticationFactor(blankToNull(request.authenticationFactor()));
        session.setIpAddress(blankToNull(request.ipAddress()));
        session.setUserAgent(blankToNull(request.userAgent()));
    }

    private UserSessionView toView(UserSession session, String currentSessionId) {
        return new UserSessionView(
            session.getSessionId(),
            session.getSessionId().equals(currentSessionId),
            session.getAuthenticationProvider(),
            session.getAuthenticationFactor(),
            session.getIpAddress(),
            session.getUserAgent(),
            session.getCreatedAt(),
            session.getLastSeenAt(),
            session.getExpiresAt()
        );
    }

    private boolean shouldTouch(UserSession session, LocalDateTime now) {
        LocalDateTime lastSeenAt = session.getLastSeenAt();
        return lastSeenAt == null || lastSeenAt.isBefore(now.minusSeconds(TOUCH_INTERVAL_SECONDS));
    }

    private boolean isExpired(UserSession session, LocalDateTime now) {
        return session.getExpiresAt() != null && session.getExpiresAt().isBefore(now);
    }

    private void revoke(UserSession session, LocalDateTime now) {
        session.setStatus(UserSessionState.REVOKED);
        session.setEndedAt(now);
        session.setLastSeenAt(now);
        userSessionRepository.save(session);
    }

    private void expire(UserSession session, LocalDateTime now) {
        session.setStatus(UserSessionState.EXPIRED);
        session.setEndedAt(now);
        session.setLastSeenAt(now);
        userSessionRepository.save(session);
    }

    private void markEnded(String sessionId, UserSessionState state) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        userSessionRepository.findBySessionId(sessionId)
            .filter(session -> session.getStatus() == UserSessionState.ACTIVE)
            .ifPresent(session -> {
                LocalDateTime now = LocalDateTime.now();
                session.setStatus(state);
                session.setEndedAt(now);
                session.setLastSeenAt(now);
                userSessionRepository.save(session);
            });
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
