package com.tiny.platform.core.oauth.filter;

import ch.qos.logback.classic.Level;
import com.tiny.platform.core.oauth.config.HttpRequestLoggingProperties;
import com.tiny.platform.core.oauth.model.HttpRequestLog;
import com.tiny.platform.core.oauth.service.HttpRequestLogService;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpRequestLoggingFilterTraceCoverageTest {

    private Level previousLevel;

    @BeforeEach
    void enableDebugForFilterLogger() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
        logger.setLevel(previousLevel);
    }

    @Test
    void shouldHandleShouldNotFilterBranches() throws Exception {
        HttpRequestLoggingProperties disabled = new HttpRequestLoggingProperties();
        disabled.setEnabled(false);
        HttpRequestLoggingFilter disabledFilter = newFilter(disabled, mock(HttpRequestLogService.class), new MockEnvironment());
        assertThat(disabledFilter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/x"))).isTrue();

        HttpRequestLoggingProperties enabled = new HttpRequestLoggingProperties();
        HttpRequestLoggingFilter filter = newFilter(enabled, mock(HttpRequestLogService.class), new MockEnvironment());

        HttpServletRequest blankUriRequest = mock(HttpServletRequest.class);
        when(blankUriRequest.getRequestURI()).thenReturn("");
        assertThat(filter.shouldNotFilter(blankUriRequest)).isFalse();

        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/users"))).isFalse();
    }

    @Test
    void shouldBypassFilterWhenDisabledInDoFilterInternal() throws Exception {
        HttpRequestLoggingProperties properties = new HttpRequestLoggingProperties();
        properties.setEnabled(false);
        HttpRequestLogService logService = mock(HttpRequestLogService.class);
        HttpRequestLoggingFilter filter = newFilter(properties, logService, new MockEnvironment());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> ((HttpServletResponse) resp).setStatus(204));

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getHeader("X-Trace-Id")).isNull();
        verify(logService, never()).save(any(HttpRequestLog.class));
    }

    @Test
    void shouldShortCircuitInsideDoFilterInternalWhenDisabled() throws Exception {
        HttpRequestLoggingProperties properties = new HttpRequestLoggingProperties();
        properties.setEnabled(false);
        HttpRequestLogService logService = mock(HttpRequestLogService.class);
        HttpRequestLoggingFilter filter = newFilter(properties, logService, new MockEnvironment());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ping");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, resp) -> ((HttpServletResponse) resp).setStatus(202));

        assertThat(response.getStatus()).isEqualTo(202);
        verify(logService, never()).save(any(HttpRequestLog.class));
    }

    @Test
    void shouldHandleAsyncRequestBranchAndRestorePreviousMdc() throws Exception {
        HttpRequestLogService logService = mock(HttpRequestLogService.class);
        HttpRequestLoggingFilter filter = newFilter(defaultProperties(), logService, new MockEnvironment());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setAsyncSupported(true);
        request.setParameter("trace_id", "0123456789abcdef0123456789abcdef");
        request.setQueryString("trace_id=0123456789abcdef0123456789abcdef");
        request.addHeader("span-id", "0123456789abcdef");
        MockHttpServletResponse response = new MockHttpServletResponse();

        response.setHeader("Access-Control-Expose-Headers", "ETag");
        MDC.put("preExisting", "v");

        filter.doFilter(request, response, (req, resp) -> {
            assertThat(MDC.get("traceId")).isNotBlank();
            AsyncContext asyncContext = req.startAsync(req, resp);
            assertThat(asyncContext).isNotNull();
            ((HttpServletResponse) resp).setStatus(200);
        });

        Object listenersObj;
        try {
            listenersObj = ReflectionTestUtils.invokeMethod(request.getAsyncContext(), "getListeners");
        } catch (IllegalStateException ex) {
            listenersObj = null;
        }
        if (listenersObj == null) {
            listenersObj = ReflectionTestUtils.getField(request.getAsyncContext(), "listeners");
        }
        assertThat(listenersObj).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<AsyncListener> listeners = (List<AsyncListener>) listenersObj;
        assertThat(listeners).isNotEmpty();
        AsyncListener listener = listeners.get(0);
        AsyncEvent event = new AsyncEvent(request.getAsyncContext(), request, response);
        listener.onStartAsync(event);
        listener.onTimeout(event);
        listener.onError(event);
        listener.onComplete(event);

        assertThat(MDC.get("preExisting")).isEqualTo("v");
        assertThat(MDC.get("traceId")).isNull();
        assertThat(response.getHeader("Access-Control-Expose-Headers"))
                .contains("ETag")
                .contains("X-Request-Id")
                .contains("X-Trace-Id");
        verify(logService, never()).save(any(HttpRequestLog.class));
    }

    @Test
    void shouldWrapExistingCachingWrappersAndMergeExposeHeaders() {
        HttpRequestLoggingFilter filter = newFilter(defaultProperties(), mock(HttpRequestLogService.class), new MockEnvironment());

        MockHttpServletRequest rawRequest = new MockHttpServletRequest("GET", "/x");
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(rawRequest, 1024);
        Object sameRequest = ReflectionTestUtils.invokeMethod(filter, "wrapRequest", requestWrapper);
        assertThat(sameRequest).isSameAs(requestWrapper);

        MockHttpServletResponse rawResponse = new MockHttpServletResponse();
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(rawResponse);
        Object sameResponse = ReflectionTestUtils.invokeMethod(filter, "wrapResponse", responseWrapper);
        assertThat(sameResponse).isSameAs(responseWrapper);

        rawResponse.setHeader("Access-Control-Expose-Headers", "ETag, X-Request-Id");
        ReflectionTestUtils.invokeMethod(filter, "setResponseCorrelationHeaders", rawResponse, "r1", "t1");
        assertThat(rawResponse.getHeader("X-Request-Id")).isEqualTo("r1");
        assertThat(rawResponse.getHeader("X-Trace-Id")).isEqualTo("t1");
        assertThat(rawResponse.getHeader("Access-Control-Expose-Headers"))
                .isEqualTo("ETag, X-Request-Id, X-Trace-Id");
    }

    @Test
    void shouldResolveTraceFromHeadersQueryAndFallbackStrategies() throws Exception {
        HttpRequestLoggingProperties properties = defaultProperties();
        HttpRequestLoggingFilter filter = newFilter(properties, mock(HttpRequestLogService.class), new MockEnvironment());

        MockHttpServletRequest request1 = new MockHttpServletRequest("GET", "/api/test");
        request1.addHeader("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
        Object trace1 = ReflectionTestUtils.invokeMethod(filter, "resolveTrace", request1, "fallback");
        assertTraceResolution(trace1, "0123456789abcdef0123456789abcdef", "header_traceparent");

        MockHttpServletRequest request2 = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request2.addHeader("X-Trace-Id", "not-hex");
        request2.setParameter("trace_id", "fedcba9876543210fedcba9876543210");
        Object trace2 = ReflectionTestUtils.invokeMethod(filter, "resolveTrace", request2, "fallback");
        assertTraceResolution(trace2, "fedcba9876543210fedcba9876543210", "query_trace_id");

        MockHttpServletRequest request3 = new MockHttpServletRequest("GET", "/api/normal");
        request3.setParameter("trace_id", "fedcba9876543210fedcba9876543210");
        Object trace3 = ReflectionTestUtils.invokeMethod(filter, "resolveTrace", request3, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(invokeRecordString(trace3, "traceSource")).isEqualTo("fallback_request_id");
        assertThat(invokeRecordString(trace3, "traceId")).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        properties.setTraceIdFallbackStrategy("GENERATED");
        MockHttpServletRequest request4 = new MockHttpServletRequest("GET", "/api/no-trace");
        Object trace4 = ReflectionTestUtils.invokeMethod(filter, "resolveTrace", request4, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        assertThat(invokeRecordString(trace4, "traceSource")).isEqualTo("generated");
        assertThat(invokeRecordString(trace4, "traceId")).matches("[0-9a-f]{32}");

        properties.setTraceIdFallbackStrategy("REQUEST_ID");
        MockHttpServletRequest request5 = new MockHttpServletRequest("GET", "/api/no-trace");
        Object trace5 = ReflectionTestUtils.invokeMethod(filter, "resolveTrace", request5, "not-hex");
        assertThat(invokeRecordString(trace5, "traceSource")).isEqualTo("generated");
        assertThat(invokeRecordString(trace5, "traceId")).matches("[0-9a-f]{32}");

        MockHttpServletRequest request6 = new MockHttpServletRequest("GET", "/api/test");
        request6.addHeader("X-Request-Id", "abcdeabcdeabcdeabcdeabcdeabcdeab");
        Object trace6 = ReflectionTestUtils.invokeMethod(filter, "resolveTrace", request6, "fallback");
        assertTraceResolution(trace6, "abcdeabcdeabcdeabcdeabcdeabcdeab", "header_x_request_id");
    }

    @Test
    void shouldCoverTraceHelperValidationMethods() {
        HttpRequestLoggingFilter filter = newFilter(defaultProperties(), mock(HttpRequestLogService.class), new MockEnvironment());

        assertThat(invokeObj(filter, "sanitizeRequestId", (Object) null)).isNull();
        assertThat(invokeObj(filter, "sanitizeRequestId", "")).isNull();
        assertThat(invokeObj(filter, "sanitizeRequestId", "1".repeat(33))).isNull();
        assertThat(invokeString(filter, "sanitizeRequestId", "ABCDEF")).isEqualTo("abcdef");

        assertThat(invokeString(filter, "sanitizeTraceIdFromHeader",
                "traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"))
                .isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(invokeString(filter, "sanitizeTraceIdFromHeader",
                "X-Trace-Id", "0123456789abcdef0123456789abcdef"))
                .isEqualTo("0123456789abcdef0123456789abcdef");

        assertThat(invokeObj(filter, "sanitizeTraceId", "00000000000000000000000000000000")).isNull();
        assertThat(invokeObj(filter, "sanitizeTraceId", "abc")).isNull();
        assertThat(invokeString(filter, "sanitizeTraceId", "0123456789abcdef0123456789abcdef"))
                .isEqualTo("0123456789abcdef0123456789abcdef");

        assertThat(invokeObj(filter, "sanitizeSpanId", "0000000000000000")).isNull();
        assertThat(invokeObj(filter, "sanitizeSpanId", "00000000000000000000000000000000")).isNull();
        assertThat(invokeObj(filter, "sanitizeSpanId", "xyz")).isNull();
        assertThat(invokeString(filter, "sanitizeSpanId", "0123456789abcdef")).isEqualTo("0123456789abcdef");
        assertThat(invokeString(filter, "sanitizeSpanId",
                "0123456789abcdef0123456789abcdef")).isEqualTo("0123456789abcdef0123456789abcdef");

        assertThat(invokeObj(filter, "extractTraceIdFromTraceparent", (Object) null)).isNull();
        assertThat(invokeObj(filter, "extractTraceIdFromTraceparent", "00-abc")).isNull();
        assertThat(invokeString(filter, "extractTraceIdFromTraceparent",
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"))
                .isEqualTo("0123456789abcdef0123456789abcdef");

        assertThat(invokeObj(filter, "normalizeHex", (Object) null)).isNull();
        assertThat(invokeObj(filter, "normalizeHex", "---")).isNull();
        assertThat(invokeObj(filter, "normalizeHex", "abcz")).isNull();
        assertThat(invokeString(filter, "normalizeHex", "AB-CD")).isEqualTo("abcd");

        assertThat(invokeString(filter, "resolveTraceSourceFromHeader", (Object) null))
                .isEqualTo("header_unknown");
        assertThat(invokeString(filter, "resolveTraceSourceFromHeader", "X-Trace-Id"))
                .isEqualTo("header_x_trace_id");
    }

    @Test
    void shouldCoverSpanAndQueryAllowlistAndFallbackAuditHelpers() {
        HttpRequestLogService logService = mock(HttpRequestLogService.class);
        HttpRequestLoggingProperties properties = defaultProperties();
        HttpRequestLoggingFilter filter = newFilter(properties, logService, new MockEnvironment());

        MockHttpServletRequest spanRequest = new MockHttpServletRequest("GET", "/x");
        spanRequest.addHeader("x-b3-spanid", "not-hex");
        spanRequest.addHeader("span-id", "0123456789abcdef");
        assertThat(invokeString(filter, "resolveSpanId", spanRequest))
                .isEqualTo("0123456789abcdef");
        assertThat(invokeObj(filter, "sanitizeSpanId", "abc123")).isNull();

        HttpServletRequest noUri = mock(HttpServletRequest.class);
        when(noUri.getRequestURI()).thenReturn(null);
        assertThat(invokeBoolean(filter, "isTraceIdQueryParamAllowed", noUri)).isFalse();

        properties.setTraceIdQueryParamAllowedPathFragments(List.of());
        MockHttpServletRequest uriReq = new MockHttpServletRequest("GET", "/oauth2/authorize");
        assertThat(invokeBoolean(filter, "isTraceIdQueryParamAllowed", uriReq)).isFalse();

        properties.setTraceIdQueryParamAllowedPathFragments(List.of("/oauth2/", "/custom"));
        assertThat(invokeBoolean(filter, "isTraceIdQueryParamAllowed", uriReq)).isTrue();

        MockHttpServletRequest auditReq = new MockHttpServletRequest("GET", "/custom/path");
        MockHttpServletResponse auditResp = new MockHttpServletResponse();
        auditResp.setStatus(200);
        auditReq.setAttribute(HttpRequestLoggingFilter.ATTR_AUDIT_LOGGED, Boolean.TRUE);
        ReflectionTestUtils.invokeMethod(filter, "saveFallbackAuditLogIfNeeded", auditReq, auditResp);
        verify(logService, never()).save(any(HttpRequestLog.class));

        MockHttpServletRequest noSaveReq = new MockHttpServletRequest("GET", "/api/ok");
        noSaveReq.setAttribute(HttpRequestLoggingFilter.ATTR_SERVICE, "svc");
        noSaveReq.setAttribute(HttpRequestLoggingFilter.ATTR_ENV, "test");
        noSaveReq.setAttribute(HttpRequestLoggingFilter.ATTR_TRACE_ID, "0123456789abcdef0123456789abcdef");
        noSaveReq.setAttribute(HttpRequestLoggingFilter.ATTR_REQUEST_ID, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        MockHttpServletResponse noSaveResp = new MockHttpServletResponse();
        noSaveResp.setStatus(200);
        ReflectionTestUtils.invokeMethod(filter, "saveFallbackAuditLogIfNeeded", noSaveReq, noSaveResp);
        verify(logService, never()).save(any(HttpRequestLog.class));

        assertThat(invokeBoolean(filter, "shouldSaveFallbackAudit", new MockHttpServletRequest("GET", "/api/x"), 500))
                .isTrue();
        HttpServletRequest blank = mock(HttpServletRequest.class);
        when(blank.getRequestURI()).thenReturn("");
        assertThat(invokeBoolean(filter, "shouldSaveFallbackAudit", blank, 200)).isFalse();
        assertThat(invokeBoolean(filter, "shouldSaveFallbackAudit", new MockHttpServletRequest("GET", "/oauth2/token"), 200))
                .isTrue();
        assertThat(invokeBoolean(filter, "shouldSaveFallbackAudit", new MockHttpServletRequest("GET", "/connect/userinfo"), 200))
                .isTrue();
        assertThat(invokeBoolean(filter, "shouldSaveFallbackAudit", new MockHttpServletRequest("GET", "/login"), 200))
                .isTrue();
        assertThat(invokeBoolean(filter, "shouldSaveFallbackAudit", new MockHttpServletRequest("GET", "/logout"), 200))
                .isTrue();
        assertThat(invokeBoolean(filter, "shouldSaveFallbackAudit", new MockHttpServletRequest("GET", "/api/x"), 200))
                .isFalse();
    }

    @Test
    void shouldSaveFallbackAuditWithoutCachingWrappersAndHandleSaveFailure() {
        HttpRequestLogService logService = mock(HttpRequestLogService.class);
        HttpRequestLoggingFilter filter = newFilter(defaultProperties(), logService, new MockEnvironment());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fail");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_SERVICE, "svc");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_ENV, "test");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_TRACE_ID, "0123456789abcdef0123456789abcdef");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_REQUEST_ID, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_START_TIME, System.currentTimeMillis() - 5);
        request.setQueryString("password=abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(401);

        doThrow(new RuntimeException("db down")).when(logService).save(any(HttpRequestLog.class));
        ReflectionTestUtils.invokeMethod(filter, "saveFallbackAuditLogIfNeeded", request, response);
        verify(logService).save(any(HttpRequestLog.class));
        assertThat(request.getAttribute(HttpRequestLoggingFilter.ATTR_AUDIT_LOGGED)).isNull();
    }

    @Test
    void shouldCoverMiscHelpersAndMdcRestoreAndLogTraceIdHeaders() {
        HttpRequestLoggingFilter filter = newFilter(defaultProperties(), mock(HttpRequestLogService.class), new MockEnvironment());

        assertThat(invokeObj(filter, "extractModuleName", (Object) null)).isNull();
        assertThat(invokeObj(filter, "extractModuleName", "")).isNull();
        assertThat(invokeString(filter, "extractModuleName", "/oauth2/token")).isEqualTo("oauth2");
        assertThat(invokeObj(filter, "extractModuleName", "/")).isNull();

        assertThat(invokeObj(filter, "truncate", (Object) null, 10)).isNull();
        assertThat(invokeObj(filter, "truncate", "", 10)).isNull();
        assertThat(invokeObj(filter, "truncate", "abc", 0)).isNull();
        assertThat(invokeString(filter, "truncate", "abc", 10)).isEqualTo("abc");
        assertThat(invokeString(filter, "truncate", "abcdef", 3)).isEqualTo("abc");

        MDC.put("k", "v");
        ReflectionTestUtils.invokeMethod(filter, "restoreMdc", (Object) null);
        assertThat(MDC.getCopyOfContextMap()).isNull();

        MDC.put("temp", "x");
        ReflectionTestUtils.invokeMethod(filter, "restoreMdc", Map.of("restored", "y"));
        assertThat(MDC.get("restored")).isEqualTo("y");
        assertThat(MDC.get("temp")).isNull();

        MockHttpServletRequest noHeaderReq = new MockHttpServletRequest("GET", "/x");
        ReflectionTestUtils.invokeMethod(filter, "logTraceIdHeaders", noHeaderReq);

        MockHttpServletRequest withHeaderReq = new MockHttpServletRequest("GET", "/x");
        withHeaderReq.addHeader("X-Trace-Id", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.invokeMethod(filter, "logTraceIdHeaders", withHeaderReq);

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(HttpRequestLoggingFilter.class);
        logger.setLevel(Level.INFO);
        ReflectionTestUtils.invokeMethod(filter, "logTraceIdHeaders", withHeaderReq);
        logger.setLevel(Level.DEBUG);
    }

    @Test
    void shouldCoverResolveClientRequestIdBranchesAndInvalidQueryTraceParam() throws Exception {
        HttpRequestLoggingFilter filter = newFilter(defaultProperties(), mock(HttpRequestLogService.class), new MockEnvironment());

        MockHttpServletRequest noHeaderReq = new MockHttpServletRequest("GET", "/x");
        assertThat(invokeObj(filter, "resolveClientRequestId", noHeaderReq)).isNull();

        MockHttpServletRequest invalidHeaderReq = new MockHttpServletRequest("GET", "/x");
        invalidHeaderReq.addHeader("X-Request-Id", "not_hex");
        assertThat(invokeObj(filter, "resolveClientRequestId", invalidHeaderReq)).isNull();

        MockHttpServletRequest validHeaderReq = new MockHttpServletRequest("GET", "/x");
        validHeaderReq.addHeader("X-Request-Id", "ABCDEF");
        assertThat(invokeString(filter, "resolveClientRequestId", validHeaderReq)).isEqualTo("abcdef");

        MockHttpServletRequest invalidQueryTraceReq = new MockHttpServletRequest("GET", "/oauth2/authorize");
        invalidQueryTraceReq.setParameter("trace_id", "not-hex");
        Object trace = ReflectionTestUtils.invokeMethod(filter, "resolveTrace", invalidQueryTraceReq,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(invokeRecordString(trace, "traceSource")).isEqualTo("fallback_request_id");
    }

    @Test
    void shouldCoverResolveEnvBranches() {
        HttpRequestLoggingFilter filter = newFilter(defaultProperties(), mock(HttpRequestLogService.class), new MockEnvironment());

        MockEnvironment activeEnv = new MockEnvironment().withProperty("x", "y");
        activeEnv.setActiveProfiles("dev");
        assertThat(invokeString(filter, "resolveEnv", activeEnv)).isEqualTo("dev");

        Environment noActiveWithDefault = mock(Environment.class);
        when(noActiveWithDefault.getActiveProfiles()).thenReturn(new String[0]);
        when(noActiveWithDefault.getDefaultProfiles()).thenReturn(new String[]{"defaultX"});
        assertThat(invokeString(filter, "resolveEnv", noActiveWithDefault)).isEqualTo("defaultX");

        Environment noProfiles = mock(Environment.class);
        when(noProfiles.getActiveProfiles()).thenReturn(new String[0]);
        when(noProfiles.getDefaultProfiles()).thenReturn(new String[0]);
        assertThat(invokeString(filter, "resolveEnv", noProfiles)).isEqualTo("default");
    }

    @Test
    void shouldCoverFallbackAuditSuccessPathViaReflectionWithoutStartTime() throws Exception {
        HttpRequestLogService logService = mock(HttpRequestLogService.class);
        HttpRequestLoggingFilter filter = newFilter(defaultProperties(), logService, new MockEnvironment());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/connect/userinfo");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_SERVICE, "svc");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_ENV, "test");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_TRACE_ID, "0123456789abcdef0123456789abcdef");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_SPAN_ID, "0123456789abcdef");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_REQUEST_ID, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_CLIENT_REQUEST_ID, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_TRACE_SOURCE, "header_x_trace_id");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_USER_ID, "u1");
        request.setQueryString("code=abc123");
        request.addHeader("Host", "example.test");
        request.addHeader("User-Agent", "JUnit");
        request.setContentType("application/json");
        request.setContent("{\"x\":1}".getBytes());

        MockHttpServletResponse rawResponse = new MockHttpServletResponse();
        ContentCachingResponseWrapper response = new ContentCachingResponseWrapper(rawResponse);
        response.setStatus(200);
        response.getWriter().write("{\"ok\":true}");
        response.getWriter().flush();

        ReflectionTestUtils.invokeMethod(filter, "saveFallbackAuditLogIfNeeded", request, response);

        ArgumentCaptor<HttpRequestLog> captor = ArgumentCaptor.forClass(HttpRequestLog.class);
        verify(logService).save(captor.capture());
        HttpRequestLog saved = captor.getValue();
        assertThat(saved.getSuccess()).isTrue();
        assertThat(saved.getError()).isNull();
        assertThat(saved.getQueryString()).isEqualTo("code=***");
        assertThat(saved.getTraceSource()).isEqualTo("header_x_trace_id");
    }

    private HttpRequestLoggingFilter newFilter(HttpRequestLoggingProperties properties,
                                               HttpRequestLogService logService,
                                               Environment environment) {
        return new HttpRequestLoggingFilter(properties, logService, environment, "tiny-oauth-server");
    }

    private HttpRequestLoggingProperties defaultProperties() {
        HttpRequestLoggingProperties properties = new HttpRequestLoggingProperties();
        properties.setEnabled(true);
        return properties;
    }

    private void assertTraceResolution(Object traceResolution, String traceId, String traceSource) throws Exception {
        assertThat(invokeRecordString(traceResolution, "traceId")).isEqualTo(traceId);
        assertThat(invokeRecordString(traceResolution, "traceSource")).isEqualTo(traceSource);
    }

    private String invokeRecordString(Object record, String methodName) throws Exception {
        var method = record.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (String) method.invoke(record);
    }

    private Object invokeObj(Object target, String methodName, Object... args) {
        return ReflectionTestUtils.invokeMethod(target, methodName, args);
    }

    private String invokeString(Object target, String methodName, Object... args) {
        return (String) ReflectionTestUtils.invokeMethod(target, methodName, args);
    }

    private boolean invokeBoolean(Object target, String methodName, Object... args) {
        Boolean result = (Boolean) ReflectionTestUtils.invokeMethod(target, methodName, args);
        return Boolean.TRUE.equals(result);
    }
}
