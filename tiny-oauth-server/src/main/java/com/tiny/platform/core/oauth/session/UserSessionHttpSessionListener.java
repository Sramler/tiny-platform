package com.tiny.platform.core.oauth.session;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * 监听 servlet session 生命周期，补齐服务端会话状态收尾。
 */
public class UserSessionHttpSessionListener implements HttpSessionListener {

    private final UserSessionService userSessionService;

    public UserSessionHttpSessionListener(UserSessionService userSessionService) {
        this.userSessionService = userSessionService;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        if (se == null || se.getSession() == null) {
            return;
        }
        userSessionService.markExpired(se.getSession().getId());
    }
}
