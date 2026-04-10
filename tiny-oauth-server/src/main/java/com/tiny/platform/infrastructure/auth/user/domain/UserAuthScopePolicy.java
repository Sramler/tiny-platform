package com.tiny.platform.infrastructure.auth.user.domain;

import jakarta.persistence.Column;
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

/**
 * 认证作用域策略实体：仅承载作用域策略，不承载认证材料。
 */
@Entity
@Table(name = "user_auth_scope_policy")
public class UserAuthScopePolicy implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "credential_id", nullable = false)
  private Long credentialId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "credential_id", insertable = false, updatable = false)
  private UserAuthCredential credential;

  @Column(name = "scope_type", length = 16, nullable = false)
  private String scopeType;

  @Column(name = "scope_id")
  private Long scopeId;

  @Column(name = "scope_key", length = 128, nullable = false)
  private String scopeKey;

  @Column(name = "is_primary_method", nullable = false)
  private Boolean isPrimaryMethod = false;

  @Column(name = "is_method_enabled", nullable = false)
  private Boolean isMethodEnabled = true;

  @Column(name = "authentication_priority", nullable = false)
  private Integer authenticationPriority = 0;

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

  public Long getCredentialId() {
    return credentialId;
  }

  public void setCredentialId(Long credentialId) {
    this.credentialId = credentialId;
  }

  public UserAuthCredential getCredential() {
    return credential;
  }

  public void setCredential(UserAuthCredential credential) {
    this.credential = credential;
  }

  public String getScopeType() {
    return scopeType;
  }

  public void setScopeType(String scopeType) {
    this.scopeType = scopeType;
  }

  public Long getScopeId() {
    return scopeId;
  }

  public void setScopeId(Long scopeId) {
    this.scopeId = scopeId;
  }

  public String getScopeKey() {
    return scopeKey;
  }

  public void setScopeKey(String scopeKey) {
    this.scopeKey = scopeKey;
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
