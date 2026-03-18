package com.tiny.platform.application.controller.resource.security;

import com.tiny.platform.core.oauth.security.LegacyAuthConstants;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component("resourceManagementAccessGuard")
public class ResourceManagementAccessGuard {

    private static final Set<String> ADMIN_AUTHORITIES = LegacyAuthConstants.ADMIN_AUTHORITIES;
    private static final Set<String> READ_AUTHORITIES = Set.of("system:resource:list");
    private static final Set<String> CREATE_AUTHORITIES = Set.of("system:resource:create");
    private static final Set<String> UPDATE_AUTHORITIES = Set.of("system:resource:edit");
    private static final Set<String> DELETE_AUTHORITIES = Set.of(
        "system:resource:delete",
        "system:resource:batch-delete"
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

    private boolean hasAnyAuthority(Authentication authentication, Set<String> requiredAuthorities) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .map(grantedAuthority -> grantedAuthority.getAuthority())
            .anyMatch(authority -> ADMIN_AUTHORITIES.contains(authority) || requiredAuthorities.contains(authority));
    }
}
