package com.tiny.platform.core.oauth.interceptor;

import com.tiny.platform.core.oauth.config.HttpRequestLoggingProperties;
import com.tiny.platform.core.oauth.filter.HttpRequestLoggingFilter;
import com.tiny.platform.core.oauth.model.HttpRequestLog;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.service.HttpRequestLogService;
import com.tiny.platform.infrastructure.core.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.slf4j.MDC;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;

@Component
public class HttpRequestLoggingInterceptor implements HandlerInterceptor {

    private final HttpRequestLoggingProperties properties;
    private final HttpRequestLogService logService;

    public HttpRequestLoggingInterceptor(HttpRequestLoggingProperties properties,
                                         HttpRequestLogService logService) {
        this.properties = properties;
        this.logService = logService;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        if (properties.isEnabled()) {
            String userId = resolveCurrentUserId();
            request.setAttribute(HttpRequestLoggingFilter.ATTR_USER_ID, userId);
            // 设置到 MDC，供 Logback 日志格式使用
            if (userId != null) {
                MDC.put("userId", userId);
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                @Nullable Exception ex) {
        if (!properties.isEnabled()) {
            return;
        }
        Object startAttr = request.getAttribute(HttpRequestLoggingFilter.ATTR_START_TIME);
        if (!(startAttr instanceof Long startTime)) {
            return;
        }

        HttpRequestLog log = new HttpRequestLog();
        log.setServiceName((String) request.getAttribute(HttpRequestLoggingFilter.ATTR_SERVICE));
        log.setEnv((String) request.getAttribute(HttpRequestLoggingFilter.ATTR_ENV));
        log.setTraceId((String) request.getAttribute(HttpRequestLoggingFilter.ATTR_TRACE_ID));
        log.setSpanId((String) request.getAttribute(HttpRequestLoggingFilter.ATTR_SPAN_ID));
        log.setRequestId((String) request.getAttribute(HttpRequestLoggingFilter.ATTR_REQUEST_ID));
        log.setRequestAt(LocalDateTime.now());

        log.setModule(extractModuleName(request.getRequestURI()));
        String userId = (String) request.getAttribute(HttpRequestLoggingFilter.ATTR_USER_ID);
        if (!StringUtils.hasText(userId)) {
            userId = resolveCurrentUserId();
        }
        log.setUserId(userId);
        log.setClientIp(IpUtils.getClientIp(request));
        log.setHost(request.getHeader(HttpHeaders.HOST));
        log.setUserAgent(truncate(request.getHeader(HttpHeaders.USER_AGENT), 512));
        log.setHttpVersion(request.getProtocol());
        log.setMethod(request.getMethod());

        String pathTemplate = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        log.setPathTemplate(StringUtils.hasText(pathTemplate) ? pathTemplate : request.getRequestURI());
        log.setRawPath(request.getRequestURI());
        log.setQueryString(truncate(request.getQueryString(), 1024));

        if (request instanceof ContentCachingRequestWrapper cachingRequest) {
            long requestSize = cachingRequest.getContentLengthLong();
            log.setRequestSize(requestSize >= 0 ? requestSize : null);
            if (properties.isIncludeRequestBody()) {
                log.setRequestBody(readBody(cachingRequest.getContentAsByteArray(),
                        cachingRequest.getCharacterEncoding(), cachingRequest.getContentType()));
            }
        } else {
            long requestSize = request.getContentLengthLong();
            log.setRequestSize(requestSize >= 0 ? requestSize : null);
        }

        if (response instanceof ContentCachingResponseWrapper cachingResponse) {
            log.setResponseSize((long) cachingResponse.getContentSize());
            if (properties.isIncludeResponseBody()) {
                log.setResponseBody(readBody(cachingResponse.getContentAsByteArray(),
                        cachingResponse.getCharacterEncoding(), response.getContentType()));
            }
        }

        int status = ex == null ? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        log.setStatus(status);
        log.setSuccess(status < 400 && ex == null);
        log.setDurationMs((int) (System.currentTimeMillis() - startTime));
        if (ex != null) {
            log.setError(truncate(ex.getMessage(), 512));
        }

        logService.save(log);
    }

    private String resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUserId() != null ? securityUser.getUserId().toString() : null;
        }
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return extractUserIdFromJwt(jwtAuthenticationToken.getToken());
        }
        if (principal instanceof String strPrincipal && !"anonymousUser".equalsIgnoreCase(strPrincipal)) {
            return strPrincipal;
        }
        return null;
    }

    private String extractUserIdFromJwt(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        String userId = jwt.getClaimAsString("user_id");
        if (!StringUtils.hasText(userId)) {
            userId = jwt.getClaimAsString("uid");
        }
        if (!StringUtils.hasText(userId)) {
            userId = jwt.getClaimAsString("username");
        }
        if (!StringUtils.hasText(userId)) {
            userId = jwt.getSubject();
        }
        return userId;
    }

    private String extractModuleName(String uri) {
        if (!StringUtils.hasText(uri)) {
            return null;
        }
        String cleaned = uri.startsWith("/") ? uri.substring(1) : uri;
        int idx = cleaned.indexOf('/');
        String module = idx >= 0 ? cleaned.substring(0, idx) : cleaned;
        return StringUtils.hasText(module) ? module : null;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || maxLength <= 0) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String readBody(byte[] content, String encoding, String contentType) {
        if (content == null || content.length == 0) {
            return null;
        }
        int maxLength = properties.getMaxBodyLength();
        byte[] truncated = content.length > maxLength ? Arrays.copyOf(content, maxLength) : content;
        if (StringUtils.hasText(contentType) && contentType.toLowerCase().contains("multipart/form-data")) {
            return "base64:" + Base64.getEncoder().encodeToString(truncated);
        }
        Charset charset = StringUtils.hasText(encoding) ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        return new String(truncated, charset);
    }
}


