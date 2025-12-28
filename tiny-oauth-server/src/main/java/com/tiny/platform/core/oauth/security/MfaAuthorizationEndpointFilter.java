package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.FrontendProperties;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.core.oauth.service.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 针对 OIDC 授权端点的 MFA 强制过滤器。
 * <p>
 * 职责：
 * <ul>
 *   <li>拦截对 {@code /oauth2/authorize} 的请求</li>
 *   <li>根据 {@code security.mfa.mode} 和用户 TOTP 绑定状态判断本次会话是否必须完成 TOTP</li>
 *   <li>如果本次需要 TOTP 且当前 Authentication 未完成 TOTP，则重定向到 TOTP 验证页</li>
 *   <li>只有当本次所需因子全部完成时，才允许进入授权端点，确保发出的 Access/ID Token 中的 {@code amr} 能准确反映认证因子</li>
 * </ul>
 *
 * 这属于“思路 A”在 OAuth2/OIDC 授权层的落地：先完成本地 MFA，再进入授权码流程。
 */
public class MfaAuthorizationEndpointFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MfaAuthorizationEndpointFilter.class);

    private final SecurityService securityService;
    private final UserRepository userRepository;
    private final FrontendProperties frontendProperties;

    public MfaAuthorizationEndpointFilter(SecurityService securityService,
                                          UserRepository userRepository,
                                          FrontendProperties frontendProperties) {
        this.securityService = securityService;
        this.userRepository = userRepository;
        this.frontendProperties = frontendProperties;
    }

    @Override
    protected boolean shouldNotFilter(@org.springframework.lang.NonNull jakarta.servlet.http.HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 仅拦截 /oauth2/authorize（授权端点），其他请求不处理
        return uri == null || !uri.startsWith("/oauth2/authorize");
    }

    @Override
    protected void doFilterInternal(@org.springframework.lang.NonNull jakarta.servlet.http.HttpServletRequest request,
                                    @org.springframework.lang.NonNull jakarta.servlet.http.HttpServletResponse response,
                                    @org.springframework.lang.NonNull jakarta.servlet.FilterChain filterChain) throws jakarta.servlet.ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            // 完全未登录，由 Spring Security 走默认登录流程（展示登录页等）
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ 关键点：支持“部分认证”的 MultiFactorAuthenticationToken
        // - 对于 mfaToken，只要已经完成了至少一个因子（例如 PASSWORD），就认为可以参与 MFA 策略判断
        // - 对于其他类型的 Authentication，则仍然要求 isAuthenticated()==true
        boolean isMfaTokenWithFactors = false;
        if (authentication instanceof MultiFactorAuthenticationToken mfaToken) {
            isMfaTokenWithFactors = !mfaToken.getCompletedFactors().isEmpty();
            if (log.isDebugEnabled()) {
                log.debug("[MFA] /oauth2/authorize 当前认证为 MultiFactorAuthenticationToken, completedFactors={}, authenticated={}",
                        mfaToken.getCompletedFactors(), mfaToken.isAuthenticated());
            }
        }

        if (!authentication.isAuthenticated() && !isMfaTokenWithFactors) {
            // 对于非 MFA Token 的未认证请求，继续让 Spring Security 处理（例如首次访问授权端点时还未登录）
            filterChain.doFilter(request, response);
            return;
        }

        // 解析当前用户
        String username;
        if (authentication instanceof MultiFactorAuthenticationToken mfaToken) {
            username = mfaToken.getUsername();
        } else {
            username = authentication.getName();
        }
        if (username == null || username.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = userRepository.findUserByUsername(username).orElse(null);
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 读取安全状态，判断本次会话是否需要绑定 / 必须 TOTP
        Map<String, Object> status = securityService.getSecurityStatus(user);
        boolean totpBound = Boolean.TRUE.equals(status.get("totpBound"));
        boolean totpActivated = Boolean.TRUE.equals(status.get("totpActivated"));
        boolean forceMfa = Boolean.TRUE.equals(status.get("forceMfa")); // REQUIRED => 必须绑定 + 必须验证
        boolean requireTotp = Boolean.TRUE.equals(status.get("requireTotp"));
        if (log.isDebugEnabled()) {
            log.debug("[MFA] /oauth2/authorize 拦截检查 - userId={}, username={}, totpBound={}, totpActivated={}, forceMfa={}, requireTotp={}",
                    user.getId(), username, totpBound, totpActivated, forceMfa, requireTotp);
        }

        // 1) 全局强制 MFA（mode=REQUIRED），但当前用户尚未完成 TOTP 绑定/激活 ⇒ 先去绑定页面
        if (forceMfa && (!totpBound || !totpActivated)) {
            String originalUrl = buildOriginalUrl(request);
            String encodedRedirect = URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);

            String totpBindUrl = frontendProperties.getTotpBindUrl();
            if (totpBindUrl == null || totpBindUrl.isBlank()) {
                // 兜底：使用服务器端 TOTP 绑定页
                totpBindUrl = "/self/security/totp-bind";
            }

            String redirectUrl;
            if (totpBindUrl.startsWith("redirect:")) {
                String base = totpBindUrl.substring("redirect:".length());
                String separator = base.contains("?") ? "&" : "?";
                redirectUrl = base + separator + "redirect=" + encodedRedirect;
            } else {
                String separator = totpBindUrl.contains("?") ? "&" : "?";
                redirectUrl = totpBindUrl + separator + "redirect=" + encodedRedirect;
            }

            log.info("[MFA] 用户 {} 处于强制 MFA 模式但尚未绑定/激活 TOTP (bound={}, activated={})，拦截 /oauth2/authorize，重定向到绑定页 {}",
                    username, totpBound, totpActivated, redirectUrl);
            response.sendRedirect(redirectUrl);
            return;
        }

        // 2) 本次会话不要求 TOTP ⇒ 直接进入授权端点（mode=NONE 或 OPTIONAL+未绑定等）
        if (!requireTotp) {
            filterChain.doFilter(request, response);
            return;
        }

        // 需要 TOTP：检查当前 Authentication 是否已经完成 TOTP 因子
        boolean totpCompleted = false;
        if (authentication instanceof MultiFactorAuthenticationToken mfaToken) {
            totpCompleted = mfaToken.hasCompletedFactor(MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP);
            if (log.isDebugEnabled()) {
                log.debug("[MFA] 当前 MultiFactorAuthenticationToken.completedFactors={} (user={})",
                        mfaToken.getCompletedFactors(), username);
            }
        }

        if (totpCompleted) {
            // 已完成 TOTP，允许进入授权端点
            log.info("[MFA] 用户 {} 已完成 TOTP，本次允许进入 /oauth2/authorize", username);
            filterChain.doFilter(request, response);
            return;
        }

        // 本次会话需要 TOTP，但当前认证未完成 TOTP ⇒ 重定向到前端 TOTP 验证页
        String originalUrl = buildOriginalUrl(request);
        String encodedRedirect = URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);

        String totpVerifyUrl = frontendProperties.getTotpVerifyUrl();
        if (totpVerifyUrl == null || totpVerifyUrl.isBlank()) {
            // 兜底：使用服务器端 TOTP 验证页
            totpVerifyUrl = "/self/security/totp-verify";
        }

        String redirectUrl;
        if (totpVerifyUrl.startsWith("redirect:")) {
            String base = totpVerifyUrl.substring("redirect:".length());
            String separator = base.contains("?") ? "&" : "?";
            redirectUrl = base + separator + "redirect=" + encodedRedirect;
        } else {
            String separator = totpVerifyUrl.contains("?") ? "&" : "?";
            redirectUrl = totpVerifyUrl + separator + "redirect=" + encodedRedirect;
        }

        log.info("用户 {} 本次会话需要 TOTP，但当前未完成，拦截 /oauth2/authorize，重定向到 {}", username, redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    private String buildOriginalUrl(jakarta.servlet.http.HttpServletRequest request) {
        StringBuilder url = new StringBuilder(request.getRequestURI());
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            url.append('?').append(query);
        }
        return url.toString();
    }
}
