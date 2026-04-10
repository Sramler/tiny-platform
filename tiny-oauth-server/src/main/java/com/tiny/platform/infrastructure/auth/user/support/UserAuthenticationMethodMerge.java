package com.tiny.platform.infrastructure.auth.user.support;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 合并「租户内认证方式」与「用户级全局认证方式（tenant_id IS NULL）」查询结果：
 * 同一 {@code provider+type} 下优先采用租户内配置，否则采用全局行。
 */
public final class UserAuthenticationMethodMerge {

    private UserAuthenticationMethodMerge() {
    }

    /**
     * @param tenantScoped 当前租户下已启用的方法（tenant_id = 登录解析租户）
     * @param global       全局已启用方法（tenant_id IS NULL）
     */
    public static List<UserAuthenticationMethod> mergePreferTenantScoped(
            List<UserAuthenticationMethod> tenantScoped,
            List<UserAuthenticationMethod> global) {
        return mergePreferPrimary(tenantScoped, global);
    }

    /**
     * 合并两组认证方式，同一 {@code provider+type} 下优先采用 {@code primary} 中的记录。
     */
    public static List<UserAuthenticationMethod> mergePreferPrimary(
            List<UserAuthenticationMethod> primary,
            List<UserAuthenticationMethod> fallback) {
        Map<String, UserAuthenticationMethod> byKey = new LinkedHashMap<>();
        if (fallback != null) {
            for (UserAuthenticationMethod m : fallback) {
                byKey.put(key(m), m);
            }
        }
        if (primary != null) {
            for (UserAuthenticationMethod m : primary) {
                byKey.put(key(m), m);
            }
        }
        return byKey.values().stream()
                .sorted(Comparator.comparingInt(UserAuthenticationMethodMerge::priority))
                .collect(Collectors.toList());
    }

    private static String key(UserAuthenticationMethod m) {
        String p = m.getAuthenticationProvider() == null ? "" : m.getAuthenticationProvider();
        String t = m.getAuthenticationType() == null ? "" : m.getAuthenticationType();
        return p.toUpperCase(Locale.ROOT) + "\0" + t.toUpperCase(Locale.ROOT);
    }

    private static int priority(UserAuthenticationMethod m) {
        Integer p = m.getAuthenticationPriority();
        return p == null ? Integer.MAX_VALUE : p;
    }
}
