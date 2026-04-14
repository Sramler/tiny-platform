package com.tiny.platform.application.controller.permission.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component("permissionManagementAccessGuard")
public class PermissionManagementAccessGuard {

    private static final Set<String> READ_AUTHORITIES = Set.of(
        "system:role:list",
        "system:role:permission:assign"
    );

    private static final Set<String> WRITE_AUTHORITIES = Set.of(
        "system:role:permission:assign"
    );

    public boolean canRead(Authentication authentication) {
        return hasAnyAuthority(authentication, READ_AUTHORITIES);
    }

    public boolean canUpdate(Authentication authentication) {
        return hasAnyAuthority(authentication, WRITE_AUTHORITIES);
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
