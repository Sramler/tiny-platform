package com.tiny.platform.core.oauth.config;

import com.tiny.platform.core.oauth.security.LoginFailurePolicy;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.core.util.IpUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 自定义登录失败处理器
 * 记录登录失败次数和时间
 */
public class CustomLoginFailureHandler implements AuthenticationFailureHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomLoginFailureHandler.class);

    private final UserRepository userRepository;
    private final AuthenticationAuditService auditService;
    private final LoginFailurePolicy loginFailurePolicy;
    private final SimpleUrlAuthenticationFailureHandler defaultHandler;

    public CustomLoginFailureHandler(UserRepository userRepository,
                                     AuthenticationAuditService auditService,
                                     LoginFailurePolicy loginFailurePolicy) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.loginFailurePolicy = loginFailurePolicy;
        this.defaultHandler = new SimpleUrlAuthenticationFailureHandler("/login?error=true");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        String username = request.getParameter("username");
        String authProvider = request.getParameter("authenticationProvider");
        String authType = request.getParameter("authenticationType");

        if (username != null && !username.isBlank()) {
            logger.info("用户 {} 登录失败，provider={}, type={}, reason={}",
                    username,
                    authProvider != null ? authProvider : "LOCAL",
                    authType != null ? authType : "PASSWORD",
                    exception.getMessage());
        }

        if (username != null && !username.isBlank()) {
            try {
                // 尝试查找用户并记录失败登录信息
                Long tenantId = TenantContext.getTenantId();
                var userOpt = tenantId != null
                        ? userRepository.findUserByUsernameAndTenantId(username, tenantId)
                        : java.util.Optional.<User>empty();
                if (tenantId != null) {
                    userOpt.ifPresent(user -> {
                        recordFailedLogin(user, request, exception);
                        // 记录登录失败审计
                        auditService.recordLoginFailure(
                            user.getUsername(), 
                            user.getId(), 
                            authProvider != null ? authProvider : "LOCAL",
                            authType != null ? authType : "PASSWORD",
                            request
                        );
                    });
                }
                
                // 如果用户不存在，也记录审计（userId为null）
                if (tenantId == null || userOpt.isEmpty()) {
                    auditService.recordLoginFailure(
                        username,
                        null,
                        authProvider != null ? authProvider : "LOCAL",
                        authType != null ? authType : "PASSWORD",
                        request
                    );
                }
            } catch (Exception e) {
                // 记录失败信息错误不应该影响登录失败流程
                logger.warn("记录用户 {} 登录失败信息异常: {}", username, e.getMessage());
                // 即使出错也尝试记录审计
                try {
                    auditService.recordLoginFailure(
                        username,
                        null,
                        authProvider != null ? authProvider : "LOCAL",
                        authType != null ? authType : "PASSWORD",
                        request
                    );
                } catch (Exception auditException) {
                    logger.error("记录登录失败审计异常: {}", auditException.getMessage());
                }
            }
        }

        // 使用默认处理器进行重定向
        defaultHandler.onAuthenticationFailure(request, response, exception);
    }

    /**
     * 记录登录失败信息（失败次数和时间）
     */
    private void recordFailedLogin(User user, HttpServletRequest request, AuthenticationException exception) {
        try {
            if (exception instanceof LockedException) {
                logger.debug("用户 {} 当前处于锁定状态，本次失败不再累计计数", user.getUsername());
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            loginFailurePolicy.recordFailure(user, now);
            
            userRepository.save(user);
            
            logger.debug("用户 {} 登录失败已记录: 失败次数={}, 时间={}, IP={}", 
                    user.getUsername(), user.getFailedLoginCount(), now, IpUtils.getClientIp(request));
        } catch (Exception e) {
            logger.warn("记录用户 {} 登录失败信息失败: {}", user.getUsername(), e.getMessage(), e);
        }
    }
}
