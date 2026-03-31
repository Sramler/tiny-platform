package com.tiny.platform.application.controller.user.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 用户管理控制面权限守卫。
 */
@Component("userManagementAccessGuard")
public class UserManagementAccessGuard {

    private static final Set<String> READ_AUTHORITIES = Set.of("system:user:list", "system:user:view");
    private static final Set<String> CREATE_AUTHORITIES = Set.of("system:user:create");
    private static final Set<String> UPDATE_AUTHORITIES = Set.of(
        "system:user:edit",
        "system:user:role:assign"
    );
    private static final Set<String> DELETE_AUTHORITIES = Set.of(
        "system:user:delete",
        "system:user:batch-delete"
    );
    private static final Set<String> ENABLE_AUTHORITIES = Set.of(
        "system:user:batch-enable",
        "system:user:enable"
    );
    private static final Set<String> DISABLE_AUTHORITIES = Set.of(
        "system:user:batch-disable",
        "system:user:disable"
    );

    public boolean canRead(Authentication authentication) {
        return hasAnyAuthority(authentication, READ_AUTHORITIES);
    }

    public boolean canCreate(Authentication authentication) {
        return hasAnyAuthority(authentication, CREATE_AUTHORITIES);
    }

    public boolean canUpdate(Authentication authentication) {
        return hasAnyAuthority(authentication, UPDATE_AUTHORITIES);
    }

    public boolean canDelete(Authentication authentication) {
        return hasAnyAuthority(authentication, DELETE_AUTHORITIES);
    }

    public boolean canEnable(Authentication authentication) {
        return hasAnyAuthority(authentication, ENABLE_AUTHORITIES);
    }

    public boolean canDisable(Authentication authentication) {
        return hasAnyAuthority(authentication, DISABLE_AUTHORITIES);
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
