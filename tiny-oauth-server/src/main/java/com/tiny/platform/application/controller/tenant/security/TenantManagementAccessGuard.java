package com.tiny.platform.application.controller.tenant.security;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 租户管理属于平台级控制面，仅允许平台作用域（{@code activeScopeType=PLATFORM}）的用户访问。
 *
 * <p>所有方法均要求 {@link TenantContext#isPlatformScope()} 为 true，
 * 在此前提下接受对应的 {@code system:tenant:*} 细粒度权限码。</p>
 */
@Component("tenantManagementAccessGuard")
public class TenantManagementAccessGuard {

    static final Set<String> READ_AUTHORITIES = Set.of("system:tenant:list", "system:tenant:view");
    static final Set<String> CREATE_AUTHORITIES = Set.of("system:tenant:create");
    static final Set<String> UPDATE_AUTHORITIES = Set.of("system:tenant:edit");
    static final Set<String> TEMPLATE_INITIALIZE_AUTHORITIES = Set.of("system:tenant:template:initialize");
    static final Set<String> FREEZE_AUTHORITIES = Set.of("system:tenant:freeze");
    static final Set<String> UNFREEZE_AUTHORITIES = Set.of("system:tenant:unfreeze");
    static final Set<String> DECOMMISSION_AUTHORITIES = Set.of("system:tenant:decommission");
    static final Set<String> DELETE_AUTHORITIES = Set.of("system:tenant:delete");

    public boolean canRead(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, READ_AUTHORITIES);
    }

    public boolean canCreate(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, CREATE_AUTHORITIES);
    }

    public boolean canUpdate(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, UPDATE_AUTHORITIES);
    }

    public boolean canInitializePlatformTemplate(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, TEMPLATE_INITIALIZE_AUTHORITIES);
    }

    public boolean canFreeze(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, FREEZE_AUTHORITIES);
    }

    public boolean canUnfreeze(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, UNFREEZE_AUTHORITIES);
    }

    public boolean canDecommission(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, DECOMMISSION_AUTHORITIES);
    }

    public boolean canDelete(Authentication authentication) {
        return isPlatformScope(authentication) && hasAnyAuthority(authentication, DELETE_AUTHORITIES);
    }

    private boolean isPlatformScope(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return TenantContext.isPlatformScope();
    }

    private boolean hasAnyAuthority(Authentication authentication, Set<String> requiredAuthorities) {
        return authentication.getAuthorities().stream()
            .map(grantedAuthority -> grantedAuthority.getAuthority())
            .anyMatch(requiredAuthorities::contains);
    }
}
