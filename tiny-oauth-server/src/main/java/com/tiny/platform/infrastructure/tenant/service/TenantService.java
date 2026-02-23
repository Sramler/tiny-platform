package com.tiny.platform.infrastructure.tenant.service;

import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
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
    void delete(Long id);
}
