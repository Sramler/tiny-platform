package com.tiny.platform.infrastructure.auth.user.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户认证审计实体
 * 记录登录、登出、MFA绑定、Token颁发等认证相关事件
 */
@Entity
@Table(name = "user_authentication_audit", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_user_auth_audit_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_username", columnList = "username"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_success", columnList = "success")
})
public class UserAuthenticationAudit implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "tenant_id")
    private Long tenantId;

    /**
     * 租户解析结果码：
     * - resolved: 成功解析 tenantId
     * - tenant_context_missing: 当前请求未解析到租户上下文
     */
    @Column(name = "tenant_resolution_code", length = 64)
    private String tenantResolutionCode;

    /**
     * 租户解析来源：
     * - token: 来自 JWT token
     * - session: 来自会话冻结上下文
     * - login_param: 来自登录/授权入口参数（预认证阶段）
     * - unknown: 无法识别来源
     */
    @Column(name = "tenant_resolution_source", length = 64)
    private String tenantResolutionSource;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    /**
     * 事件类型：LOGIN, LOGOUT, MFA_BIND, MFA_UNBIND, TOKEN_ISSUE, TOKEN_REVOKE, PASSWORD_CHANGE, etc.
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "success", nullable = false)
    private Boolean success = true;

    @Column(name = "authentication_provider", length = 50)
    private String authenticationProvider;

    @Column(name = "authentication_factor", length = 50)
    private String authenticationFactor;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "token_id", length = 128)
    private String tokenId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantResolutionCode() {
        return tenantResolutionCode;
    }

    public void setTenantResolutionCode(String tenantResolutionCode) {
        this.tenantResolutionCode = tenantResolutionCode;
    }

    public String getTenantResolutionSource() {
        return tenantResolutionSource;
    }

    public void setTenantResolutionSource(String tenantResolutionSource) {
        this.tenantResolutionSource = tenantResolutionSource;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getAuthenticationProvider() {
        return authenticationProvider;
    }

    public void setAuthenticationProvider(String authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
    }

    public String getAuthenticationFactor() {
        return authenticationFactor;
    }

    public void setAuthenticationFactor(String authenticationFactor) {
        this.authenticationFactor = authenticationFactor;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
