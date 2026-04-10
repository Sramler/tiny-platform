package com.tiny.platform.core.oauth.tenant;

import java.util.Locale;

/**
 * 运行时当前授权作用域。
 *
 * <p>该对象用于显式表达当前 scope，而不是让调用方根据 {@code activeTenantId} 是否为空去猜测
 * PLATFORM/TENANT。</p>
 */
public record ActiveScope(String scopeType, Long scopeId) {

    public ActiveScope {
        scopeType = normalizeScopeType(scopeType);
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType)) {
            scopeId = null;
        } else if (scopeId != null && scopeId <= 0) {
            scopeId = null;
        }
    }

    public static ActiveScope of(String scopeType, Long scopeId) {
        return new ActiveScope(scopeType, scopeId);
    }

    public boolean isPlatform() {
        return TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType);
    }

    public boolean isTenant() {
        return TenantContextContract.SCOPE_TYPE_TENANT.equals(scopeType);
    }

    public boolean isOrg() {
        return TenantContextContract.SCOPE_TYPE_ORG.equals(scopeType);
    }

    public boolean isDept() {
        return TenantContextContract.SCOPE_TYPE_DEPT.equals(scopeType);
    }

    public boolean isOrgOrDept() {
        return isOrg() || isDept();
    }

    public static String normalizeScopeType(String rawScopeType) {
        if (rawScopeType == null || rawScopeType.isBlank()) {
            return null;
        }
        String normalized = rawScopeType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case TenantContextContract.SCOPE_TYPE_PLATFORM,
                 TenantContextContract.SCOPE_TYPE_TENANT,
                 TenantContextContract.SCOPE_TYPE_ORG,
                 TenantContextContract.SCOPE_TYPE_DEPT -> normalized;
            default -> null;
        };
    }
}
