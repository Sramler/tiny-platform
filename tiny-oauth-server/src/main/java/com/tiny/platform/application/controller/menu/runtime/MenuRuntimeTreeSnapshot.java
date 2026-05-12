package com.tiny.platform.application.controller.menu.runtime;

import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;

import java.util.List;

public record MenuRuntimeTreeSnapshot(
    List<ResourceResponseDto> menus,
    String etag,
    String menuConfigVersion,
    String permissionsVersion,
    String cacheKey,
    boolean notModified,
    boolean cacheHit
) {
    public static MenuRuntimeTreeSnapshot notModified(MenuRuntimeTreeContext context, String etag, String cacheKey) {
        return new MenuRuntimeTreeSnapshot(
            List.of(),
            etag,
            context.menuConfigVersion(),
            context.permissionsVersion(),
            cacheKey,
            true,
            true
        );
    }
}
