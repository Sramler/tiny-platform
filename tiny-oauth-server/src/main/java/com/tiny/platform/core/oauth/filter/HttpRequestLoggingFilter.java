package com.tiny.platform.core.oauth.filter;

import com.tiny.platform.core.oauth.config.HttpRequestLoggingProperties;
import com.tiny.platform.core.oauth.logging.HttpLogSanitizer;
import com.tiny.platform.core.oauth.model.HttpRequestLog;
import com.tiny.platform.core.oauth.service.HttpRequestLogService;
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
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
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
    public static final String ATTR_CLIENT_REQUEST_ID = HttpRequestLoggingFilter.class.getName() + ".CLIENT_REQUEST_ID";
    public static final String ATTR_TRACE_SOURCE = HttpRequestLoggingFilter.class.getName() + ".TRACE_SOURCE";
    public static final String ATTR_AUDIT_LOGGED = HttpRequestLoggingFilter.class.getName() + ".AUDIT_LOGGED";
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
    private final HttpRequestLogService logService;
    private final String serviceName;
    private final String env;

    public HttpRequestLoggingFilter(HttpRequestLoggingProperties properties,
                                    HttpRequestLogService logService,
                                    Environment environment,
                                    @Value("${spring.application.name:oauth-server}") String serviceName) {
        this.properties = properties;
        this.logService = logService;
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

        String clientRequestId = resolveClientRequestId(requestWrapper);
        String requestId = createRequestId();
        TraceResolution traceResolution = resolveTrace(requestWrapper, requestId);
        String traceId = traceResolution.traceId();
        String spanId = resolveSpanId(requestWrapper);
        setResponseCorrelationHeaders(responseWrapper, requestId, traceId);

        requestWrapper.setAttribute(ATTR_REQUEST_ID, requestId);
        requestWrapper.setAttribute(ATTR_CLIENT_REQUEST_ID, clientRequestId);
        requestWrapper.setAttribute(ATTR_TRACE_ID, traceId);
        requestWrapper.setAttribute(ATTR_TRACE_SOURCE, traceResolution.traceSource());
        requestWrapper.setAttribute(ATTR_SPAN_ID, spanId);

        var previousMdc = MDC.getCopyOfContextMap();
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
            log.debug("[TRACE_ID] 请求路径: {}, 方法: {}, traceId: {}, traceSource: {}, requestId: {}, clientRequestId: {}, MDC中的traceId: {}, MDC中的requestId: {}",
                    requestWrapper.getRequestURI(),
                    requestWrapper.getMethod(),
                    traceId,
                    traceResolution.traceSource(),
                    requestId,
                    clientRequestId,
                    MDC.get("traceId"),
                    MDC.get("requestId"));
        }

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            if (!requestWrapper.isAsyncStarted()) {
                saveFallbackAuditLogIfNeeded(requestWrapper, responseWrapper);
            }
            // 恢复进入过滤器前的 MDC，避免误清理其他框架写入的上下文
            restoreMdc(previousMdc);
            
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

    private String resolveClientRequestId(HttpServletRequest request) {
        String requestIdHeader = request.getHeader("X-Request-Id");
        if (!StringUtils.hasText(requestIdHeader)) {
            return null;
        }
        String clientRequestId = sanitizeRequestId(requestIdHeader);
        if (clientRequestId == null) {
            if (log.isDebugEnabled()) {
                log.debug("[REQUEST_ID] 客户端 X-Request-Id 格式非法，忽略。value={}", requestIdHeader);
            }
            return null;
        }
        if (log.isDebugEnabled()) {
            log.debug("[REQUEST_ID] 从请求头获取客户端 X-Request-Id: {} -> {}", requestIdHeader, clientRequestId);
        }
        return clientRequestId;
    }

    private String createRequestId() {
        String requestId = randomHex();
        if (log.isDebugEnabled()) {
            log.debug("[REQUEST_ID] 生成服务端 requestId: {}", requestId);
        }
        return requestId;
    }

    private TraceResolution resolveTrace(HttpServletRequest request, String fallbackRequestId) {
        for (String header : TRACE_ID_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                String sanitized = sanitizeTraceIdFromHeader(header, value);
                if (sanitized != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("[TRACE_ID] 从请求头 '{}' 获取到 traceId: {} -> {}", header, value, sanitized);
                    }
                    return new TraceResolution(sanitized, resolveTraceSourceFromHeader(header));
                }
                if (log.isDebugEnabled()) {
                    log.debug("[TRACE_ID] 请求头 '{}' 存在但格式非法，忽略。value={}", header, value);
                }
            }
        }
        // 2. 再看 query 参数 trace_id（用于不可控重定向场景）
        String traceParam = request.getParameter("trace_id");
        if (StringUtils.hasText(traceParam) && isTraceIdQueryParamAllowed(request)) {
            String sanitized = sanitizeTraceId(traceParam);
            if (sanitized != null) {
                if (log.isDebugEnabled()) {
                    log.debug("[TRACE_ID] 从 query 参数 trace_id 获取到 traceId: {} -> {}", traceParam, sanitized);
                }
                return new TraceResolution(sanitized, "query_trace_id");
            }
            if (log.isDebugEnabled()) {
                log.debug("[TRACE_ID] query 参数 trace_id 存在但格式非法，忽略。value={}", traceParam);
            }
        } else if (StringUtils.hasText(traceParam) && log.isDebugEnabled()) {
            log.debug("[TRACE_ID] query 参数 trace_id 仅在配置白名单路径生效，当前路径忽略。path={}",
                    request.getRequestURI());
        }

        // 3. 如果仍然没有找到任何 trace 相关信息，使用 fallback (requestId)
        HttpRequestLoggingProperties.TraceIdFallbackStrategy fallbackStrategy =
                properties.resolveTraceIdFallbackStrategy();
        String sanitized;
        String traceSource;
        if (fallbackStrategy == HttpRequestLoggingProperties.TraceIdFallbackStrategy.GENERATED) {
            sanitized = randomHex();
            traceSource = "generated";
            if (log.isDebugEnabled()) {
                log.debug("[TRACE_ID] 未找到 traceId，按策略 GENERATED 生成新的 traceId: {}", sanitized);
            }
        } else {
            sanitized = sanitizeTraceId(fallbackRequestId);
            traceSource = "fallback_request_id";
            if (sanitized == null) {
                sanitized = randomHex();
                traceSource = "generated";
                if (log.isDebugEnabled()) {
                    log.debug("[TRACE_ID] fallback(requestId) 不是合法 traceId，生成新的 traceId。fallback={}", fallbackRequestId);
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("[TRACE_ID] 未找到 traceId 请求头或 trace_id 参数，fallbackStrategy={} 最终 traceId={} traceSource={}",
                    fallbackStrategy, sanitized, traceSource);
        }
        // 额外输出一条 INFO 级别日志，便于运维排查外部系统未传递 traceId 的问题
        if (log.isInfoEnabled()) {
            String host = HttpLogSanitizer.sanitizeHeaderValue(HttpHeaders.HOST, request.getHeader(HttpHeaders.HOST));
            String userAgent = HttpLogSanitizer.sanitizeHeaderValue(HttpHeaders.USER_AGENT,
                    request.getHeader(HttpHeaders.USER_AGENT));
            log.info(
                    "[TRACE_ID][FALLBACK] 上游未传递任何 trace 相关请求头或 trace_id 参数。strategy={} traceSource={} method={} path={} host={} remoteIp={} userAgent={}",
                    fallbackStrategy,
                    traceSource,
                    request.getMethod(),
                    request.getRequestURI(),
                    host,
                    request.getRemoteAddr(),
                    userAgent
            );
        }
        return new TraceResolution(sanitized, traceSource);
    }

    private String resolveSpanId(HttpServletRequest request) {
        for (String header : SPAN_ID_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                String spanId = sanitizeSpanId(value);
                if (spanId != null) {
                    return spanId;
                }
                if (log.isDebugEnabled()) {
                    log.debug("[SPAN_ID] 请求头 '{}' 存在但格式非法，忽略。value={}", header, value);
                }
            }
        }
        return null;
    }

    private void setResponseCorrelationHeaders(HttpServletResponse response, String requestId, String traceId) {
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("X-Trace-Id", traceId);
        exposeHeader(response, "X-Request-Id");
        exposeHeader(response, "X-Trace-Id");
    }

    private void saveFallbackAuditLogIfNeeded(HttpServletRequest request, HttpServletResponse response) {
        if (Boolean.TRUE.equals(request.getAttribute(ATTR_AUDIT_LOGGED))) {
            return;
        }
        int status = response.getStatus();
        if (!shouldSaveFallbackAudit(request, status)) {
            return;
        }

        try {
            HttpRequestLog fallbackLog = new HttpRequestLog();
            fallbackLog.setServiceName((String) request.getAttribute(ATTR_SERVICE));
            fallbackLog.setEnv((String) request.getAttribute(ATTR_ENV));
            fallbackLog.setTraceId((String) request.getAttribute(ATTR_TRACE_ID));
            fallbackLog.setSpanId((String) request.getAttribute(ATTR_SPAN_ID));
            fallbackLog.setRequestId((String) request.getAttribute(ATTR_REQUEST_ID));
            fallbackLog.setClientRequestId((String) request.getAttribute(ATTR_CLIENT_REQUEST_ID));
            fallbackLog.setTraceSource((String) request.getAttribute(ATTR_TRACE_SOURCE));
            fallbackLog.setRequestAt(LocalDateTime.now());
            fallbackLog.setModule(extractModuleName(request.getRequestURI()));
            fallbackLog.setUserId((String) request.getAttribute(ATTR_USER_ID));
            fallbackLog.setClientIp(IpUtils.getClientIp(request));
            fallbackLog.setHost(truncate(HttpLogSanitizer.sanitizeHeaderValue(HttpHeaders.HOST,
                    request.getHeader(HttpHeaders.HOST)), 128));
            fallbackLog.setUserAgent(truncate(HttpLogSanitizer.sanitizeHeaderValue(HttpHeaders.USER_AGENT,
                    request.getHeader(HttpHeaders.USER_AGENT)), 512));
            fallbackLog.setHttpVersion(request.getProtocol());
            fallbackLog.setMethod(request.getMethod());
            fallbackLog.setPathTemplate(request.getRequestURI());
            fallbackLog.setRawPath(request.getRequestURI());
            fallbackLog.setQueryString(truncate(HttpLogSanitizer.sanitizeQueryString(request.getQueryString()), 1024));

            if (request instanceof ContentCachingRequestWrapper cachingRequest) {
                long requestSize = cachingRequest.getContentLengthLong();
                fallbackLog.setRequestSize(requestSize >= 0 ? requestSize : null);
            } else {
                long requestSize = request.getContentLengthLong();
                fallbackLog.setRequestSize(requestSize >= 0 ? requestSize : null);
            }
            if (response instanceof ContentCachingResponseWrapper cachingResponse) {
                fallbackLog.setResponseSize((long) cachingResponse.getContentSize());
            }

            fallbackLog.setStatus(status);
            fallbackLog.setSuccess(status < 400);
            Object startAttr = request.getAttribute(ATTR_START_TIME);
            if (startAttr instanceof Long startTime) {
                fallbackLog.setDurationMs((int) (System.currentTimeMillis() - startTime));
            }
            if (status >= 400) {
                fallbackLog.setError("filter-fallback status=" + status);
            }

            logService.save(fallbackLog);
            request.setAttribute(ATTR_AUDIT_LOGGED, Boolean.TRUE);
            if (log.isDebugEnabled()) {
                log.debug("[REQ_LOG][FALLBACK] 通过 Filter 兜底记录请求日志: method={} path={} status={} traceId={} requestId={}",
                        fallbackLog.getMethod(),
                        fallbackLog.getRawPath(),
                        fallbackLog.getStatus(),
                        fallbackLog.getTraceId(),
                        fallbackLog.getRequestId());
            }
        } catch (Exception e) {
            log.warn("[REQ_LOG][FALLBACK] Filter 兜底记录失败: {}", e.getMessage(), e);
        }
    }

    private boolean shouldSaveFallbackAudit(HttpServletRequest request, int status) {
        if (status >= 400) {
            return true;
        }
        String uri = request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return false;
        }
        return uri.contains("/oauth2/")
                || uri.contains("/connect/")
                || "/login".equals(uri)
                || "/logout".equals(uri);
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

    private void exposeHeader(HttpServletResponse response, String headerName) {
        String existing = response.getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        LinkedHashSet<String> exposed = new LinkedHashSet<>();
        if (StringUtils.hasText(existing)) {
            for (String item : existing.split(",")) {
                if (StringUtils.hasText(item)) {
                    exposed.add(item.trim());
                }
            }
        }
        exposed.add(headerName);
        response.setHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, String.join(", ", exposed));
    }

    private void restoreMdc(java.util.Map<String, String> previousMdc) {
        if (previousMdc == null || previousMdc.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(previousMdc);
    }

    private String sanitizeRequestId(String value) {
        String normalized = normalizeHex(value);
        if (normalized == null || normalized.length() == 0 || normalized.length() > 32) {
            return null;
        }
        return normalized;
    }

    private String sanitizeTraceIdFromHeader(String headerName, String value) {
        if ("traceparent".equalsIgnoreCase(headerName)) {
            return extractTraceIdFromTraceparent(value);
        }
        return sanitizeTraceId(value);
    }

    private String sanitizeTraceId(String value) {
        String normalized = normalizeHex(value);
        if (normalized == null || normalized.length() != 32) {
            return null;
        }
        if ("00000000000000000000000000000000".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private String sanitizeSpanId(String value) {
        String normalized = normalizeHex(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() != 16 && normalized.length() != 32) {
            return null;
        }
        if ("0000000000000000".equals(normalized) || "00000000000000000000000000000000".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    private String extractTraceIdFromTraceparent(String traceparent) {
        if (!StringUtils.hasText(traceparent)) {
            return null;
        }
        String[] parts = traceparent.trim().split("-");
        if (parts.length != 4) {
            return null;
        }
        return sanitizeTraceId(parts[1]);
    }

    private boolean isTraceIdQueryParamAllowed(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            return false;
        }
        List<String> allowlist = properties.getTraceIdQueryParamAllowedPathFragments();
        if (allowlist == null || allowlist.isEmpty()) {
            return false;
        }
        return allowlist.stream()
                .filter(StringUtils::hasText)
                .anyMatch(uri::contains);
    }

    private String normalizeHex(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace("-", "").toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            boolean isHex = (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f');
            if (!isHex) {
                return null;
            }
        }
        return normalized;
    }

    private String resolveTraceSourceFromHeader(String headerName) {
        if (!StringUtils.hasText(headerName)) {
            return "header_unknown";
        }
        String normalized = headerName.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return "header_" + normalized;
    }

    private record TraceResolution(String traceId, String traceSource) {
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
