package com.tiny.platform.infrastructure.idempotent.starter.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 幂等性配置属性
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "tiny.idempotent")
public class IdempotentProperties {
    
    /**
     * 是否启用幂等性功能，默认为 true
     */
    private boolean enabled = true;
    
    /**
     * 存储类型：database、redis 或 memory，默认为 database
     */
    private String store = "database";
    
    /**
     * 默认过期时间（秒），默认为 300 秒（5分钟）
     */
    private long ttl = 300;
    
    /**
     * 失败策略：true 表示 fail-open（失败时继续执行），false 表示 fail-close（失败时抛出异常）
     * 默认为 true
     */
    private boolean failOpen = true;
    
    /**
     * HTTP API 配置
     */
    private HttpApi httpApi = new HttpApi();
    
    public static class HttpApi {
        /**
         * 是否启用 HTTP API 接口（轻量模式），默认为 false
         */
        private boolean enabled = false;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
    
    public HttpApi getHttpApi() {
        return httpApi;
    }
    
    public void setHttpApi(HttpApi httpApi) {
        this.httpApi = httpApi;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getStore() {
        return store;
    }
    
    public void setStore(String store) {
        this.store = store;
    }
    
    public long getTtl() {
        return ttl;
    }
    
    public void setTtl(long ttl) {
        this.ttl = ttl;
    }
    
    public boolean isFailOpen() {
        return failOpen;
    }
    
    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }
}
