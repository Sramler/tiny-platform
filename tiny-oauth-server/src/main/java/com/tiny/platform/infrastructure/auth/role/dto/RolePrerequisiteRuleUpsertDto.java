package com.tiny.platform.infrastructure.auth.role.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RolePrerequisiteRuleUpsertDto {

    @NotNull
    @Min(1)
    private Long roleId;

    @NotNull
    @Min(1)
    private Long requiredRoleId;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getRequiredRoleId() {
        return requiredRoleId;
    }

    public void setRequiredRoleId(Long requiredRoleId) {
        this.requiredRoleId = requiredRoleId;
    }
}

