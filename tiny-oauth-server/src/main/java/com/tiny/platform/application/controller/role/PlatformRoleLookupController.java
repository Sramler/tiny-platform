package com.tiny.platform.application.controller.role;

import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleOptionDto;
import com.tiny.platform.infrastructure.auth.role.service.PlatformRoleLookupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/platform/roles")
public class PlatformRoleLookupController {

    private final PlatformRoleLookupService platformRoleLookupService;

    public PlatformRoleLookupController(PlatformRoleLookupService platformRoleLookupService) {
        this.platformRoleLookupService = platformRoleLookupService;
    }

    @GetMapping("/options")
    @PreAuthorize(
        "@platformUserManagementAccessGuard.canReadRoleCatalog(authentication)"
            + " || @roleManagementAccessGuard.canReadRoleCatalog(authentication)"
    )
    public ResponseEntity<List<PlatformRoleOptionDto>> getOptions(
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit
    ) {
        return ResponseEntity.ok(platformRoleLookupService.findOptions(keyword, limit == null ? 200 : limit));
    }
}
