package com.tiny.platform.application.controller.idempotent.security;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.idempotent.starter.properties.IdempotentProperties;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.util.Objects;
import org.springframework.util.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 幂等治理页属于平台级运维入口，当前只允许默认平台租户的管理员访问。
 *
 * <p>即使底层指标已支持租户过滤，治理入口仍承载全局视角与跨租户治理能力，因此继续限制为平台管理员。</p>
 */
@Component("idempotentMetricsAccessGuard")
public class IdempotentMetricsAccessGuard {

    static final String PLATFORM_METRICS_AUTHORITY = "idempotentOps";

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

        Long currentTenantId = resolveTenantId(authentication);
        return Objects.equals(platformTenantId, currentTenantId)
                && hasAuthority(authentication, "ROLE_ADMIN")
                && hasAuthority(authentication, PLATFORM_METRICS_AUTHORITY);
    }

    private String resolvePlatformTenantCode() {
        String configuredCode = properties.getOps().getPlatformTenantCode();
        return StringUtils.hasText(configuredCode) ? configuredCode.trim() : "default";
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .anyMatch(authority::equals);
    }

    private static Long resolveTenantId(Authentication authentication) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && tenantId > 0) {
            return tenantId;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getTenantId();
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return toLong(jwtAuthenticationToken.getTokenAttributes().get("tenantId"));
        }

        return null;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
