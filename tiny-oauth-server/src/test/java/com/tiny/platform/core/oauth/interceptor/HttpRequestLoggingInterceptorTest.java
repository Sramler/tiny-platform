package com.tiny.platform.core.oauth.interceptor;

import com.tiny.platform.core.oauth.config.HttpRequestLoggingProperties;
import com.tiny.platform.core.oauth.filter.HttpRequestLoggingFilter;
import com.tiny.platform.core.oauth.model.HttpRequestLog;
import com.tiny.platform.core.oauth.service.HttpRequestLogService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HttpRequestLoggingInterceptorTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
        TenantContext.clear();
    }

    @Test
    void shouldSaveSanitizedAuditLogAndMarkRequestAsLogged() throws Exception {
        HttpRequestLoggingProperties properties = new HttpRequestLoggingProperties();
        properties.setIncludeRequestBody(true);
        properties.setIncludeResponseBody(true);
        HttpRequestLogService logService = mock(HttpRequestLogService.class);
        HttpRequestLoggingInterceptor interceptor = new HttpRequestLoggingInterceptor(properties, logService);

        MockHttpServletRequest rawRequest = new MockHttpServletRequest("POST", "/oauth2/token");
        rawRequest.setCharacterEncoding(StandardCharsets.UTF_8.name());
        rawRequest.setContentType("application/json");
        rawRequest.setContent("{\"username\":\"alice\",\"password\":\"secret123\",\"refresh_token\":\"r1\"}"
                .getBytes(StandardCharsets.UTF_8));
        rawRequest.setQueryString("client_id=web&code=abc123&trace_id=0123456789abcdef0123456789abcdef");
        rawRequest.addHeader("User-Agent", "JUnit");
        rawRequest.addHeader("Host", "localhost");
        rawRequest.setRemoteAddr("127.0.0.1");

        ContentCachingRequestWrapper request = new ContentCachingRequestWrapper(rawRequest, properties.getMaxBodyLength());
        request.getInputStream().readAllBytes();
        request.setAttribute(HttpRequestLoggingFilter.ATTR_START_TIME, System.currentTimeMillis() - 25);
        request.setAttribute(HttpRequestLoggingFilter.ATTR_SERVICE, "tiny-oauth-server");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_ENV, "test");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_TRACE_ID, "0123456789abcdef0123456789abcdef");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_SPAN_ID, "0123456789abcdef");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_REQUEST_ID, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_CLIENT_REQUEST_ID, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        request.setAttribute(HttpRequestLoggingFilter.ATTR_TRACE_SOURCE, "query_trace_id");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/oauth2/token");

        MockHttpServletResponse rawResponse = new MockHttpServletResponse();
        ContentCachingResponseWrapper response = new ContentCachingResponseWrapper(rawResponse);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setStatus(400);
        response.getWriter().write("{\"access_token\":\"token-value\",\"error\":\"invalid_grant\"}");
        response.getWriter().flush();

        TenantContext.setTenantId(9L);

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<HttpRequestLog> captor = ArgumentCaptor.forClass(HttpRequestLog.class);
        verify(logService).save(captor.capture());

        HttpRequestLog saved = captor.getValue();
        assertThat(saved.getQueryString()).isEqualTo("client_id=web&code=***&trace_id=0123456789abcdef0123456789abcdef");
        assertThat(saved.getRequestBody()).contains("\"password\":\"***\"");
        assertThat(saved.getRequestBody()).contains("\"refresh_token\":\"***\"");
        assertThat(saved.getResponseBody()).contains("\"access_token\":\"***\"");
        assertThat(saved.getTenantId()).isEqualTo(9L);
        assertThat(saved.getStatus()).isEqualTo(400);
        assertThat(saved.getSuccess()).isFalse();
        assertThat(request.getAttribute(HttpRequestLoggingFilter.ATTR_AUDIT_LOGGED)).isEqualTo(Boolean.TRUE);
        assertThat(MDC.get("tenantId")).isEqualTo("9");
    }
}
