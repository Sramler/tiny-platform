package com.tiny.platform.core.oauth.tenant;

/**
 * TenantContext - ThreadLocal 保存当前请求的 tenantId。
 * 仅用于单请求内的租户隔离，必须在请求结束时清理。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
