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
     * 需要跳过记录的路径前缀
     */
    private List<String> excludedPathPrefixes = new ArrayList<>(List.of(
            "/actuator",
            "/dist",
            "/static",
            "/webjars"
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

    public List<String> getExcludedPathPrefixes() {
        return excludedPathPrefixes;
    }

    public void setExcludedPathPrefixes(List<String> excludedPathPrefixes) {
        this.excludedPathPrefixes = excludedPathPrefixes;
    }
}
