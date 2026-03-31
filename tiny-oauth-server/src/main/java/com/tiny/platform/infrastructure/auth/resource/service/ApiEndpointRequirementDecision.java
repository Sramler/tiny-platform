package com.tiny.platform.infrastructure.auth.resource.service;

/**
 * Decision for unified api_endpoint requirement guard.
 *
 * <p>Contract:
 * <ul>
 *   <li>{@link #NOT_REGISTERED}: no api_endpoint entry matched the request; keep legacy behavior.</li>
 *   <li>{@link #ALLOWED}: matched and requirement satisfied.</li>
 *   <li>{@link #DENIED}: matched but requirement missing/disabled/not satisfied; must fail-closed.</li>
 * </ul>
 */
public enum ApiEndpointRequirementDecision {
    NOT_REGISTERED,
    ALLOWED,
    DENIED
}

