package com.tiny.platform.application.controller.menu.runtime;

import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;

import java.time.Instant;
import java.util.List;

public record MenuRuntimeTreeCacheEntry(
    String cacheKey,
    String etag,
    String menuConfigVersion,
    String permissionsVersion,
    List<ResourceResponseDto> menus,
    Instant createdAt
) {
}
