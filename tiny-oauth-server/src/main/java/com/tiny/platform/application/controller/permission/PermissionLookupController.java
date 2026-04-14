package com.tiny.platform.application.controller.permission;

import com.tiny.platform.infrastructure.auth.permission.service.PermissionLookupService;
import com.tiny.platform.infrastructure.auth.resource.dto.PermissionOptionDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sys/permissions")
public class PermissionLookupController {

    private final PermissionLookupService permissionLookupService;

    public PermissionLookupController(PermissionLookupService permissionLookupService) {
        this.permissionLookupService = permissionLookupService;
    }

    @GetMapping("/options")
    @PreAuthorize("@menuManagementAccessGuard.canRead(authentication) or @resourceManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<List<PermissionOptionDto>> getPermissionOptions(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit) {
        return ResponseEntity.ok(permissionLookupService.findPermissionOptions(keyword, limit == null ? 50 : limit));
    }
}

