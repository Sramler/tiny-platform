package com.tiny.platform.infrastructure.menu.runtime;

import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.runtime.version.RuntimeVersionKey;

import java.util.Locale;

public final class MenuConfigVersionKeys {

    public static final String MENU_CONFIG_DOMAIN = "MENU_CONFIG";

    private MenuConfigVersionKeys() {
    }

    public static RuntimeVersionKey fromActiveScope(Long activeTenantId, String activeScopeType) {
        String menuScopeType = TenantContextContract.SCOPE_TYPE_PLATFORM.equals(normalizeScopeType(activeScopeType))
            ? TenantContextContract.SCOPE_TYPE_PLATFORM
            : TenantContextContract.SCOPE_TYPE_TENANT;
        Long menuTenantId = TenantContextContract.SCOPE_TYPE_PLATFORM.equals(menuScopeType)
            ? null
            : activeTenantId;
        Long menuScopeId = TenantContextContract.SCOPE_TYPE_PLATFORM.equals(menuScopeType) ? null : menuTenantId;
        return RuntimeVersionKey.of(MENU_CONFIG_DOMAIN, menuTenantId, menuScopeType, menuScopeId);
    }

    private static String normalizeScopeType(String value) {
        if (value == null || value.isBlank()) {
            return TenantContextContract.SCOPE_TYPE_TENANT;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
