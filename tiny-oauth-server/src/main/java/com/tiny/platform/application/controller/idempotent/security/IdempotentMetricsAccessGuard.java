package com.tiny.platform.application.controller.idempotent.security;

import com.tiny.platform.core.oauth.security.LegacyAuthConstants;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.infrastructure.idempotent.starter.properties.IdempotentProperties;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.util.Objects;
import org.springframework.util.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 幂等治理页属于平台级运维入口，当前只允许默认平台租户的管理员访问。
 *
 * <p>即使底层指标已支持租户过滤，治理入口仍承载全局视角与跨租户治理能力，因此继续限制为平台管理员。</p>
 */
@Component("idempotentMetricsAccessGuard")
public class IdempotentMetricsAccessGuard {

    static final String PLATFORM_METRICS_AUTHORITY = "idempotent:ops:view";
    private final TenantRepository tenantRepository;
    private final IdempotentProperties properties;

    public IdempotentMetricsAccessGuard(TenantRepository tenantRepository, IdempotentProperties properties) {
        this.tenantRepository = tenantRepository;
        this.properties = properties;
    }

    public boolean canAccess(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Long platformTenantId = tenantRepository.findByCode(resolvePlatformTenantCode())
                .map(tenant -> tenant.getId())
                .orElse(null);
        if (platformTenantId == null) {
            return false;
        }

        Long currentActiveTenantId = resolveActiveTenantId(authentication);
        return Objects.equals(platformTenantId, currentActiveTenantId)
                && hasAdminAuthority(authentication)
                && hasAuthority(authentication, PLATFORM_METRICS_AUTHORITY);
    }

    private String resolvePlatformTenantCode() {
        String configuredCode = properties.getOps().getPlatformTenantCode();
        return StringUtils.hasText(configuredCode) ? configuredCode.trim() : "default";
    }

    private static boolean hasAdminAuthority(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .anyMatch(LegacyAuthConstants::isAdminAuthority);
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .anyMatch(authority::equals);
    }

    private static Long resolveActiveTenantId(Authentication authentication) {
        return ActiveTenantResponseSupport.resolveActiveTenantId(authentication);
    }
}
