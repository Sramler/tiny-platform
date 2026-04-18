package com.tiny.platform.application.controller.user;

import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserCreateDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserDetailDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserListItemDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserRoleDto;
import com.tiny.platform.infrastructure.auth.user.service.PlatformUserManagementService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/platform/users")
public class PlatformUserManagementController {

    private final PlatformUserManagementService platformUserManagementService;

    public PlatformUserManagementController(PlatformUserManagementService platformUserManagementService) {
        this.platformUserManagementService = platformUserManagementService;
    }

    @GetMapping
    @PreAuthorize("@platformUserManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<PageResponse<PlatformUserListItemDto>> list(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "enabled", required = false) Boolean enabled,
        @RequestParam(value = "status", required = false) String status,
        @PageableDefault(size = 10, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(new PageResponse<>(
            platformUserManagementService.list(keyword, enabled, status, pageable)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@platformUserManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<PlatformUserDetailDto> get(@PathVariable("id") Long id) {
        return platformUserManagementService.get(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("@platformUserManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<List<PlatformUserRoleDto>> getRoles(@PathVariable("id") Long id) {
        return ResponseEntity.ok(platformUserManagementService.getRoles(id));
    }

    @PostMapping
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@platformUserManagementAccessGuard.canCreate(authentication)")
    public ResponseEntity<PlatformUserDetailDto> create(@RequestBody PlatformUserCreateRequest request) {
        if (request == null || request.userId() == null || request.userId() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求体必须提供合法 userId");
        }
        return ResponseEntity.ok(platformUserManagementService.create(
            new PlatformUserCreateDto(request.userId(), request.displayName(), request.status())
        ));
    }

    @PatchMapping("/{id}/status")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@platformUserManagementAccessGuard.canUpdate(authentication)")
    public ResponseEntity<Void> updateStatus(@PathVariable("id") Long id,
                                             @RequestBody PlatformUserStatusUpdateRequest request) {
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求体必须提供 status");
        }
        boolean updated = platformUserManagementService.updateStatus(id, request.status());
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/roles")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@platformUserManagementAccessGuard.canUpdate(authentication)")
    public ResponseEntity<List<PlatformUserRoleDto>> replaceRoles(@PathVariable("id") Long id,
                                                                   @RequestBody PlatformUserRolesReplaceRequest request) {
        if (request == null || request.roleIds() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求体必须提供 roleIds");
        }
        return ResponseEntity.ok(platformUserManagementService.replaceRoles(id, request.roleIds()));
    }

    public record PlatformUserCreateRequest(Long userId, String displayName, String status) {
    }

    public record PlatformUserStatusUpdateRequest(String status) {
    }

    public record PlatformUserRolesReplaceRequest(List<Long> roleIds) {
    }
}
