package com.tiny.platform.application.controller.menu.runtime;

/**
 * Runtime menu-tree cache identity.
 *
 * <p>The context is intentionally storage-neutral. DB is the current authoritative
 * fallback, but Redis/distributed stores can reuse the same key contract.</p>
 */
public record MenuRuntimeTreeContext(
    String userKey,
    Long activeTenantId,
    String activeScopeType,
    Long activeScopeId,
    String permissionsVersion,
    String menuConfigVersion
) {
}
