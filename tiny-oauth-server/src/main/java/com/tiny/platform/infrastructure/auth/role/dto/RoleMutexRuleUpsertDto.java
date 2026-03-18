package com.tiny.platform.infrastructure.auth.role.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class RoleMutexRuleUpsertDto {

    @NotNull
    @Min(1)
    private Long roleIdA;

    @NotNull
    @Min(1)
    private Long roleIdB;

    public Long getRoleIdA() {
        return roleIdA;
    }

    public void setRoleIdA(Long roleIdA) {
        this.roleIdA = roleIdA;
    }

    public Long getRoleIdB() {
        return roleIdB;
    }

    public void setRoleIdB(Long roleIdB) {
        this.roleIdB = roleIdB;
    }
}

