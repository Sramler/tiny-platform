package com.tiny.platform.core.oauth.multitenancy;

import com.tiny.platform.core.oauth.tenant.IssuerTenantSupport;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于 issuerKey 的组件注册表。
 * <p>
 * 严格遵循 SAS 官方多 issuer 指南：按 issuer 路由各类 OAuth2 组件。
 */
public final class TenantPerIssuerComponentRegistry {

    private final Map<String, Map<Class<?>, Object>> registry = new ConcurrentHashMap<>();

    public <T> void registerIssuerKey(String issuerKey, Class<T> componentType, T component) {
        registry.computeIfAbsent(issuerKey, key -> new ConcurrentHashMap<>()).put(componentType, component);
    }

    public <T> void register(String tenantCode, Class<T> componentType, T component) {
        registerIssuerKey(tenantCode, componentType, component);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String issuerKey, Class<T> componentType) {
        Map<Class<?>, Object> components = registry.get(issuerKey);
        if (components == null) {
            return null;
        }
        return (T) components.get(componentType);
    }

    public boolean containsIssuerKey(String issuerKey) {
        return registry.containsKey(issuerKey);
    }

    public Set<String> issuerKeys() {
        return Set.copyOf(registry.keySet());
    }

    public boolean containsTenant(String tenantCode) {
        return !IssuerTenantSupport.PLATFORM_ISSUER_KEY.equals(tenantCode)
            && registry.containsKey(tenantCode);
    }

    public Set<String> tenantCodes() {
        return registry.keySet().stream()
            .filter(key -> !IssuerTenantSupport.PLATFORM_ISSUER_KEY.equals(key))
            .collect(Collectors.toUnmodifiableSet());
    }
}
