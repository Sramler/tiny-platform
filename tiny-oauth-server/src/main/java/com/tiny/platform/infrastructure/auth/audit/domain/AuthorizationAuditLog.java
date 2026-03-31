package com.tiny.platform.infrastructure.auth.audit.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 授权审计日志实体。
 *
 * <p>记录所有授权相关变更事件：角色赋权/撤权、数据范围变更、约束规则变更、
 * 组织/部门变更、用户归属变更、约束违例等。</p>
 *
 * <p>{@code event_type} 常量定义在 {@link AuthorizationAuditEventType}。</p>
 */
@Entity
@Table(name = "authorization_audit_log")
public class AuthorizationAuditLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(name = "scope_type", length = 16)
    private String scopeType;

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "role_id")
    private Long roleId;

    @Column(length = 64)
    private String module;

    @Column(name = "resource_permission", length = 128)
    private String resourcePermission;

    @Column(name = "event_detail", columnDefinition = "json")
    private String eventDetail;

    @Column(nullable = false, length = 16)
    private String result = "SUCCESS";

    @Column(name = "result_reason", length = 512)
    private String resultReason;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

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

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long actorUserId) { this.actorUserId = actorUserId; }

    public Long getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Long targetUserId) { this.targetUserId = targetUserId; }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }

    public Long getScopeId() { return scopeId; }
    public void setScopeId(Long scopeId) { this.scopeId = scopeId; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getResourcePermission() { return resourcePermission; }
    public void setResourcePermission(String resourcePermission) { this.resourcePermission = resourcePermission; }

    public String getEventDetail() { return eventDetail; }
    public void setEventDetail(String eventDetail) { this.eventDetail = eventDetail; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getResultReason() { return resultReason; }
    public void setResultReason(String resultReason) { this.resultReason = resultReason; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
