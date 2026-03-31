package com.tiny.platform.core.oauth.session;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 服务端会话登记表，用于当前登录用户会话管理。
 */
@Entity
@Table(name = "user_session", indexes = {
    @Index(name = "idx_user_session_user_tenant_status", columnList = "user_id, tenant_id, status"),
    @Index(name = "idx_user_session_last_seen", columnList = "last_seen_at")
})
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 128, unique = true)
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UserSessionState status = UserSessionState.ACTIVE;

    @Column(name = "authentication_provider", length = 50)
    private String authenticationProvider;

    @Column(name = "authentication_factor", length = 50)
    private String authenticationFactor;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UserSessionState getStatus() {
        return status;
    }

    public void setStatus(UserSessionState status) {
        this.status = status;
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

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
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
