package com.tiny.platform.infrastructure.tenant.service;

/**
 * 新租户创建前，对平台模板 bootstrap 可用性与有效快照规模的只读预览。
 */
public record TenantBootstrapPreview(
    boolean ready,
    boolean currentPlatformTemplatePresent,
    boolean requiresHistoricalBackfill,
    String message,
    long roleCount,
    long permissionCount,
    long menuCount,
    long uiActionCount,
    long apiEndpointCount
) {
    public static TenantBootstrapPreview blocked(String message) {
        return new TenantBootstrapPreview(false, false, false, message, 0L, 0L, 0L, 0L, 0L);
    }
}
