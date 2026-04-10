package com.tiny.platform.infrastructure.auth.user.support;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import java.util.Map;
import java.util.Objects;

/**
 * 认证桥接视图：把旧表 {@code user_authentication_method} 拆解成
 * 「凭证」+「作用域策略」两层，同时保留底层存储记录供桥接期写回。
 */
public record UserAuthenticationMethodProfile(
        UserAuthenticationCredential credential,
        UserAuthenticationScopePolicy scopePolicy,
        UserAuthenticationMethod storageRecord) {

    public UserAuthenticationMethodProfile {
        Objects.requireNonNull(credential, "credential");
        Objects.requireNonNull(scopePolicy, "scopePolicy");
        Objects.requireNonNull(storageRecord, "storageRecord");
    }

    public Long userId() {
        return credential.userId();
    }

    public String authenticationProvider() {
        return credential.authenticationProvider();
    }

    public String authenticationType() {
        return credential.authenticationType();
    }

    public Map<String, Object> authenticationConfiguration() {
        return credential.authenticationConfiguration();
    }

    public Long scopeTenantId() {
        return scopePolicy.scopeTenantId();
    }

    public boolean isPrimaryMethod() {
        return Boolean.TRUE.equals(scopePolicy.isPrimaryMethod());
    }

    public boolean isMethodEnabled() {
        return Boolean.TRUE.equals(scopePolicy.isMethodEnabled());
    }

    public int authenticationPriority() {
        Integer priority = scopePolicy.authenticationPriority();
        return priority == null ? Integer.MAX_VALUE : priority;
    }
}
