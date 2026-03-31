package com.tiny.platform.application.controller.dict.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 租户级字典管理权限守卫。
 *
 * <p>权限码遵循 {@code dict:<resource>:<action>} 三段式规范。</p>
 *
 * <p>注意：字典查找类接口（按编码查、取映射、取标签、可见类型列表）
 * 由全应用渲染使用，不在本 Guard 管控范围内，仅要求已认证。</p>
 */
@Component("dictManagementAccessGuard")
public class DictManagementAccessGuard {

    static final Set<String> TYPE_READ_AUTHORITIES = Set.of("dict:type:list");
    static final Set<String> TYPE_CREATE_AUTHORITIES = Set.of("dict:type:create");
    static final Set<String> TYPE_UPDATE_AUTHORITIES = Set.of("dict:type:edit");
    static final Set<String> TYPE_DELETE_AUTHORITIES = Set.of("dict:type:delete");

    static final Set<String> ITEM_READ_AUTHORITIES = Set.of("dict:item:list");
    static final Set<String> ITEM_CREATE_AUTHORITIES = Set.of("dict:item:create");
    static final Set<String> ITEM_UPDATE_AUTHORITIES = Set.of("dict:item:edit");
    static final Set<String> ITEM_DELETE_AUTHORITIES = Set.of("dict:item:delete");

    public boolean canReadType(Authentication authentication) {
        return hasAnyAuthority(authentication, TYPE_READ_AUTHORITIES);
    }

    public boolean canCreateType(Authentication authentication) {
        return hasAnyAuthority(authentication, TYPE_CREATE_AUTHORITIES);
    }

    public boolean canUpdateType(Authentication authentication) {
        return hasAnyAuthority(authentication, TYPE_UPDATE_AUTHORITIES);
    }

    public boolean canDeleteType(Authentication authentication) {
        return hasAnyAuthority(authentication, TYPE_DELETE_AUTHORITIES);
    }

    public boolean canReadItem(Authentication authentication) {
        return hasAnyAuthority(authentication, ITEM_READ_AUTHORITIES);
    }

    public boolean canCreateItem(Authentication authentication) {
        return hasAnyAuthority(authentication, ITEM_CREATE_AUTHORITIES);
    }

    public boolean canUpdateItem(Authentication authentication) {
        return hasAnyAuthority(authentication, ITEM_UPDATE_AUTHORITIES);
    }

    public boolean canDeleteItem(Authentication authentication) {
        return hasAnyAuthority(authentication, ITEM_DELETE_AUTHORITIES);
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
