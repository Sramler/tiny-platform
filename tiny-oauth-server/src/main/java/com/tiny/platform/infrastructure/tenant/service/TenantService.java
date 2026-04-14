package com.tiny.platform.infrastructure.tenant.service;

import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantPermissionSummaryDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantRequestDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TenantService {
    Page<TenantResponseDto> list(TenantRequestDto query, Pageable pageable);
    Optional<Tenant> findById(Long id);
    TenantResponseDto create(TenantCreateUpdateDto dto);
    TenantResponseDto update(Long id, TenantCreateUpdateDto dto);
    TenantResponseDto freeze(Long id);
    TenantResponseDto unfreeze(Long id);
    TenantResponseDto decommission(Long id);

    /** 平台模板（tenant_id IS NULL）缺失时回填；非租户副本重建。见 {@code TINY_PLATFORM_TENANT_GOVERNANCE.md} §3.2。 */
    boolean initializePlatformTemplates();

    /** 只读差异证据 + 审计；非重建。 */
    PlatformTemplateDiffResult diffPlatformTemplate(Long tenantId);

    TenantPermissionSummaryDto summarizeTenantPermissions(Long tenantId);
    void delete(Long id);
}
