package com.tiny.platform.core.oauth.tenant;

import com.tiny.platform.core.oauth.model.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 统一处理当前活动租户的响应回写与运行时解析。
 */
public final class ActiveTenantResponseSupport {

    private ActiveTenantResponseSupport() {
    }

    public static Long resolveActiveTenantId(Authentication authentication) {
        Long activeTenantId = TenantContext.getActiveTenantId();
        if (activeTenantId != null && activeTenantId > 0) {
            return activeTenantId;
        }

        activeTenantId = resolveFromAuthentication(authentication);
        if (activeTenantId != null && activeTenantId > 0) {
            return activeTenantId;
        }

        return null;
    }

    public static void putTenantFields(Map<String, Object> payload, Long activeTenantId) {
        if (payload == null || activeTenantId == null || activeTenantId <= 0) {
            return;
        }
        payload.put("activeTenantId", activeTenantId);
    }

    public static Long resolveActiveTenantId(HttpServletRequest request) {
        Long activeTenantId = TenantContext.getActiveTenantId();
        if (activeTenantId != null && activeTenantId > 0) {
            return activeTenantId;
        }

        if (request == null) {
            return null;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        return toLong(session.getAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY));
    }

    public static Long resolveActiveTenantIdFromRequestContext() {
        Long activeTenantId = TenantContext.getActiveTenantId();
        if (activeTenantId != null && activeTenantId > 0) {
            return activeTenantId;
        }

        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }

        return resolveActiveTenantId(attributes.getRequest());
    }

    private static Long resolveFromAuthentication(Authentication authentication) {
        if (authentication == null) {
            return null;
        }

        Object details = authentication.getDetails();
        if (details instanceof SecurityUser securityUser) {
            Long activeTenantId = securityUser.getActiveTenantId();
            if (activeTenantId != null && activeTenantId > 0) {
                return activeTenantId;
            }
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            Long activeTenantId = securityUser.getActiveTenantId();
            if (activeTenantId != null && activeTenantId > 0) {
                return activeTenantId;
            }
        }

        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Object tenantClaim = jwtAuthenticationToken.getTokenAttributes().get(TenantContextContract.ACTIVE_TENANT_ID_CLAIM);
            return toLong(tenantClaim);
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
