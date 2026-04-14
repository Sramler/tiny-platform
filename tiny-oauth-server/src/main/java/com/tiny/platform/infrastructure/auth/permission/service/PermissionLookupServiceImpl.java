package com.tiny.platform.infrastructure.auth.permission.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.resource.dto.PermissionOptionDto;
import com.tiny.platform.infrastructure.auth.role.repository.PermissionOptionProjection;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class PermissionLookupServiceImpl implements PermissionLookupService {

    private final RoleRepository roleRepository;

    public PermissionLookupServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public List<PermissionOptionDto> findPermissionOptions(String keyword, int limit) {
        Long tenantId = TenantContext.isPlatformScope() ? null : TenantContext.getActiveTenantId();
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return roleRepository.findPermissionOptionsByTenantIdAndKeyword(
                tenantId,
                normalizedKeyword,
                PageRequest.of(0, normalizedLimit)
            ).stream()
            .map(this::toPermissionOptionDto)
            .toList();
    }

    private PermissionOptionDto toPermissionOptionDto(PermissionOptionProjection projection) {
        if (projection == null) {
            return null;
        }
        return new PermissionOptionDto(
            projection.getId(),
            projection.getPermissionCode(),
            projection.getPermissionName()
        );
    }
}

