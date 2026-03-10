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

    /**
     * 治理与运维配置
     */
    private Ops ops = new Ops();
    
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

    public static class Ops {
        /**
         * 平台租户编码。
         *
         * <p>幂等治理入口默认仅允许该租户下的管理员访问。</p>
         */
        private String platformTenantCode = "default";

        /**
         * 治理接口统计窗口大小（分钟）。
         *
         * <p>治理页默认只展示最近一段时间内的幂等事件，避免全局累计数据持续膨胀。</p>
         */
        private long metricsWindowMinutes = 60;

        /**
         * 治理指标存储模式。
         *
         * <p>database 表示写入分钟聚合表，多实例可共享；memory 表示仅保留单实例内存快照。</p>
         */
        private String metricsStore = "database";

        /**
         * 治理指标保留天数。
         */
        private long metricsRetentionDays = 7;

        /**
         * 治理指标清理固定间隔（毫秒）。
         */
        private long metricsCleanupFixedDelayMs = 600_000;

        public String getPlatformTenantCode() {
            return platformTenantCode;
        }

        public void setPlatformTenantCode(String platformTenantCode) {
            this.platformTenantCode = platformTenantCode;
        }

        public long getMetricsWindowMinutes() {
            return metricsWindowMinutes;
        }

        public void setMetricsWindowMinutes(long metricsWindowMinutes) {
            this.metricsWindowMinutes = metricsWindowMinutes;
        }

        public String getMetricsStore() {
            return metricsStore;
        }

        public void setMetricsStore(String metricsStore) {
            this.metricsStore = metricsStore;
        }

        public long getMetricsRetentionDays() {
            return metricsRetentionDays;
        }

        public void setMetricsRetentionDays(long metricsRetentionDays) {
            this.metricsRetentionDays = metricsRetentionDays;
        }

        public long getMetricsCleanupFixedDelayMs() {
            return metricsCleanupFixedDelayMs;
        }

        public void setMetricsCleanupFixedDelayMs(long metricsCleanupFixedDelayMs) {
            this.metricsCleanupFixedDelayMs = metricsCleanupFixedDelayMs;
        }
    }
    
    public HttpApi getHttpApi() {
        return httpApi;
    }
    
    public void setHttpApi(HttpApi httpApi) {
        this.httpApi = httpApi;
    }

    public Ops getOps() {
        return ops;
    }

    public void setOps(Ops ops) {
        this.ops = ops;
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
