package com.tiny.platform.core.oauth.tenant;

/**
 * TenantContext - ThreadLocal 保存当前请求的 activeTenantId 和 activeScopeType。
 * 仅用于单请求内的租户隔离，必须在请求结束时清理。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TENANT_SOURCE = new ThreadLocal<>();
    private static final ThreadLocal<String> SCOPE_TYPE = new ThreadLocal<>();
    private static final ThreadLocal<Long> SCOPE_ID = new ThreadLocal<>();

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

    /**
     * 当前请求的作用域类型：{@link TenantContextContract#SCOPE_TYPE_PLATFORM} 或
     * {@link TenantContextContract#SCOPE_TYPE_TENANT}。
     */
    public static void setActiveScopeType(String scopeType) {
        SCOPE_TYPE.set(scopeType);
    }

    public static String getActiveScopeType() {
        return SCOPE_TYPE.get();
    }

    public static void setActiveScopeId(Long scopeId) {
        SCOPE_ID.set(scopeId);
    }

    public static Long getActiveScopeId() {
        return SCOPE_ID.get();
    }

    public static boolean isPlatformScope() {
        return TenantContextContract.SCOPE_TYPE_PLATFORM.equals(SCOPE_TYPE.get());
    }

    public static void clear() {
        TENANT_ID.remove();
        TENANT_SOURCE.remove();
        SCOPE_TYPE.remove();
        SCOPE_ID.remove();
    }
}
