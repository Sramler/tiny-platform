package com.tiny.platform.infrastructure.auth.resource.support;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import com.tiny.platform.infrastructure.auth.resource.enums.ResourceType;
import org.springframework.util.StringUtils;

/**
 * 平台级控制面资源策略。
 *
 * <p>这类资源只应存在于默认平台租户，并且只对平台管理员开放。当前先收口
 * “租户管理” 与 “幂等治理” 两类平台入口，避免它们在普通租户模板中被复制或在菜单树里暴露。
 */
public final class PlatformControlPlaneResourcePolicy {
    private static final String TENANT_MENU_NAME = "tenant";
    private static final String TENANT_MENU_URL = "/system/tenant";
    private static final String TENANT_MENU_URI = "/sys/tenants";
    private static final String TENANT_PERMISSION_PREFIX = "system:tenant:";

    private static final String IDEMPOTENT_MENU_NAME = "idempotentOps";
    private static final String IDEMPOTENT_MENU_URL = "/ops/idempotent";
    private static final String IDEMPOTENT_MENU_URI = "/metrics/idempotent";
    private static final String IDEMPOTENT_PERMISSION_PREFIX = "idempotent:";

    private PlatformControlPlaneResourcePolicy() {
    }

    public static boolean isPlatformOnlyResource(Resource resource) {
        if (resource == null) {
            return false;
        }
        return isPlatformOnlyResource(
            resource.getType(),
            resource.getName(),
            resource.getPermission(),
            resource.getUrl(),
            resource.getUri()
        );
    }

    public static boolean isPlatformOnlyResource(ResourceResponseDto resource) {
        if (resource == null) {
            return false;
        }
        return isPlatformOnlyResource(
            resource.getType() == null ? null : ResourceType.fromCode(resource.getType()),
            resource.getName(),
            resource.getPermission(),
            resource.getUrl(),
            resource.getUri()
        );
    }

    public static boolean isTenantManagementResource(Resource resource) {
        return resource != null && isTenantManagementResource(
            resource.getType(),
            resource.getName(),
            resource.getPermission(),
            resource.getUrl(),
            resource.getUri()
        );
    }

    public static boolean isIdempotentOpsResource(Resource resource) {
        return resource != null && isIdempotentOpsResource(
            resource.getType(),
            resource.getName(),
            resource.getPermission(),
            resource.getUrl(),
            resource.getUri()
        );
    }

    public static boolean isTenantManagementResource(ResourceResponseDto resource) {
        return resource != null && isTenantManagementResource(
            resource.getType() == null ? null : ResourceType.fromCode(resource.getType()),
            resource.getName(),
            resource.getPermission(),
            resource.getUrl(),
            resource.getUri()
        );
    }

    public static boolean isIdempotentOpsResource(ResourceResponseDto resource) {
        return resource != null && isIdempotentOpsResource(
            resource.getType() == null ? null : ResourceType.fromCode(resource.getType()),
            resource.getName(),
            resource.getPermission(),
            resource.getUrl(),
            resource.getUri()
        );
    }

    public static boolean isPlatformOnlyResource(String name, String permission, String url, String uri) {
        return isPlatformOnlyResource(null, name, permission, url, uri);
    }

    private static boolean isPlatformOnlyResource(ResourceType type, String name, String permission, String url, String uri) {
        return isTenantManagementResource(type, name, permission, url, uri)
            || isIdempotentOpsResource(type, name, permission, url, uri);
    }

    private static boolean isTenantManagementResource(ResourceType type, String name, String permission, String url, String uri) {
        return matches(name, TENANT_MENU_NAME)
            || matchesPathPrefix(url, TENANT_MENU_URL)
            || matchesPathPrefix(uri, TENANT_MENU_URI)
            || matchesPermissionPrefix(type, permission, url, uri, TENANT_PERMISSION_PREFIX);
    }

    private static boolean isIdempotentOpsResource(ResourceType type, String name, String permission, String url, String uri) {
        return matches(name, IDEMPOTENT_MENU_NAME)
            || matchesPathPrefix(url, IDEMPOTENT_MENU_URL)
            || matchesPathPrefix(uri, IDEMPOTENT_MENU_URI)
            || matchesPermissionPrefix(type, permission, url, uri, IDEMPOTENT_PERMISSION_PREFIX);
    }

    private static boolean matches(String actual, String expected) {
        return StringUtils.hasText(actual) && expected.equals(actual.trim());
    }

    private static boolean matchesPathPrefix(String actual, String expectedPrefix) {
        if (!StringUtils.hasText(actual)) {
            return false;
        }
        String normalized = actual.trim();
        return normalized.equals(expectedPrefix) || normalized.startsWith(expectedPrefix + "/");
    }

    private static boolean matchesPermissionPrefix(ResourceType type,
                                                   String permission,
                                                   String url,
                                                   String uri,
                                                   String expectedPrefix) {
        if (!StringUtils.hasText(permission) || !permission.trim().startsWith(expectedPrefix)) {
            return false;
        }
        if (type == ResourceType.BUTTON || type == ResourceType.API) {
            return true;
        }
        if (type == ResourceType.MENU || type == ResourceType.DIRECTORY) {
            return false;
        }
        return !StringUtils.hasText(url) && !StringUtils.hasText(uri);
    }
}
