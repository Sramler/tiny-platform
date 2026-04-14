package com.tiny.platform.application.controller.permission;

import com.tiny.platform.infrastructure.auth.permission.dto.PermissionManagementDtos.PermissionDetailDto;
import com.tiny.platform.infrastructure.auth.permission.dto.PermissionManagementDtos.PermissionListItemDto;
import com.tiny.platform.infrastructure.auth.permission.service.PermissionManagementService;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sys/permissions")
public class PermissionManagementController {

    private final PermissionManagementService permissionManagementService;

    public PermissionManagementController(PermissionManagementService permissionManagementService) {
        this.permissionManagementService = permissionManagementService;
    }

    @GetMapping
    @PreAuthorize("@permissionManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<List<PermissionListItemDto>> list(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "moduleCode", required = false) String moduleCode,
        @RequestParam(value = "enabled", required = false) Boolean enabled) {
        return ResponseEntity.ok(permissionManagementService.list(keyword, moduleCode, enabled));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<PermissionDetailDto> get(@PathVariable("id") Long id) {
        return permissionManagementService.get(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("@permissionManagementAccessGuard.canUpdate(authentication)")
    public ResponseEntity<Void> updateEnabled(@PathVariable("id") Long id,
                                              @RequestBody PermissionEnabledUpdateRequest request) {
        if (request == null || request.enabled() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求体必须提供 enabled");
        }
        boolean updated = permissionManagementService.updateEnabled(id, request.enabled());
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    public record PermissionEnabledUpdateRequest(Boolean enabled) {
    }
}
