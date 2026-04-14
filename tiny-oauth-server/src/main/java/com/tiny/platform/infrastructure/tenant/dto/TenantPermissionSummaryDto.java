package com.tiny.platform.infrastructure.tenant.dto;

public record TenantPermissionSummaryDto(
    Long tenantId,
    long totalRoles,
    long enabledRoles,
    long totalPermissions,
    long assignedPermissions,
    long totalCarriers,
    long boundCarriers,
    long menuCarriers,
    long uiActionCarriers,
    long apiEndpointCarriers
) {
}
