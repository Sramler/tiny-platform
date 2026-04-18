package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleOptionDto;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PlatformRoleLookupServiceImpl implements PlatformRoleLookupService {

    private static final String ROLE_LEVEL_PLATFORM = "PLATFORM";
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 500;

    private final RoleRepository roleRepository;

    public PlatformRoleLookupServiceImpl(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public List<PlatformRoleOptionDto> findOptions(String keyword, int limit) {
        if (!TenantContext.isPlatformScope()) {
            throw BusinessException.forbidden("平台角色候选仅支持 PLATFORM 作用域读取");
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        return roleRepository.findAll(
                platformRoleOptionSpec(normalizedKeyword),
                PageRequest.of(0, normalizeLimit(limit), Sort.by(Sort.Direction.ASC, "id"))
            ).stream()
            .map(this::toDto)
            .toList();
    }

    private Specification<Role> platformRoleOptionSpec(String keyword) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isNull(root.get("tenantId")));
            predicates.add(criteriaBuilder.equal(root.<String>get("roleLevel"), ROLE_LEVEL_PLATFORM));
            if (keyword != null) {
                String likeKeyword = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.<String>get("code")), likeKeyword),
                    criteriaBuilder.like(criteriaBuilder.lower(root.<String>get("name")), likeKeyword)
                ));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private PlatformRoleOptionDto toDto(Role role) {
        return new PlatformRoleOptionDto(
            role.getId(),
            role.getCode(),
            role.getName(),
            role.getDescription(),
            role.isEnabled(),
            role.isBuiltin(),
            role.getRiskLevel(),
            role.getApprovalMode()
        );
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
