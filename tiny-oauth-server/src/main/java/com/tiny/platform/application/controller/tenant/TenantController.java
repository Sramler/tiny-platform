package com.tiny.platform.application.controller.tenant;

import com.tiny.platform.core.oauth.tenant.TenantLifecycleAccessGuard;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantRequestDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import com.tiny.platform.infrastructure.tenant.service.TenantService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/sys/tenants")
public class TenantController {
    private final TenantService tenantService;
    private final TenantLifecycleAccessGuard tenantLifecycleAccessGuard;

    public TenantController(TenantService tenantService, TenantLifecycleAccessGuard tenantLifecycleAccessGuard) {
        this.tenantService = tenantService;
        this.tenantLifecycleAccessGuard = tenantLifecycleAccessGuard;
    }

    @GetMapping
    @PreAuthorize("@tenantManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<PageResponse<TenantResponseDto>> list(TenantRequestDto query, @PageableDefault Pageable pageable) {
        return ResponseEntity.ok(new PageResponse<>(tenantService.list(query, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@tenantManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<TenantResponseDto> get(@PathVariable("id") Long id) {
        tenantLifecycleAccessGuard.assertPlatformTargetTenantReadable(id, "system:tenant:view");
        return tenantService.findById(id)
            .map(tenant -> {
                TenantResponseDto dto = new TenantResponseDto();
                dto.setId(tenant.getId());
                dto.setCode(tenant.getCode());
                dto.setName(tenant.getName());
                dto.setDomain(tenant.getDomain());
                dto.setEnabled(tenant.isEnabled());
                dto.setLifecycleStatus(tenant.getLifecycleStatus());
                dto.setPlanCode(tenant.getPlanCode());
                dto.setExpiresAt(tenant.getExpiresAt() != null ? tenant.getExpiresAt().toString() : null);
                dto.setMaxUsers(tenant.getMaxUsers());
                dto.setMaxStorageGb(tenant.getMaxStorageGb());
                dto.setContactName(tenant.getContactName());
                dto.setContactEmail(tenant.getContactEmail());
                dto.setContactPhone(tenant.getContactPhone());
                dto.setRemark(tenant.getRemark());
                dto.setCreatedAt(tenant.getCreatedAt());
                dto.setUpdatedAt(tenant.getUpdatedAt());
                return ResponseEntity.ok(dto);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("@tenantManagementAccessGuard.canCreate(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<TenantResponseDto> create(@RequestBody TenantCreateUpdateDto dto) {
        return ResponseEntity.ok(tenantService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@tenantManagementAccessGuard.canUpdate(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<TenantResponseDto> update(@PathVariable("id") Long id, @RequestBody TenantCreateUpdateDto dto) {
        return ResponseEntity.ok(tenantService.update(id, dto));
    }

    /**
     * 回填缺失的 {@code tenant_id IS NULL} 平台模板；**不是**租户副本「重建/回退」。契约见 {@code TINY_PLATFORM_TENANT_GOVERNANCE.md} §3.2。
     */
    @PostMapping("/platform-template/initialize")
    @PreAuthorize("@tenantManagementAccessGuard.canInitializePlatformTemplate(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Map<String, Object>> initializePlatformTemplate() {
        boolean initialized = tenantService.initializePlatformTemplates();
        return ResponseEntity.ok(Map.of(
            "initialized", initialized,
            "message", initialized
                ? "平台模板缺失，已按配置的平台租户回填完成"
                : "平台模板已存在，无需重复初始化"
        ));
    }

    /**
     * 平台模板与租户副本差异（只读证据 + 审计）；**不**执行重建。
     */
    @GetMapping("/{id}/platform-template/diff")
    @PreAuthorize("@tenantManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<com.tiny.platform.infrastructure.tenant.service.PlatformTemplateDiffResult> diffPlatformTemplate(
        @PathVariable("id") Long id
    ) {
        return ResponseEntity.ok(tenantService.diffPlatformTemplate(id));
    }

    @PostMapping("/{id}/freeze")
    @PreAuthorize("@tenantManagementAccessGuard.canFreeze(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<TenantResponseDto> freeze(@PathVariable("id") Long id) {
        return ResponseEntity.ok(tenantService.freeze(id));
    }

    @PostMapping("/{id}/unfreeze")
    @PreAuthorize("@tenantManagementAccessGuard.canUnfreeze(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<TenantResponseDto> unfreeze(@PathVariable("id") Long id) {
        return ResponseEntity.ok(tenantService.unfreeze(id));
    }

    @PostMapping("/{id}/decommission")
    @PreAuthorize("@tenantManagementAccessGuard.canDecommission(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<TenantResponseDto> decommission(@PathVariable("id") Long id) {
        return ResponseEntity.ok(tenantService.decommission(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@tenantManagementAccessGuard.canDelete(authentication)")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        tenantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
