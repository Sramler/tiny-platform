package com.tiny.platform.infrastructure.tenant.service;

import com.tiny.platform.infrastructure.tenant.domain.Tenant;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantRequestDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantResponseDto;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantServiceImpl implements TenantService {
    private static final Pattern TENANT_CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,31}$");

    private final TenantRepository tenantRepository;
    private final TenantBootstrapService tenantBootstrapService;

    public TenantServiceImpl(TenantRepository tenantRepository, TenantBootstrapService tenantBootstrapService) {
        this.tenantRepository = tenantRepository;
        this.tenantBootstrapService = tenantBootstrapService;
    }

    @Override
    public Page<TenantResponseDto> list(TenantRequestDto query, Pageable pageable) {
        Specification<Tenant> spec = (root, cq, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();

            if (query.getCode() != null && !query.getCode().trim().isEmpty()) {
                predicates.add(cb.like(root.get("code"), "%" + query.getCode().trim() + "%"));
            }

            if (query.getName() != null && !query.getName().trim().isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + query.getName().trim() + "%"));
            }

            if (query.getEnabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), query.getEnabled()));
            }

            if (query.getDomain() != null && !query.getDomain().trim().isEmpty()) {
                predicates.add(cb.like(root.get("domain"), "%" + query.getDomain().trim() + "%"));
            }

            if (query.getIncludeDeleted() == null || !query.getIncludeDeleted()) {
                predicates.add(cb.isNull(root.get("deletedAt")));
            }

            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return tenantRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Override
    public Optional<Tenant> findById(Long id) {
        return tenantRepository.findById(id).filter(t -> t.getDeletedAt() == null);
    }

    @Override
    @Transactional
    public TenantResponseDto create(TenantCreateUpdateDto dto) {
        String normalizedCode = normalizeTenantCode(dto.getCode());
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new RuntimeException("租户名称不能为空");
        }
        if (tenantRepository.existsByCode(normalizedCode)) {
            throw new RuntimeException("租户编码已存在");
        }
        String domain = normalizeNullable(dto.getDomain());
        if (domain != null && tenantRepository.existsByDomain(domain)) {
            throw new RuntimeException("租户域名已存在");
        }
        Tenant tenant = new Tenant();
        tenant.setCode(normalizedCode);
        tenant.setName(dto.getName().trim());
        tenant.setDomain(domain);
        tenant.setEnabled(dto.getEnabled() == null || dto.getEnabled());
        tenant.setPlanCode(normalizeNullable(dto.getPlanCode()));
        tenant.setExpiresAt(parseDateTime(dto.getExpiresAt()));
        tenant.setMaxUsers(dto.getMaxUsers());
        tenant.setMaxStorageGb(dto.getMaxStorageGb());
        tenant.setContactName(normalizeNullable(dto.getContactName()));
        tenant.setContactEmail(normalizeNullable(dto.getContactEmail()));
        tenant.setContactPhone(normalizeNullable(dto.getContactPhone()));
        tenant.setRemark(dto.getRemark());
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenant.setLifecycleStatus("ACTIVE");
        Tenant saved = tenantRepository.save(tenant);
        tenantBootstrapService.bootstrapFromDefaultTenant(saved);
        return toDto(saved);
    }

    @Override
    @Transactional
    public TenantResponseDto update(Long id, TenantCreateUpdateDto dto) {
        Tenant tenant = tenantRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("租户不存在"));

        if (tenant.getDeletedAt() != null) {
            throw new RuntimeException("租户已删除");
        }

        if (dto.getCode() != null && !dto.getCode().trim().isEmpty()) {
            String normalizedCode = normalizeTenantCode(dto.getCode());
            if (!normalizedCode.equals(tenant.getCode()) && tenantRepository.existsByCode(normalizedCode)) {
                throw new RuntimeException("租户编码已存在");
            }
            tenant.setCode(normalizedCode);
        }

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            tenant.setName(dto.getName().trim());
        }

        if (dto.getDomain() != null) {
            String domain = normalizeNullable(dto.getDomain());
            if (domain != null && !domain.equals(tenant.getDomain()) && tenantRepository.existsByDomain(domain)) {
                throw new RuntimeException("租户域名已存在");
            }
            tenant.setDomain(domain);
        }

        if (dto.getEnabled() != null) {
            tenant.setEnabled(dto.getEnabled());
        }

        if (dto.getLifecycleStatus() != null && !dto.getLifecycleStatus().isBlank()) {
            String status = dto.getLifecycleStatus().trim().toUpperCase(Locale.ROOT);
            if (!status.equals("ACTIVE") && !status.equals("FROZEN") && !status.equals("DECOMMISSIONED")) {
                throw new RuntimeException("无效的租户生命周期状态");
            }
            tenant.setLifecycleStatus(status);
        }

        if (dto.getPlanCode() != null) {
            tenant.setPlanCode(normalizeNullable(dto.getPlanCode()));
        }

        if (dto.getExpiresAt() != null) {
            tenant.setExpiresAt(parseDateTime(dto.getExpiresAt()));
        }

        if (dto.getMaxUsers() != null) {
            tenant.setMaxUsers(dto.getMaxUsers());
        }

        if (dto.getMaxStorageGb() != null) {
            tenant.setMaxStorageGb(dto.getMaxStorageGb());
        }

        if (dto.getContactName() != null) {
            tenant.setContactName(normalizeNullable(dto.getContactName()));
        }

        if (dto.getContactEmail() != null) {
            tenant.setContactEmail(normalizeNullable(dto.getContactEmail()));
        }

        if (dto.getContactPhone() != null) {
            tenant.setContactPhone(normalizeNullable(dto.getContactPhone()));
        }

        if (dto.getRemark() != null) {
            tenant.setRemark(dto.getRemark());
        }

        tenant.setUpdatedAt(LocalDateTime.now());
        Tenant saved = tenantRepository.save(tenant);
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        tenantRepository.findById(id).ifPresent(tenant -> {
            if (tenant.getDeletedAt() == null) {
                tenant.setEnabled(false);
                tenant.setDeletedAt(LocalDateTime.now());
                tenant.setUpdatedAt(LocalDateTime.now());
                tenantRepository.save(tenant);
            }
        });
    }

    private TenantResponseDto toDto(Tenant tenant) {
        TenantResponseDto dto = new TenantResponseDto();
        BeanUtils.copyProperties(tenant, dto);
        if (tenant.getExpiresAt() != null) {
            dto.setExpiresAt(tenant.getExpiresAt().toString());
        }
        return dto;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value.trim());
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeTenantCode(String rawCode) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            throw new RuntimeException("租户编码不能为空");
        }
        String normalizedCode = rawCode.trim().toLowerCase(Locale.ROOT);
        if (!TENANT_CODE_PATTERN.matcher(normalizedCode).matches()) {
            throw new RuntimeException("租户编码格式不正确，仅支持小写字母、数字和中划线，长度 2-32");
        }
        return normalizedCode;
    }
}
