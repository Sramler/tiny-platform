package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.core.oauth.model.HttpRequestLog;
import com.tiny.platform.core.oauth.repository.HttpRequestLogRepository;
import com.tiny.platform.core.oauth.service.HttpRequestLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class HttpRequestLogServiceImpl implements HttpRequestLogService {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestLogServiceImpl.class);

    private final HttpRequestLogRepository repository;

    public HttpRequestLogServiceImpl(HttpRequestLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(HttpRequestLog requestLog) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            bindLogMdc(requestLog);
            repository.save(requestLog);
            if (log.isInfoEnabled()) {
                log.info("REQ_LOG service={} env={} method={} path={} status={} duration={}ms user={} activeTenantId={} traceId={} requestId={} clientRequestId={} traceSource={}",
                        requestLog.getServiceName(),
                        requestLog.getEnv(),
                        requestLog.getMethod(),
                        requestLog.getPathTemplate(),
                        requestLog.getStatus(),
                        requestLog.getDurationMs(),
                        requestLog.getUserId(),
                        requestLog.getActiveTenantId(),
                        requestLog.getTraceId(),
                        requestLog.getRequestId(),
                        requestLog.getClientRequestId(),
                        requestLog.getTraceSource());
            }
        } catch (Exception ex) {
            log.warn("保存 HTTP 请求日志失败: {}", ex.getMessage(), ex);
        } finally {
            restoreMdc(previous);
        }
    }

    private void bindLogMdc(HttpRequestLog requestLog) {
        if (requestLog == null) {
            return;
        }
        putIfText("traceId", requestLog.getTraceId());
        putIfText("requestId", requestLog.getRequestId());
        putIfText("userId", requestLog.getUserId());
        if (requestLog.getActiveTenantId() != null) {
            MDC.put("activeTenantId", String.valueOf(requestLog.getActiveTenantId()));
        }
    }

    private void putIfText(String key, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        MDC.put(key, value);
    }

    private void restoreMdc(Map<String, String> previous) {
        if (previous == null || previous.isEmpty()) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(previous);
    }
}
