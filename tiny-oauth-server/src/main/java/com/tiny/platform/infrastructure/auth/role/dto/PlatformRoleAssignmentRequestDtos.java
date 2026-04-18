package com.tiny.platform.infrastructure.auth.role.dto;

import java.time.LocalDateTime;

public final class PlatformRoleAssignmentRequestDtos {

    public record PlatformRoleAssignmentRequestSubmitDto(
        Long targetUserId,
        Long roleId,
        String actionType,
        String reason
    ) {
    }

    public record PlatformRoleAssignmentRequestReviewDto(
        String comment
    ) {
    }

    public record PlatformRoleAssignmentRequestResponseDto(
        Long id,
        Long targetUserId,
        Long roleId,
        String roleCode,
        String roleName,
        String actionType,
        String status,
        Long requestedBy,
        LocalDateTime requestedAt,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String reason,
        String reviewComment,
        LocalDateTime appliedAt,
        String applyError
    ) {
    }

    private PlatformRoleAssignmentRequestDtos() {
    }
}
