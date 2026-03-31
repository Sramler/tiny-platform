package com.tiny.platform.core.oauth.service;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationAudit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 认证审计服务接口
 */
public interface AuthenticationAuditService {

    /**
     * 记录认证事件
     */
    void recordEvent(String username, Long userId, String eventType, boolean success,
                    String authenticationProvider, String authenticationFactor,
                    HttpServletRequest request);

    /**
     * 记录登录成功事件
     */
    void recordLoginSuccess(String username, Long userId, String authenticationProvider, 
                           String authenticationFactor, HttpServletRequest request);

    /**
     * 记录登录失败事件
     */
    void recordLoginFailure(String username, Long userId, String authenticationProvider,
                           String authenticationFactor, HttpServletRequest request);

    /**
     * 记录登出事件
     */
    void recordLogout(String username, Long userId, HttpServletRequest request);

    /**
     * 记录MFA绑定事件
     */
    void recordMfaBind(String username, Long userId, String authenticationFactor, 
                      HttpServletRequest request);

    /**
     * 记录MFA解绑事件
     */
    void recordMfaUnbind(String username, Long userId, String authenticationFactor,
                        HttpServletRequest request);

    /**
     * 记录Token颁发事件
     */
    void recordTokenIssue(String username, Long userId, String tokenId, 
                         HttpServletRequest request);

    /**
     * 记录Token撤销事件
     */
    void recordTokenRevoke(String username, Long userId, String tokenId,
                          HttpServletRequest request);

    /**
     * 记录服务端会话被强制下线事件。
     */
    void recordSessionRevoke(String username, Long userId, String sessionId,
                             HttpServletRequest request);

    /**
     * 分页查询认证审计记录。
     */
    Page<UserAuthenticationAudit> search(AuthenticationAuditQuery query, Pageable pageable);

    /**
     * 统计认证审计概览。
     */
    AuthenticationAuditSummary summarize(AuthenticationAuditQuery query);

    /**
     * 查询当前用户的登录历史。
     */
    Page<UserAuthenticationAudit> listCurrentUserLoginHistory(Long userId, Pageable pageable);
}
