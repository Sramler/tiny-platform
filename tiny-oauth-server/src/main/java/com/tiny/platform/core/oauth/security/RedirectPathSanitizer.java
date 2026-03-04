package com.tiny.platform.core.oauth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * 统一约束登录/MFA 流程中的 redirect 参数：
 * - 只允许站内相对路径
 * - 兼容 SavedRequest 产生的“同源绝对 URL”，并转换为相对路径
 * - 显式拒绝外部站点、协议相对路径和控制字符
 */
public final class RedirectPathSanitizer {

    public static final String DEFAULT_REDIRECT_PATH = "/";

    private RedirectPathSanitizer() {
    }

    public static String sanitize(String candidate, HttpServletRequest request) {
        return sanitize(candidate, request, DEFAULT_REDIRECT_PATH);
    }

    public static String sanitize(String candidate, HttpServletRequest request, String fallback) {
        String safeFallback = normalizeFallback(fallback);
        if (!StringUtils.hasText(candidate)) {
            return safeFallback;
        }

        String trimmed = candidate.trim();
        if (containsUnsafeChars(trimmed)) {
            return safeFallback;
        }

        if (isSafeRelativePath(trimmed)) {
            return trimmed;
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            return safeFallback;
        }

        if (!uri.isAbsolute() || request == null || !isSameOrigin(uri, request)) {
            return safeFallback;
        }

        String relativePath = toRelativePath(uri);
        return isSafeRelativePath(relativePath) ? relativePath : safeFallback;
    }

    public static boolean isSafeRelativePath(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        if (!candidate.startsWith("/") || candidate.startsWith("//")) {
            return false;
        }
        return !containsUnsafeChars(candidate);
    }

    public static String buildSanitizedQueryString(HttpServletRequest request, Set<String> redirectParameterNames) {
        if (request == null) {
            return "";
        }

        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap == null || parameterMap.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner("&");
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String name = entry.getKey();
            if (!StringUtils.hasText(name)) {
                continue;
            }
            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                joiner.add(urlEncode(name) + "=");
                continue;
            }
            for (String value : values) {
                String actualValue = value;
                if (redirectParameterNames != null && redirectParameterNames.contains(name)) {
                    actualValue = sanitize(value, request);
                }
                joiner.add(urlEncode(name) + "=" + urlEncode(actualValue == null ? "" : actualValue));
            }
        }
        return joiner.toString();
    }

    private static String normalizeFallback(String fallback) {
        if (!isSafeRelativePath(fallback)) {
            return DEFAULT_REDIRECT_PATH;
        }
        return fallback;
    }

    private static boolean containsUnsafeChars(String value) {
        if (value == null) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch) || ch == '\\') {
                return true;
            }
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("javascript:") || lower.startsWith("data:");
    }

    private static boolean isSameOrigin(URI uri, HttpServletRequest request) {
        if (!StringUtils.hasText(uri.getHost()) || !StringUtils.hasText(request.getServerName())) {
            return false;
        }
        boolean sameScheme = request.getScheme().equalsIgnoreCase(uri.getScheme());
        boolean sameHost = request.getServerName().equalsIgnoreCase(uri.getHost());
        return sameScheme && sameHost && resolvePort(uri) == resolvePort(request);
    }

    private static int resolvePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static int resolvePort(HttpServletRequest request) {
        int port = request.getServerPort();
        if (port > 0) {
            return port;
        }
        return "https".equalsIgnoreCase(request.getScheme()) ? 443 : 80;
    }

    private static String toRelativePath(URI uri) {
        String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : DEFAULT_REDIRECT_PATH;
        StringBuilder builder = new StringBuilder(path);
        if (StringUtils.hasText(uri.getRawQuery())) {
            builder.append('?').append(uri.getRawQuery());
        }
        if (StringUtils.hasText(uri.getRawFragment())) {
            builder.append('#').append(uri.getRawFragment());
        }
        return builder.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
