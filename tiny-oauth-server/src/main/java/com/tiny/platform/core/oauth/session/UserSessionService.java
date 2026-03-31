package com.tiny.platform.core.oauth.session;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface UserSessionService {

    UserSessionState registerOrTouch(SessionTouchRequest request);

    List<UserSessionView> listCurrentUserSessions(Long userId, Long tenantId, String currentSessionId);

    boolean revokeSession(Long userId, Long tenantId, String sessionId, String actorUsername, HttpServletRequest request);

    int revokeOtherSessions(Long userId, Long tenantId, String currentSessionId, String actorUsername, HttpServletRequest request);

    void markLoggedOut(String sessionId);

    void markExpired(String sessionId);
}
