package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagerFactories;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 授权端点专用 MFA 授权管理器。
 *
 * <p>仅当当前会话确实要求 TOTP 时，才附加 {@code FACTOR_TOTP} 的因子要求；
 * 未登录/匿名访问时直接 abstain，让 Spring Authorization Server 自己处理
 * {@code prompt=none}、{@code login_required} 与常规登录重定向语义。</p>
 */
public final class AuthorizationEndpointMfaAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    public static final String BIND_REQUIRED_ATTRIBUTE =
            AuthorizationEndpointMfaAuthorizationManager.class.getName() + ".bindRequired";

    private static final String TOTP_FACTOR_AUTHORITY = AuthenticationFactorAuthorities.toAuthority(
            MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP
    );

    private final SecurityService securityService;
    private final AuthUserResolutionService authUserResolutionService;
    private final AuthorizationManager<RequestAuthorizationContext> totpAuthorizationManager;

    public AuthorizationEndpointMfaAuthorizationManager(SecurityService securityService,
                                                        AuthUserResolutionService authUserResolutionService) {
        this.securityService = securityService;
        this.authUserResolutionService = authUserResolutionService;
        this.totpAuthorizationManager =
                AuthorizationManagerFactories.<RequestAuthorizationContext>multiFactor()
                        .requireFactors(TOTP_FACTOR_AUTHORITY)
                        .build()
                        .authenticated();
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends org.springframework.security.core.Authentication> authentication,
                                         RequestAuthorizationContext context) {
        Object candidate = authentication.get();
        if (!(candidate instanceof org.springframework.security.core.Authentication auth)) {
            return null;
        }
        if (!auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }

        User user = resolveUser(auth);
        if (user == null) {
            return null;
        }

        Map<String, Object> status = this.securityService.getSecurityStatus(user);
        boolean totpBound = Boolean.TRUE.equals(status.get("totpBound"));
        boolean totpActivated = Boolean.TRUE.equals(status.get("totpActivated"));
        boolean forceMfa = Boolean.TRUE.equals(status.get("forceMfa"));
        boolean requireTotp = Boolean.TRUE.equals(status.get("requireTotp"));
        boolean bindRequired = forceMfa && (!totpBound || !totpActivated);

        context.getRequest().setAttribute(BIND_REQUIRED_ATTRIBUTE, bindRequired);

        if (!requireTotp && !bindRequired) {
            return null;
        }

        return this.totpAuthorizationManager.authorize(authentication, context);
    }

    private User resolveUser(org.springframework.security.core.Authentication authentication) {
        String username = authentication.getName();
        if (username == null || username.isBlank()) {
            return null;
        }

        Long activeTenantId = ActiveTenantResponseSupport.resolveActiveTenantId(authentication);
        String activeScopeType = TenantContext.getActiveScopeType();
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equalsIgnoreCase(activeScopeType)) {
            return this.authUserResolutionService.resolveUserRecordInPlatform(username).orElse(null);
        }
        if (activeTenantId == null) {
            return null;
        }
        return this.authUserResolutionService.resolveUserRecordInActiveTenant(username, activeTenantId).orElse(null);
    }
}
