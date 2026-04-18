package com.tiny.platform.application.controller.role.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 角色管理控制面权限守卫。
 */
@Component("roleManagementAccessGuard")
public class RoleManagementAccessGuard {

    private static final Set<String> READ_AUTHORITIES = Set.of("system:role:list");
    private static final Set<String> CREATE_AUTHORITIES = Set.of("system:role:create");
    private static final Set<String> UPDATE_AUTHORITIES = Set.of("system:role:edit");
    private static final Set<String> DELETE_AUTHORITIES = Set.of(
        "system:role:delete",
        "system:role:batch-delete"
    );
    private static final Set<String> ASSIGN_USER_AUTHORITIES = Set.of(
        "system:user:role:assign"
    );
    private static final Set<String> ASSIGN_PERMISSION_AUTHORITIES = Set.of(
        "system:role:permission:assign"
    );

    // RBAC3 control-plane permissions.
    private static final Set<String> ROLE_CONSTRAINT_VIEW_AUTHORITIES = Set.of("system:role:constraint:view");
    private static final Set<String> ROLE_CONSTRAINT_MANAGE_AUTHORITIES = Set.of("system:role:constraint:edit");
    private static final Set<String> ROLE_CONSTRAINT_VIOLATION_VIEW_AUTHORITIES = Set.of("system:role:constraint:violation:view");
    private static final Set<String> ROLE_CONSTRAINT_ROLE_CATALOG_AUTHORITIES = Set.of(
        "system:role:constraint:view",
        "system:role:constraint:edit"
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

    public boolean canAssignUsers(Authentication authentication) {
        return hasAnyAuthority(authentication, ASSIGN_USER_AUTHORITIES);
    }

    public boolean canAssignPermissions(Authentication authentication) {
        return hasAnyAuthority(authentication, ASSIGN_PERMISSION_AUTHORITIES);
    }

    public boolean canViewRoleConstraints(Authentication authentication) {
        return hasAnyAuthority(authentication, ROLE_CONSTRAINT_VIEW_AUTHORITIES);
    }

    public boolean canManageRoleConstraints(Authentication authentication) {
        return hasAnyAuthority(authentication, ROLE_CONSTRAINT_MANAGE_AUTHORITIES);
    }

    public boolean canViewRoleConstraintViolations(Authentication authentication) {
        return hasAnyAuthority(authentication, ROLE_CONSTRAINT_VIOLATION_VIEW_AUTHORITIES);
    }

    public boolean canReadRoleCatalog(Authentication authentication) {
        return hasAnyAuthority(authentication, ROLE_CONSTRAINT_ROLE_CATALOG_AUTHORITIES);
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
