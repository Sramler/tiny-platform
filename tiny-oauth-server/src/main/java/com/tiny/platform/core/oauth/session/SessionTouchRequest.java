package com.tiny.platform.core.oauth.session;

import java.time.LocalDateTime;

/**
 * 当前请求对应的服务端会话快照。
 */
public record SessionTouchRequest(
    String sessionId,
    Long userId,
    Long tenantId,
    String username,
    String authenticationProvider,
    String authenticationFactor,
    String ipAddress,
    String userAgent,
    LocalDateTime touchAt,
    LocalDateTime expiresAt
) {
}
