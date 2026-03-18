package com.tiny.platform.infrastructure.auth.role.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RoleCardinalityRuleUpsertDto {

    @NotNull
    @Min(1)
    private Long roleId;

    @NotBlank
    private String scopeType;

    @Min(1)
    private int maxAssignments;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public int getMaxAssignments() {
        return maxAssignments;
    }

    public void setMaxAssignments(int maxAssignments) {
        this.maxAssignments = maxAssignments;
    }
}

