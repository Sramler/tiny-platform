package com.tiny.platform.core.oauth.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import com.tiny.platform.core.oauth.security.PermissionVersionService;
import com.tiny.platform.infrastructure.auth.audit.domain.AuthorizationAuditEventType;
import com.tiny.platform.infrastructure.auth.audit.service.AuthorizationAuditService;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import com.tiny.platform.infrastructure.tenant.config.PlatformTenantResolver;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
    private final PlatformTenantResolver platformTenantResolver;
    private final AuthorizationAuditService authorizationAuditService;
    private final TenantLifecycleReadPolicy tenantLifecycleReadPolicy;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final UserUnitRepository userUnitRepository;

    public TenantContextFilter(TenantRepository tenantRepository) {
        this(tenantRepository, null, null, null, new TenantLifecycleReadPolicy(), null, null);
    }

    public TenantContextFilter(TenantRepository tenantRepository, PermissionVersionService permissionVersionService) {
        this(tenantRepository, permissionVersionService, null, null, new TenantLifecycleReadPolicy(), null, null);
    }

    public TenantContextFilter(TenantRepository tenantRepository,
                               PermissionVersionService permissionVersionService,
                               PlatformTenantResolver platformTenantResolver) {
        this(tenantRepository, permissionVersionService, platformTenantResolver, null, new TenantLifecycleReadPolicy(), null, null);
    }

    public TenantContextFilter(TenantRepository tenantRepository,
                               PermissionVersionService permissionVersionService,
                               PlatformTenantResolver platformTenantResolver,
                               AuthorizationAuditService authorizationAuditService,
                               TenantLifecycleReadPolicy tenantLifecycleReadPolicy,
                               OrganizationUnitRepository organizationUnitRepository,
                               UserUnitRepository userUnitRepository) {
        this.tenantRepository = tenantRepository;
        this.permissionVersionService = permissionVersionService;
        this.platformTenantResolver = platformTenantResolver;
        this.authorizationAuditService = authorizationAuditService;
        this.tenantLifecycleReadPolicy = tenantLifecycleReadPolicy != null
            ? tenantLifecycleReadPolicy
            : new TenantLifecycleReadPolicy();
        this.organizationUnitRepository = organizationUnitRepository;
        this.userUnitRepository = userUnitRepository;
    }

    // Backward-compatible constructor for older tests/config code paths.
    public TenantContextFilter(TenantRepository tenantRepository,
                               PermissionVersionService permissionVersionService,
                               PlatformTenantResolver platformTenantResolver,
                               AuthorizationAuditService authorizationAuditService,
                               TenantLifecycleReadPolicy tenantLifecycleReadPolicy) {
        this(tenantRepository, permissionVersionService, platformTenantResolver, authorizationAuditService, tenantLifecycleReadPolicy, null, null);
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
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String matchPath = resolvePathForTenantMatching(request);
            if (IssuerTenantSupport.isDefaultIssuerOAuth2ProtocolPostEndpoint(matchPath)) {
                return true;
            }
        }
        for (String pattern : SKIP_PATTERNS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 与 {@link #isLoginPostRequest(HttpServletRequest)} 一致：优先 servletPath，便于带 context-path 部署时匹配端点。
     */
    private static String resolvePathForTenantMatching(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        return stripContextPath(request.getRequestURI(), request.getContextPath());
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

        // 单入口：POST /login 且未提交 tenantCode 时视为平台登录尝试，忽略会话/头里残留的活动租户，
        // 否则上次租户登录写入的 SESSION_ACTIVE_TENANT_ID 会串到平台登录并触发「缺少租户信息」等误报。
        if (isPlatformLoginFormAttemptWithoutTenantCode(request)) {
            activeTenantId = null;
            tenantSource = TenantContext.SOURCE_LOGIN_PARAM;
        }

        ScopedAuthResolution scopedAuth = resolvePairedActiveScope(activeTenantId, request);
        if (!scopedAuth.valid()) {
            logger.warn(
                "非法 active scope（成对解析/bearer-session 一致性），受控拒绝: method={}, uri={}, activeTenantId={}, bearerPresent={}, reason={}",
                request.getMethod(),
                request.getRequestURI(),
                activeTenantId,
                scopedAuth.bearerPresent(),
                scopedAuth.failureReason()
            );
            handleInvalidActiveScope(request, response, activeTenantId, scopedAuth.bearerPresent());
            return;
        }
        String scopeType = scopedAuth.scopeType();
        Long scopeId = scopedAuth.scopeId();
        // 单入口登录：未解析出活动租户且未提交有效 tenantCode 参数时，按 PLATFORM 进入认证链，
        // 由后端判定用户是否具备 PLATFORM 赋权；若提交了非空 tenantCode 但无法解析，则 missing_tenant。
        if (isLoginPostRequest(request) && (activeTenantId == null || activeTenantId <= 0)) {
            String rawTenantCode = request.getParameter(TENANT_CODE_PARAM);
            if (rawTenantCode != null && !rawTenantCode.isBlank()) {
                rejectMissingTenant(request, response);
                return;
            }
            scopeType = TenantContextContract.SCOPE_TYPE_PLATFORM;
            scopeId = null;
        }
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType)
            && headerActiveTenantId != null
            && isMismatch(headerActiveTenantId, authenticatedActiveTenantId)
            && isMismatch(headerActiveTenantId, bearerTokenActiveTenantId)
            && isMismatch(headerActiveTenantId, issuerActiveTenantId)
            && isMismatch(headerActiveTenantId, sessionActiveTenantId)) {
            rejectTenantMismatch(response);
            return;
        }
        boolean allowPlatformWithoutTenant = TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType);
        if ((activeTenantId == null || activeTenantId <= 0) && !allowPlatformWithoutTenant) {
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
        Optional<String> blockedLifecycleStatus = activeTenantId == null || activeTenantId <= 0
            ? Optional.empty()
            : tenantRepository.findLoginBlockedLifecycleStatus(activeTenantId);
        if (blockedLifecycleStatus == null) {
            blockedLifecycleStatus = Optional.empty();
        }
        if (blockedLifecycleStatus.isPresent()) {
            String lifecycleStatus = blockedLifecycleStatus.get();
            if ("FROZEN".equalsIgnoreCase(lifecycleStatus) && isLoginPostRequest(request)) {
                // keep authentication-chain behavior for a clearer business error
            } else {
                Optional<TenantLifecycleReadPolicy.AllowedReadAccess> allowlistedAccess =
                    resolveAllowlistedReadAccess(request, lifecycleStatus, scopeType);
                if (allowlistedAccess.isPresent()) {
                    auditAllowlistedReadAccess(activeTenantId, lifecycleStatus, scopeType, request, allowlistedAccess.get());
                } else if ("DECOMMISSIONED".equalsIgnoreCase(lifecycleStatus)) {
                    logger.warn(
                            "活动租户已下线，拒绝请求: method={}, uri={}, activeTenantId={}, scopeType={}",
                            request.getMethod(),
                            request.getRequestURI(),
                            activeTenantId,
                            scopeType
                    );
                    rejectDecommissionedTenant(response);
                    return;
                } else if ("FROZEN".equalsIgnoreCase(lifecycleStatus)) {
                    logger.warn(
                            "活动租户已冻结，拒绝请求: method={}, uri={}, activeTenantId={}, scopeType={}",
                            request.getMethod(),
                            request.getRequestURI(),
                            activeTenantId,
                            scopeType
                    );
                    rejectFrozenTenant(response);
                    return;
                }
            }
        }

        if (!validateSessionPermissionsVersion(request, response, activeTenantId, scopeType, scopeId)) {
            return;
        }

        if (!validateBearerPermissionsVersion(request, response, activeTenantId, scopeType, scopeId)) {
            return;
        }

        String normalizedTenantSource = normalizeTenantSource(tenantSource);
        TenantContext.setActiveTenantId(activeTenantId);
        TenantContext.setTenantSource(normalizedTenantSource);
        TenantContext.setActiveScopeType(scopeType);
        TenantContext.setActiveScopeId(scopeId);
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

    /**
     * Bearer 优先的成对解析：{@code activeScopeType} 与 {@code activeScopeId} 必须来自同一来源（JWT 成对或 Session 成对），禁止 bearer type + session id 混拼。
     * <ul>
     *   <li>存在 Bearer 且 JWT 显式携带 {@code activeScopeType}：整对来自 JWT；若 Session 写了 {@code activeScopeType} 或仅写了 {@code activeScopeId}，则必须与 JWT 成对可比一致，否则 {@link ActiveScopeFailureReason#SCOPE_SOURCE_CONFLICT}。</li>
     *   <li>存在 Bearer 但 JWT 未携带 {@code activeScopeType}：整对来自 Session（ORG/DEPT 的 id 仅来自 Session）；JWT 单独带 {@code activeScopeId} 而无 type 视为孤儿声明，拒绝。</li>
     *   <li>无 Bearer：仅 Session + 租户推断，与历史 session-only 行为一致。</li>
     * </ul>
     */
    private ScopedAuthResolution resolvePairedActiveScope(Long activeTenantId, HttpServletRequest request) {
        boolean bearerPresent = resolveBearerToken(request) != null;
        BearerScopeTypeResolution bearerTypeRes = bearerPresent ? resolveBearerScopeTypeClaim(request) : BearerScopeTypeResolution.absent();

        if (bearerPresent) {
            if (bearerTypeRes.mode() == BearerScopeTypeMode.INVALID) {
                return ScopedAuthResolution.invalid(true, ActiveScopeFailureReason.UNSUPPORTED_EXPLICIT_SCOPE);
            }
            if (bearerHasOrphanActiveScopeIdClaim(request, bearerTypeRes)) {
                return ScopedAuthResolution.invalid(true, ActiveScopeFailureReason.ORPHAN_SCOPE_CLAIM);
            }
        }

        String effectiveScopeType;
        Long effectiveScopeId;

        if (bearerPresent && bearerTypeRes.mode() == BearerScopeTypeMode.SUPPORTED) {
            effectiveScopeType = bearerTypeRes.normalizedType();
            ScopedAuthResolution bearerPair = resolveBearerScopeIdPair(effectiveScopeType, activeTenantId, request);
            if (!bearerPair.valid()) {
                return bearerPair;
            }
            effectiveScopeId = bearerPair.scopeId();
            if (sessionResidualScopeIdConflictsWithBearerPair(
                request, effectiveScopeType, effectiveScopeId, activeTenantId)) {
                return ScopedAuthResolution.invalid(true, ActiveScopeFailureReason.SCOPE_SOURCE_CONFLICT);
            }
            if (sessionAssertsActiveScopeType(request)
                && !sessionMatchesEffectiveScope(request, effectiveScopeType, effectiveScopeId, activeTenantId)) {
                return ScopedAuthResolution.invalid(true, ActiveScopeFailureReason.SCOPE_SOURCE_CONFLICT);
            }
        } else {
            SessionScopePair sessionPair = resolveSessionScopePair(activeTenantId, request);
            effectiveScopeType = sessionPair.scopeType();
            effectiveScopeId = sessionPair.scopeId();
        }

        if (effectiveScopeType == null || !isSupportedScopeType(effectiveScopeType)) {
            effectiveScopeType = inferScopeTypeByTenantId(activeTenantId);
        }

        return validatePairedOrgDeptMembership(effectiveScopeType, effectiveScopeId, activeTenantId, request, bearerPresent);
    }

    private enum BearerScopeTypeMode {
        ABSENT,
        INVALID,
        SUPPORTED
    }

    private record BearerScopeTypeResolution(BearerScopeTypeMode mode, String normalizedType) {
        static BearerScopeTypeResolution absent() {
            return new BearerScopeTypeResolution(BearerScopeTypeMode.ABSENT, null);
        }

        static BearerScopeTypeResolution invalid() {
            return new BearerScopeTypeResolution(BearerScopeTypeMode.INVALID, null);
        }

        static BearerScopeTypeResolution supported(String type) {
            return new BearerScopeTypeResolution(BearerScopeTypeMode.SUPPORTED, type);
        }
    }

    private BearerScopeTypeResolution resolveBearerScopeTypeClaim(HttpServletRequest request) {
        boolean keyPresent = false;
        String normalizedFromJwtAuth = null;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            var claims = jwtAuthenticationToken.getToken().getClaims();
            if (claims != null && claims.containsKey(TenantContextContract.ACTIVE_SCOPE_TYPE_CLAIM)) {
                keyPresent = true;
                normalizedFromJwtAuth = normalizeScopeType(parseStringClaim(claims.get(TenantContextContract.ACTIVE_SCOPE_TYPE_CLAIM)));
            }
        }
        if (!keyPresent) {
            JsonNode payload = resolveJwtPayloadNode(request);
            if (payload != null && payload.has(TenantContextContract.ACTIVE_SCOPE_TYPE_CLAIM)) {
                keyPresent = true;
                normalizedFromJwtAuth = normalizeScopeType(parseStringClaim(payload.get(TenantContextContract.ACTIVE_SCOPE_TYPE_CLAIM)));
            }
        }
        if (!keyPresent) {
            return BearerScopeTypeResolution.absent();
        }
        if (normalizedFromJwtAuth == null) {
            return BearerScopeTypeResolution.invalid();
        }
        if (!isSupportedScopeType(normalizedFromJwtAuth)) {
            return BearerScopeTypeResolution.invalid();
        }
        return BearerScopeTypeResolution.supported(normalizedFromJwtAuth);
    }

    private boolean bearerHasOrphanActiveScopeIdClaim(HttpServletRequest request, BearerScopeTypeResolution bearerTypeRes) {
        if (bearerTypeRes.mode() == BearerScopeTypeMode.SUPPORTED) {
            return false;
        }
        Long id = readBearerActiveScopeIdClaim(request);
        return id != null && id > 0;
    }

    private Long readBearerActiveScopeIdClaim(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            var claims = jwtAuthenticationToken.getToken().getClaims();
            if (claims != null && claims.containsKey(TenantContextContract.ACTIVE_SCOPE_ID_CLAIM)) {
                return parseLongClaim(claims.get(TenantContextContract.ACTIVE_SCOPE_ID_CLAIM));
            }
        }
        JsonNode payload = resolveJwtPayloadNode(request);
        if (payload != null && payload.has(TenantContextContract.ACTIVE_SCOPE_ID_CLAIM)) {
            return parseLongClaim(payload.get(TenantContextContract.ACTIVE_SCOPE_ID_CLAIM));
        }
        return null;
    }

    /**
     * JWT 显式 type 下的 id：TENANT/PLATFORM 不读 session；ORG/DEPT 仅读 JWT 的 {@code activeScopeId}。
     */
    private ScopedAuthResolution resolveBearerScopeIdPair(String scopeType, Long activeTenantId, HttpServletRequest request) {
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType)) {
            return ScopedAuthResolution.ok(TenantContextContract.SCOPE_TYPE_PLATFORM, null, true);
        }
        if (TenantContextContract.SCOPE_TYPE_TENANT.equals(scopeType)) {
            return ScopedAuthResolution.ok(TenantContextContract.SCOPE_TYPE_TENANT, activeTenantId, true);
        }
        Long id = readBearerActiveScopeIdClaim(request);
        if (id == null || id <= 0) {
            return ScopedAuthResolution.invalid(true, ActiveScopeFailureReason.MISSING_SCOPE_ID);
        }
        return ScopedAuthResolution.ok(scopeType, id, true);
    }

    private record SessionScopePair(String scopeType, Long scopeId) {}

    private SessionScopePair resolveSessionScopePair(Long activeTenantId, HttpServletRequest request) {
        String sessionType = normalizeScopeType(resolveScopeTypeFromSession(request));
        Long sessionId = resolveScopeIdFromSession(request);
        String effectiveType = isSupportedScopeType(sessionType) ? sessionType : inferScopeTypeByTenantId(activeTenantId);
        Long effectiveId;
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equals(effectiveType)) {
            effectiveId = null;
        } else if (TenantContextContract.SCOPE_TYPE_TENANT.equals(effectiveType)) {
            effectiveId = activeTenantId;
        } else {
            effectiveId = sessionId;
        }
        return new SessionScopePair(effectiveType, effectiveId);
    }

    /**
     * Session 在属性里显式写了 {@code activeScopeType}（与 id 成对校验的另一侧入口）。
     */
    private boolean sessionAssertsActiveScopeType(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        return session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY) != null;
    }

    /**
     * JWT 已显式断言成对作用域时，Session 中若仅有 {@code activeScopeId} 而无 type，不得与 JWT 解析结果不一致（禁止「bearer type + 异源 session id」静默混用）。
     */
    private boolean sessionResidualScopeIdConflictsWithBearerPair(HttpServletRequest request,
                                                                  String effectiveScopeType,
                                                                  Long effectiveScopeId,
                                                                  Long activeTenantId) {
        String sessionType = normalizeScopeType(resolveScopeTypeFromSession(request));
        if (sessionType != null) {
            return false;
        }
        Long sessionId = resolveScopeIdFromSession(request);
        if (sessionId == null || sessionId <= 0) {
            return false;
        }
        Long expectedComparable = comparableScopeId(effectiveScopeType, effectiveScopeId, activeTenantId);
        Long sessionComparable = comparableScopeId(effectiveScopeType, sessionId, activeTenantId);
        return !Objects.equals(expectedComparable, sessionComparable);
    }

    private boolean sessionMatchesEffectiveScope(HttpServletRequest request,
                                                 String effectiveScopeType,
                                                 Long effectiveScopeId,
                                                 Long activeTenantId) {
        String sessionType = normalizeScopeType(resolveScopeTypeFromSession(request));
        Long sessionId = resolveScopeIdFromSession(request);
        if (sessionType == null) {
            if (sessionId == null || sessionId <= 0) {
                return true;
            }
            Long expectedComparable = comparableScopeId(effectiveScopeType, effectiveScopeId, activeTenantId);
            Long sessionComparable = comparableScopeId(effectiveScopeType, sessionId, activeTenantId);
            return Objects.equals(expectedComparable, sessionComparable);
        }
        if (!effectiveScopeType.equals(sessionType)) {
            return false;
        }
        return Objects.equals(comparableScopeId(effectiveScopeType, effectiveScopeId, activeTenantId),
            comparableScopeId(sessionType, sessionId, activeTenantId));
    }

    private static Long comparableScopeId(String scopeType, Long scopeId, Long activeTenantId) {
        if (TenantContextContract.SCOPE_TYPE_TENANT.equals(scopeType)) {
            return activeTenantId;
        }
        return scopeId;
    }

    private ScopedAuthResolution validatePairedOrgDeptMembership(String scopeType,
                                                                  Long scopeId,
                                                                  Long activeTenantId,
                                                                  HttpServletRequest request,
                                                                  boolean bearerPresent) {
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType)) {
            return ScopedAuthResolution.ok(TenantContextContract.SCOPE_TYPE_PLATFORM, null, bearerPresent);
        }
        if (TenantContextContract.SCOPE_TYPE_TENANT.equals(scopeType)) {
            return ScopedAuthResolution.ok(TenantContextContract.SCOPE_TYPE_TENANT, activeTenantId, bearerPresent);
        }
        if (scopeId == null || scopeId <= 0) {
            return ScopedAuthResolution.invalid(bearerPresent, ActiveScopeFailureReason.MISSING_SCOPE_ID);
        }
        if (organizationUnitRepository == null || userUnitRepository == null) {
            return ScopedAuthResolution.invalid(bearerPresent, ActiveScopeFailureReason.REPOS_MISSING);
        }
        if (activeTenantId == null || activeTenantId <= 0) {
            return ScopedAuthResolution.invalid(bearerPresent, ActiveScopeFailureReason.NO_TENANT);
        }
        var unitOpt = organizationUnitRepository.findByIdAndTenantId(scopeId, activeTenantId);
        if (unitOpt.isEmpty()) {
            return ScopedAuthResolution.invalid(bearerPresent, ActiveScopeFailureReason.UNIT_NOT_IN_TENANT);
        }
        var unit = unitOpt.get();
        String unitType = unit.getUnitType();
        if (TenantContextContract.SCOPE_TYPE_ORG.equals(scopeType) && (unitType == null || !"ORG".equalsIgnoreCase(unitType))) {
            return ScopedAuthResolution.invalid(bearerPresent, ActiveScopeFailureReason.TYPE_MISMATCH);
        }
        if (TenantContextContract.SCOPE_TYPE_DEPT.equals(scopeType) && (unitType == null || !"DEPT".equalsIgnoreCase(unitType))) {
            return ScopedAuthResolution.invalid(bearerPresent, ActiveScopeFailureReason.TYPE_MISMATCH);
        }
        Long userId = resolveCurrentUserId(request);
        if (userId == null || userId <= 0) {
            return ScopedAuthResolution.invalid(bearerPresent, ActiveScopeFailureReason.NO_USER_CONTEXT);
        }
        boolean allowed = TenantContextContract.SCOPE_TYPE_DEPT.equals(scopeType)
            ? userUnitRepository.existsByTenantIdAndUserIdAndUnitId(activeTenantId, userId, scopeId)
            : isUserInOrg(activeTenantId, userId, scopeId);
        if (!allowed) {
            return ScopedAuthResolution.invalid(bearerPresent, ActiveScopeFailureReason.NOT_MEMBER);
        }
        return ScopedAuthResolution.ok(scopeType, scopeId, bearerPresent);
    }

    private void handleInvalidActiveScope(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Long activeTenantId,
                                         boolean bearerDriven) throws IOException {
        if (bearerDriven) {
            if (activeTenantId != null && activeTenantId > 0) {
                resetSessionActiveScopeToTenant(request, activeTenantId);
            }
            clearSecurityContextFromSession(request);
            rejectInvalidActiveScopeBearer(response);
            return;
        }
        rejectInvalidActiveScopeSession(request, response, activeTenantId);
    }

    private void resetSessionActiveScopeToTenant(HttpServletRequest request, Long activeTenantId) {
        if (activeTenantId == null || activeTenantId <= 0) {
            return;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, TenantContextContract.SCOPE_TYPE_TENANT);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, activeTenantId);
    }

    private void clearSecurityContextFromSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }
    }

    private void rejectInvalidActiveScopeBearer(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        response.getWriter().write("{\"error\":\"" + TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE
            + "\",\"error_description\":\"bearer active scope is invalid or not permitted\"}");
    }

    private void rejectInvalidActiveScopeSession(HttpServletRequest request,
                                                 HttpServletResponse response,
                                                 Long activeTenantId) throws IOException {
        resetSessionActiveScopeToTenant(request, activeTenantId);
        clearSecurityContextFromSession(request);
        if (shouldRedirectToLogin(request)) {
            String currentPath = request.getRequestURI();
            String query = request.getQueryString();
            String redirectTarget = currentPath;
            if (query != null && !query.isBlank()) {
                redirectTarget = redirectTarget + "?" + query;
            }
            String encodedRedirect = URLEncoder.encode(redirectTarget, StandardCharsets.UTF_8);
            response.sendRedirect("/login?redirect=" + encodedRedirect + "&error=" + TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + TenantContextContract.ERROR_INVALID_ACTIVE_SCOPE
            + "\",\"error_description\":\"session active scope is invalid or not permitted\"}");
    }

    private String inferScopeTypeByTenantId(Long activeTenantId) {
        if (platformTenantResolver != null && platformTenantResolver.isPlatformTenant(activeTenantId)) {
            return TenantContextContract.SCOPE_TYPE_PLATFORM;
        }
        return TenantContextContract.SCOPE_TYPE_TENANT;
    }

    private ResolvedTenant resolveActiveTenantIdForUnauthenticatedRequest(HttpServletRequest request, Long headerActiveTenantId) {
        String uri = request.getRequestURI();
        boolean isLoginPost = isLoginPostRequest(request);
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

    private String resolveScopeTypeFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object raw = session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY);
        if (raw == null) {
            return null;
        }
        return normalizeScopeType(String.valueOf(raw));
    }

    private Long resolveScopeIdFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object raw = session.getAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text) {
            return parseActiveTenantId(text);
        }
        return null;
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

    private String normalizeScopeType(String scopeType) {
        if (scopeType == null || scopeType.isBlank()) {
            return null;
        }
        return scopeType.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isSupportedScopeType(String scopeType) {
        return TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType)
            || TenantContextContract.SCOPE_TYPE_TENANT.equals(scopeType)
            || TenantContextContract.SCOPE_TYPE_ORG.equals(scopeType)
            || TenantContextContract.SCOPE_TYPE_DEPT.equals(scopeType);
    }

    private Long resolveCurrentUserId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof SecurityUser securityUser) {
                return securityUser.getUserId();
            }
            Object details = authentication.getDetails();
            if (details instanceof SecurityUser securityUser) {
                return securityUser.getUserId();
            }
        }
        JsonNode payload = resolveJwtPayloadNode(request);
        if (payload != null) {
            return parseLongClaim(payload.get(USER_ID_CLAIM));
        }
        return null;
    }

    private boolean isUserInOrg(Long tenantId, Long userId, Long orgId) {
        List<Long> unitIds = userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE");
        if (unitIds == null || unitIds.isEmpty()) {
            return false;
        }
        java.util.Map<Long, com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit> cache = new java.util.HashMap<>();
        for (Long unitId : unitIds) {
            Long current = unitId;
            int guard = 0;
            java.util.Set<Long> visited = new java.util.LinkedHashSet<>();
            while (current != null && current > 0 && visited.add(current) && guard++ < 50) {
                if (orgId.equals(current)) {
                    return true;
                }
                com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit unit =
                    cache.computeIfAbsent(current, id -> organizationUnitRepository.findByIdAndTenantId(id, tenantId).orElse(null));
                if (unit == null) {
                    break;
                }
                current = unit.getParentId();
            }
        }
        return false;
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

    private boolean isLoginPostRequest(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = stripContextPath(request.getRequestURI(), request.getContextPath());
        }
        return "/login".equals(path);
    }

    /**
     * 未带租户编码的表单登录：与前端平台 Tab（不渲染 tenantCode）一致，按平台认证链处理。
     */
    private boolean isPlatformLoginFormAttemptWithoutTenantCode(HttpServletRequest request) {
        if (!isLoginPostRequest(request)) {
            return false;
        }
        String raw = request.getParameter(TENANT_CODE_PARAM);
        return raw == null || raw.isBlank();
    }

    private static String stripContextPath(String requestUri, String contextPath) {
        if (requestUri == null) {
            return "";
        }
        if (contextPath == null || contextPath.isEmpty()) {
            return requestUri;
        }
        return requestUri.startsWith(contextPath) ? requestUri.substring(contextPath.length()) : requestUri;
    }

    private Optional<TenantLifecycleReadPolicy.AllowedReadAccess> resolveAllowlistedReadAccess(HttpServletRequest request,
                                                                                                String lifecycleStatus,
                                                                                                String scopeType) {
        if (!TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType)) {
            return Optional.empty();
        }
        return tenantLifecycleReadPolicy.resolve(request, lifecycleStatus);
    }

    private void auditAllowlistedReadAccess(Long activeTenantId,
                                            String lifecycleStatus,
                                            String scopeType,
                                            HttpServletRequest request,
                                            TenantLifecycleReadPolicy.AllowedReadAccess allowedReadAccess) {
        if (authorizationAuditService == null) {
            return;
        }
        String resourcePermission = resolveMatchedAuthority(allowedReadAccess.requiredAuthorities());
        String eventDetail = buildAllowlistedAccessDetail(activeTenantId, lifecycleStatus, scopeType, request, resourcePermission);
        authorizationAuditService.log(
            AuthorizationAuditEventType.TENANT_LIFECYCLE_ALLOWLIST_ACCESS,
            activeTenantId,
            null,
            null,
            scopeType,
            activeTenantId,
            allowedReadAccess.module(),
            resourcePermission,
            eventDetail,
            "SUCCESS",
            null
        );
    }

    private String resolveMatchedAuthority(Set<String> requiredAuthorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (requiredAuthorities == null || requiredAuthorities.isEmpty()) {
            return null;
        }
        if (authentication == null) {
            return requiredAuthorities.iterator().next();
        }
        return authentication.getAuthorities().stream()
            .map(grantedAuthority -> grantedAuthority.getAuthority())
            .filter(requiredAuthorities::contains)
            .findFirst()
            .orElse(requiredAuthorities.iterator().next());
    }

    private String buildAllowlistedAccessDetail(Long activeTenantId,
                                                String lifecycleStatus,
                                                String scopeType,
                                                HttpServletRequest request,
                                                String resourcePermission) {
        try {
            return OBJECT_MAPPER.writeValueAsString(java.util.Map.of(
                "action", AuthorizationAuditEventType.TENANT_LIFECYCLE_ALLOWLIST_ACCESS,
                "tenantId", activeTenantId,
                "tenantLifecycleStatus", lifecycleStatus,
                "scopeType", scopeType,
                "request", java.util.Map.of(
                    "method", request.getMethod(),
                    "path", request.getRequestURI()
                ),
                "resourcePermission", resourcePermission,
                "reason", "platform_lifecycle_read_allowlist"
            ));
        } catch (Exception ex) {
            logger.warn("租户生命周期白名单访问审计序列化失败: tenantId={}, path={}",
                activeTenantId, request.getRequestURI(), ex);
            return "{\"reason\":\"platform_lifecycle_read_allowlist\"}";
        }
    }

    private void rejectTenantMismatch(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"tenant_mismatch\",\"error_description\":\"tenant does not match token\"}");
    }

    private void rejectDecommissionedTenant(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"tenant_decommissioned\",\"error_description\":\"tenant has been decommissioned\"}");
    }

    private void rejectFrozenTenant(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"tenant_frozen\",\"error_description\":\"tenant has been frozen\"}");
    }

    private boolean validateBearerPermissionsVersion(HttpServletRequest request,
                                                     HttpServletResponse response,
                                                     Long activeTenantId,
                                                     String scopeType,
                                                     Long scopeId) throws IOException {
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
            activeTenantId,
            scopeType,
            scopeId
        );
        if (currentPermissionsVersion == null || !currentPermissionsVersion.equals(bearerClaims.permissionsVersion())) {
            rejectStalePermissions(response);
            return false;
        }
        return true;
    }

    private boolean validateSessionPermissionsVersion(HttpServletRequest request,
                                                      HttpServletResponse response,
                                                      Long activeTenantId,
                                                      String scopeType,
                                                      Long scopeId) throws IOException {
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

        String currentPermissionsVersion = permissionVersionService.resolvePermissionsVersion(
            userId,
            activeTenantId,
            scopeType,
            scopeId
        );
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

    private enum ActiveScopeFailureReason {
        MISSING_SCOPE_ID,
        REPOS_MISSING,
        NO_TENANT,
        UNIT_NOT_IN_TENANT,
        TYPE_MISMATCH,
        NO_USER_CONTEXT,
        NOT_MEMBER,
        /** Bearer 与 Session 对 active scope 的成对断言不一致 */
        SCOPE_SOURCE_CONFLICT,
        /** JWT 带 activeScopeId 但未带可解析的 activeScopeType（禁止与 Session 拼单） */
        ORPHAN_SCOPE_CLAIM,
        /** JWT activeScopeType 显式出现但值非 PLATFORM/TENANT/ORG/DEPT */
        UNSUPPORTED_EXPLICIT_SCOPE
    }

    private record ScopedAuthResolution(
        String scopeType,
        Long scopeId,
        boolean bearerPresent,
        ActiveScopeFailureReason failureReason
    ) {
        static ScopedAuthResolution ok(String scopeType, Long scopeId, boolean bearerPresent) {
            return new ScopedAuthResolution(scopeType, scopeId, bearerPresent, null);
        }

        static ScopedAuthResolution invalid(boolean bearerPresent, ActiveScopeFailureReason reason) {
            return new ScopedAuthResolution(null, null, bearerPresent, reason);
        }

        boolean valid() {
            return failureReason == null;
        }
    }
}
