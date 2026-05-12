package com.tiny.platform.infrastructure.runtime.version;

import org.springframework.util.StringUtils;

import java.util.Locale;

public record RuntimeVersionKey(
    String domain,
    Long tenantId,
    String scopeType,
    Long scopeId
) {

    public RuntimeVersionKey {
        if (!StringUtils.hasText(domain)) {
            throw new IllegalArgumentException("version domain must not be blank");
        }
        domain = domain.trim().toUpperCase(Locale.ROOT);
        scopeType = StringUtils.hasText(scopeType)
            ? scopeType.trim().toUpperCase(Locale.ROOT)
            : "TENANT";
    }

    public static RuntimeVersionKey of(String domain, Long tenantId, String scopeType, Long scopeId) {
        return new RuntimeVersionKey(domain, tenantId, scopeType, scopeId);
    }
}
