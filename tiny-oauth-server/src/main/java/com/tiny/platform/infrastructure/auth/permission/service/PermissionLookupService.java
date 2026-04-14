package com.tiny.platform.infrastructure.auth.permission.service;

import com.tiny.platform.infrastructure.auth.resource.dto.PermissionOptionDto;

import java.util.List;

public interface PermissionLookupService {

    List<PermissionOptionDto> findPermissionOptions(String keyword, int limit);
}

