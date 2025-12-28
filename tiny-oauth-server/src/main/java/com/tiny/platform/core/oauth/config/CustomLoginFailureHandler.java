package com.tiny.platform.core.oauth.config;

import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.infrastructure.core.util.IpUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
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
    private final SimpleUrlAuthenticationFailureHandler defaultHandler;

    public CustomLoginFailureHandler(UserRepository userRepository, AuthenticationAuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
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
                userRepository.findUserByUsername(username).ifPresent(user -> {
                    recordFailedLogin(user, request);
                    // 记录登录失败审计
                    auditService.recordLoginFailure(
                        user.getUsername(), 
                        user.getId(), 
                        authProvider != null ? authProvider : "LOCAL",
                        authType != null ? authType : "PASSWORD",
                        request
                    );
                });
                
                // 如果用户不存在，也记录审计（userId为null）
                if (userRepository.findUserByUsername(username).isEmpty()) {
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
    private void recordFailedLogin(User user, HttpServletRequest request) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Integer currentCount = user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0;
            
            user.setFailedLoginCount(currentCount + 1);
            user.setLastFailedLoginAt(now);
            
            userRepository.save(user);
            
            logger.debug("用户 {} 登录失败已记录: 失败次数={}, 时间={}, IP={}", 
                    user.getUsername(), user.getFailedLoginCount(), now, IpUtils.getClientIp(request));
        } catch (Exception e) {
            logger.warn("记录用户 {} 登录失败信息失败: {}", user.getUsername(), e.getMessage(), e);
        }
    }
}
