package com.tiny.platform.application.oauth.workflow;

import org.springframework.security.core.Authentication;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Resolve tenant ids for current request.
 */
public interface TenantResolver {
    /**
     * Resolve the current tenant id list for Camunda authentication. Return non-empty list.
     */
    List<String> resolveTenantIds(HttpServletRequest request, Authentication authentication);
}


