package com.tiny.platform.application.controller.org.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 组织/部门管理权限守卫。
 *
 * <p>权限码遵循 {@code system:org:<action>} 三段式规范；
 * 用户归属管理使用四段式 {@code system:org:user:assign|remove}。</p>
 */
@Component("organizationAccessGuard")
public class OrganizationAccessGuard {

    static final Set<String> READ_AUTHORITIES = Set.of("system:org:list", "system:org:view");
    static final Set<String> CREATE_AUTHORITIES = Set.of("system:org:create");
    static final Set<String> UPDATE_AUTHORITIES = Set.of("system:org:edit");
    static final Set<String> DELETE_AUTHORITIES = Set.of("system:org:delete");
    static final Set<String> USER_ASSIGN_AUTHORITIES = Set.of("system:org:user:assign");
    static final Set<String> USER_REMOVE_AUTHORITIES = Set.of("system:org:user:remove");

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

    public boolean canAssignUser(Authentication authentication) {
        return hasAnyAuthority(authentication, USER_ASSIGN_AUTHORITIES);
    }

    public boolean canRemoveUser(Authentication authentication) {
        return hasAnyAuthority(authentication, USER_REMOVE_AUTHORITIES);
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
