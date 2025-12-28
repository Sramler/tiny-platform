package com.tiny.platform.core.oauth.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * HTTP 请求日志实体
 */
@Entity
@Table(name = "http_request_log", indexes = {
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_service_env_created", columnList = "service_name, env, created_at"),
        @Index(name = "idx_path_template_created", columnList = "path_template, created_at"),
        @Index(name = "idx_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_status_created", columnList = "status, created_at"),
        @Index(name = "idx_trace", columnList = "trace_id"),
        @Index(name = "idx_request_at", columnList = "request_at")
})
public class HttpRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 32)
    private String traceId;

    @Column(name = "span_id", length = 32)
    private String spanId;

    @Column(name = "request_id", nullable = false, length = 32, unique = true)
    private String requestId;

    @Column(name = "service_name", nullable = false, length = 64)
    private String serviceName;

    @Column(name = "env", nullable = false, length = 32)
    private String env;

    @Column(name = "module", length = 64)
    private String module;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "host", length = 128)
    private String host;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "http_version", length = 16)
    private String httpVersion;

    @Column(name = "method", nullable = false, length = 10)
    private String method;

    @Column(name = "path_template", nullable = false, length = 256)
    private String pathTemplate;

    @Column(name = "raw_path", length = 1024)
    private String rawPath;

    @Column(name = "query_string", length = 1024)
    private String queryString;

    @Column(name = "request_size")
    private Long requestSize;

    @Column(name = "response_size")
    private Long responseSize;

    @Column(name = "status")
    private Integer status;

    @Column(name = "success", nullable = false)
    private Boolean success = Boolean.TRUE;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error", length = 512)
    private String error;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "request_body", columnDefinition = "MEDIUMTEXT")
    private String requestBody;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "response_body", columnDefinition = "MEDIUMTEXT")
    private String responseBody;

    @Column(name = "request_at", nullable = false)
    private LocalDateTime requestAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (requestAt == null) {
            requestAt = LocalDateTime.now();
        }
        createdAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public void setHttpVersion(String httpVersion) {
        this.httpVersion = httpVersion;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPathTemplate() {
        return pathTemplate;
    }

    public void setPathTemplate(String pathTemplate) {
        this.pathTemplate = pathTemplate;
    }

    public String getRawPath() {
        return rawPath;
    }

    public void setRawPath(String rawPath) {
        this.rawPath = rawPath;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public Long getRequestSize() {
        return requestSize;
    }

    public void setRequestSize(Long requestSize) {
        this.requestSize = requestSize;
    }

    public Long getResponseSize() {
        return responseSize;
    }

    public void setResponseSize(Long responseSize) {
        this.responseSize = responseSize;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Integer durationMs) {
        this.durationMs = durationMs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public LocalDateTime getRequestAt() {
        return requestAt;
    }

    public void setRequestAt(LocalDateTime requestAt) {
        this.requestAt = requestAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}


