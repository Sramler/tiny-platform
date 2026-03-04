package com.tiny.platform.core.oauth.logging;

import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一处理 HTTP 请求日志中的敏感信息脱敏。
 */
public final class HttpLogSanitizer {

    private static final String MASK = "***";

    private static final Set<String> SENSITIVE_QUERY_KEYS = new LinkedHashSet<>(List.of(
            "password",
            "passwd",
            "pwd",
            "token",
            "access_token",
            "refresh_token",
            "id_token",
            "client_secret",
            "clientsecret",
            "secret",
            "authorization",
            "code",
            "otp",
            "totp",
            "mfa_code",
            "verification_code"
    ));

    private static final Set<String> SENSITIVE_HEADER_NAMES = Set.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "api-key",
            "x-auth-token",
            "x-access-token"
    );

    private static final String JSON_SENSITIVE_FIELD_NAME_REGEX =
            "(?:password|passwd|pwd|token|access_token|refresh_token|id_token|client_secret|secret|authorization|code|otp|totp|mfa_code|verification_code)";

    private static final Pattern JSON_STRING_SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(\\\"" + JSON_SENSITIVE_FIELD_NAME_REGEX + "\\\"\\s*:\\s*\\\")([^\\\"\\\\]*(?:\\\\.[^\\\"\\\\]*)*)(\\\")",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JSON_NON_STRING_SENSITIVE_FIELD_PATTERN = Pattern.compile(
            "(\\\"" + JSON_SENSITIVE_FIELD_NAME_REGEX + "\\\"\\s*:\\s*)(true|false|null|-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)",
            Pattern.CASE_INSENSITIVE
    );

    private HttpLogSanitizer() {
    }

    @Nullable
    public static String sanitizeQueryString(@Nullable String queryString) {
        if (!StringUtils.hasText(queryString)) {
            return queryString;
        }
        String[] pairs = queryString.split("&", -1);
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            int idx = pair.indexOf('=');
            String rawKey = idx >= 0 ? pair.substring(0, idx) : pair;
            if (!isSensitiveFieldName(rawKey)) {
                continue;
            }
            pairs[i] = idx >= 0 ? rawKey + "=" + MASK : rawKey;
        }
        return String.join("&", pairs);
    }

    @Nullable
    public static String sanitizeBody(@Nullable String body, @Nullable String contentType) {
        if (!StringUtils.hasText(body)) {
            return body;
        }
        if (body.startsWith("base64:")) {
            return body;
        }
        String mediaType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (mediaType.contains("application/x-www-form-urlencoded")) {
            return sanitizeQueryString(body);
        }
        if (mediaType.contains("/json") || mediaType.contains("+json") || looksLikeJson(body)) {
            return sanitizeJson(body);
        }
        return body;
    }

    @Nullable
    public static String sanitizeHeaderValue(@Nullable String headerName, @Nullable String value) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(headerName)) {
            return value;
        }
        String normalized = headerName.trim().toLowerCase(Locale.ROOT);
        if (!isSensitiveHeaderName(normalized)) {
            return value;
        }
        if ("authorization".equals(normalized) || "proxy-authorization".equals(normalized)) {
            int idx = value.indexOf(' ');
            if (idx > 0) {
                return value.substring(0, idx) + " " + MASK;
            }
        }
        return MASK;
    }

    private static String sanitizeJson(String body) {
        String masked = replaceJsonStringFields(body);
        return replaceJsonNonStringFields(masked);
    }

    private static String replaceJsonStringFields(String input) {
        Matcher matcher = JSON_STRING_SENSITIVE_FIELD_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + MASK + matcher.group(3);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String replaceJsonNonStringFields(String input) {
        Matcher matcher = JSON_NON_STRING_SENSITIVE_FIELD_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + "\"" + MASK + "\"";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static boolean looksLikeJson(String body) {
        String trimmed = body.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private static boolean isSensitiveHeaderName(String normalizedHeaderName) {
        if (SENSITIVE_HEADER_NAMES.contains(normalizedHeaderName)) {
            return true;
        }
        return normalizedHeaderName.contains("secret")
                || normalizedHeaderName.contains("api-key")
                || normalizedHeaderName.endsWith("token");
    }

    private static boolean isSensitiveFieldName(String rawKey) {
        if (!StringUtils.hasText(rawKey)) {
            return false;
        }
        String normalized = rawKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        normalized = normalized.replace('-', '_');
        if (SENSITIVE_QUERY_KEYS.contains(normalized)) {
            return true;
        }
        for (String key : SENSITIVE_QUERY_KEYS) {
            if (normalized.endsWith("." + key)
                    || normalized.endsWith("[" + key + "]")
                    || normalized.endsWith("_" + key)) {
                return true;
            }
        }
        return false;
    }
}
