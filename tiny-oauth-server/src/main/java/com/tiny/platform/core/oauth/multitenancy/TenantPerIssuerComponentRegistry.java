package com.tiny.platform.core.oauth.multitenancy;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 issuer(tenantCode) 的组件注册表。
 * <p>
 * 严格遵循 SAS 官方多 issuer 指南：按 issuer 路由各类 OAuth2 组件。
 */
public final class TenantPerIssuerComponentRegistry {

    private final Map<String, Map<Class<?>, Object>> registry = new ConcurrentHashMap<>();

    public <T> void register(String tenantCode, Class<T> componentType, T component) {
        registry.computeIfAbsent(tenantCode, key -> new ConcurrentHashMap<>()).put(componentType, component);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String tenantCode, Class<T> componentType) {
        Map<Class<?>, Object> components = registry.get(tenantCode);
        if (components == null) {
            return null;
        }
        return (T) components.get(componentType);
    }

    public boolean containsTenant(String tenantCode) {
        return registry.containsKey(tenantCode);
    }

    public Set<String> tenantCodes() {
        return Set.copyOf(registry.keySet());
    }
}
