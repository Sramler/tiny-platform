package com.tiny.platform.core.oauth.session;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.infrastructure.core.util.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 维护服务端活跃会话，并在会话被撤销后阻断后续请求。
 */
@Component
public class UserSessionActivityFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(UserSessionActivityFilter.class);

    private final UserSessionService userSessionService;

    public UserSessionActivityFilter(UserSessionService userSessionService) {
        this.userSessionService = userSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);

        if (session == null || authentication == null || !authentication.isAuthenticated() || authentication instanceof JwtAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        SecurityUser securityUser = resolveSecurityUser(authentication);
        Long activeTenantId = ActiveTenantResponseSupport.resolveActiveTenantId(authentication);
        if (securityUser == null || securityUser.getUserId() == null || activeTenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        SessionTouchRequest touchRequest = new SessionTouchRequest(
            session.getId(),
            securityUser.getUserId(),
            activeTenantId,
            securityUser.getUsername(),
            resolveAuthenticationProvider(authentication),
            resolveAuthenticationFactor(authentication),
            IpUtils.getClientIp(request),
            request.getHeader("User-Agent"),
            LocalDateTime.now(),
            resolveExpiresAt(session)
        );

        UserSessionState state = userSessionService.registerOrTouch(touchRequest);
        if (state != UserSessionState.ACTIVE) {
            logger.info("session blocked because it is no longer active: sessionId={}, state={}", session.getId(), state);
            invalidateSession(request);
            reject(response, state);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private SecurityUser resolveSecurityUser(Authentication authentication) {
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

    private String resolveAuthenticationProvider(Authentication authentication) {
        if (authentication instanceof MultiFactorAuthenticationToken mfaToken && mfaToken.getProvider() != null) {
            return mfaToken.getProvider().name();
        }
        return null;
    }

    private String resolveAuthenticationFactor(Authentication authentication) {
        if (authentication instanceof MultiFactorAuthenticationToken mfaToken) {
            if (mfaToken.getCompletedFactors().contains(MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP)) {
                return "MFA";
            }
            if (mfaToken.getCompletedFactors().contains(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD)) {
                return "PASSWORD";
            }
        }
        return null;
    }

    private LocalDateTime resolveExpiresAt(HttpSession session) {
        if (session == null || session.getMaxInactiveInterval() <= 0) {
            return null;
        }
        return LocalDateTime.now().plusSeconds(session.getMaxInactiveInterval());
    }

    private void invalidateSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ex) {
                logger.debug("session already invalidated while handling session activity filter", ex);
            }
        }
    }

    private void reject(HttpServletResponse response, UserSessionState state) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("""
            {"error":"session_inactive","error_description":"session is no longer active","state":"%s"}
            """.formatted(state.name()));
    }
}
