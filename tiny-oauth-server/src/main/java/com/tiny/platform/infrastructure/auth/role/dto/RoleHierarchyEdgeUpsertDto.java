package com.tiny.platform.infrastructure.auth.role.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RoleHierarchyEdgeUpsertDto {

    @NotNull
    @Min(1)
    private Long childRoleId;

    @NotNull
    @Min(1)
    private Long parentRoleId;

    public Long getChildRoleId() {
        return childRoleId;
    }

    public void setChildRoleId(Long childRoleId) {
        this.childRoleId = childRoleId;
    }

    public Long getParentRoleId() {
        return parentRoleId;
    }

    public void setParentRoleId(Long parentRoleId) {
        this.parentRoleId = parentRoleId;
    }
}

