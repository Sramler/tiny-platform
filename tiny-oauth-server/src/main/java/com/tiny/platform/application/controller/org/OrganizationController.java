package com.tiny.platform.application.controller.org;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.org.dto.OrganizationUnitResponseDto;
import com.tiny.platform.infrastructure.auth.org.dto.UserUnitResponseDto;
import com.tiny.platform.infrastructure.auth.org.service.OrganizationUnitService;
import com.tiny.platform.infrastructure.auth.org.service.UserUnitService;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 组织/部门管理控制器。
 *
 * <p>所有操作基于当前活动租户（从 {@code TenantContext} 获取）。</p>
 */
@RestController
@RequestMapping("/sys/org")
public class OrganizationController {

    private final OrganizationUnitService orgUnitService;
    private final UserUnitService userUnitService;

    public OrganizationController(OrganizationUnitService orgUnitService,
                                  UserUnitService userUnitService) {
        this.orgUnitService = orgUnitService;
        this.userUnitService = userUnitService;
    }

    @GetMapping("/tree")
    @PreAuthorize("@organizationAccessGuard.canRead(authentication)")
    public ResponseEntity<List<OrganizationUnitResponseDto>> getTree() {
        Long tenantId = requireTenantId();
        return ResponseEntity.ok(orgUnitService.getTree(tenantId));
    }

    @GetMapping("/list")
    @PreAuthorize("@organizationAccessGuard.canRead(authentication)")
    public ResponseEntity<List<OrganizationUnitResponseDto>> list() {
        Long tenantId = requireTenantId();
        return ResponseEntity.ok(orgUnitService.list(tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@organizationAccessGuard.canRead(authentication)")
    public ResponseEntity<OrganizationUnitResponseDto> getById(@PathVariable Long id) {
        Long tenantId = requireTenantId();
        return ResponseEntity.ok(orgUnitService.getById(tenantId, id));
    }

    @PostMapping
    @PreAuthorize("@organizationAccessGuard.canCreate(authentication)")
    public ResponseEntity<OrganizationUnitResponseDto> create(@RequestBody Map<String, Object> body,
                                                              Authentication authentication) {
        Long tenantId = requireTenantId();
        String unitType = (String) body.get("unitType");
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        Long parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : null;
        Integer sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : null;

        return ResponseEntity.ok(orgUnitService.create(tenantId, unitType, code, name, parentId, sortOrder, null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@organizationAccessGuard.canUpdate(authentication)")
    public ResponseEntity<OrganizationUnitResponseDto> update(@PathVariable Long id,
                                                              @RequestBody Map<String, Object> body) {
        Long tenantId = requireTenantId();
        String name = (String) body.get("name");
        Long parentId = body.get("parentId") != null ? ((Number) body.get("parentId")).longValue() : null;
        Integer sortOrder = body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : null;
        String status = (String) body.get("status");

        return ResponseEntity.ok(orgUnitService.update(tenantId, id, name, parentId, sortOrder, status));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@organizationAccessGuard.canDelete(authentication)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long tenantId = requireTenantId();
        orgUnitService.delete(tenantId, id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 用户归属管理 ====================

    @GetMapping("/{unitId}/users")
    @PreAuthorize("@organizationAccessGuard.canRead(authentication)")
    public ResponseEntity<List<UserUnitResponseDto>> listUnitMembers(@PathVariable Long unitId) {
        Long tenantId = requireTenantId();
        return ResponseEntity.ok(userUnitService.listByUnit(tenantId, unitId));
    }

    @GetMapping("/user/{userId}/units")
    @PreAuthorize("@organizationAccessGuard.canRead(authentication)")
    public ResponseEntity<List<UserUnitResponseDto>> listUserUnits(@PathVariable Long userId) {
        Long tenantId = requireTenantId();
        return ResponseEntity.ok(userUnitService.listByUser(tenantId, userId));
    }

    @PostMapping("/{unitId}/users/{userId}")
    @PreAuthorize("@organizationAccessGuard.canAssignUser(authentication)")
    public ResponseEntity<UserUnitResponseDto> addUserToUnit(@PathVariable Long unitId,
                                                             @PathVariable Long userId,
                                                             @RequestParam(defaultValue = "false") boolean isPrimary) {
        Long tenantId = requireTenantId();
        return ResponseEntity.ok(userUnitService.addUserToUnit(tenantId, userId, unitId, isPrimary));
    }

    @DeleteMapping("/{unitId}/users/{userId}")
    @PreAuthorize("@organizationAccessGuard.canRemoveUser(authentication)")
    public ResponseEntity<Void> removeUserFromUnit(@PathVariable Long unitId,
                                                   @PathVariable Long userId) {
        Long tenantId = requireTenantId();
        userUnitService.removeUserFromUnit(tenantId, userId, unitId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/user/{userId}/primary/{unitId}")
    @PreAuthorize("@organizationAccessGuard.canAssignUser(authentication)")
    public ResponseEntity<Void> setPrimaryUnit(@PathVariable Long userId,
                                               @PathVariable Long unitId) {
        Long tenantId = requireTenantId();
        userUnitService.setPrimaryUnit(tenantId, userId, unitId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user/{userId}/primary")
    @PreAuthorize("@organizationAccessGuard.canRead(authentication)")
    public ResponseEntity<UserUnitResponseDto> getPrimaryUnit(@PathVariable Long userId) {
        Long tenantId = requireTenantId();
        return userUnitService.getPrimaryUnit(tenantId, userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getActiveTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "缺少租户上下文");
        }
        return tenantId;
    }
}
