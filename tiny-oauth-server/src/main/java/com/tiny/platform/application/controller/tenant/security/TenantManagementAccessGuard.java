package com.tiny.platform.application.controller.tenant.security;

import com.tiny.platform.core.oauth.security.LegacyAuthConstants;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.infrastructure.tenant.config.PlatformTenantProperties;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 租户管理属于平台级控制面，仅允许配置的平台租户中的管理员访问。
 * 平台租户编码由 {@link PlatformTenantProperties#getPlatformTenantCode()} 提供，默认 "default"。
 */
@Component("tenantManagementAccessGuard")
public class TenantManagementAccessGuard {

    private final TenantRepository tenantRepository;
    private final PlatformTenantProperties platformTenantProperties;

    public TenantManagementAccessGuard(TenantRepository tenantRepository,
                                      PlatformTenantProperties platformTenantProperties) {
        this.tenantRepository = tenantRepository;
        this.platformTenantProperties = platformTenantProperties;
    }

    public boolean canManage(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        Long platformTenantId = tenantRepository.findByCode(platformTenantProperties.getPlatformTenantCode())
            .map(tenant -> tenant.getId())
            .orElse(null);
        if (platformTenantId == null) {
            return false;
        }

        Long currentActiveTenantId = resolveActiveTenantId(authentication);
        return Objects.equals(platformTenantId, currentActiveTenantId)
            && hasAdminAuthority(authentication);
    }

    private boolean hasAdminAuthority(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(grantedAuthority -> grantedAuthority.getAuthority())
            .anyMatch(LegacyAuthConstants::isAdminAuthority);
    }

    private Long resolveActiveTenantId(Authentication authentication) {
        return ActiveTenantResponseSupport.resolveActiveTenantId(authentication);
    }
}
