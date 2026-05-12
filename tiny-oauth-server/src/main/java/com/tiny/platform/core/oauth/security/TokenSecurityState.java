package com.tiny.platform.core.oauth.security;

import java.time.LocalDateTime;

public record TokenSecurityState(
    Long userId,
    Long tenantId,
    String scopeType,
    Long scopeId,
    String tokenSecurityVersion,
    LocalDateTime tokenNotBefore,
    long globalTokenVersion,
    long scopeTokenVersion
) {
}
