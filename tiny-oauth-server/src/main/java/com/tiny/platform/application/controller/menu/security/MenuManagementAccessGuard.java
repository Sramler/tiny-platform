package com.tiny.platform.application.controller.menu.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 菜单管理属于后台配置面，和运行态菜单树读取分离：
 * - /sys/menus/tree 继续服务当前登录用户的侧边栏菜单
 * - /sys/menus、/sys/menus/tree/all 以及菜单增删改属于控制面，需要额外 RBAC
 */
@Component("menuManagementAccessGuard")
public class MenuManagementAccessGuard {

    static final Set<String> READ_AUTHORITIES = Set.of("system:menu:list");
    static final Set<String> CREATE_AUTHORITIES = Set.of("system:menu:create");
    static final Set<String> UPDATE_AUTHORITIES = Set.of("system:menu:edit");
    static final Set<String> DELETE_AUTHORITIES = Set.of(
        "system:menu:delete",
        "system:menu:batch-delete"
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
            .anyMatch(requiredAuthorities::contains);
    }
}
