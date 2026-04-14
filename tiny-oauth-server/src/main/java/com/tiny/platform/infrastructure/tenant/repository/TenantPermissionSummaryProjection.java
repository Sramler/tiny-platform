package com.tiny.platform.infrastructure.tenant.repository;

public interface TenantPermissionSummaryProjection {
    long getTotalRoles();

    long getEnabledRoles();

    long getTotalPermissions();

    long getAssignedPermissions();

    long getTotalCarriers();

    long getBoundCarriers();

    long getMenuCarriers();

    long getUiActionCarriers();

    long getApiEndpointCarriers();
}
