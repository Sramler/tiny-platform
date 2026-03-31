package com.tiny.platform.infrastructure.auth.audit.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Requirement-aware authorization audit detail.
 *
 * <p>This detail is serialized into {@code AuthorizationAuditLog.eventDetail} as JSON.
 * Do NOT build it by hand-string concatenation.</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record RequirementAwareAuditDetail(
    String carrierType,
    Long carrierId,
    Integer requirementGroup,
    List<String> matchedPermissionCodes,
    List<String> missingPermissionCodes,
    List<String> negatedPermissionCodes,
    String decision,
    String reason
) {}

