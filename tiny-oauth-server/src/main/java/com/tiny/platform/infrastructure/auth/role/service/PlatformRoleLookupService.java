package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.dto.PlatformRoleOptionDto;

import java.util.List;

public interface PlatformRoleLookupService {

    List<PlatformRoleOptionDto> findOptions(String keyword, int limit);
}
