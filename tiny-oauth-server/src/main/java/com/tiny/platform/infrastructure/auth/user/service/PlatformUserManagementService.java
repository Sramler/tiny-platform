package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserCreateDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserDetailDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserListItemDto;
import com.tiny.platform.infrastructure.auth.user.dto.PlatformUserManagementDtos.PlatformUserRoleDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface PlatformUserManagementService {

    Page<PlatformUserListItemDto> list(String keyword, Boolean enabled, String status, Pageable pageable);

    Optional<PlatformUserDetailDto> get(Long userId);

    PlatformUserDetailDto create(PlatformUserCreateDto request);

    boolean updateStatus(Long userId, String status);

    List<PlatformUserRoleDto> getRoles(Long userId);

    List<PlatformUserRoleDto> replaceRoles(Long userId, List<Long> roleIds);

    boolean isPlatformUserActive(Long userId);
}
