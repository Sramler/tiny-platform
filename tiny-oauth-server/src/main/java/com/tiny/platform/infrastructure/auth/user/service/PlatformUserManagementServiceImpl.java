package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.infrastructure.auth.user.domain.PlatformUserProfile;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserCreateDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserDetailDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserListItemDto;
import com.tiny.platform.infrastructure.auth.user.repository.PlatformUserDetailProjection;
import com.tiny.platform.infrastructure.auth.user.repository.PlatformUserListProjection;
import com.tiny.platform.infrastructure.auth.user.repository.PlatformUserProfileRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

@Service
public class PlatformUserManagementServiceImpl implements PlatformUserManagementService {

    private final PlatformUserProfileRepository platformUserProfileRepository;
    private final UserRepository userRepository;

    public PlatformUserManagementServiceImpl(PlatformUserProfileRepository platformUserProfileRepository,
                                             UserRepository userRepository) {
        this.platformUserProfileRepository = platformUserProfileRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PlatformUserListItemDto> list(String keyword, Boolean enabled, String status, Pageable pageable) {
        String normalizedKeyword = normalize(keyword);
        String normalizedStatus = normalizeStatus(status, false);
        return platformUserProfileRepository.findPage(normalizedKeyword, enabled, normalizedStatus, pageable)
            .map(this::toListItemDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlatformUserDetailDto> get(Long userId) {
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }
        return platformUserProfileRepository.findDetailByUserId(userId)
            .map(this::toDetailDto);
    }

    @Override
    @Transactional
    public PlatformUserDetailDto create(PlatformUserCreateDto request) {
        if (request == null || request.userId() == null || request.userId() <= 0) {
            throw BusinessException.validationError("请求体必须提供合法 userId");
        }
        String normalizedStatus = normalizeStatus(request.status(), true);
        if (platformUserProfileRepository.existsByUserId(request.userId())) {
            throw BusinessException.alreadyExists("平台用户档案已存在");
        }
        var user = userRepository.findById(request.userId())
            .orElseThrow(() -> BusinessException.notFound("用户不存在"));

        PlatformUserProfile profile = new PlatformUserProfile();
        profile.setUserId(user.getId());
        profile.setStatus(normalizedStatus);
        String displayName = normalize(request.displayName());
        if (displayName == null) {
            displayName = normalize(user.getNickname());
        }
        profile.setDisplayName(displayName);
        platformUserProfileRepository.saveAndFlush(profile);

        return get(user.getId()).orElseThrow(() -> new IllegalStateException("平台用户档案创建后读取失败"));
    }

    @Override
    @Transactional
    public boolean updateStatus(Long userId, String status) {
        if (userId == null || userId <= 0) {
            return false;
        }
        return platformUserProfileRepository.findByUserId(userId)
            .map(profile -> {
                profile.setStatus(normalizeStatus(status, true));
                platformUserProfileRepository.save(profile);
                return true;
            })
            .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPlatformUserActive(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        return platformUserProfileRepository.existsByUserIdAndStatus(userId, PlatformUserProfile.STATUS_ACTIVE);
    }

    private PlatformUserListItemDto toListItemDto(PlatformUserListProjection row) {
        return new PlatformUserListItemDto(
            row.getUserId(),
            row.getUsername(),
            row.getNickname(),
            row.getDisplayName(),
            toBoolean(row.getUserEnabled()),
            row.getPlatformStatus(),
            toBoolean(row.getHasPlatformRoleAssignment()),
            row.getUpdatedAt()
        );
    }

    private PlatformUserDetailDto toDetailDto(PlatformUserDetailProjection row) {
        return new PlatformUserDetailDto(
            row.getUserId(),
            row.getUsername(),
            row.getNickname(),
            row.getDisplayName(),
            row.getEmail(),
            row.getPhone(),
            toBoolean(row.getUserEnabled()),
            toBoolean(row.getAccountNonExpired()),
            toBoolean(row.getAccountNonLocked()),
            toBoolean(row.getCredentialsNonExpired()),
            row.getPlatformStatus(),
            toBoolean(row.getHasPlatformRoleAssignment()),
            row.getLastLoginAt(),
            row.getCreatedAt(),
            row.getUpdatedAt()
        );
    }

    private boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        if (value instanceof CharSequence charSequence) {
            String normalized = charSequence.toString().trim();
            if ("1".equals(normalized) || "true".equalsIgnoreCase(normalized) || "yes".equalsIgnoreCase(normalized)) {
                return true;
            }
            if ("0".equals(normalized) || "false".equalsIgnoreCase(normalized) || "no".equalsIgnoreCase(normalized)) {
                return false;
            }
        }
        throw new IllegalArgumentException("unsupported boolean projection value type: " + value.getClass().getName());
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status, boolean defaultActive) {
        String normalized = normalize(status);
        if (normalized == null) {
            return defaultActive ? PlatformUserProfile.STATUS_ACTIVE : null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (PlatformUserProfile.STATUS_ACTIVE.equals(upper) || PlatformUserProfile.STATUS_DISABLED.equals(upper)) {
            return upper;
        }
        throw BusinessException.validationError("platform status 仅支持 ACTIVE 或 DISABLED");
    }
}
