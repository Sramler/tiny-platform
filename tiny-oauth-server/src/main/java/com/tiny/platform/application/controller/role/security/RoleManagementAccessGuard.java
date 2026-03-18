package com.tiny.platform.application.controller.role.security;

import com.tiny.platform.core.oauth.security.LegacyAuthConstants;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component("roleManagementAccessGuard")
public class RoleManagementAccessGuard {

    private static final Set<String> ADMIN_AUTHORITIES = LegacyAuthConstants.ADMIN_AUTHORITIES;
    private static final Set<String> READ_AUTHORITIES = Set.of("system:role:list");
    private static final Set<String> CREATE_AUTHORITIES = Set.of("system:role:create");
    private static final Set<String> UPDATE_AUTHORITIES = Set.of("system:role:edit");
    private static final Set<String> DELETE_AUTHORITIES = Set.of(
        "system:role:delete",
        "system:role:batch-delete"
    );
    private static final Set<String> ASSIGN_USER_AUTHORITIES = Set.of(
        "system:user:assign-role",
        "system:user:role:assign"
    );
    private static final Set<String> ASSIGN_PERMISSION_AUTHORITIES = Set.of(
        "system:role:assign-permission",
        "system:role:permission:assign"
    );

    // RBAC3 control-plane permissions (new identifiers).
    // Keep legacy compatibility by also accepting ASSIGN_PERMISSION_AUTHORITIES in methods below.
    private static final Set<String> ROLE_CONSTRAINT_VIEW_AUTHORITIES = Set.of("system:role:constraint:view");
    private static final Set<String> ROLE_CONSTRAINT_MANAGE_AUTHORITIES = Set.of("system:role:constraint:edit");
    private static final Set<String> ROLE_CONSTRAINT_VIOLATION_VIEW_AUTHORITIES = Set.of("system:role:constraint:violation:view");

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

    public boolean canAssignUsers(Authentication authentication) {
        return hasAnyAuthority(authentication, ASSIGN_USER_AUTHORITIES);
    }

    public boolean canAssignPermissions(Authentication authentication) {
        return hasAnyAuthority(authentication, ASSIGN_PERMISSION_AUTHORITIES);
    }

    public boolean canViewRoleConstraints(Authentication authentication) {
        return hasAnyAuthority(authentication, ROLE_CONSTRAINT_VIEW_AUTHORITIES)
            || canAssignPermissions(authentication);
    }

    public boolean canManageRoleConstraints(Authentication authentication) {
        return hasAnyAuthority(authentication, ROLE_CONSTRAINT_MANAGE_AUTHORITIES)
            || canAssignPermissions(authentication);
    }

    public boolean canViewRoleConstraintViolations(Authentication authentication) {
        return hasAnyAuthority(authentication, ROLE_CONSTRAINT_VIOLATION_VIEW_AUTHORITIES)
            || canAssignPermissions(authentication);
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
