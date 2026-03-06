package com.tiny.platform.core.oauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP 请求日志配置
 */
@Component
@ConfigurationProperties(prefix = "http.request-log")
public class HttpRequestLoggingProperties {

    /**
     * 是否启用 HTTP 请求日志
     */
    private boolean enabled = true;

    /**
     * 是否记录请求体
     */
    private boolean includeRequestBody = false;

    /**
     * 是否记录响应体
     */
    private boolean includeResponseBody = false;

    /**
     * 请求/响应体最大记录长度，单位字节
     */
    private int maxBodyLength = 5 * 1024;

    /**
     * traceId 缺失时的兜底策略：
     * - REQUEST_ID: 使用当前请求的 requestId 作为 traceId（兼容旧行为）
     * - GENERATED: 生成新的独立 traceId（语义更清晰）
     */
    private String traceIdFallbackStrategy = TraceIdFallbackStrategy.REQUEST_ID.name();

    /**
     * 允许从 query 参数 trace_id 读取 traceId 的路径片段白名单（contains 匹配）
     */
    private List<String> traceIdQueryParamAllowedPathFragments = new ArrayList<>(List.of(
            "/oauth2/",
            "/connect/",
            "/logout"
    ));

    /**
     * 需要跳过记录的路径前缀
     */
    private List<String> excludedPathPrefixes = new ArrayList<>(List.of(
            "/actuator",
            "/dist",
            "/static",
            "/webjars"
    ));

    /**
     * 对大流量/大文件响应禁用 ContentCachingResponseWrapper，避免整包响应驻留堆内存。
     */
    private List<String> responseBodyPassthroughPathPrefixes = new ArrayList<>(List.of(
            "/export"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isIncludeRequestBody() {
        return includeRequestBody;
    }

    public void setIncludeRequestBody(boolean includeRequestBody) {
        this.includeRequestBody = includeRequestBody;
    }

    public boolean isIncludeResponseBody() {
        return includeResponseBody;
    }

    public void setIncludeResponseBody(boolean includeResponseBody) {
        this.includeResponseBody = includeResponseBody;
    }

    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    public void setMaxBodyLength(int maxBodyLength) {
        this.maxBodyLength = maxBodyLength;
    }

    public String getTraceIdFallbackStrategy() {
        return traceIdFallbackStrategy;
    }

    public void setTraceIdFallbackStrategy(String traceIdFallbackStrategy) {
        this.traceIdFallbackStrategy = traceIdFallbackStrategy;
    }

    public TraceIdFallbackStrategy resolveTraceIdFallbackStrategy() {
        if (traceIdFallbackStrategy == null || traceIdFallbackStrategy.isBlank()) {
            return TraceIdFallbackStrategy.REQUEST_ID;
        }
        try {
            String normalized = traceIdFallbackStrategy.trim()
                    .replace('-', '_')
                    .replace(' ', '_')
                    .toUpperCase();
            return TraceIdFallbackStrategy.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return TraceIdFallbackStrategy.REQUEST_ID;
        }
    }

    public List<String> getExcludedPathPrefixes() {
        return excludedPathPrefixes;
    }

    public void setExcludedPathPrefixes(List<String> excludedPathPrefixes) {
        this.excludedPathPrefixes = excludedPathPrefixes;
    }

    public List<String> getTraceIdQueryParamAllowedPathFragments() {
        return traceIdQueryParamAllowedPathFragments;
    }

    public void setTraceIdQueryParamAllowedPathFragments(List<String> traceIdQueryParamAllowedPathFragments) {
        this.traceIdQueryParamAllowedPathFragments = traceIdQueryParamAllowedPathFragments;
    }

    public List<String> getResponseBodyPassthroughPathPrefixes() {
        return responseBodyPassthroughPathPrefixes;
    }

    public void setResponseBodyPassthroughPathPrefixes(List<String> responseBodyPassthroughPathPrefixes) {
        this.responseBodyPassthroughPathPrefixes = responseBodyPassthroughPathPrefixes;
    }

    public enum TraceIdFallbackStrategy {
        REQUEST_ID,
        GENERATED
    }
}
