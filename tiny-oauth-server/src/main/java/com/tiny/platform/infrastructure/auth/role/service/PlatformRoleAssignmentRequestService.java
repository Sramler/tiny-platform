package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleAssignmentRequestDtos.PlatformRoleAssignmentRequestResponseDto;
import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleAssignmentRequestDtos.PlatformRoleAssignmentRequestReviewDto;
import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleAssignmentRequestDtos.PlatformRoleAssignmentRequestSubmitDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlatformRoleAssignmentRequestService {

    Page<PlatformRoleAssignmentRequestResponseDto> list(Long targetUserId, String status, Pageable pageable);

    PlatformRoleAssignmentRequestResponseDto submit(PlatformRoleAssignmentRequestSubmitDto request);

    PlatformRoleAssignmentRequestResponseDto approve(Long id, PlatformRoleAssignmentRequestReviewDto review);

    PlatformRoleAssignmentRequestResponseDto reject(Long id, PlatformRoleAssignmentRequestReviewDto review);

    void cancel(Long id);
}
