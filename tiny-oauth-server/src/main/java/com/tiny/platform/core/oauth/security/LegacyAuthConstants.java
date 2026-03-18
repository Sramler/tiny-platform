package com.tiny.platform.core.oauth.security;

import java.util.Set;

/**
 * 鉴权用角色/权限常量，统一收口避免散落字符串。
 *
 * <p>ROLE_ADMIN 为唯一规范管理员角色码；历史 ADMIN 值不再作为管理员 authority 处理。</p>
 *
 * @see docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md
 */
public final class LegacyAuthConstants {

    /** 规范管理员角色码（Spring Security 约定 ROLE_ 前缀） */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    /** 粗粒度“管理员”判断时接受的 authority 集合（仅规范 ROLE_ADMIN） */
    public static final Set<String> ADMIN_AUTHORITIES = Set.of(ROLE_ADMIN);

    private LegacyAuthConstants() {
    }

    /**
     * 判断 authority 是否为管理员（仅 ROLE_ADMIN）。
     */
    public static boolean isAdminAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return false;
        }
        return ROLE_ADMIN.equalsIgnoreCase(authority);
    }
}
