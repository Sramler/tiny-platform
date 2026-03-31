package com.tiny.platform.application.controller.audit.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 认证审计查询权限守卫。
 */
@Component("authenticationAuditAccessGuard")
public class AuthenticationAuditAccessGuard {

    static final Set<String> VIEW_AUTHORITIES = Set.of("system:audit:authentication:view");
    static final Set<String> EXPORT_AUTHORITIES = Set.of("system:audit:authentication:export");

    public boolean canView(Authentication authentication) {
        return hasAnyAuthority(authentication, VIEW_AUTHORITIES);
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
