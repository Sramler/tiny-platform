package com.tiny.platform.infrastructure.auth.resource.repository;

public interface RoleResourcePermissionBindingView {
    Long getId();

    String getCarrierType();

    String getPermission();

    Long getRequiredPermissionId();
}
