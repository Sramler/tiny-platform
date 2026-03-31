package com.tiny.platform.infrastructure.auth.resource.repository;

public interface RoleResourcePermissionBindingView {
    Long getId();

    String getPermission();

    Long getRequiredPermissionId();
}
