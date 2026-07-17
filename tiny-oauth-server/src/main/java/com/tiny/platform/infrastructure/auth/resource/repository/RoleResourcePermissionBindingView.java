package com.tiny.platform.infrastructure.auth.resource.repository;

public interface RoleResourcePermissionBindingView {
    Long getId();

    default String getCarrierType() {
        return null;
    }

    String getPermission();

    Long getRequiredPermissionId();
}
