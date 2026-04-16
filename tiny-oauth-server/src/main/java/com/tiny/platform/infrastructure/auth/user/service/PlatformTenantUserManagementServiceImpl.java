package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.core.oauth.security.LoginFailurePolicy;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.dto.UserRequestDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import com.tiny.platform.infrastructure.auth.user.repository.TenantUserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PlatformTenantUserManagementServiceImpl implements PlatformTenantUserManagementService {

    private static final String ACTIVE = "ACTIVE";

    private final TenantUserRepository tenantUserRepository;
    private final UserRepository userRepository;
    private final LoginFailurePolicy loginFailurePolicy;

    public PlatformTenantUserManagementServiceImpl(TenantUserRepository tenantUserRepository,
                                                   UserRepository userRepository,
                                                   LoginFailurePolicy loginFailurePolicy) {
        this.tenantUserRepository = tenantUserRepository;
        this.userRepository = userRepository;
        this.loginFailurePolicy = loginFailurePolicy;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponseDto> list(Long tenantId, UserRequestDto query, Pageable pageable) {
        Long normalizedTenantId = requireTenantId(tenantId);
        LinkedHashSet<Long> visibleUserIds = new LinkedHashSet<>(
            tenantUserRepository.findUserIdsByTenantIdAndStatus(normalizedTenantId, ACTIVE)
        );
        if (visibleUserIds.isEmpty()) {
            return Page.empty(pageable);
        }
        String username = normalize(query != null ? query.getUsername() : null);
        String nickname = normalize(query != null ? query.getNickname() : null);
        return userRepository.findAll((Specification<User>) (root, ignored, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("id").in(visibleUserIds));
            if (StringUtils.hasText(username)) {
                predicates.add(cb.like(root.get("username"), "%" + username + "%"));
            }
            if (StringUtils.hasText(nickname)) {
                predicates.add(cb.like(root.get("nickname"), "%" + nickname + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserResponseDto> get(Long tenantId, Long userId) {
        Long normalizedTenantId = requireTenantId(tenantId);
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }
        if (!tenantUserRepository.existsByTenantIdAndUserIdAndStatus(normalizedTenantId, userId, ACTIVE)) {
            return Optional.empty();
        }
        return userRepository.findById(userId).map(this::toDto);
    }

    private Long requireTenantId(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw BusinessException.validationError("tenantId 必须为正整数");
        }
        return tenantId;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private UserResponseDto toDto(User user) {
        LocalDateTime now = LocalDateTime.now();
        boolean temporarilyLocked = loginFailurePolicy.isTemporarilyLocked(user, now);
        return new UserResponseDto(
            user.getId(),
            user.getUsername(),
            user.getNickname(),
            user.isEnabled(),
            user.isAccountNonExpired(),
            user.isAccountNonLocked(),
            user.isCredentialsNonExpired(),
            user.getLastLoginAt(),
            user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0,
            user.getLastFailedLoginAt(),
            temporarilyLocked,
            temporarilyLocked ? loginFailurePolicy.remainingLockMinutes(user, now) : null
        );
    }
}
