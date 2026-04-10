package com.tiny.platform.infrastructure.auth.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认证凭证实体：仅承载认证材料，不承载作用域策略。
 */
@Entity
@Table(name = "user_auth_credential")
public class UserAuthCredential implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @Column(name = "authentication_provider", length = 50, nullable = false)
  private String authenticationProvider;

  @Column(name = "authentication_type", length = 50, nullable = false)
  private String authenticationType;

  @Column(name = "authentication_configuration", columnDefinition = "JSON", nullable = false)
  @Convert(converter = com.tiny.platform.infrastructure.core.converter.JsonStringConverter.class)
  private Map<String, Object> authenticationConfiguration;

  @Column(name = "last_verified_at")
  private LocalDateTime lastVerifiedAt;

  @Column(name = "last_verified_ip", length = 50)
  private String lastVerifiedIp;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

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

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
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
