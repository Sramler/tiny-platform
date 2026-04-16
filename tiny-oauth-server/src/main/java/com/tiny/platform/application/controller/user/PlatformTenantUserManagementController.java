package com.tiny.platform.application.controller.user;

import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.auth.user.dto.UserRequestDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import com.tiny.platform.infrastructure.auth.user.service.PlatformTenantUserManagementService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/platform/tenants/{tenantId}/users")
public class PlatformTenantUserManagementController {

    private final PlatformTenantUserManagementService platformTenantUserManagementService;
    private final TenantLifecycleAccessGuard tenantLifecycleAccessGuard;

    public PlatformTenantUserManagementController(
        PlatformTenantUserManagementService platformTenantUserManagementService,
        TenantLifecycleAccessGuard tenantLifecycleAccessGuard
    ) {
        this.platformTenantUserManagementService = platformTenantUserManagementService;
        this.tenantLifecycleAccessGuard = tenantLifecycleAccessGuard;
    }

    @GetMapping
    @PreAuthorize("@userManagementAccessGuard.canPlatformStewardRead(authentication)")
    public ResponseEntity<PageResponse<UserResponseDto>> list(
        @PathVariable("tenantId") Long tenantId,
        @Valid UserRequestDto query,
        @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        tenantLifecycleAccessGuard.assertPlatformTargetTenantReadable(tenantId, "system:user:list");
        return ResponseEntity.ok(new PageResponse<>(
            platformTenantUserManagementService.list(tenantId, query, pageable)
        ));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("@userManagementAccessGuard.canPlatformStewardRead(authentication)")
    public ResponseEntity<UserResponseDto> get(
        @PathVariable("tenantId") Long tenantId,
        @PathVariable("userId") Long userId
    ) {
        tenantLifecycleAccessGuard.assertPlatformTargetTenantReadable(tenantId, "system:user:view");
        return platformTenantUserManagementService.get(tenantId, userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
