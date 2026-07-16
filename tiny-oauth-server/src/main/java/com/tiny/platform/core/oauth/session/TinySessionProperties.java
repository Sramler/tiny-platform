package com.tiny.platform.core.oauth.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tiny.session")
public record TinySessionProperties(
        StoreType storeType,
        String namespace,
        String tableName,
        boolean cookieSecure,
        String sameSite) {

    public TinySessionProperties {
        storeType = storeType == null ? StoreType.MEMORY : storeType;
        namespace = hasText(namespace) ? namespace : "tiny-platform:session";
        tableName = hasText(tableName) ? tableName : "SPRING_SESSION";
        sameSite = hasText(sameSite) ? sameSite : "Lax";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public enum StoreType {
        MEMORY,
        JDBC,
        REDIS
    }
}
