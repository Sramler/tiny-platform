package com.tiny.platform.application.controller.user.security;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component("platformUserManagementAccessGuard")
public class PlatformUserManagementAccessGuard {

    private static final Set<String> READ_AUTHORITIES = Set.of(
        "platform:user:list",
        "platform:user:view"
    );

    private static final Set<String> CREATE_AUTHORITIES = Set.of(
        "platform:user:create"
    );

    private static final Set<String> UPDATE_AUTHORITIES = Set.of(
        "platform:user:edit",
        "platform:user:disable"
    );

    public boolean canRead(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, READ_AUTHORITIES);
    }

    public boolean canCreate(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, CREATE_AUTHORITIES);
    }

    public boolean canUpdate(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, UPDATE_AUTHORITIES);
    }

    private boolean isPlatformScope(Authentication authentication) {
        return authentication != null
            && authentication.isAuthenticated()
            && TenantContext.isPlatformScope();
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
