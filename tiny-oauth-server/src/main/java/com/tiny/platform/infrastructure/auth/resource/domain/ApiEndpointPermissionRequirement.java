package com.tiny.platform.infrastructure.auth.resource.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_endpoint_permission_requirement")
public class ApiEndpointPermissionRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "api_endpoint_id", nullable = false)
    private Long apiEndpointId;

    @Column(name = "requirement_group", nullable = false)
    private Integer requirementGroup = 0;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 1;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    @Column(nullable = false)
    private Boolean negated = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getApiEndpointId() { return apiEndpointId; }
    public void setApiEndpointId(Long apiEndpointId) { this.apiEndpointId = apiEndpointId; }
    public Integer getRequirementGroup() { return requirementGroup; }
    public void setRequirementGroup(Integer requirementGroup) { this.requirementGroup = requirementGroup; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
    public Boolean getNegated() { return negated; }
    public void setNegated(Boolean negated) { this.negated = negated; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
