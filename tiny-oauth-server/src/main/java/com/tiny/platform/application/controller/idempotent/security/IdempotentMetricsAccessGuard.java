package com.tiny.platform.application.controller.idempotent.security;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 幂等治理页属于平台级运维入口，仅允许平台作用域（{@code activeScopeType=PLATFORM}）的用户访问。
 *
 * <p>即使底层指标已支持租户过滤，治理入口仍承载全局视角与跨租户治理能力，因此继续限制为平台管理员。</p>
 */
@Component("idempotentMetricsAccessGuard")
public class IdempotentMetricsAccessGuard {

    static final String PLATFORM_METRICS_AUTHORITY = "idempotent:ops:view";

    public boolean canAccess(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return TenantContext.isPlatformScope()
                && hasAuthority(authentication, PLATFORM_METRICS_AUTHORITY);
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .anyMatch(authority::equals);
    }
}
