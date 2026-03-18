package com.tiny.platform.application.oauth.workflow;

/**
 * 用 ThreadLocal 保存当前请求的活动租户 ID
 */
public class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    public static void setCurrentTenant(String activeTenantId) {
        CURRENT_TENANT.set(activeTenantId);
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
