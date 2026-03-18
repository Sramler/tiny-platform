package com.tiny.platform.application.controller.role;

import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleRequestDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleResponseDto;
import com.tiny.platform.infrastructure.auth.role.service.RoleService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ResponseEntity<List<Long>> getRoleUsers(@PathVariable("id") Long id) {
        return ResponseEntity.ok(roleService.getUserIdsByRoleId(id));
    }

    /**
     * 保存角色与用户的分配关系
     */
    @PostMapping("/{id}/users")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canAssignUsers(authentication)")
    public ResponseEntity<?> updateRoleUsers(@PathVariable("id") Long id, @RequestBody List<Long> userIds) {
        roleService.updateRoleUsers(id, userIds);
        return ResponseEntity.ok().build();
    }

    // ==================== 角色资源分配API ====================

    /**
     * 获取角色已分配资源ID列表
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
     * 保存角色与资源的分配关系
     * @param id 角色ID
     * @param resourceIds 资源ID列表
     * @return 响应结果
     */
    @PostMapping("/{id}/resources")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@roleManagementAccessGuard.canAssignPermissions(authentication)")
    public ResponseEntity<?> updateRoleResources(@PathVariable("id") Long id, @RequestBody List<Long> resourceIds) {
        roleService.updateRoleResources(id, resourceIds);
        return ResponseEntity.ok().build();
    }
} 
