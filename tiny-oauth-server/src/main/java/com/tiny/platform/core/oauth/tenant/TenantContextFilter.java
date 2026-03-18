package com.tiny.platform.core.oauth.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import com.tiny.platform.core.oauth.security.PermissionVersionService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.net.URLEncoder;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TenantContextFilter
 * - 认证后只信任 SecurityContext / Session 中冻结的 activeTenantId
 * - 未认证阶段只允许：
 *   1) POST /login 参数解析 tenant（兼容 tenantCode）
 *   2) issuer path (`/{tenantCode}/oauth2/**`) 解析 tenant
 * - 若无有效 tenant，则拒绝请求（fail-closed）
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TenantContextFilter.class);
    public static final String TENANT_SOURCE_REQUEST_ATTRIBUTE = "tenantSource";

    private static final String TENANT_ID_HEADER = TenantContextContract.ACTIVE_TENANT_ID_HEADER;
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACTIVE_TENANT_ID_CLAIM = TenantContextContract.ACTIVE_TENANT_ID_CLAIM;
    private static final String USER_ID_CLAIM = "userId";
    private static final String AUTHORITIES_CLAIM = "authorities";
    private static final String PERMISSIONS_CLAIM = "permissions";
    private static final String PERMISSIONS_VERSION_CLAIM = "permissionsVersion";
    private static final String SESSION_ACTIVE_TENANT_ID_KEY = TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY;
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
    private final PermissionVersionService permissionVersionService;

    public TenantContextFilter(TenantRepository tenantRepository) {
        this(tenantRepository, null);
    }

    public TenantContextFilter(TenantRepository tenantRepository, PermissionVersionService permissionVersionService) {
        this.tenantRepository = tenantRepository;
        this.permissionVersionService = permissionVersionService;
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
        ResolvedTenant authenticatedTenant = resolveTenantFromAuthentication();
        Long authenticatedActiveTenantId = authenticatedTenant.activeTenantId();
        Long issuerActiveTenantId = resolveActiveTenantIdFromIssuerPath(request);
        Long sessionActiveTenantId = resolveActiveTenantIdFromSession(request);
        Long headerActiveTenantId = parseActiveTenantId(request.getHeader(TENANT_ID_HEADER));
        Long bearerTokenActiveTenantId = resolveActiveTenantIdFromAuthorizationHeader(request);
        Long activeTenantId;
        String tenantSource;

        if (authenticatedActiveTenantId != null) {
            if (isMismatch(authenticatedActiveTenantId, issuerActiveTenantId)
                    || isMismatch(authenticatedActiveTenantId, sessionActiveTenantId)
                    || isMismatch(authenticatedActiveTenantId, headerActiveTenantId)) {
                rejectTenantMismatch(response);
                return;
            }
            activeTenantId = authenticatedActiveTenantId;
            tenantSource = authenticatedTenant.source();
            freezeActiveTenantIdInSession(request, activeTenantId);
        } else if (bearerTokenActiveTenantId != null) {
            if (isMismatch(bearerTokenActiveTenantId, issuerActiveTenantId)
                    || isMismatch(bearerTokenActiveTenantId, sessionActiveTenantId)
                    || isMismatch(bearerTokenActiveTenantId, headerActiveTenantId)) {
                rejectTenantMismatch(response);
                return;
            }
            activeTenantId = bearerTokenActiveTenantId;
            tenantSource = TenantContext.SOURCE_TOKEN;
            freezeActiveTenantIdInSession(request, activeTenantId);
        } else if (issuerActiveTenantId != null) {
            if (isMismatch(issuerActiveTenantId, sessionActiveTenantId) || isMismatch(issuerActiveTenantId, headerActiveTenantId)) {
                rejectTenantMismatch(response);
                return;
            }
            activeTenantId = issuerActiveTenantId;
            tenantSource = TenantContext.SOURCE_ISSUER;
            freezeActiveTenantIdInSession(request, activeTenantId);
        } else if (sessionActiveTenantId != null) {
            if (isMismatch(sessionActiveTenantId, headerActiveTenantId)) {
                rejectTenantMismatch(response);
                return;
            }
            activeTenantId = sessionActiveTenantId;
            tenantSource = TenantContext.SOURCE_SESSION;
        } else {
            ResolvedTenant unauthenticatedTenant = resolveActiveTenantIdForUnauthenticatedRequest(request, headerActiveTenantId);
            activeTenantId = unauthenticatedTenant.activeTenantId();
            tenantSource = unauthenticatedTenant.source();
        }

        if (activeTenantId == null || activeTenantId <= 0) {
            logger.warn(
                    "缺少有效活动租户上下文，拒绝请求: method={}, uri={}, authenticatedActiveTenantId={}, bearerActiveTenantId={}, issuerActiveTenantId={}, sessionActiveTenantId={}, headerActiveTenantId={}, paramTenantCode={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    authenticatedActiveTenantId,
                    bearerTokenActiveTenantId,
                    issuerActiveTenantId,
                    sessionActiveTenantId,
                    headerActiveTenantId,
                    request.getParameter(TENANT_CODE_PARAM)
            );
            rejectMissingTenant(request, response);
            return;
        }

        if (!validateSessionPermissionsVersion(request, response, activeTenantId)) {
            return;
        }

        if (!validateBearerPermissionsVersion(request, response, activeTenantId)) {
            return;
        }

        String normalizedTenantSource = normalizeTenantSource(tenantSource);
        TenantContext.setActiveTenantId(activeTenantId);
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

    private ResolvedTenant resolveActiveTenantIdForUnauthenticatedRequest(HttpServletRequest request, Long headerActiveTenantId) {
        String uri = request.getRequestURI();
        boolean isLoginPost = "/login".equals(uri) && "POST".equalsIgnoreCase(request.getMethod());
        boolean canReadTenantFromParams = isLoginPost;
        if (!canReadTenantFromParams) {
            return new ResolvedTenant(null, null);
        }

        if (headerActiveTenantId != null) {
            if (logger.isDebugEnabled()) {
                logger.debug(
                        "[tenant-login] 解析未认证请求的活动租户: method={}, uri={}, headerActiveTenantId={}, paramTenantCode={}",
                        request.getMethod(),
                        uri,
                        headerActiveTenantId,
                        request.getParameter(TENANT_CODE_PARAM)
                );
            }
            return new ResolvedTenant(headerActiveTenantId, TenantContext.SOURCE_LOGIN_PARAM);
        }

        String rawTenantCode = request.getParameter(TENANT_CODE_PARAM);
        Long activeTenantIdByCode = resolveActiveTenantIdByCode(rawTenantCode);
        if (logger.isDebugEnabled()) {
            logger.debug(
                    "[tenant-login] 解析未认证请求的活动租户: method={}, uri={}, paramTenantCode={}, resolvedActiveTenantId={}",
                    request.getMethod(),
                    uri,
                    rawTenantCode,
                    activeTenantIdByCode
            );
        }
        if (activeTenantIdByCode != null) {
            return new ResolvedTenant(activeTenantIdByCode, TenantContext.SOURCE_LOGIN_PARAM);
        }
        return new ResolvedTenant(null, null);
    }

    private Long resolveActiveTenantIdFromIssuerPath(HttpServletRequest request) {
        String tenantCode = IssuerTenantSupport.extractTenantCodeFromRequestPath(request.getRequestURI());
        if (tenantCode == null) {
            return null;
        }
        return tenantRepository.findByCode(tenantCode)
                .filter(this::isTenantActive)
                .map(Tenant::getId)
                .orElse(null);
    }

    private Long parseActiveTenantId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            logger.warn("非法 activeTenantId: {}", raw);
            return null;
        }
    }

    private Long resolveActiveTenantIdByCode(String rawCode) {
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

    private Long resolveActiveTenantIdFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object raw = session.getAttribute(SESSION_ACTIVE_TENANT_ID_KEY);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String str) {
            return parseActiveTenantId(str);
        }
        return null;
    }

    private Long resolveActiveTenantIdFromAuthorizationHeader(HttpServletRequest request) {
        JsonNode payload = resolveJwtPayloadNode(request);
        return parseTenantIdFromPayload(payload);
    }

    private JsonNode resolveJwtPayloadNode(HttpServletRequest request) {
        String token = resolveBearerToken(request);
        if (token == null) {
            return null;
        }
        try {
            String[] segments = token.split("\\.");
            if (segments.length < 2) {
                return null;
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(segments[1]);
            return OBJECT_MAPPER.readTree(payloadBytes);
        } catch (Exception ex) {
            logger.debug("无法从 Bearer Token 解析 payload", ex);
        }
        return null;
    }

    private Long parseTenantIdFromPayload(JsonNode payload) {
        if (payload == null) {
            return null;
        }
        JsonNode tenantNode = payload.get(ACTIVE_TENANT_ID_CLAIM);
        return parseLongClaim(tenantNode);
    }

    private void freezeActiveTenantIdInSession(HttpServletRequest request, Long activeTenantId) {
        if (activeTenantId == null || activeTenantId <= 0) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(SESSION_ACTIVE_TENANT_ID_KEY, activeTenantId);
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
            return new ResolvedTenant(securityUser.getActiveTenantId(), TenantContext.SOURCE_SESSION);
        }
        Object details = authentication.getDetails();
        if (details instanceof SecurityUser securityUser) {
            return new ResolvedTenant(securityUser.getActiveTenantId(), TenantContext.SOURCE_SESSION);
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            Object claim = resolveTenantClaim(jwtAuthenticationToken);
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

    private Object resolveTenantClaim(JwtAuthenticationToken jwtAuthenticationToken) {
        return jwtAuthenticationToken.getToken().getClaims().get(ACTIVE_TENANT_ID_CLAIM);
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
        response.getWriter().write("{\"error\":\"missing_tenant\",\"error_description\":\"valid active tenant is required\"}");
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

    private boolean validateBearerPermissionsVersion(HttpServletRequest request,
                                                     HttpServletResponse response,
                                                     Long activeTenantId) throws IOException {
        if (permissionVersionService == null) {
            return true;
        }
        ResolvedBearerClaims bearerClaims = resolveBearerClaims(request);
        if (bearerClaims == null || bearerClaims.permissionsVersion() == null || bearerClaims.permissionsVersion().isBlank()) {
            return true;
        }
        if (bearerClaims.userId() == null || bearerClaims.authorities().isEmpty()) {
            rejectStalePermissions(response);
            return false;
        }

        String currentPermissionsVersion = permissionVersionService.resolvePermissionsVersion(
            bearerClaims.userId(),
            activeTenantId
        );
        if (currentPermissionsVersion == null || !currentPermissionsVersion.equals(bearerClaims.permissionsVersion())) {
            rejectStalePermissions(response);
            return false;
        }
        return true;
    }

    private boolean validateSessionPermissionsVersion(HttpServletRequest request,
                                                      HttpServletResponse response,
                                                      Long activeTenantId) throws IOException {
        if (permissionVersionService == null) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        SecurityUser securityUser = resolveSessionSecurityUser(authentication);
        if (securityUser == null) {
            return true;
        }

        String storedPermissionsVersion = securityUser.getPermissionsVersion();
        if (storedPermissionsVersion == null || storedPermissionsVersion.isBlank()) {
            return true;
        }

        Long userId = securityUser.getUserId();
        if (userId == null) {
            invalidateAuthenticatedSession(request);
            rejectStaleSessionPermissions(request, response);
            return false;
        }

        String currentPermissionsVersion = permissionVersionService.resolvePermissionsVersion(userId, activeTenantId);
        if (currentPermissionsVersion == null || !currentPermissionsVersion.equals(storedPermissionsVersion)) {
            invalidateAuthenticatedSession(request);
            rejectStaleSessionPermissions(request, response);
            return false;
        }
        return true;
    }

    private ResolvedBearerClaims resolveBearerClaims(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return resolveBearerClaims(jwtAuthenticationToken.getToken().getClaims());
        }
        JsonNode payload = resolveJwtPayloadNode(request);
        if (payload == null) {
            return null;
        }
        return resolveBearerClaims(payload);
    }

    private ResolvedBearerClaims resolveBearerClaims(java.util.Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            return null;
        }
        Long userId = parseLongClaim(claims.get(USER_ID_CLAIM));
        String permissionsVersion = parseStringClaim(claims.get(PERMISSIONS_VERSION_CLAIM));
        Set<String> authorities = parseAuthoritiesClaim(claims.get(AUTHORITIES_CLAIM));
        if (authorities.isEmpty()) {
            authorities = parseAuthoritiesClaim(claims.get(PERMISSIONS_CLAIM));
        }
        return new ResolvedBearerClaims(userId, permissionsVersion, authorities);
    }

    private ResolvedBearerClaims resolveBearerClaims(JsonNode payload) {
        if (payload == null || payload.isMissingNode()) {
            return null;
        }
        Long userId = parseLongClaim(payload.get(USER_ID_CLAIM));
        String permissionsVersion = parseStringClaim(payload.get(PERMISSIONS_VERSION_CLAIM));
        Set<String> authorities = parseAuthoritiesClaim(payload.get(AUTHORITIES_CLAIM));
        if (authorities.isEmpty()) {
            authorities = parseAuthoritiesClaim(payload.get(PERMISSIONS_CLAIM));
        }
        return new ResolvedBearerClaims(userId, permissionsVersion, authorities);
    }

    private SecurityUser resolveSessionSecurityUser(Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (authentication instanceof JwtAuthenticationToken) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser;
        }
        Object details = authentication.getDetails();
        if (details instanceof SecurityUser securityUser) {
            return securityUser;
        }
        return null;
    }

    private Set<String> parseAuthoritiesClaim(Object rawClaim) {
        if (rawClaim instanceof Iterable<?> iterable) {
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (Object item : iterable) {
                String normalized = parseStringClaim(item);
                if (normalized != null && !normalized.isBlank()) {
                    values.add(normalized);
                }
            }
            return values;
        }
        String normalized = parseStringClaim(rawClaim);
        if (normalized == null || normalized.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : normalized.split("[\\s,]+")) {
            String candidate = item.trim();
            if (!candidate.isEmpty()) {
                values.add(candidate);
            }
        }
        return values;
    }

    private Set<String> parseAuthoritiesClaim(JsonNode rawClaim) {
        if (rawClaim == null || rawClaim.isNull()) {
            return Set.of();
        }
        if (rawClaim.isArray()) {
            LinkedHashSet<String> values = new LinkedHashSet<>();
            rawClaim.forEach(item -> {
                String normalized = parseStringClaim(item);
                if (normalized != null && !normalized.isBlank()) {
                    values.add(normalized);
                }
            });
            return values;
        }
        String normalized = parseStringClaim(rawClaim);
        if (normalized == null || normalized.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : normalized.split("[\\s,]+")) {
            String candidate = item.trim();
            if (!candidate.isEmpty()) {
                values.add(candidate);
            }
        }
        return values;
    }

    private Long parseLongClaim(Object rawClaim) {
        if (rawClaim instanceof Number number) {
            return number.longValue();
        }
        if (rawClaim instanceof String text) {
            return parseActiveTenantId(text);
        }
        return null;
    }

    private Long parseLongClaim(JsonNode rawClaim) {
        if (rawClaim == null || rawClaim.isNull()) {
            return null;
        }
        if (rawClaim.isIntegralNumber()) {
            return rawClaim.longValue();
        }
        if (rawClaim.isTextual()) {
            return parseActiveTenantId(rawClaim.asText());
        }
        return null;
    }

    private String parseStringClaim(Object rawClaim) {
        if (rawClaim instanceof String text) {
            String normalized = text.trim();
            return normalized.isEmpty() ? null : normalized;
        }
        return rawClaim != null ? String.valueOf(rawClaim) : null;
    }

    private String parseStringClaim(JsonNode rawClaim) {
        if (rawClaim == null || rawClaim.isNull()) {
            return null;
        }
        if (rawClaim.isTextual()) {
            String normalized = rawClaim.asText().trim();
            return normalized.isEmpty() ? null : normalized;
        }
        if (rawClaim.isNumber() || rawClaim.isBoolean()) {
            return rawClaim.asText();
        }
        return null;
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (authorization == null || authorization.isBlank()
            || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private void rejectStalePermissions(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        response.getWriter().write("{\"error\":\"stale_permissions\",\"error_description\":\"token permissions are outdated\"}");
    }

    private void rejectStaleSessionPermissions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (shouldRedirectToLogin(request)) {
            String currentPath = request.getRequestURI();
            String query = request.getQueryString();
            String redirectTarget = currentPath;
            if (query != null && !query.isBlank()) {
                redirectTarget = redirectTarget + "?" + query;
            }
            String encodedRedirect = URLEncoder.encode(redirectTarget, StandardCharsets.UTF_8);
            response.sendRedirect("/login?redirect=" + encodedRedirect + "&error=stale_permissions");
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"stale_permissions\",\"error_description\":\"session permissions are outdated\"}");
    }

    private void invalidateAuthenticatedSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ex) {
                logger.debug("session already invalidated while clearing stale permissions", ex);
            }
        }
    }

    private record ResolvedTenant(Long activeTenantId, String source) {}

    private record ResolvedBearerClaims(Long userId, String permissionsVersion, Set<String> authorities) {}
}
