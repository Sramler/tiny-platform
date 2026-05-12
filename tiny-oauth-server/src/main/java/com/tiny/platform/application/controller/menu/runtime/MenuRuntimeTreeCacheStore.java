package com.tiny.platform.application.controller.menu.runtime;

import java.util.Optional;

/**
 * Runtime menu-tree cache store abstraction.
 *
 * <p>Current implementation is local-memory with DB fallback through
 * {@link com.tiny.platform.infrastructure.menu.service.MenuService#menuTree()} on miss.
 * A Redis/distributed implementation can replace this bean without changing the controller.</p>
 */
public interface MenuRuntimeTreeCacheStore {

    Optional<MenuRuntimeTreeCacheEntry> get(String cacheKey, String etag);

    void put(MenuRuntimeTreeCacheEntry entry);

    void evict(String cacheKey);
}
