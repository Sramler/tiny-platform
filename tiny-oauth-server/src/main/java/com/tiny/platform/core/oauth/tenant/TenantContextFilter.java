package com.tiny.platform.core.oauth.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.net.URLEncoder;
import java.util.regex.Pattern;

/**
 * TenantContextFilter
 * - 认证后只信任 SecurityContext / Session 中冻结的 tenantId
 * - 未认证阶段只允许：
 *   1) POST /login 参数解析 tenant（兼容 tenantCode）
 *   2) issuer path (`/{tenantCode}/oauth2/**`) 解析 tenant
 * - 若无有效 tenant，则拒绝请求（fail-closed）
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TenantContextFilter.class);
    public static final String TENANT_SOURCE_REQUEST_ATTRIBUTE = "tenantSource";

    private static final String TENANT_ID_HEADER = "X-Tenant-Id";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TENANT_ID_CLAIM = "tenantId";
    private static final String SESSION_TENANT_ID_KEY = "AUTH_TENANT_ID";
    private static final String TENANT_ID_PARAM = "tenantId";
    private static final String TENANT_CODE_PARAM = "tenantCode";
    private static final Pattern TENANT_CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,31}$");
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final List<String> SKIP_PATTERNS = List.of(
            "/favicon.ico",
            "/error",
            "/webjars/**",
            "/assets/**",
            "/css/**",
            "/js/**",
            "/dist/**"
    );

    private final TenantRepository tenantRepository;

    public TenantContextFilter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod()) && ("/login".equals(path) || "/csrf".equals(path))) {
            return true;
        }
        if (IssuerTenantSupport.isWellKnownOrJwkSetPath(path)) {
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
        ResolvedTenant authTenant = resolveTenantFromAuthentication();
        Long authTenantId = authTenant.tenantId();
        Long issuerTenantId = resolveTenantIdFromIssuerPath(request);
        Long sessionTenantId = resolveTenantIdFromSession(request);
        Long headerTenantId = parseTenantId(request.getHeader(TENANT_ID_HEADER));
        Long bearerTokenTenantId = resolveTenantIdFromAuthorizationHeader(request);
        Long tenantId;
        String tenantSource;

        if (authTenantId != null) {
            if (isMismatch(authTenantId, issuerTenantId) || isMismatch(authTenantId, sessionTenantId) || isMismatch(authTenantId, headerTenantId)) {
                rejectTenantMismatch(response);
                return;
            }
            tenantId = authTenantId;
            tenantSource = authTenant.source();
            freezeTenantIdInSession(request, tenantId);
        } else if (bearerTokenTenantId != null) {
            if (isMismatch(bearerTokenTenantId, issuerTenantId) || isMismatch(bearerTokenTenantId, sessionTenantId) || isMismatch(bearerTokenTenantId, headerTenantId)) {
                rejectTenantMismatch(response);
                return;
            }
            tenantId = bearerTokenTenantId;
            tenantSource = TenantContext.SOURCE_TOKEN;
            freezeTenantIdInSession(request, tenantId);
        } else if (issuerTenantId != null) {
            if (isMismatch(issuerTenantId, sessionTenantId) || isMismatch(issuerTenantId, headerTenantId)) {
                rejectTenantMismatch(response);
                return;
            }
            tenantId = issuerTenantId;
            tenantSource = TenantContext.SOURCE_ISSUER;
            freezeTenantIdInSession(request, tenantId);
        } else if (sessionTenantId != null) {
            if (isMismatch(sessionTenantId, headerTenantId)) {
                rejectTenantMismatch(response);
                return;
            }
            tenantId = sessionTenantId;
            tenantSource = TenantContext.SOURCE_SESSION;
        } else {
            ResolvedTenant unauthTenant = resolveTenantIdForUnauthenticatedRequest(request, headerTenantId);
            tenantId = unauthTenant.tenantId();
            tenantSource = unauthTenant.source();
        }

        if (tenantId == null || tenantId <= 0) {
            logger.warn(
                    "缺少有效租户上下文，拒绝请求: method={}, uri={}, authTenantId={}, bearerTenantId={}, issuerTenantId={}, sessionTenantId={}, headerTenantId={}, paramTenantId={}, paramTenantCode={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    authTenantId,
                    bearerTokenTenantId,
                    issuerTenantId,
                    sessionTenantId,
                    headerTenantId,
                    parseTenantId(request.getParameter(TENANT_ID_PARAM)),
                    request.getParameter(TENANT_CODE_PARAM)
            );
            rejectMissingTenant(request, response);
            return;
        }

        String normalizedTenantSource = normalizeTenantSource(tenantSource);
        TenantContext.setTenantId(tenantId);
        TenantContext.setTenantSource(normalizedTenantSource);
        request.setAttribute(TENANT_SOURCE_REQUEST_ATTRIBUTE, normalizedTenantSource);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isMismatch(Long left, Long right) {
        return left != null && right != null && !left.equals(right);
    }

    private ResolvedTenant resolveTenantIdForUnauthenticatedRequest(HttpServletRequest request, Long headerTenantId) {
        String uri = request.getRequestURI();
        boolean isLoginPost = "/login".equals(uri) && "POST".equalsIgnoreCase(request.getMethod());
        boolean canReadTenantFromParams = isLoginPost;
        if (!canReadTenantFromParams) {
            return new ResolvedTenant(null, null);
        }

        if (headerTenantId != null) {
            return new ResolvedTenant(headerTenantId, TenantContext.SOURCE_LOGIN_PARAM);
        }

        Long paramTenantId = parseTenantId(request.getParameter(TENANT_ID_PARAM));
        if (paramTenantId != null) {
            return new ResolvedTenant(paramTenantId, TenantContext.SOURCE_LOGIN_PARAM);
        }

        // 登录入口兼容 tenantCode，统一在过滤器内转换为 tenantId
        Long paramTenantIdByCode = resolveTenantIdByCode(request.getParameter(TENANT_CODE_PARAM));
        if (paramTenantIdByCode != null) {
            return new ResolvedTenant(paramTenantIdByCode, TenantContext.SOURCE_LOGIN_PARAM);
        }
        return new ResolvedTenant(null, null);
    }

    private Long resolveTenantIdFromIssuerPath(HttpServletRequest request) {
        String tenantCode = IssuerTenantSupport.extractTenantCodeFromRequestPath(request.getRequestURI());
        if (tenantCode == null) {
            return null;
        }
        return tenantRepository.findByCode(tenantCode)
                .filter(this::isTenantActive)
                .map(Tenant::getId)
                .orElse(null);
    }

    private Long parseTenantId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            logger.warn("非法 tenantId: {}", raw);
            return null;
        }
    }

    private Long resolveTenantIdByCode(String rawCode) {
        String normalizedCode = normalizeTenantCode(rawCode);
        if (normalizedCode == null) {
            return null;
        }

        return tenantRepository.findByCode(normalizedCode)
                .filter(this::isTenantActive)
                .map(Tenant::getId)
                .orElseGet(() -> {
                    logger.warn("无效或不可用 tenantCode: {}", normalizedCode);
                    return null;
                });
    }

    private String normalizeTenantCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return null;
        }
        String normalized = rawCode.trim().toLowerCase(Locale.ROOT);
        if (!TENANT_CODE_PATTERN.matcher(normalized).matches()) {
            logger.warn("非法 tenantCode: {}", rawCode);
            return null;
        }
        return normalized;
    }

    private Long resolveTenantIdFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object raw = session.getAttribute(SESSION_TENANT_ID_KEY);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String str) {
            return parseTenantId(str);
        }
        return null;
    }

    private Long resolveTenantIdFromAuthorizationHeader(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization == null || authorization.isBlank()
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return null;
        }
        return parseTenantIdFromJwtPayload(token);
    }

    private Long parseTenantIdFromJwtPayload(String token) {
        String[] segments = token.split("\\.");
        if (segments.length < 2) {
            return null;
        }
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(segments[1]);
            JsonNode payload = OBJECT_MAPPER.readTree(payloadBytes);
            JsonNode tenantNode = payload.get(TENANT_ID_CLAIM);
            if (tenantNode == null || tenantNode.isNull()) {
                return null;
            }
            if (tenantNode.isIntegralNumber()) {
                return tenantNode.longValue();
            }
            if (tenantNode.isTextual()) {
                return parseTenantId(tenantNode.asText());
            }
        } catch (Exception ex) {
            logger.debug("无法从 Bearer Token 解析 tenantId", ex);
        }
        return null;
    }

    private void freezeTenantIdInSession(HttpServletRequest request, Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(SESSION_TENANT_ID_KEY, tenantId);
        }
    }

    private boolean isTenantActive(Tenant tenant) {
        if (tenant == null) {
            return false;
        }
        if (!tenant.isEnabled()) {
            return false;
        }
        if (tenant.getDeletedAt() != null) {
            return false;
        }
        LocalDateTime expiresAt = tenant.getExpiresAt();
        return expiresAt == null || !expiresAt.isBefore(LocalDateTime.now());
    }

    private ResolvedTenant resolveTenantFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean hasPartialFactors = AuthenticationFactorAuthorities.hasAnyFactor(authentication);
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return new ResolvedTenant(null, null);
        }
        if (!authentication.isAuthenticated() && !hasPartialFactors) {
            return new ResolvedTenant(null, null);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return new ResolvedTenant(securityUser.getTenantId(), TenantContext.SOURCE_SESSION);
        }
        Object details = authentication.getDetails();
        if (details instanceof SecurityUser securityUser) {
            return new ResolvedTenant(securityUser.getTenantId(), TenantContext.SOURCE_SESSION);
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Object claim = jwtAuthenticationToken.getToken().getClaims().get(TENANT_ID_CLAIM);
            if (claim instanceof Number number) {
                return new ResolvedTenant(number.longValue(), TenantContext.SOURCE_TOKEN);
            }
            if (claim instanceof String str) {
                try {
                    return new ResolvedTenant(Long.parseLong(str), TenantContext.SOURCE_TOKEN);
                } catch (NumberFormatException e) {
                    return new ResolvedTenant(null, null);
                }
            }
        }
        return new ResolvedTenant(null, null);
    }

    private String normalizeTenantSource(String tenantSource) {
        if (tenantSource == null || tenantSource.isBlank()) {
            return TenantContext.SOURCE_UNKNOWN;
        }
        return tenantSource;
    }

    private void rejectMissingTenant(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (shouldRedirectToLogin(request)) {
            String currentPath = request.getRequestURI();
            String query = request.getQueryString();
            String redirectTarget = currentPath;
            if (query != null && !query.isBlank()) {
                redirectTarget = redirectTarget + "?" + query;
            }
            String encodedRedirect = URLEncoder.encode(redirectTarget, StandardCharsets.UTF_8);
            response.sendRedirect("/login?redirect=" + encodedRedirect + "&error=missing_tenant");
            return;
        }

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"missing_tenant\",\"error_description\":\"valid tenantId is required\"}");
    }

    private boolean shouldRedirectToLogin(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        boolean isAuthNavigationPath = IssuerTenantSupport.isAuthorizationEndpointPath(uri);
        if (!isAuthNavigationPath) {
            return false;
        }

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) {
            return true;
        }

        String secFetchMode = request.getHeader("Sec-Fetch-Mode");
        return "navigate".equalsIgnoreCase(secFetchMode);
    }

    private void rejectTenantMismatch(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"tenant_mismatch\",\"error_description\":\"tenant does not match token\"}");
    }

    private record ResolvedTenant(Long tenantId, String source) {}
}
