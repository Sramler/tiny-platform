package com.tiny.platform.core.oauth.tenant;

import com.tiny.platform.core.oauth.model.SecurityUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * TenantContextFilter
 * - 优先从 SecurityContext 获取 tenantId（Session SecurityUser / JWT claim）
 * - 其次从请求头 X-Tenant-Id 获取
 * - 若无 tenantId，则拒绝请求（fail-closed）
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TenantContextFilter.class);

    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final String TENANT_CLAIM = "tenantId";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<String> SKIP_PATTERNS = List.of(
            "/favicon.ico",
            "/error",
            "/webjars/**",
            "/assets/**",
            "/css/**",
            "/js/**",
            "/dist/**"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod()) && "/login".equals(path)) {
            return true;
        }
        for (String pattern : SKIP_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Long tenantId = resolveTenantId(request);
        if (tenantId == null || tenantId <= 0) {
            rejectMissingTenant(response);
            return;
        }

        TenantContext.setTenantId(tenantId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Long resolveTenantId(HttpServletRequest request) {
        Long tenantId = resolveTenantIdFromAuthentication();
        if (tenantId != null) {
            return tenantId;
        }
        String header = request.getHeader(TENANT_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            logger.warn("非法 tenantId header: {}", header);
            return null;
        }
    }

    private Long resolveTenantIdFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getTenantId();
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Object claim = jwtAuthenticationToken.getToken().getClaims().get(TENANT_CLAIM);
            if (claim instanceof Number number) {
                return number.longValue();
            }
            if (claim instanceof String str) {
                try {
                    return Long.parseLong(str);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private void rejectMissingTenant(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"missing_tenant\",\"error_description\":\"tenantId is required\"}");
    }
}
