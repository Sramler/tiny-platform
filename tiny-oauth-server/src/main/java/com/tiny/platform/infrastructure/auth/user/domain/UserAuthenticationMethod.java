package com.tiny.platform.infrastructure.auth.user.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户认证方法实体
 * 支持多种认证方式：LOCAL + PASSWORD, LOCAL + TOTP, GITHUB, GOOGLE 等
 */
@Entity
@Table(name = "user_authentication_method")
public class UserAuthenticationMethod implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /**
     * 认证提供者：LOCAL, GITHUB, GOOGLE, LDAP 等
     */
    @Column(name = "authentication_provider", length = 50, nullable = false)
    private String authenticationProvider;

    /**
     * 认证类型：PASSWORD, TOTP, OAUTH2 等
     */
    @Column(name = "authentication_type", length = 50, nullable = false)
    private String authenticationType;

    /**
     * 认证配置 JSON 数据
     * 例如：
     * - PASSWORD: {"password": "{bcrypt}$2a$10$..."}
     * - TOTP: {"secret": "...", "issuer": "..."}
     */
    @Column(name = "authentication_configuration", columnDefinition = "JSON", nullable = false)
    @Convert(converter = com.tiny.platform.infrastructure.core.converter.JsonStringConverter.class)
    private Map<String, Object> authenticationConfiguration;

    /**
     * 是否为主要认证方法
     */
    @Column(name = "is_primary_method")
    private Boolean isPrimaryMethod = false;

    /**
     * 认证方法是否启用
     */
    @Column(name = "is_method_enabled")
    private Boolean isMethodEnabled = true;

    /**
     * 认证优先级，数字越小优先级越高
     */
    @Column(name = "authentication_priority")
    private Integer authenticationPriority = 0;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Column(name = "last_verified_ip", length = 50)
    private String lastVerifiedIp;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getAuthenticationProvider() {
        return authenticationProvider;
    }

    public void setAuthenticationProvider(String authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
    }

    public String getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(String authenticationType) {
        this.authenticationType = authenticationType;
    }

    public Map<String, Object> getAuthenticationConfiguration() {
        return authenticationConfiguration;
    }

    public void setAuthenticationConfiguration(Map<String, Object> authenticationConfiguration) {
        this.authenticationConfiguration = authenticationConfiguration;
    }

    public Boolean getIsPrimaryMethod() {
        return isPrimaryMethod;
    }

    public void setIsPrimaryMethod(Boolean isPrimaryMethod) {
        this.isPrimaryMethod = isPrimaryMethod;
    }

    public Boolean getIsMethodEnabled() {
        return isMethodEnabled;
    }

    public void setIsMethodEnabled(Boolean isMethodEnabled) {
        this.isMethodEnabled = isMethodEnabled;
    }

    public Integer getAuthenticationPriority() {
        return authenticationPriority;
    }

    public void setAuthenticationPriority(Integer authenticationPriority) {
        this.authenticationPriority = authenticationPriority;
    }

    public LocalDateTime getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public void setLastVerifiedAt(LocalDateTime lastVerifiedAt) {
        this.lastVerifiedAt = lastVerifiedAt;
    }

    public String getLastVerifiedIp() {
        return lastVerifiedIp;
    }

    public void setLastVerifiedIp(String lastVerifiedIp) {
        this.lastVerifiedIp = lastVerifiedIp;
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

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
