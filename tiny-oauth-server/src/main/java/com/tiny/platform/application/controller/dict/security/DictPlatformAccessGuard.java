package com.tiny.platform.application.controller.dict.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * 平台字典管理权限守卫。
 */
@Component("dictPlatformAccessGuard")
public class DictPlatformAccessGuard {

    public boolean canManagePlatformDict(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> "ROLE_ADMIN".equalsIgnoreCase(authority) || "ADMIN".equalsIgnoreCase(authority));
    }
}
