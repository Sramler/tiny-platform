package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.FrontendProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 授权端点缺少 MFA 因子时的跳转入口。
 *
 * <p>当授权层判定缺少 {@code FACTOR_TOTP} 时：
 * <ul>
 *   <li>强制 MFA 且尚未绑定/激活 TOTP：跳转绑定页</li>
 *   <li>其他需要补 TOTP 的场景：跳转验证页</li>
 * </ul>
 * </p>
 */
public final class AuthorizationEndpointMfaEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationEndpointMfaEntryPoint.class);

    private final FrontendProperties frontendProperties;

    public AuthorizationEndpointMfaEntryPoint(FrontendProperties frontendProperties) {
        this.frontendProperties = frontendProperties;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean bindRequired = Boolean.TRUE.equals(
                request.getAttribute(AuthorizationEndpointMfaAuthorizationManager.BIND_REQUIRED_ATTRIBUTE)
        );
        String originalUrl = buildOriginalUrl(request);
        String redirectUrl = buildRedirectUrl(bindRequired, originalUrl);

        if (bindRequired) {
            log.info("[MFA] /oauth2/authorize 当前用户尚未绑定/激活 TOTP，重定向到 {}", redirectUrl);
        } else {
            log.info("[MFA] /oauth2/authorize 当前用户缺少 TOTP 因子，重定向到 {}", redirectUrl);
        }
        response.sendRedirect(redirectUrl);
    }

    private String buildRedirectUrl(boolean bindRequired, String originalUrl) {
        String configured = bindRequired ? this.frontendProperties.getTotpBindUrl() : this.frontendProperties.getTotpVerifyUrl();
        String fallback = bindRequired ? "/self/security/totp-bind" : "/self/security/totp-verify";
        String target = (configured == null || configured.isBlank()) ? fallback : configured;
        String base = target.startsWith("redirect:") ? target.substring("redirect:".length()) : target;
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "redirect="
                + URLEncoder.encode(originalUrl, StandardCharsets.UTF_8);
    }

    private String buildOriginalUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder(request.getRequestURI());
        String query = request.getQueryString();
        if (query != null && !query.isEmpty()) {
            url.append('?').append(query);
        }
        return url.toString();
    }
}
