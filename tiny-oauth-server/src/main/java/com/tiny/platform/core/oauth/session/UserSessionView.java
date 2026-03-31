package com.tiny.platform.core.oauth.session;

import java.time.LocalDateTime;

/**
 * 当前用户可见的活跃会话视图。
 */
public record UserSessionView(
    String sessionId,
    boolean current,
    String authenticationProvider,
    String authenticationFactor,
    String ipAddress,
    String userAgent,
    LocalDateTime createdAt,
    LocalDateTime lastSeenAt,
    LocalDateTime expiresAt
) {
}
