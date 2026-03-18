package com.tiny.platform.application.controller.tenant;

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

@RestController
@RequestMapping("/sys/tenants")
@PreAuthorize("@tenantManagementAccessGuard.canManage(authentication)")
public class TenantController {
    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<TenantResponseDto>> list(TenantRequestDto query, @PageableDefault Pageable pageable) {
        return ResponseEntity.ok(new PageResponse<>(tenantService.list(query, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TenantResponseDto> get(@PathVariable("id") Long id) {
        return tenantService.findById(id)
            .map(tenant -> {
                TenantResponseDto dto = new TenantResponseDto();
                dto.setId(tenant.getId());
                dto.setCode(tenant.getCode());
                dto.setName(tenant.getName());
                dto.setDomain(tenant.getDomain());
                dto.setEnabled(tenant.isEnabled());
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
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<TenantResponseDto> create(@RequestBody TenantCreateUpdateDto dto) {
        return ResponseEntity.ok(tenantService.create(dto));
    }

    @PutMapping("/{id}")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<TenantResponseDto> update(@PathVariable("id") Long id, @RequestBody TenantCreateUpdateDto dto) {
        return ResponseEntity.ok(tenantService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        tenantService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
