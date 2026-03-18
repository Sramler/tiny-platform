package com.tiny.platform.core.oauth.tenant;

/**
 * TenantContext - ThreadLocal 保存当前请求的 activeTenantId。
 * 仅用于单请求内的租户隔离，必须在请求结束时清理。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TENANT_SOURCE = new ThreadLocal<>();

    public static final String SOURCE_TOKEN = "token";
    public static final String SOURCE_SESSION = "session";
    public static final String SOURCE_LOGIN_PARAM = "login_param";
    public static final String SOURCE_ISSUER = "issuer";
    public static final String SOURCE_UNKNOWN = "unknown";

    private TenantContext() {}

    public static void setActiveTenantId(Long activeTenantId) {
        TENANT_ID.set(activeTenantId);
    }

    public static Long getActiveTenantId() {
        return TENANT_ID.get();
    }

    public static void setTenantSource(String tenantSource) {
        TENANT_SOURCE.set(tenantSource);
    }

    public static String getTenantSource() {
        return TENANT_SOURCE.get();
    }

    public static void clear() {
        TENANT_ID.remove();
        TENANT_SOURCE.remove();
    }
}
