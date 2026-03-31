package com.tiny.platform.infrastructure.auth.org.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-组织/部门归属关系。
 *
 * <p>一个用户在同一租户内可属于多个部门（兼任），但主部门最多一个
 * （{@code is_primary = true} 的记录在同一 {@code (tenant_id, user_id)} 下最多 1 条，
 * 由 Service 层保证）。</p>
 *
 * <p>唯一键 {@code (tenant_id, user_id, unit_id)} 防止同一用户在同一部门重复归属。</p>
 */
@Entity
@Table(name = "user_unit",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_unit_tenant_user_unit", columnNames = {"tenant_id", "user_id", "unit_id"})
    }
)
public class UserUnit implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "unit_id", nullable = false)
    private Long unitId;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = Boolean.FALSE;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (joinedAt == null) {
            joinedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public Boolean getIsPrimary() {
        return isPrimary;
    }

    public void setIsPrimary(Boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt) {
        this.joinedAt = joinedAt;
    }

    public LocalDateTime getLeftAt() {
        return leftAt;
    }

    public void setLeftAt(LocalDateTime leftAt) {
        this.leftAt = leftAt;
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
