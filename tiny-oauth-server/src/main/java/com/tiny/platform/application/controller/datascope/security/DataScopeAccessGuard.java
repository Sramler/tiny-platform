package com.tiny.platform.application.controller.datascope.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 数据范围规则管理权限守卫。
 *
 * <p>权限码遵循 {@code system:datascope:<action>} 三段式规范。</p>
 */
@Component("dataScopeAccessGuard")
public class DataScopeAccessGuard {

    static final Set<String> VIEW_AUTHORITIES = Set.of("system:datascope:view");
    static final Set<String> EDIT_AUTHORITIES = Set.of("system:datascope:edit");

    public boolean canView(Authentication authentication) {
        return hasAnyAuthority(authentication, VIEW_AUTHORITIES);
    }

    public boolean canEdit(Authentication authentication) {
        return hasAnyAuthority(authentication, EDIT_AUTHORITIES);
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
