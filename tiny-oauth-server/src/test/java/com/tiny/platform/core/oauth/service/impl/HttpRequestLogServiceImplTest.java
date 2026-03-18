package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.core.oauth.model.HttpRequestLog;
import com.tiny.platform.core.oauth.repository.HttpRequestLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpRequestLogServiceImplTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldSaveLogAndRestorePreviousMdc() {
        HttpRequestLogRepository repository = mock(HttpRequestLogRepository.class);
        when(repository.save(any(HttpRequestLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        HttpRequestLogServiceImpl service = new HttpRequestLogServiceImpl(repository);

        HttpRequestLog log = new HttpRequestLog();
        log.setServiceName("svc");
        log.setEnv("test");
        log.setMethod("GET");
        log.setPathTemplate("/api/x");
        log.setStatus(200);
        log.setDurationMs(12);
        log.setUserId("u1");
        log.setActiveTenantId(9L);
        log.setTraceId("0123456789abcdef0123456789abcdef");
        log.setRequestId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        log.setClientRequestId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        log.setTraceSource("header_x_trace_id");

        MDC.put("pre", "keep");
        service.save(log);

        verify(repository).save(log);
        assertThat(MDC.get("pre")).isEqualTo("keep");
        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("activeTenantId")).isNull();
    }

    @Test
    void shouldCatchRepositoryExceptionAndRestoreEmptyMdc() {
        HttpRequestLogRepository repository = mock(HttpRequestLogRepository.class);
        Mockito.doThrow(new RuntimeException("db down")).when(repository).save(any(HttpRequestLog.class));
        HttpRequestLogServiceImpl service = new HttpRequestLogServiceImpl(repository);

        HttpRequestLog log = new HttpRequestLog();
        log.setTraceId("0123456789abcdef0123456789abcdef");
        log.setRequestId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        MDC.clear();
        service.save(log);

        verify(repository).save(log);
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }

    @Test
    void shouldCoverPrivateMdcHelpers() {
        HttpRequestLogRepository repository = mock(HttpRequestLogRepository.class);
        HttpRequestLogServiceImpl service = new HttpRequestLogServiceImpl(repository);

        ReflectionTestUtils.invokeMethod(service, "bindLogMdc", new Object[]{null});
        assertThat(MDC.getCopyOfContextMap()).isNull();

        HttpRequestLog log = new HttpRequestLog();
        log.setTraceId("0123456789abcdef0123456789abcdef");
        log.setRequestId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        log.setUserId("u1");
        log.setActiveTenantId(1L);
        ReflectionTestUtils.invokeMethod(service, "bindLogMdc", log);
        assertThat(MDC.get("traceId")).isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(MDC.get("requestId")).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(MDC.get("userId")).isEqualTo("u1");
        assertThat(MDC.get("activeTenantId")).isEqualTo("1");

        ReflectionTestUtils.invokeMethod(service, "putIfText", "traceId", "   ");
        assertThat(MDC.get("traceId")).isEqualTo("0123456789abcdef0123456789abcdef");

        ReflectionTestUtils.invokeMethod(service, "restoreMdc", new Object[]{null});
        assertThat(MDC.getCopyOfContextMap()).isNull();

        MDC.put("tmp", "x");
        ReflectionTestUtils.invokeMethod(service, "restoreMdc", java.util.Map.of("k", "v"));
        assertThat(MDC.get("k")).isEqualTo("v");
        assertThat(MDC.get("tmp")).isNull();
    }
}
