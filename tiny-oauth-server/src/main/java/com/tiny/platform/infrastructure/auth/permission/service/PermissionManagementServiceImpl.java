package com.tiny.platform.infrastructure.auth.permission.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.permission.dto.PermissionManagementDtos.PermissionDetailDto;
import com.tiny.platform.infrastructure.auth.permission.dto.PermissionManagementDtos.PermissionListItemDto;
import com.tiny.platform.infrastructure.auth.permission.dto.PermissionManagementDtos.PermissionRoleBindingDto;
import com.tiny.platform.infrastructure.auth.permission.repository.PermissionManagementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class PermissionManagementServiceImpl implements PermissionManagementService {

    private final PermissionManagementRepository permissionManagementRepository;

    public PermissionManagementServiceImpl(PermissionManagementRepository permissionManagementRepository) {
        this.permissionManagementRepository = permissionManagementRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionListItemDto> list(String keyword, String moduleCode, Boolean enabled) {
        Long tenantId = currentManagedTenantId();
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String normalizedModuleCode = StringUtils.hasText(moduleCode) ? moduleCode.trim() : null;
        return permissionManagementRepository.findPermissionList(tenantId, normalizedKeyword, normalizedModuleCode, enabled)
            .stream()
            .map(row -> new PermissionListItemDto(
                row.getId(),
                row.getPermissionCode(),
                row.getPermissionName(),
                row.getModuleCode(),
                Boolean.TRUE.equals(row.getEnabled()),
                row.getBoundRoleCount() == null ? 0 : row.getBoundRoleCount(),
                row.getUpdatedAt()
            ))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PermissionDetailDto> get(Long permissionId) {
        Long tenantId = currentManagedTenantId();
        return permissionManagementRepository.findPermissionDetail(tenantId, permissionId)
            .map(row -> new PermissionDetailDto(
                row.getId(),
                row.getPermissionCode(),
                row.getPermissionName(),
                row.getModuleCode(),
                Boolean.TRUE.equals(row.getEnabled()),
                row.getUpdatedAt(),
                permissionManagementRepository.findBoundRoles(tenantId, permissionId).stream()
                    .map(binding -> new PermissionRoleBindingDto(
                        binding.getRoleId(),
                        binding.getRoleCode(),
                        binding.getRoleName()
                    ))
                    .toList()
            ));
    }

    @Override
    @Transactional
    public boolean updateEnabled(Long permissionId, boolean enabled) {
        int updated = permissionManagementRepository.updatePermissionEnabled(
            currentManagedTenantId(),
            permissionId,
            enabled,
            LocalDateTime.now()
        );
        return updated > 0;
    }

    private Long currentManagedTenantId() {
        return TenantContext.isPlatformScope() ? null : TenantContext.getActiveTenantId();
    }
}
