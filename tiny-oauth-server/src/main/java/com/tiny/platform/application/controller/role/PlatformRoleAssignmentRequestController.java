package com.tiny.platform.application.controller.role;

import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleAssignmentRequestDtos.PlatformRoleAssignmentRequestResponseDto;
import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleAssignmentRequestDtos.PlatformRoleAssignmentRequestReviewDto;
import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleAssignmentRequestDtos.PlatformRoleAssignmentRequestSubmitDto;
import com.tiny.platform.infrastructure.auth.role.service.PlatformRoleAssignmentRequestService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/role-assignment-requests")
public class PlatformRoleAssignmentRequestController {

    private final PlatformRoleAssignmentRequestService platformRoleAssignmentRequestService;

    public PlatformRoleAssignmentRequestController(PlatformRoleAssignmentRequestService platformRoleAssignmentRequestService) {
        this.platformRoleAssignmentRequestService = platformRoleAssignmentRequestService;
    }

    @GetMapping
    @PreAuthorize("@platformRoleApprovalAccessGuard.canQueryQueue(authentication)")
    public ResponseEntity<PageResponse<PlatformRoleAssignmentRequestResponseDto>> list(
        @RequestParam(value = "targetUserId", required = false) Long targetUserId,
        @RequestParam(value = "status", required = false) String status,
        @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(new PageResponse<>(
            platformRoleAssignmentRequestService.list(targetUserId, status, pageable)
        ));
    }

    @PostMapping
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@platformRoleApprovalAccessGuard.canSubmit(authentication)")
    public ResponseEntity<PlatformRoleAssignmentRequestResponseDto> submit(
        jakarta.servlet.http.HttpServletRequest request,
        @RequestBody PlatformRoleAssignmentRequestSubmitRequest body
    ) {
        if (body == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求体不能为空");
        }
        return ResponseEntity.ok(platformRoleAssignmentRequestService.submit(
            new PlatformRoleAssignmentRequestSubmitDto(body.targetUserId(), body.roleId(), body.actionType(), body.reason())
        ));
    }

    @PostMapping("/{id}/approve")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@platformRoleApprovalAccessGuard.canApprove(authentication)")
    public ResponseEntity<PlatformRoleAssignmentRequestResponseDto> approve(
        jakarta.servlet.http.HttpServletRequest request,
        @PathVariable("id") Long id,
        @RequestBody(required = false) PlatformRoleAssignmentRequestReviewRequest body
    ) {
        return ResponseEntity.ok(platformRoleAssignmentRequestService.approve(id, toReviewDto(body)));
    }

    @PostMapping("/{id}/reject")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@platformRoleApprovalAccessGuard.canReject(authentication)")
    public ResponseEntity<PlatformRoleAssignmentRequestResponseDto> reject(
        jakarta.servlet.http.HttpServletRequest request,
        @PathVariable("id") Long id,
        @RequestBody(required = false) PlatformRoleAssignmentRequestReviewRequest body
    ) {
        return ResponseEntity.ok(platformRoleAssignmentRequestService.reject(id, toReviewDto(body)));
    }

    @PostMapping("/{id}/cancel")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@platformRoleApprovalAccessGuard.canCancel(authentication)")
    public ResponseEntity<Void> cancel(
        jakarta.servlet.http.HttpServletRequest request,
        @PathVariable("id") Long id
    ) {
        platformRoleAssignmentRequestService.cancel(id);
        return ResponseEntity.noContent().build();
    }

    private static PlatformRoleAssignmentRequestReviewDto toReviewDto(PlatformRoleAssignmentRequestReviewRequest body) {
        if (body == null) {
            return new PlatformRoleAssignmentRequestReviewDto(null);
        }
        return new PlatformRoleAssignmentRequestReviewDto(body.comment());
    }

    public record PlatformRoleAssignmentRequestSubmitRequest(
        Long targetUserId,
        Long roleId,
        String actionType,
        String reason
    ) {
    }

    public record PlatformRoleAssignmentRequestReviewRequest(
        String comment
    ) {
    }
}
