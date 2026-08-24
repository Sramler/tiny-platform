package com.tiny.platform.core.oauth.security;

import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;

/**
 * HttpSession 中的强制失效版本快照。
 *
 * <p>Session 创建时间不等于认证完成时间：密码 -> TOTP 等多阶段认证会复用并旋转
 * 同一 Session。因此以完整认证当时的 tokenSecurityVersion 作为精确失效依据。</p>
 */
public final class SessionTokenSecurityState {

    private static final String ATTRIBUTE = SessionTokenSecurityState.class.getName() + ".versions";

    private SessionTokenSecurityState() {
    }

    public static String readVersion(HttpSession session, TokenSecurityState state) {
        if (session == null || state == null) {
            return null;
        }
        Object value = session.getAttribute(ATTRIBUTE);
        if (!(value instanceof Map<?, ?> versions)) {
            return null;
        }
        Object version = versions.get(scopeKey(state));
        return version instanceof String text && !text.isBlank() ? text : null;
    }

    public static void stamp(HttpSession session, TokenSecurityState state) {
        if (session == null || state == null || state.tokenSecurityVersion() == null
            || state.tokenSecurityVersion().isBlank()) {
            return;
        }
        Map<String, String> versions = new HashMap<>();
        Object existing = session.getAttribute(ATTRIBUTE);
        if (existing instanceof Map<?, ?> existingVersions) {
            existingVersions.forEach((key, value) -> {
                if (key instanceof String textKey && value instanceof String textValue) {
                    versions.put(textKey, textValue);
                }
            });
        }
        versions.put(scopeKey(state), state.tokenSecurityVersion());
        // 始终 set 回可序列化副本，确保 Spring Session JDBC / Redis 能感知属性变更。
        session.setAttribute(ATTRIBUTE, versions);
    }

    private static String scopeKey(TokenSecurityState state) {
        return String.join(":",
            normalize(state.scopeType()),
            String.valueOf(normalizeId(state.tenantId())),
            String.valueOf(normalizeId(state.scopeId()))
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static long normalizeId(Long value) {
        return value == null ? 0L : value;
    }
}
