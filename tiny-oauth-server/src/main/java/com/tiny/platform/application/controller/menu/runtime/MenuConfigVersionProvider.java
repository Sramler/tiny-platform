package com.tiny.platform.application.controller.menu.runtime;

/**
 * Resolves a version for menu configuration in the active runtime scope.
 *
 * <p>The default implementation derives the version from DB rows. Future Redis or
 * event-sourced version stores should implement this interface and keep the same
 * semantic contract: any menu tree shape/routing/requirement change must change
 * the returned version.</p>
 */
public interface MenuConfigVersionProvider {

    String resolveMenuConfigVersion(Long activeTenantId, String activeScopeType, Long activeScopeId);
}
