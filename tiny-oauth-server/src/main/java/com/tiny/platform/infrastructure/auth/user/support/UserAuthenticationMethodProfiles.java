package com.tiny.platform.infrastructure.auth.user.support;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 旧表 {@link UserAuthenticationMethod} 与桥接域模型之间的映射工具。
 */
public final class UserAuthenticationMethodProfiles {

    private UserAuthenticationMethodProfiles() {
    }

    public static UserAuthenticationMethodProfile from(UserAuthenticationMethod method) {
        Objects.requireNonNull(method, "method");
        return new UserAuthenticationMethodProfile(
                credentialOf(method),
                scopePolicyOf(method),
                method
        );
    }

    public static UserAuthenticationCredential credentialOf(UserAuthenticationMethod method) {
        Objects.requireNonNull(method, "method");
        return new UserAuthenticationCredential(
                method.getUserId(),
                method.getAuthenticationProvider(),
                method.getAuthenticationType(),
                copyConfiguration(method.getAuthenticationConfiguration()),
                method.getLastVerifiedAt(),
                method.getLastVerifiedIp(),
                method.getExpiresAt()
        );
    }

    public static UserAuthenticationScopePolicy scopePolicyOf(UserAuthenticationMethod method) {
        Objects.requireNonNull(method, "method");
        return new UserAuthenticationScopePolicy(
                method.getUserId(),
                method.getTenantId(),
                method.getAuthenticationProvider(),
                method.getAuthenticationType(),
                method.getIsPrimaryMethod(),
                method.getIsMethodEnabled(),
                method.getAuthenticationPriority()
        );
    }

    public static UserAuthenticationMethod create(
            UserAuthenticationCredential credential,
            UserAuthenticationScopePolicy scopePolicy) {
        return apply(new UserAuthenticationMethod(), credential, scopePolicy);
    }

    public static UserAuthenticationMethod apply(
            UserAuthenticationMethod method,
            UserAuthenticationCredential credential,
            UserAuthenticationScopePolicy scopePolicy) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(credential, "credential");
        Objects.requireNonNull(scopePolicy, "scopePolicy");

        Long userId = firstNonNull(credential.userId(), scopePolicy.userId(), method.getUserId());
        String provider = firstNonBlank(
                credential.authenticationProvider(),
                scopePolicy.authenticationProvider(),
                method.getAuthenticationProvider()
        );
        String type = firstNonBlank(
                credential.authenticationType(),
                scopePolicy.authenticationType(),
                method.getAuthenticationType()
        );

        method.setUserId(userId);
        method.setTenantId(firstNonNull(scopePolicy.scopeTenantId(), method.getTenantId(), null));
        method.setAuthenticationProvider(provider);
        method.setAuthenticationType(type);
        method.setAuthenticationConfiguration(copyConfiguration(credential.authenticationConfiguration()));
        method.setLastVerifiedAt(credential.lastVerifiedAt());
        method.setLastVerifiedIp(credential.lastVerifiedIp());
        method.setExpiresAt(credential.expiresAt());
        method.setIsPrimaryMethod(scopePolicy.isPrimaryMethod());
        method.setIsMethodEnabled(scopePolicy.isMethodEnabled());
        method.setAuthenticationPriority(scopePolicy.authenticationPriority());
        return method;
    }

    public static Map<String, Object> copyConfiguration(Map<String, Object> configuration) {
        if (configuration == null || configuration.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(configuration);
    }

    private static <T> T firstNonNull(T first, T second, T fallback) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return fallback;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }
}
