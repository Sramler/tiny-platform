package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.infrastructure.auth.user.dto.UserRequestDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlatformTenantUserManagementService {

    Page<UserResponseDto> list(Long tenantId, UserRequestDto query, Pageable pageable);

    Optional<UserResponseDto> get(Long tenantId, Long userId);
}
