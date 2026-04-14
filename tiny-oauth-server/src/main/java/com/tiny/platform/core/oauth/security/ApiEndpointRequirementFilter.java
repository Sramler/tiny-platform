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
    private static final String RUNTIME_MENU_TREE_PATH = "/sys/menus/tree";
    private static final String PLATFORM_TOKEN_DEBUG_PREFIX = "/sys/platform/token-debug/";

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
        return "/login".equals(path)
            || "/csrf".equals(path)
            || "/favicon.ico".equals(path)
            || "/error".equals(path)
            || path.startsWith("/webjars/")
            || path.startsWith("/assets/")
            || path.startsWith("/css/")
            || path.startsWith("/js/")
            // Runtime sidebar tree is guarded by menu carrier requirements instead of control-plane api_endpoint rows.
            || RUNTIME_MENU_TREE_PATH.equals(path)
            // Platform token debug is a readonly troubleshooting endpoint guarded by PreAuthorize + platform-scope checks.
            || path.startsWith(PLATFORM_TOKEN_DEBUG_PREFIX)
            || "/self/security".equals(path)
            || path.startsWith(SELF_SECURITY_PREFIX);
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
