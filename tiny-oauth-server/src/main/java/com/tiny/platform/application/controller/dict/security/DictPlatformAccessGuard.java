package com.tiny.platform.application.controller.dict.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 平台字典管理权限守卫。
 * 规范权限码：dict:platform:manage。
 */
@Component("dictPlatformAccessGuard")
public class DictPlatformAccessGuard {

    private static final Set<String> PLATFORM_DICT_AUTHORITIES = Set.of("dict:platform:manage");

    public boolean canManagePlatformDict(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PLATFORM_DICT_AUTHORITIES::contains);
    }
}
