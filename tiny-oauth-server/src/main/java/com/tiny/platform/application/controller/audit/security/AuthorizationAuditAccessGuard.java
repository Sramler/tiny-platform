package com.tiny.platform.application.controller.audit.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 授权审计日志查询权限守卫。
 *
 * <p>权限码遵循 {@code system:audit:auth:<action>} 四段式规范。</p>
 */
@Component("authorizationAuditAccessGuard")
public class AuthorizationAuditAccessGuard {

    static final Set<String> VIEW_AUTHORITIES = Set.of("system:audit:auth:view");
    static final Set<String> EXPORT_AUTHORITIES = Set.of("system:audit:auth:export");
    static final Set<String> PURGE_AUTHORITIES = Set.of("system:audit:auth:purge");

    public boolean canView(Authentication authentication) {
        return hasAnyAuthority(authentication, VIEW_AUTHORITIES);
    }

    public boolean canPurge(Authentication authentication) {
        return hasAnyAuthority(authentication, PURGE_AUTHORITIES);
    }

    public boolean canExport(Authentication authentication) {
        return hasAnyAuthority(authentication, EXPORT_AUTHORITIES);
    }

    private boolean hasAnyAuthority(Authentication authentication, Set<String> requiredAuthorities) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .map(grantedAuthority -> grantedAuthority.getAuthority())
            .anyMatch(requiredAuthorities::contains);
    }
}
