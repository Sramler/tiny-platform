package com.tiny.platform.core.oauth.tenant;

import com.tiny.platform.core.oauth.model.SecurityUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 统一处理当前活动租户的响应回写与运行时解析。
 *
 * <p>与 {@link com.tiny.platform.core.oauth.security.CurrentActorResolver} 分工：
 * 本类负责租户 / active scope 的运行态与请求上下文；CurrentActorResolver 负责从 Session 主体与 JWT 主体解析
 * userId、permissionsVersion 等身份快照字段（供控制面响应稳定输出）。</p>
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

    public static void putTenantFields(Map<String, Object> payload, Long activeTenantId, ActiveScope activeScope) {
        if (payload == null) {
            return;
        }
        if (activeScope != null && activeScope.isPlatform()) {
            payload.remove(TenantContextContract.ACTIVE_TENANT_ID_CLAIM);
            return;
        }
        putTenantFields(payload, activeTenantId);
    }

    public static void putScopeFields(Map<String, Object> payload, ActiveScope activeScope) {
        if (payload == null || activeScope == null || activeScope.scopeType() == null) {
            return;
        }
        payload.put(TenantContextContract.ACTIVE_SCOPE_TYPE_CLAIM, activeScope.scopeType());
        if (activeScope.scopeId() != null && activeScope.scopeId() > 0) {
            payload.put(TenantContextContract.ACTIVE_SCOPE_ID_CLAIM, activeScope.scopeId());
        } else {
            payload.remove(TenantContextContract.ACTIVE_SCOPE_ID_CLAIM);
        }
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

    public static String resolveActiveScopeTypeFromRequestContext() {
        ActiveScope activeScope = resolveActiveScopeFromRequestContext();
        return activeScope != null ? activeScope.scopeType() : null;
    }

    public static Long resolveActiveScopeIdFromRequestContext() {
        ActiveScope activeScope = resolveActiveScopeFromRequestContext();
        return activeScope != null ? activeScope.scopeId() : null;
    }

    public static ActiveScope resolveActiveScopeFromRequestContext() {
        String scopeType = ActiveScope.normalizeScopeType(TenantContext.getActiveScopeType());
        Long scopeId = TenantContext.getActiveScopeId();
        if (scopeType == null) {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpSession session = attributes.getRequest().getSession(false);
            if (session != null) {
                Object rawScopeType = session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY);
                scopeType = ActiveScope.normalizeScopeType(rawScopeType != null ? String.valueOf(rawScopeType) : null);
                scopeId = scopeId != null && scopeId > 0
                    ? scopeId
                    : toLong(session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY));
            }
            if (scopeType == null) {
                Long activeTenantId = resolveActiveTenantId(attributes.getRequest());
                if (activeTenantId != null && activeTenantId > 0) {
                    return ActiveScope.of(TenantContextContract.SCOPE_TYPE_TENANT, activeTenantId);
                }
                return ActiveScope.of(TenantContextContract.SCOPE_TYPE_PLATFORM, null);
            }
        }
        if (TenantContextContract.SCOPE_TYPE_TENANT.equals(scopeType) && (scopeId == null || scopeId <= 0)) {
            Long activeTenantId = resolveActiveTenantIdFromRequestContext();
            if (activeTenantId != null && activeTenantId > 0) {
                scopeId = activeTenantId;
            }
        }
        return ActiveScope.of(scopeType, scopeId);
    }

    public static String resolveSignalSourceFromRequestContext() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String headerValue = normalizeSignalSource(request.getHeader(TenantContextContract.SIGNAL_SOURCE_HEADER));
            if (headerValue != null) {
                return headerValue;
            }
        }

        String propertyValue = normalizeSignalSource(System.getProperty("permission.signal.source"));
        if (propertyValue != null) {
            return propertyValue;
        }
        String envValue = normalizeSignalSource(System.getenv("PERMISSION_SIGNAL_SOURCE"));
        if (envValue != null) {
            return envValue;
        }
        return TenantContextContract.SIGNAL_SOURCE_RUNTIME;
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

    private static String normalizeSignalSource(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (TenantContextContract.SIGNAL_SOURCE_TEST.equals(normalized)) {
            return TenantContextContract.SIGNAL_SOURCE_TEST;
        }
        if (TenantContextContract.SIGNAL_SOURCE_RUNTIME.equals(normalized)) {
            return TenantContextContract.SIGNAL_SOURCE_RUNTIME;
        }
        return null;
    }
}
