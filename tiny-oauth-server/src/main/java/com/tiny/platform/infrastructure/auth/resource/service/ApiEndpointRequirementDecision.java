package com.tiny.platform.infrastructure.auth.resource.service;

/**
 * Decision for unified api_endpoint requirement guard.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #ALLOWED}: matched and requirement satisfied.</li>
 *   <li>{@link #DENIED}: request is unregistered, ambiguous, or requirement missing/disabled/not satisfied; must fail-closed.</li>
 * </ul>
 */
public enum ApiEndpointRequirementDecision {
    ALLOWED,
    DENIED
}
