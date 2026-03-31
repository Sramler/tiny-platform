package com.tiny.platform.infrastructure.auth.datascope.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色数据范围规则。
 *
 * <p>定义某个角色在某个业务模块中可见的数据范围。
 * 同一 {@code (tenant_id, role_id, module, access_type)} 只允许一条规则。</p>
 *
 * <p>{@code scope_type} 取值：</p>
 * <ul>
 *   <li>{@code ALL} — 租户内全部数据</li>
 *   <li>{@code TENANT} — 等同于 ALL（语义保留）</li>
 *   <li>{@code ORG} — 用户所属组织的数据</li>
 *   <li>{@code ORG_AND_CHILD} — 用户所属组织及所有下级组织的数据</li>
 *   <li>{@code DEPT} — 用户所属部门的数据</li>
 *   <li>{@code DEPT_AND_CHILD} — 用户所属部门及所有下级部门的数据</li>
 *   <li>{@code SELF} — 仅用户本人创建/拥有的数据</li>
 *   <li>{@code CUSTOM} — 由 {@link RoleDataScopeItem} 明细定义的自选范围</li>
 * </ul>
 */
@Entity
@Table(name = "role_data_scope",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_rds_tenant_role_module_access",
            columnNames = {"tenant_id", "role_id", "module", "access_type"})
    }
)
public class RoleDataScope implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(nullable = false, length = 64)
    private String module;

    @Column(name = "scope_type", nullable = false, length = 32)
    private String scopeType;

    @Column(name = "access_type", nullable = false, length = 16)
    private String accessType = "READ";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public String getAccessType() { return accessType; }
    public void setAccessType(String accessType) { this.accessType = accessType; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
