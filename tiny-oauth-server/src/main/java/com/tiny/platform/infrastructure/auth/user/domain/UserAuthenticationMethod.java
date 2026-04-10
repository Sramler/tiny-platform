package com.tiny.platform.infrastructure.auth.user.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认证方式运行时载体（内存投影 / 写回桥接），不再映射数据库表 {@code user_authentication_method}。
 *
 * <p>材料与策略的真源为 {@code user_auth_credential} 与 {@code user_auth_scope_policy}。</p>
 */
public class UserAuthenticationMethod implements Serializable {

    private Long id;

    private Long userId;

    /**
     * 租户作用域载体；{@code null} 表示全局/平台侧 carrier（与 {@link #runtimeScopeType} 配合解释）。
     */
    private Long tenantId;

    private String authenticationProvider;

    private String authenticationType;

    private Map<String, Object> authenticationConfiguration;

    private Boolean isPrimaryMethod = false;

    private Boolean isMethodEnabled = true;

    private Integer authenticationPriority = 0;

    private LocalDateTime lastVerifiedAt;

    private String lastVerifiedIp;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime expiresAt;

    /**
     * 运行时作用域元数据：用于桥接写回新模型，不对应旧表列。
     */
    private String runtimeScopeType;

    private String runtimeScopeKey;

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

    public String getRuntimeScopeType() {
        return runtimeScopeType;
    }

    public void setRuntimeScopeType(String runtimeScopeType) {
        this.runtimeScopeType = runtimeScopeType;
    }

    public String getRuntimeScopeKey() {
        return runtimeScopeKey;
    }

    public void setRuntimeScopeKey(String runtimeScopeKey) {
        this.runtimeScopeKey = runtimeScopeKey;
    }
}
