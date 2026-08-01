package com.tiny.platform.core.oauth.security;

import com.tiny.platform.infrastructure.auth.resource.service.ApiEndpointRequirementDecision;
import com.tiny.platform.infrastructure.auth.resource.service.ResourceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Unified api_endpoint requirement guard (fail-closed).
 *
 * <p>Behavior:
 * - Only enforces for requests that match an enabled api_endpoint by exact method+uri within current tenant scope.
 * - When matched, require api_endpoint_permission_requirement to be present and satisfied (including permission.enabled).
 * - When request is unregistered or ambiguous, fail-closed.
 */
public class ApiEndpointRequirementFilter extends OncePerRequestFilter {

    private static final String SELF_SECURITY_PREFIX = "/self/security/";
    private static final String CURRENT_USER_PATH = "/sys/users/current";
    private static final String RUNTIME_MENU_TREE_PATH = "/sys/menus/tree";
    private static final String RUNTIME_UI_ACTIONS_PATH = "/sys/resources/runtime/ui-actions";
    private static final String RUNTIME_API_ACCESS_PATH = "/sys/resources/runtime/api-access";
    private static final String PLATFORM_TOKEN_DEBUG_PREFIX = "/sys/platform/token-debug/";
    private static final String CURRENT_USER_LOGIN_HISTORY_PATH = "/sys/users/current/login-history";
    private static final String CURRENT_USER_AVATAR_PATH = "/sys/users/current/avatar";
    private static final Pattern USER_AVATAR_PATH = Pattern.compile("^/sys/users/[^/]+/avatar$");
    private static final Pattern DICT_LOOKUP_PATH = Pattern.compile(
        "^/dict/(?:types/code/[^/]+|types/current|items/(?:code|map)/[^/]+|items/label/[^/]+/[^/]+)$"
    );

    private final ResourceService resourceService;

    public ApiEndpointRequirementFilter(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (!StringUtils.hasText(path)) {
            path = request.getRequestURI();
        }
        if (!StringUtils.hasText(path)) {
            return false;
        }
        return "/".equals(path)
            || "/login".equals(path)
            || "/auth/login".equals(path)
            || "/auth/logout".equals(path)
            || "/csrf".equals(path)
            || "/favicon.ico".equals(path)
            || "/error".equals(path)
            || path.startsWith("/webjars/")
            || path.startsWith("/assets/")
            || path.startsWith("/css/")
            || path.startsWith("/js/")
            // Session/BFF bootstrap endpoint: authenticated users must be able to restore their runtime identity.
            || CURRENT_USER_PATH.equals(path)
            // Runtime sidebar tree is guarded by menu carrier requirements instead of control-plane api_endpoint rows.
            || RUNTIME_MENU_TREE_PATH.equals(path)
            // Runtime button discovery is guarded by ui_action carrier requirements in ResourceService.
            || RUNTIME_UI_ACTIONS_PATH.equals(path)
            // Runtime API access discovery evaluates the target api_endpoint itself; guarding this
            // infrastructure endpoint through api_endpoint would create a circular dependency.
            || RUNTIME_API_ACCESS_PATH.equals(path)
            // Authenticated lookup/self-service endpoints have ownership/runtime semantics rather
            // than management permissions. Keep the exemption method+template precise.
            || isAuthenticatedLookupOrSelfService(request.getMethod(), path)
            // Platform token debug is a readonly troubleshooting endpoint guarded by PreAuthorize + platform-scope checks.
            || path.startsWith(PLATFORM_TOKEN_DEBUG_PREFIX)
            || "/self/security".equals(path)
            || path.startsWith(SELF_SECURITY_PREFIX);
    }

    private static boolean isAuthenticatedLookupOrSelfService(String method, String path) {
        if ("GET".equalsIgnoreCase(method)) {
            return DICT_LOOKUP_PATH.matcher(path).matches()
                || CURRENT_USER_LOGIN_HISTORY_PATH.equals(path)
                || USER_AVATAR_PATH.matcher(path).matches()
                || "/process/health".equals(path);
        }
        return CURRENT_USER_AVATAR_PATH.equals(path)
            && ("POST".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method));
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        ApiEndpointRequirementDecision decision =
            resourceService.evaluateApiEndpointRequirement(request.getMethod(), request.getRequestURI());

        if (decision == ApiEndpointRequirementDecision.ALLOWED) {
            filterChain.doFilter(request, response);
            return;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "api_endpoint requirement denied");
    }
}
