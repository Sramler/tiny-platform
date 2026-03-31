package com.tiny.platform.core.oauth.config;

import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.security.RedirectPathSanitizer;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.core.oauth.tenant.IssuerTenantSupport;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.core.util.IpUtils;
import com.tiny.platform.infrastructure.core.util.DeviceUtils;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

public class CustomLoginSuccessHandler implements AuthenticationSuccessHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomLoginSuccessHandler.class);
    private static final String SESSION_ACTIVE_TENANT_ID_KEY = TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY;

    private final SecurityService securityService;
    private final UserRepository userRepository;
    private final AuthUserResolutionService authUserResolutionService;
    private final FrontendProperties frontendProperties;
    private final MultiFactorAuthenticationSessionManager sessionManager;
    private final AuthenticationAuditService auditService;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public CustomLoginSuccessHandler(SecurityService securityService, 
                                    UserRepository userRepository,
                                    FrontendProperties frontendProperties,
                                    MultiFactorAuthenticationSessionManager sessionManager,
                                    AuthUserResolutionService authUserResolutionService,
                                    AuthenticationAuditService auditService) {
        this.securityService = securityService;
        this.userRepository = userRepository;
        this.authUserResolutionService = authUserResolutionService;
        this.frontendProperties = frontendProperties;
        this.sessionManager = sessionManager;
        this.auditService = auditService;
    }

    @Override
    /**
     * 登录成功后处理流程：
     * 1. 获取当前登录用户名，查对应用户实例。
     * 2. 解析原意图跳转 URL（优先 SavedRequest，其次 redirect 参数，且统一经 RedirectPathSanitizer 收口）。
     * 3. 拉取用户当前 MFA 状态（是否已绑定、激活、本次会话是否要求 TOTP、是否允许跳过提醒）。
     * 5. 跳转策略：
     *    - 完全关闭 MFA：直接跳原意图页面。
     *    - partial MFA token（authenticated=false）且仅具备 PASSWORD factor authority、同时 requireTotp=true：跳转到 TOTP 验证页面。
     *    - 已绑定但未激活：统一跳转至 totp-bind 继续绑定流程。
     *    - 未绑定且当前允许提醒：跳转至 totp-bind。
     *    - 已完成所需因子或本次无需 TOTP：跳回原意图页面。
     * 6. 只有本次会话确实 requireTotp 时，才升级为 fully authenticated 会话，避免把 OPTIONAL/NONE 场景错误补成 MFA。
     */
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String username = authentication.getName();
        Long activeTenantId = ActiveTenantResponseSupport.resolveActiveTenantId(authentication);
        String activeScopeType = resolveActiveScopeType();
        User user = resolveUserByScope(username, activeTenantId, activeScopeType);
        if (user == null) {
            response.sendRedirect("/");
            return;
        }

        freezeActiveContext(request, activeTenantId, activeScopeType);
        
        Map<String, Object> status = securityService.getSecurityStatus(user);
        boolean totpBound = Boolean.TRUE.equals(status.get("totpBound"));
        boolean totpActivated = Boolean.TRUE.equals(status.get("totpActivated"));
        boolean disableMfa = Boolean.TRUE.equals(status.get("disableMfa"));
        boolean skipMfaRemind = Boolean.TRUE.equals(status.get("skipMfaRemind"));
        boolean forceMfa = Boolean.TRUE.equals(status.get("forceMfa"));
        boolean requireTotp = Boolean.TRUE.equals(status.get("requireTotp"));

        // 解析原意图跳转 URL。这里只允许站内相对路径或内部授权端点路径，
        // 外部 redirect_uri 只能由后续 /oauth2/authorize 标准流程决定。
        String intendedUrl = extractIntendedUrl(request, response);
        if (intendedUrl == null || intendedUrl.isBlank()) intendedUrl = "/";
        if ("/login".equals(intendedUrl) || intendedUrl.startsWith("/login?")) {
            intendedUrl = "/";
        }
        String encodedUrl = URLEncoder.encode(intendedUrl, StandardCharsets.UTF_8);

        // 提取认证提供者和因子类型
        String authProvider = extractAuthenticationProvider(authentication);
        String authFactor = extractAuthenticationFactor(authentication);

        // 1️⃣ 完全关闭 MFA，直接跳转
        if (disableMfa) {
            logger.info("用户 {} 登录成功（MFA 已关闭），将跳转 {}", user.getUsername(), intendedUrl);
            // 记录登录IP和登录时间
            recordLoginInfo(user, request);
            // 记录登录成功审计
            auditService.recordLoginSuccess(user.getUsername(), user.getId(), authProvider, authFactor, request);
            // mode=NONE 时不应人为补全 TOTP 因子，保留 PASSWORD-only 认证结果，
            // 以确保 JWT amr 与实际认证流程一致（仅 password）。
            redirectToIntendedUrl(intendedUrl, request, response);
            return;
        }

        // 2️⃣ 检查是否仅具备 PASSWORD factor authority、仍需继续 TOTP challenge
        if (authentication instanceof MultiFactorAuthenticationToken) {
            if (AuthenticationFactorAuthorities.hasFactor(authentication, MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD) &&
                !AuthenticationFactorAuthorities.hasFactor(authentication, MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP) &&
                totpActivated &&
                requireTotp) {
                logger.info("用户 {} 已完成密码验证，但还需 TOTP 验证，跳转 TOTP 验证页", user.getUsername());
                String totpVerifyUrl = buildFrontendUrl(frontendProperties.getTotpVerifyUrl(), request, "redirect", encodedUrl);
                redirectToFrontend(totpVerifyUrl, request, response);
                return;
            }
        }

        // 3️⃣ 已绑定但未激活 TOTP → 跳转绑定页面
        if (totpBound && !totpActivated) {
            logger.info("用户 {} 已绑定 TOTP 但未激活，跳转 TOTP 绑定页", user.getUsername());
            String totpBindUrl = buildFrontendUrl(frontendProperties.getTotpBindUrl(), request, "redirect", encodedUrl);
            redirectToFrontend(totpBindUrl, request, response);
            return;
        }

        // 4️⃣ 未绑定 TOTP 且未跳过或系统强制 MFA → 跳转绑定页面
        if (!totpBound && !disableMfa && (forceMfa || !skipMfaRemind)) {
            logger.info("用户 {} 未绑定 TOTP，跳转 TOTP 绑定页 (forceMfa={}, skipMfaRemind={})",
                    user.getUsername(), forceMfa, skipMfaRemind);
            String totpBindUrl = buildFrontendUrl(frontendProperties.getTotpBindUrl(), request, "redirect", encodedUrl);
            redirectToFrontend(totpBindUrl, request, response);
            return;
        }

        // 5️⃣ 已完成所有认证或无需 MFA → 升级为完全认证并跳转目标页面
        // 记录登录IP和登录时间
        recordLoginInfo(user, request);
        // 记录登录成功审计
        auditService.recordLoginSuccess(user.getUsername(), user.getId(), authProvider, authFactor, request);
        logger.info("用户 {} 登录成功（MFA 校验完成或不需要 MFA），将跳转 {}", user.getUsername(), intendedUrl);

        // 仅在本次会话确实要求 TOTP 时，才升级为“完整 MFA 认证”状态。
        // OPTIONAL / NONE 下不应无条件补上 TOTP 因子，否则 amr 会错误包含 totp。
        if (requireTotp) {
            sessionManager.promoteToFullyAuthenticated(user, request, response);
        }
        redirectToIntendedUrl(intendedUrl, request, response);
    }

    private void redirectToIntendedUrl(String intendedUrl, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        if (isBackendOnlyPath(intendedUrl)) {
            response.sendRedirect(intendedUrl);
            return;
        }

        if (intendedUrl.startsWith("/") && !intendedUrl.startsWith("/api/") && !intendedUrl.startsWith("/oauth2/")) {
            // 前端路由处理
            String loginUrl = frontendProperties.getLoginUrl();
            if (loginUrl.startsWith("redirect:")) {
                String baseUrl = loginUrl.substring("redirect:".length());
                String devServerBase = baseUrl.substring(0, baseUrl.indexOf("/", baseUrl.indexOf("://") + 3));
                String redirectUrl = devServerBase + intendedUrl;
                logger.info("开发环境重定向前端路由: {}", redirectUrl);
                response.sendRedirect(redirectUrl);
                return;
            } else {
                try {
                    RequestDispatcher dispatcher = request.getRequestDispatcher("/dist/index.html");
                    dispatcher.forward(request, response);
                    return;
                } catch (Exception e) {
                    logger.warn("前端页面转发失败，使用重定向: {}", e.getMessage());
                }
            }
        }

        // 默认重定向
        response.sendRedirect(intendedUrl);
    }

    private boolean isBackendOnlyPath(String path) {
        if (path == null || path.isBlank() || !path.startsWith("/")) {
            return false;
        }
        return path.startsWith("/oauth2/")
                || path.matches("^/[a-z0-9][a-z0-9-]{1,31}/oauth2/.*$")
                || path.startsWith("/login")
                || path.startsWith("/logout")
                || path.startsWith("/error")
                || path.startsWith("/actuator")
                || path.startsWith("/self/security/")
                || IssuerTenantSupport.isAuthorizationServerEndpointPath(path);
    }

    private String extractIntendedUrl(HttpServletRequest request, HttpServletResponse response) {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null && savedRequest.getRedirectUrl() != null) {
            return RedirectPathSanitizer.sanitize(savedRequest.getRedirectUrl(), request);
        }
        String redirect = request.getParameter("redirect");
        return RedirectPathSanitizer.sanitize(redirect, request);
    }

    private String buildFrontendUrl(String configuredUrl, HttpServletRequest request, String paramName, String paramValue) {
        if (configuredUrl.startsWith("redirect:")) {
            StringBuilder url = new StringBuilder(configuredUrl);
            url.append(configuredUrl.contains("?") ? "&" : "?");
            url.append(paramName).append("=").append(paramValue);
            return url.toString();
        }
        return configuredUrl; // forward 默认返回
    }

    private void redirectToFrontend(String url, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        if (url.startsWith("redirect:")) {
            response.sendRedirect(url.substring("redirect:".length()));
        } else if (url.startsWith("forward:")) {
            RequestDispatcher dispatcher = request.getRequestDispatcher(url.substring("forward:".length()));
            dispatcher.forward(request, response);
        } else {
            response.sendRedirect(url);
        }
    }

    /**
     * 从 Authentication 对象中提取认证提供者
     */
    private String extractAuthenticationProvider(Authentication authentication) {
        if (authentication instanceof MultiFactorAuthenticationToken mfaToken) {
            MultiFactorAuthenticationToken.AuthenticationProviderType provider = mfaToken.getProvider();
            return provider != null && provider != MultiFactorAuthenticationToken.AuthenticationProviderType.UNKNOWN
                ? provider.name() 
                : "LOCAL";
        } else if (authentication.getDetails() instanceof com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails details) {
            return details.getAuthenticationProvider() != null ? details.getAuthenticationProvider() : "LOCAL";
        }
        return "LOCAL"; // 默认值
    }

    /**
     * 从 Authentication 对象中提取认证因子类型
     */
    private String extractAuthenticationFactor(Authentication authentication) {
        if (authentication instanceof MultiFactorAuthenticationToken mfaToken) {
            boolean hasPassword = AuthenticationFactorAuthorities.hasFactor(authentication,
                    MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD);
            boolean hasTotp = AuthenticationFactorAuthorities.hasFactor(authentication,
                    MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP);
            if (hasPassword && hasTotp) {
                return "MFA";
            } else if (hasTotp) {
                return "TOTP";
            } else if (hasPassword) {
                return "PASSWORD";
            }
            return mfaToken.getAuthenticationType() != null ? mfaToken.getAuthenticationType() : "PASSWORD";
        } else if (authentication.getDetails() instanceof com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails details) {
            return details.getAuthenticationType() != null ? details.getAuthenticationType() : "PASSWORD";
        }
        return "PASSWORD"; // 默认值
    }

    /**
     * 记录用户登录信息（IP地址、登录时间、设备信息）
     * 登录成功时重置失败登录次数
     */
    private void recordLoginInfo(User user, HttpServletRequest request) {
        try {
            String clientIp = IpUtils.getClientIp(request);
            String deviceInfo = DeviceUtils.getDeviceInfo(request);
            
            user.setLastLoginIp(clientIp);
            user.setLastLoginAt(LocalDateTime.now());
            user.setLastLoginDevice(deviceInfo);
            // 登录成功，重置失败登录次数
            user.setFailedLoginCount(0);
            
            userRepository.save(user);
            logger.debug("用户 {} 登录信息已记录: IP={}, Device={}, Time={}", 
                    user.getUsername(), clientIp, deviceInfo, user.getLastLoginAt());
        } catch (Exception e) {
            // 记录登录信息失败不应该影响登录流程，只记录日志
            logger.warn("记录用户 {} 登录信息失败: {}", user.getUsername(), e.getMessage(), e);
        }
    }

    private void freezeActiveContext(HttpServletRequest request, Long activeTenantId, String activeScopeType) {
        if (request == null) {
            return;
        }
        var session = request.getSession(true);
        if (activeTenantId != null && activeTenantId > 0) {
            session.setAttribute(SESSION_ACTIVE_TENANT_ID_KEY, activeTenantId);
        } else {
            session.removeAttribute(SESSION_ACTIVE_TENANT_ID_KEY);
        }
        String normalizedScopeType = activeScopeType == null || activeScopeType.isBlank()
            ? TenantContextContract.SCOPE_TYPE_TENANT
            : activeScopeType.trim().toUpperCase(java.util.Locale.ROOT);
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, normalizedScopeType);
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equals(normalizedScopeType)) {
            session.removeAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY);
        } else if (activeTenantId != null && activeTenantId > 0) {
            session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, activeTenantId);
        } else {
            session.removeAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY);
        }
    }

    private User resolveUserByScope(String username, Long activeTenantId, String activeScopeType) {
        if (username == null || username.isBlank()) {
            return null;
        }
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equalsIgnoreCase(activeScopeType)) {
            return requireAuthUserResolutionService().resolveUserRecordInPlatform(username).orElse(null);
        }
        if (activeTenantId == null) {
            return null;
        }
        return requireAuthUserResolutionService().resolveUserRecordInActiveTenant(username, activeTenantId).orElse(null);
    }

    private String resolveActiveScopeType() {
        String scopeType = TenantContext.getActiveScopeType();
        if (scopeType == null || scopeType.isBlank()) {
            scopeType = TenantContextContract.SCOPE_TYPE_TENANT;
        }
        return scopeType.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private AuthUserResolutionService requireAuthUserResolutionService() {
        if (authUserResolutionService == null) {
            throw new IllegalStateException("AuthUserResolutionService 未配置");
        }
        return authUserResolutionService;
    }
}
