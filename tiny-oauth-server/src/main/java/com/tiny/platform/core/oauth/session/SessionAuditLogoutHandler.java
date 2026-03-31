package com.tiny.platform.core.oauth.session;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 注销时同步写认证审计，并关闭当前服务端会话登记。
 */
public class SessionAuditLogoutHandler implements LogoutHandler {

    private final UserSessionService userSessionService;
    private final AuthenticationAuditService authenticationAuditService;

    public SessionAuditLogoutHandler(UserSessionService userSessionService,
                                     AuthenticationAuditService authenticationAuditService) {
        this.userSessionService = userSessionService;
        this.authenticationAuditService = authenticationAuditService;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        HttpSession session = request != null ? request.getSession(false) : null;
        if (session != null) {
            userSessionService.markLoggedOut(session.getId());
        }
        if (authentication == null) {
            return;
        }
        authenticationAuditService.recordLogout(authentication.getName(), resolveUserId(authentication), request);
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        Object details = authentication.getDetails();
        if (details instanceof SecurityUser securityUser) {
            return securityUser.getUserId();
        }
        return null;
    }
}
