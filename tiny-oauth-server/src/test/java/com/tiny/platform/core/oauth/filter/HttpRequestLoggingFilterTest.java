package com.tiny.platform.core.oauth.filter;

import com.tiny.platform.core.oauth.config.HttpRequestLoggingProperties;
import com.tiny.platform.core.oauth.model.HttpRequestLog;
import com.tiny.platform.core.oauth.service.HttpRequestLogService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class HttpRequestLoggingFilterTest {

    @Test
    void shouldSaveFallbackAuditLogForSecurityRejectedRequest() throws Exception {
        HttpRequestLogService logService = mock(HttpRequestLogService.class);
        HttpRequestLoggingFilter filter = newFilter(defaultProperties(), logService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setQueryString("client_id=web&password=secret123");
        request.setParameter("client_id", "web");
        request.setParameter("password", "secret123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> ((HttpServletResponse) resp).setStatus(401));

        ArgumentCaptor<HttpRequestLog> captor = ArgumentCaptor.forClass(HttpRequestLog.class);
        verify(logService).save(captor.capture());

        HttpRequestLog saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(401);
        assertThat(saved.getSuccess()).isFalse();
        assertThat(saved.getQueryString()).isEqualTo("client_id=web&password=***");
        assertThat(request.getAttribute(HttpRequestLoggingFilter.ATTR_AUDIT_LOGGED)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void shouldNotDuplicateAuditLogWhenInterceptorAlreadyMarkedRequest() throws Exception {
        HttpRequestLogService logService = mock(HttpRequestLogService.class);
        HttpRequestLoggingFilter filter = newFilter(defaultProperties(), logService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> {
            req.setAttribute(HttpRequestLoggingFilter.ATTR_AUDIT_LOGGED, Boolean.TRUE);
            ((HttpServletResponse) resp).setStatus(401);
        });

        verify(logService, never()).save(org.mockito.ArgumentMatchers.any(HttpRequestLog.class));
    }

    @Test
    void shouldUseQueryTraceIdOnAllowedPath() throws Exception {
        HttpRequestLogService logService = mock(HttpRequestLogService.class);
        HttpRequestLoggingFilter filter = newFilter(defaultProperties(), logService);

        String traceId = "0123456789abcdef0123456789abcdef";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setQueryString("client_id=web&trace_id=" + traceId);
        request.setParameter("client_id", "web");
        request.setParameter("trace_id", traceId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> ((HttpServletResponse) resp).setStatus(302));

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(traceId);
        assertThat(request.getAttribute(HttpRequestLoggingFilter.ATTR_TRACE_SOURCE)).isEqualTo("query_trace_id");

        ArgumentCaptor<HttpRequestLog> captor = ArgumentCaptor.forClass(HttpRequestLog.class);
        verify(logService).save(captor.capture());
        assertThat(captor.getValue().getTraceId()).isEqualTo(traceId);
    }

    @Test
    void shouldIgnoreQueryTraceIdWhenPathNotInConfiguredAllowlist() throws Exception {
        HttpRequestLoggingProperties properties = defaultProperties();
        properties.setTraceIdQueryParamAllowedPathFragments(java.util.List.of("/custom/callback"));
        HttpRequestLogService logService = mock(HttpRequestLogService.class);
        HttpRequestLoggingFilter filter = newFilter(properties, logService);

        String traceId = "fedcba9876543210fedcba9876543210";
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorize");
        request.setQueryString("trace_id=" + traceId);
        request.setParameter("trace_id", traceId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, resp) -> ((HttpServletResponse) resp).setStatus(302));

        assertThat(request.getAttribute(HttpRequestLoggingFilter.ATTR_TRACE_SOURCE)).isNotEqualTo("query_trace_id");
        assertThat(response.getHeader("X-Trace-Id")).isNotEqualTo(traceId);
    }

    private HttpRequestLoggingFilter newFilter(HttpRequestLoggingProperties properties, HttpRequestLogService logService) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        return new HttpRequestLoggingFilter(properties, logService, environment, "tiny-oauth-server");
    }

    private HttpRequestLoggingProperties defaultProperties() {
        HttpRequestLoggingProperties properties = new HttpRequestLoggingProperties();
        properties.setEnabled(true);
        return properties;
    }
}
