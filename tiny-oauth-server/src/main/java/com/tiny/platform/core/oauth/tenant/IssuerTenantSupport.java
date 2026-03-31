package com.tiny.platform.core.oauth.tenant;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SAS 多 issuer 路由工具。
 *
 * 约定 issuer 为 path 形式，例如：
 * - http://localhost:9000/default
 * - 对应端点：/default/oauth2/authorize
 */
public final class IssuerTenantSupport {

    private static final Pattern TENANT_CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,31}$");
    private static final Pattern ISSUER_PATH_PATTERN = Pattern.compile("^/([a-z0-9][a-z0-9-]{1,31})/(oauth2/.*|\\.well-known/.*|connect/.*)$");

    private IssuerTenantSupport() {
    }

    public static String extractTenantCodeFromRequestPath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return null;
        }
        Matcher matcher = ISSUER_PATH_PATTERN.matcher(requestPath);
        if (!matcher.matches()) {
            return null;
        }
        return normalizeTenantCode(matcher.group(1));
    }

    public static String extractTenantCodeFromIssuer(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(issuer.trim());
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String[] segments = path.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                String candidate = normalizeTenantCode(segments[i]);
                if (candidate != null) {
                    return candidate;
                }
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    public static boolean isAuthorizationEndpointPath(String requestPath) {
        if (requestPath == null) {
            return false;
        }
        return requestPath.startsWith("/oauth2/authorize")
                || requestPath.matches("^/[a-z0-9][a-z0-9-]{1,31}/oauth2/authorize(?:/.*)?$");
    }

    public static boolean isAuthorizationServerEndpointPath(String requestPath) {
        if (requestPath == null) {
            return false;
        }
        if (requestPath.startsWith("/oauth2/") || requestPath.startsWith("/.well-known/") || requestPath.startsWith("/connect/")) {
            return true;
        }
        return ISSUER_PATH_PATTERN.matcher(requestPath).matches();
    }

    public static boolean isWellKnownOrJwkSetPath(String requestPath) {
        if (requestPath == null) {
            return false;
        }
        if (requestPath.startsWith("/.well-known/") || requestPath.startsWith("/oauth2/jwks")) {
            return true;
        }
        return requestPath.matches("^/[a-z0-9][a-z0-9-]{1,31}/\\.well-known/.*$")
                || requestPath.matches("^/[a-z0-9][a-z0-9-]{1,31}/oauth2/jwks(?:/.*)?$");
    }

    /**
     * 默认 issuer（非 /{tenantCode}/… 前缀）下的 OAuth2 授权服务器「客户端认证 + 表单」类 POST 端点。
     * <p>
     * 此类请求进入过滤器链时 SecurityContext 往往是 Registered Client 认证而非业务用户主体，
     * 且静默换票等场景可能无法依赖会话冻结租户；JWT 内租户与 scope 由 token 定制器从授权记录解析。
     * 带租户前缀的 issuer 路径仍应走租户上下文过滤器常规则。
     */
    public static boolean isDefaultIssuerOAuth2ProtocolPostEndpoint(String requestPath) {
        if (requestPath == null) {
            return false;
        }
        return switch (requestPath) {
            case "/oauth2/token",
                 "/oauth2/introspect",
                 "/oauth2/revoke",
                 "/oauth2/device_authorization",
                 "/oauth2/par" -> true;
            default -> false;
        };
    }

    private static String normalizeTenantCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return TENANT_CODE_PATTERN.matcher(normalized).matches() ? normalized : null;
    }
}
