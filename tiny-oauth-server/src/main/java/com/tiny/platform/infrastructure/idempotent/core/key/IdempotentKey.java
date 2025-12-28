package com.tiny.platform.infrastructure.idempotent.core.key;

/**
 * 幂等性 Key 规范
 * 
 * <p>统一 Key 结构：IdempotentKey = namespace : scope : uniqueKey</p>
 * 
 * <p>示例：</p>
 * <ul>
 *   <li>HTTP: http:POST:/api/orders:userId123:bodyHash</li>
 *   <li>MQ: mq:kafka:order.create:partition0:offset12345</li>
 *   <li>Job: job:orderSync:20241220:shard0</li>
 * </ul>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
public class IdempotentKey {
    
    /**
     * 命名空间（http / mq / job）
     */
    private String namespace;
    
    /**
     * 作用域（方法路径 / topic / job名称）
     */
    private String scope;
    
    /**
     * 唯一标识（用户ID + 请求体Hash / messageId / 业务日期）
     */
    private String uniqueKey;
    
    /**
     * 完整的 Key 字符串
     */
    private String fullKey;
    
    public IdempotentKey() {
    }
    
    public IdempotentKey(String namespace, String scope, String uniqueKey) {
        this.namespace = namespace;
        this.scope = scope;
        this.uniqueKey = uniqueKey;
        this.fullKey = String.format("%s:%s:%s", namespace, scope, uniqueKey);
    }
    
    public static IdempotentKey of(String namespace, String scope, String uniqueKey) {
        return new IdempotentKey(namespace, scope, uniqueKey);
    }
    
    public static IdempotentKey parse(String fullKey) {
        String[] parts = fullKey.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid idempotent key format: " + fullKey);
        }
        return new IdempotentKey(parts[0], parts[1], parts[2]);
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void setNamespace(String namespace) {
        this.namespace = namespace;
        updateFullKey();
    }
    
    public String getScope() {
        return scope;
    }
    
    public void setScope(String scope) {
        this.scope = scope;
        updateFullKey();
    }
    
    public String getUniqueKey() {
        return uniqueKey;
    }
    
    public void setUniqueKey(String uniqueKey) {
        this.uniqueKey = uniqueKey;
        updateFullKey();
    }
    
    public String getFullKey() {
        return fullKey;
    }
    
    private void updateFullKey() {
        this.fullKey = String.format("%s:%s:%s", namespace, scope, uniqueKey);
    }
    
    @Override
    public String toString() {
        return fullKey;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IdempotentKey that = (IdempotentKey) o;
        return fullKey != null ? fullKey.equals(that.fullKey) : that.fullKey == null;
    }
    
    @Override
    public int hashCode() {
        return fullKey != null ? fullKey.hashCode() : 0;
    }
}

