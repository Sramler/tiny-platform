package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * 统一从 Session 主体（{@link SecurityUser}）与 Bearer 主体（{@link JwtAuthenticationToken}）解析当前用户标识，
 * 避免各控制器重复实现 instanceof 分支。
 */
public final class CurrentActorResolver {

    private static final String USER_ID_CLAIM = "userId";
    private static final String PERMISSIONS_VERSION_CLAIM = "permissionsVersion";

    private CurrentActorResolver() {
    }

    /**
     * 当前活动租户 ID：委托 {@link ActiveTenantResponseSupport#resolveActiveTenantId(Authentication)}，
     * 与 {@link com.tiny.platform.core.oauth.tenant.TenantContextFilter} 裁决口径一致。
     */
    public static Long resolveActiveTenantId(Authentication authentication) {
        return ActiveTenantResponseSupport.resolveActiveTenantId(authentication);
    }

    /**
     * 解析当前登录用户 ID：优先 {@link SecurityUser#getUserId()}，否则 JWT {@code userId} claim。
     */
    public static Long resolveUserId(Authentication authentication) {
        SecurityUser securityUser = resolveSecurityUser(authentication);
        if (securityUser != null && securityUser.getUserId() != null && securityUser.getUserId() > 0) {
            return securityUser.getUserId();
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Object raw = jwtAuthenticationToken.getToken().getClaims().get(USER_ID_CLAIM);
            return toLong(raw);
        }
        return null;
    }

    /**
     * 解析权限版本指纹用于控制面响应：
     * <ul>
     *   <li>Session 主体：使用 {@link SecurityUser#getPermissionsVersion()}（与登录会话一致）</li>
     *   <li>Bearer 主体：优先 JWT {@code permissionsVersion} claim；若缺失且提供了 {@link PermissionVersionService}，
     *       则按当前活动租户与 scope 做权威解析（与 {@link com.tiny.platform.core.oauth.tenant.TenantContextFilter} 校验口径一致）</li>
     * </ul>
     */
    public static String resolvePermissionsVersionForResponse(
        Authentication authentication,
        Long activeTenantId,
        String activeScopeType,
        Long activeScopeId,
        PermissionVersionService permissionVersionService
    ) {
        SecurityUser securityUser = resolveSecurityUser(authentication);
        if (securityUser != null) {
            return securityUser.getPermissionsVersion();
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Object raw = jwtAuthenticationToken.getToken().getClaims().get(PERMISSIONS_VERSION_CLAIM);
            if (raw != null) {
                String fromClaim = raw instanceof String s ? s.trim() : String.valueOf(raw);
                if (!fromClaim.isEmpty()) {
                    return fromClaim;
                }
            }
            if (permissionVersionService != null) {
                Long userId = toLong(jwtAuthenticationToken.getToken().getClaims().get(USER_ID_CLAIM));
                if (userId != null && userId > 0) {
                    return permissionVersionService.resolvePermissionsVersion(
                        userId,
                        activeTenantId,
                        activeScopeType,
                        activeScopeId
                    );
                }
            }
        }
        return null;
    }

    public static SecurityUser resolveSecurityUser(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser;
        }
        Object details = authentication.getDetails();
        if (details instanceof SecurityUser securityUser) {
            return securityUser;
        }
        return null;
    }

    public static boolean isJwtAuthenticationPrincipal(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
