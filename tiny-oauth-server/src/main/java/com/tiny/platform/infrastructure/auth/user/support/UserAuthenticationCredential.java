package com.tiny.platform.infrastructure.auth.user.support;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认证凭证视图：描述用户持有的认证材料，不承载作用域策略。
 */
public record UserAuthenticationCredential(
        Long userId,
        String authenticationProvider,
        String authenticationType,
        Map<String, Object> authenticationConfiguration,
        LocalDateTime lastVerifiedAt,
        String lastVerifiedIp,
        LocalDateTime expiresAt) {
}
