package com.tiny.platform.infrastructure.auth.resource.service;

import java.time.LocalDateTime;

public record CarrierCompatibilityBinding(
    String carrierType,
    Long carrierSourceId,
    Long tenantId,
    Long requiredPermissionId,
    LocalDateTime createdAt,
    Long createdBy,
    LocalDateTime updatedAt
) {
}
