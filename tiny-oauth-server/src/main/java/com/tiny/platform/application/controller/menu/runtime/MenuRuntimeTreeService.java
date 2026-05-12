package com.tiny.platform.application.controller.menu.runtime;

import com.tiny.platform.core.oauth.security.CurrentActorResolver;
import com.tiny.platform.core.oauth.security.PermissionVersionService;
import com.tiny.platform.core.oauth.tenant.ActiveScope;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.menu.service.MenuService;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

@Service
public class MenuRuntimeTreeService {

    private static final String ETAG_SCHEMA_VERSION = "menu-tree-v1";

    private final MenuService menuService;
    private final MenuConfigVersionProvider menuConfigVersionProvider;
    private final MenuRuntimeTreeCacheStore cacheStore;
    private final PermissionVersionService permissionVersionService;

    public MenuRuntimeTreeService(MenuService menuService,
                                  MenuConfigVersionProvider menuConfigVersionProvider,
                                  MenuRuntimeTreeCacheStore cacheStore,
                                  PermissionVersionService permissionVersionService) {
        this.menuService = menuService;
        this.menuConfigVersionProvider = menuConfigVersionProvider;
        this.cacheStore = cacheStore;
        this.permissionVersionService = permissionVersionService;
    }

    public MenuRuntimeTreeSnapshot loadRuntimeTree(Authentication authentication, String ifNoneMatch) {
        MenuRuntimeTreeContext context = resolveContext(authentication);
        String etag = quoteEtag(sha256Hex(String.join("|",
            ETAG_SCHEMA_VERSION,
            context.userKey(),
            String.valueOf(context.activeTenantId()),
            context.activeScopeType(),
            String.valueOf(context.activeScopeId()),
            context.permissionsVersion(),
            context.menuConfigVersion()
        )));
        String cacheKey = sha256Hex("menu-runtime-cache|" + etag + "|" + context.userKey());

        if (matchesIfNoneMatch(ifNoneMatch, etag)) {
            return MenuRuntimeTreeSnapshot.notModified(context, etag, cacheKey);
        }

        var cached = cacheStore.get(cacheKey, etag);
        if (cached.isPresent()) {
            MenuRuntimeTreeCacheEntry entry = cached.get();
            return new MenuRuntimeTreeSnapshot(
                entry.menus(),
                entry.etag(),
                entry.menuConfigVersion(),
                entry.permissionsVersion(),
                entry.cacheKey(),
                false,
                true
            );
        }

        List<ResourceResponseDto> menus = menuService.menuTree();
        cacheStore.put(new MenuRuntimeTreeCacheEntry(
            cacheKey,
            etag,
            context.menuConfigVersion(),
            context.permissionsVersion(),
            menus,
            Instant.now()
        ));
        return new MenuRuntimeTreeSnapshot(
            menus,
            etag,
            context.menuConfigVersion(),
            context.permissionsVersion(),
            cacheKey,
            false,
            false
        );
    }

    private MenuRuntimeTreeContext resolveContext(Authentication authentication) {
        Long activeTenantId = ActiveTenantResponseSupport.resolveActiveTenantId(authentication);
        ActiveScope activeScope = ActiveTenantResponseSupport.resolveActiveScopeFromRequestContext();
        String scopeType = activeScope != null && activeScope.scopeType() != null
            ? activeScope.scopeType()
            : TenantContext.isPlatformScope()
                ? TenantContextContract.SCOPE_TYPE_PLATFORM
                : TenantContextContract.SCOPE_TYPE_TENANT;
        Long scopeId = activeScope != null ? activeScope.scopeId() : activeTenantId;
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType)) {
            scopeId = null;
        }

        String permissionsVersion = normalizeVersion(CurrentActorResolver.resolvePermissionsVersionForResponse(
            authentication,
            activeTenantId,
            scopeType,
            scopeId,
            permissionVersionService
        ));
        String menuConfigVersion = normalizeVersion(menuConfigVersionProvider.resolveMenuConfigVersion(
            activeTenantId,
            scopeType,
            scopeId
        ));
        return new MenuRuntimeTreeContext(
            resolveUserKey(authentication),
            activeTenantId,
            scopeType,
            scopeId,
            permissionsVersion,
            menuConfigVersion
        );
    }

    private static String resolveUserKey(Authentication authentication) {
        Long userId = CurrentActorResolver.resolveUserId(authentication);
        if (userId != null && userId > 0) {
            return "id:" + userId;
        }
        String name = authentication == null ? null : authentication.getName();
        return "name:" + (name == null || name.isBlank() ? "anonymous" : name.trim());
    }

    private static String normalizeVersion(String version) {
        if (version == null || version.isBlank()) {
            return "none";
        }
        return version.trim();
    }

    static boolean matchesIfNoneMatch(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank() || etag == null || etag.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String trimmed = candidate.trim();
            if ("*".equals(trimmed) || etag.equals(trimmed) || stripWeakPrefix(etag).equals(stripWeakPrefix(trimmed))) {
                return true;
            }
        }
        return false;
    }

    private static String stripWeakPrefix(String value) {
        return value != null && value.startsWith("W/") ? value.substring(2) : value;
    }

    private static String quoteEtag(String value) {
        return "\"" + value + "\"";
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
