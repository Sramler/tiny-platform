package com.tiny.platform.core.oauth.filter;

import com.tiny.platform.core.oauth.config.HttpRequestLoggingProperties;
import com.tiny.platform.infrastructure.core.util.IpUtils;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 负责包装请求/响应并生成 trace/request 信息的过滤器
 * 日志写入在 {@link com.tiny.oauthserver.sys.interceptor.HttpRequestLoggingInterceptor} 中完成
 * 
 * 注意：此过滤器通过 {@link com.tiny.platform.core.oauth.config.HttpRequestLoggingFilterConfig} 
 * 使用 FilterRegistrationBean 注册，设置为最高优先级（HIGHEST_PRECEDENCE），
 * 确保在 Spring Security 过滤器链之前执行。
 * 这样可以实现完整的追踪闭环：从前端请求到最终返回，所有日志都包含 traceId
 */
public class HttpRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLoggingFilter.class);

    public static final String ATTR_START_TIME = HttpRequestLoggingFilter.class.getName() + ".START";
    public static final String ATTR_TRACE_ID = HttpRequestLoggingFilter.class.getName() + ".TRACE_ID";
    public static final String ATTR_SPAN_ID = HttpRequestLoggingFilter.class.getName() + ".SPAN_ID";
    public static final String ATTR_REQUEST_ID = HttpRequestLoggingFilter.class.getName() + ".REQUEST_ID";
    public static final String ATTR_ENV = HttpRequestLoggingFilter.class.getName() + ".ENV";
    public static final String ATTR_SERVICE = HttpRequestLoggingFilter.class.getName() + ".SERVICE";
    public static final String ATTR_USER_ID = HttpRequestLoggingFilter.class.getName() + ".USER_ID";

    private static final List<String> TRACE_ID_HEADERS = List.of(
            "X-Trace-Id",      // 前端发送的标准格式（大写）
            "x-trace-id",      // 小写格式
            "traceparent",
            "x-b3-traceid",
            "trace-id",
            "X-Request-Id",    // 也支持作为 trace-id 的 fallback
            "x-request-id"
    );

    private static final List<String> SPAN_ID_HEADERS = List.of(
            "x-b3-spanid",
            "span-id"
    );

    private final HttpRequestLoggingProperties properties;
    private final String serviceName;
    private final String env;

    public HttpRequestLoggingFilter(HttpRequestLoggingProperties properties,
                                    Environment environment,
                                    @Value("${spring.application.name:oauth-server}") String serviceName) {
        this.properties = properties;
        this.serviceName = serviceName;
        this.env = resolveEnv(environment);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String uri = request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return false;
        }
        return properties.getExcludedPathPrefixes().stream()
                .filter(StringUtils::hasText)
                .anyMatch(uri::startsWith);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = wrapRequest(request);
        ContentCachingResponseWrapper responseWrapper = wrapResponse(response);

        long start = System.currentTimeMillis();
        requestWrapper.setAttribute(ATTR_START_TIME, start);
        requestWrapper.setAttribute(ATTR_SERVICE, serviceName);
        requestWrapper.setAttribute(ATTR_ENV, env);

        String requestId = getOrCreateRequestId(requestWrapper, responseWrapper);
        String traceId = resolveTraceId(requestWrapper, requestId);
        String spanId = resolveSpanId(requestWrapper);

        requestWrapper.setAttribute(ATTR_REQUEST_ID, requestId);
        requestWrapper.setAttribute(ATTR_TRACE_ID, traceId);
        requestWrapper.setAttribute(ATTR_SPAN_ID, spanId);

        // ⭐️ 关键：尽早设置到 MDC，确保整个请求生命周期（包括 Spring Security）都有 traceId / requestId / clientIp
        // 这样实现了完整的追踪闭环：从前端请求到最终返回，所有日志都包含关键追踪信息
        MDC.put("traceId", traceId);
        MDC.put("requestId", requestId);
        if (spanId != null) {
            MDC.put("spanId", spanId);
        }
        // 补充 clientIp，便于在所有日志 pattern 中统一输出 IP
        String clientIp = IpUtils.getClientIp(requestWrapper);
        if (StringUtils.hasText(clientIp)) {
            MDC.put("clientIp", clientIp);
        }

        // 调试日志：记录 traceId 的获取过程（在 MDC 设置之后，所以这些日志本身也有 traceId）
        if (log.isDebugEnabled()) {
            logTraceIdHeaders(requestWrapper);
            log.debug("[TRACE_ID] 请求路径: {}, 方法: {}, 从请求头解析的 traceId: {}, requestId: {}, MDC中的traceId: {}, MDC中的requestId: {}",
                    requestWrapper.getRequestURI(),
                    requestWrapper.getMethod(),
                    traceId,
                    requestId,
                    MDC.get("traceId"),
                    MDC.get("requestId"));
        }

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            // 清理 MDC，避免线程池复用导致的问题
            MDC.clear();
            
            if (requestWrapper.isAsyncStarted()) {
                requestWrapper.getAsyncContext().addListener(new AsyncListener() {
                    @Override
                    public void onComplete(AsyncEvent event) throws IOException {
                        responseWrapper.copyBodyToResponse();
                    }

                    @Override
                    public void onTimeout(AsyncEvent event) {
                    }

                    @Override
                    public void onError(AsyncEvent event) {
                    }

                    @Override
                    public void onStartAsync(AsyncEvent event) {
                    }
                });
            } else {
                responseWrapper.copyBodyToResponse();
            }
        }
    }

    private ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper existing) {
            return existing;
        }
        return new ContentCachingRequestWrapper(request, properties.getMaxBodyLength());
    }

    private ContentCachingResponseWrapper wrapResponse(HttpServletResponse response) {
        if (response instanceof ContentCachingResponseWrapper existing) {
            return existing;
        }
        return new ContentCachingResponseWrapper(response);
    }

    private String getOrCreateRequestId(HttpServletRequest request, HttpServletResponse response) {
        String requestId = request.getHeader("X-Request-Id");
        boolean fromHeader = StringUtils.hasText(requestId);
        if (!fromHeader) {
            requestId = randomHex();
            if (log.isDebugEnabled()) {
                log.debug("[REQUEST_ID] 未找到 X-Request-Id 请求头，生成新的 requestId: {}", requestId);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("[REQUEST_ID] 从请求头获取到 X-Request-Id: {}", requestId);
            }
        }
        response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Request-Id");
        response.setHeader("X-Request-Id", requestId);
        return sanitizeId(requestId);
    }

    private String resolveTraceId(HttpServletRequest request, String fallback) {
        for (String header : TRACE_ID_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                String sanitized = sanitizeId(value);
                if (log.isDebugEnabled()) {
                    log.debug("[TRACE_ID] 从请求头 '{}' 获取到 traceId: {} -> {}", header, value, sanitized);
                }
                return sanitized;
            }
        }
        // 2. 再看 query 参数 trace_id（用于不可控重定向场景）
        String traceParam = request.getParameter("trace_id");
        if (StringUtils.hasText(traceParam)) {
            String sanitized = sanitizeId(traceParam);
            if (log.isDebugEnabled()) {
                log.debug("[TRACE_ID] 从 query 参数 trace_id 获取到 traceId: {} -> {}", traceParam, sanitized);
        }
            return sanitized;
        }

        // 3. 如果仍然没有找到任何 trace 相关信息，使用 fallback (requestId)
        String sanitized = sanitizeId(fallback);
        if (log.isDebugEnabled()) {
            log.debug("[TRACE_ID] 未找到 traceId 请求头或 trace_id 参数，使用 fallback (requestId): {}", sanitized);
        }
        // 额外输出一条 INFO 级别日志，便于运维排查外部系统未传递 traceId 的问题
        if (log.isInfoEnabled()) {
            String host = request.getHeader(HttpHeaders.HOST);
            String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
            log.info(
                    "[TRACE_ID][FALLBACK] 上游未传递任何 trace 相关请求头或 trace_id 参数，使用 fallback(requestId) 作为 traceId。method={} path={} host={} remoteIp={} userAgent={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    host,
                    request.getRemoteAddr(),
                    userAgent
            );
        }
        return sanitized;
    }

    private String resolveSpanId(HttpServletRequest request) {
        for (String header : SPAN_ID_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                return sanitizeId(value);
            }
        }
        return null;
    }

    private String sanitizeId(String value) {
        if (!StringUtils.hasText(value)) {
            return randomHex();
        }
        String normalized = value.trim().replace("-", "").toLowerCase(Locale.ROOT);
        if (normalized.length() > 64) {
            normalized = normalized.substring(0, 64);
        }
        return normalized;
    }

    private String randomHex() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveEnv(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length > 0) {
            return activeProfiles[0];
        }
        String[] defaultProfiles = environment.getDefaultProfiles();
        return defaultProfiles.length > 0 ? defaultProfiles[0] : "default";
    }

    /**
     * 记录所有 trace-id 相关的请求头，用于调试
     */
    private void logTraceIdHeaders(HttpServletRequest request) {
        if (!log.isDebugEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder("[TRACE_ID] 请求头检查: ");
        boolean found = false;
        for (String header : TRACE_ID_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                sb.append(header).append("=").append(value).append(", ");
                found = true;
            }
        }
        if (!found) {
            sb.append("未找到任何 trace-id 相关的请求头");
        } else {
            // 移除最后的 ", "
            sb.setLength(sb.length() - 2);
        }
        log.debug(sb.toString());
    }
}


