package com.tiny.platform.infrastructure.auth.datascope.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 自定义数据范围明细条目。
 *
 * <p>仅当父 {@link RoleDataScope} 的 {@code scope_type = CUSTOM} 时使用。
 * 每条记录表示一个可见的目标实体（组织/部门/用户）。</p>
 */
@Entity
@Table(name = "role_data_scope_item",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_rdsi_scope_target",
            columnNames = {"role_data_scope_id", "target_type", "target_id"})
    }
)
public class RoleDataScopeItem implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_data_scope_id", nullable = false)
    private Long roleDataScopeId;

    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoleDataScopeId() { return roleDataScopeId; }
    public void setRoleDataScopeId(Long roleDataScopeId) { this.roleDataScopeId = roleDataScopeId; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
