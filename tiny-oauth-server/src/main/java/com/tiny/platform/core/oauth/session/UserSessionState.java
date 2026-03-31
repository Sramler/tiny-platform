package com.tiny.platform.core.oauth.session;

/**
 * 服务端会话状态。
 */
public enum UserSessionState {
    ACTIVE,
    REVOKED,
    LOGGED_OUT,
    EXPIRED
}
