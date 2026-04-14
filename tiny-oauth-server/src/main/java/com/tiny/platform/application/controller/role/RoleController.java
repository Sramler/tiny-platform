package com.tiny.platform.application.controller.role;

import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleRequestDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleResponseDto;
import com.tiny.platform.infrastructure.auth.role.service.RoleService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sys/roles")
public class RoleController {
    private final RoleService roleService;
    public RoleController(RoleService roleService) { this.roleService = roleService; }

    @GetMapping
    @PreAuthorize("@roleManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<PageResponse<RoleResponseDto>> list(RoleRequestDto query, @PageableDefault Pageable pageable) {
        return ResponseEntity.ok(new PageResponse<>(roleService.roles(query, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@roleManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<RoleResponseDto> get(@PathVariable("id") Long id) {
        return roleService.findById(id)
            .map(role -> {
                // name/code 仅用于展示与下游，鉴权不依赖 role.name
                RoleResponseDto dto = new RoleResponseDto(
                    role.getId(),
                    role.getName(),
                    role.getCode(),
                    role.getDescription(),
                    role.isBuiltin(),
                    role.isEnabled(),
                    role.getCreatedAt(),
                    role.getUpdatedAt()
                );
                dto.setRecordTenantId(role.getTenantId());
                return ResponseEntity.ok(dto);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canCreate(authentication)")
    public ResponseEntity<RoleResponseDto> create(@RequestBody RoleCreateUpdateDto dto) {
        return ResponseEntity.ok(roleService.create(dto));
    }

    @PutMapping("/{id}")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canUpdate(authentication)")
    public ResponseEntity<RoleResponseDto> update(@PathVariable("id") Long id, @RequestBody RoleCreateUpdateDto dto) {
        return ResponseEntity.ok(roleService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canDelete(authentication)")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 获取所有角色列表（不分页，适用于a-transfer）
     */
    @GetMapping("/all")
    @PreAuthorize("@roleManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<List<RoleResponseDto>> getAllRoles() {
        // 直接查全部
        return ResponseEntity.ok(roleService.roles(new RoleRequestDto(), Pageable.unpaged()).getContent());
    }

    /**
     * 获取该角色下所有已分配用户ID列表
     */
    @GetMapping("/{id}/users")
    @PreAuthorize("@roleManagementAccessGuard.canAssignUsers(authentication)")
    public ResponseEntity<List<Long>> getRoleUsers(@PathVariable("id") Long id,
                                                   @RequestParam(value = "scopeType", required = false) String scopeType,
                                                   @RequestParam(value = "scopeId", required = false) Long scopeId) {
        return ResponseEntity.ok(roleService.getDirectUserIdsByRoleId(id, scopeType, scopeId));
    }

    /**
     * 保存角色与用户的分配关系
     */
    @PostMapping("/{id}/users")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canAssignUsers(authentication)")
    public ResponseEntity<?> updateRoleUsers(@PathVariable("id") Long id, @RequestBody Object body) {
        var request = parseUserAssignmentRequest(body);
        roleService.updateRoleUsers(id, request.scopeType(), request.scopeId(), request.userIds());
        return ResponseEntity.ok().build();
    }

    // ==================== 角色权限分配API ====================

    @GetMapping("/{id}/permissions")
    @PreAuthorize("@roleManagementAccessGuard.canAssignPermissions(authentication)")
    public ResponseEntity<List<Long>> getRolePermissions(@PathVariable("id") Long id) {
        return ResponseEntity.ok(roleService.getPermissionIdsByRoleId(id));
    }

    @PostMapping("/{id}/permissions")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canAssignPermissions(authentication)")
    public ResponseEntity<?> updateRolePermissions(@PathVariable("id") Long id, @RequestBody Object body) {
        PermissionAssignmentRequest request = parsePermissionAssignmentRequest(body);
        roleService.updateRolePermissions(id, request.permissionIds());
        return ResponseEntity.ok().build();
    }

    /**
     * 获取角色已分配资源ID列表
     * @deprecated 兼容历史 /resources 入口，推荐使用 /permissions
     * @param id 角色ID
     * @return 资源ID列表
     */
    @GetMapping("/{id}/resources")
    @PreAuthorize("@roleManagementAccessGuard.canAssignPermissions(authentication)")
    public ResponseEntity<List<Long>> getRoleResources(@PathVariable("id") Long id) {
        // 使用Service层方法查询角色资源，避免懒加载问题
        return ResponseEntity.ok(roleService.getResourceIdsByRoleId(id));
    }

    /**
     * 保存角色与权限的分配关系
     * @deprecated 兼容历史 /resources 入口，推荐使用 /permissions
     * @param id 角色ID
     * @param body 仅接受包含 permissionIds 的请求体
     * @return 响应结果
     */
    @PostMapping("/{id}/resources")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canAssignPermissions(authentication)")
    public ResponseEntity<?> updateRoleResources(@PathVariable("id") Long id, @RequestBody Object body) {
        PermissionAssignmentRequest request = parsePermissionAssignmentRequest(body);
        roleService.updateRolePermissions(id, request.permissionIds());
        return ResponseEntity.ok().build();
    }

    private PermissionAssignmentRequest parsePermissionAssignmentRequest(Object body) {
        if (body instanceof Map<?, ?> requestMap) {
            List<Long> permissionIds = requestMap.get("permissionIds") instanceof List<?> rawPermissionIds
                ? parseIdList(rawPermissionIds)
                : List.of();
            if (permissionIds.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求体必须提供 permissionIds");
            }
            return new PermissionAssignmentRequest(permissionIds);
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求体必须提供 permissionIds");
    }

    private UserAssignmentRequest parseUserAssignmentRequest(Object body) {
        if (body instanceof List<?>) {
            throw new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "POST /sys/roles/{id}/users 请求体必须为 JSON 对象，包含 userIds 与可选 scopeType、scopeId；"
                    + "已停止接受 JSON 数组（CARD-14C）。示例：{\"userIds\":[1,2],\"scopeType\":\"TENANT\"} 或 {\"userIds\":[],\"scopeType\":\"DEPT\",\"scopeId\":200}。"
            );
        }
        if (!(body instanceof Map<?, ?> requestMap)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求体必须为 JSON 对象");
        }
        Object userIdsObj = requestMap.get("userIds");
        if (!(userIdsObj instanceof List<?> rawUserIds)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请求体必须包含 userIds 数组字段");
        }
        return new UserAssignmentRequest(
            requestMap.get("scopeType") != null ? String.valueOf(requestMap.get("scopeType")) : null,
            parseNullableLong(requestMap.get("scopeId")),
            parseIdList(rawUserIds)
        );
    }

    private List<Long> parseIdList(List<?> rawIds) {
        List<Long> ids = new ArrayList<>();
        for (Object rawId : rawIds) {
            ids.add(parseNullableLong(rawId));
        }
        return ids;
    }

    private Long parseNullableLong(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(rawValue));
    }

    private record UserAssignmentRequest(String scopeType, Long scopeId, List<Long> userIds) {}
    private record PermissionAssignmentRequest(List<Long> permissionIds) {}
}
