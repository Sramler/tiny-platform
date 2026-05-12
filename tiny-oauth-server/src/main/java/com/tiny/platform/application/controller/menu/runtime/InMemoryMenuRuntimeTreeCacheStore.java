package com.tiny.platform.application.controller.menu.runtime;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryMenuRuntimeTreeCacheStore implements MenuRuntimeTreeCacheStore {

    private static final int MAX_ENTRIES = 512;
    private final ConcurrentHashMap<String, MenuRuntimeTreeCacheEntry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<MenuRuntimeTreeCacheEntry> get(String cacheKey, String etag) {
        if (cacheKey == null || cacheKey.isBlank() || etag == null || etag.isBlank()) {
            return Optional.empty();
        }
        MenuRuntimeTreeCacheEntry entry = entries.get(cacheKey);
        if (entry == null || !etag.equals(entry.etag())) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    @Override
    public void put(MenuRuntimeTreeCacheEntry entry) {
        if (entry == null || entry.cacheKey() == null || entry.cacheKey().isBlank()) {
            return;
        }
        entries.put(entry.cacheKey(), entry);
        trimIfNeeded();
    }

    @Override
    public void evict(String cacheKey) {
        if (cacheKey != null) {
            entries.remove(cacheKey);
        }
    }

    private void trimIfNeeded() {
        int overflow = entries.size() - MAX_ENTRIES;
        if (overflow <= 0) {
            return;
        }
        entries.values().stream()
            .sorted(Comparator.comparing(MenuRuntimeTreeCacheEntry::createdAt))
            .limit(Math.max(overflow, MAX_ENTRIES / 10))
            .map(MenuRuntimeTreeCacheEntry::cacheKey)
            .toList()
            .forEach(entries::remove);
    }
}
