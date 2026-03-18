package com.tiny.platform.infrastructure.auth.role.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "role_constraint_violation_log")
public class RoleConstraintViolationLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "principal_type", nullable = false, length = 16)
    private String principalType;

    @Column(name = "principal_id")
    private Long principalId;

    @Column(name = "scope_type", nullable = false, length = 16)
    private String scopeType;

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "violation_type", nullable = false, length = 32)
    private String violationType;

    @Column(name = "violation_code", nullable = false, length = 64)
    private String violationCode;

    @Column(name = "direct_role_ids", length = 1024)
    private String directRoleIds;

    @Column(name = "effective_role_ids", length = 1024)
    private String effectiveRoleIds;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getPrincipalType() {
        return principalType;
    }

    public void setPrincipalType(String principalType) {
        this.principalType = principalType;
    }

    public Long getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(Long principalId) {
        this.principalId = principalId;
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

    public String getViolationType() {
        return violationType;
    }

    public void setViolationType(String violationType) {
        this.violationType = violationType;
    }

    public String getViolationCode() {
        return violationCode;
    }

    public void setViolationCode(String violationCode) {
        this.violationCode = violationCode;
    }

    public String getDirectRoleIds() {
        return directRoleIds;
    }

    public void setDirectRoleIds(String directRoleIds) {
        this.directRoleIds = directRoleIds;
    }

    public String getEffectiveRoleIds() {
        return effectiveRoleIds;
    }

    public void setEffectiveRoleIds(String effectiveRoleIds) {
        this.effectiveRoleIds = effectiveRoleIds;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

