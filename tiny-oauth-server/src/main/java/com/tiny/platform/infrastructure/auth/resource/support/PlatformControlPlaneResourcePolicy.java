package com.tiny.platform.infrastructure.auth.resource.support;

import com.tiny.platform.infrastructure.auth.resource.domain.Resource;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceResponseDto;
import org.springframework.util.StringUtils;

/**
 * 平台级控制面资源策略。
 *
 * <p>这类资源只应存在于默认平台租户，并且只对平台管理员开放。当前先收口
 * “租户管理” 与 “幂等治理” 两类平台入口，避免它们在普通租户模板中被复制或在菜单树里暴露。
 */
public final class PlatformControlPlaneResourcePolicy {
    private static final String TENANT_MENU_NAME = "tenant";
    private static final String TENANT_MENU_PERMISSION = "system:tenant:list";
    private static final String TENANT_MENU_URL = "/system/tenant";
    private static final String TENANT_MENU_URI = "/sys/tenants";

    private static final String IDEMPOTENT_MENU_NAME = "idempotentOps";
    private static final String IDEMPOTENT_MENU_PERMISSION = "idempotent:ops:view";
    private static final String IDEMPOTENT_MENU_URL = "/ops/idempotent";
    private static final String IDEMPOTENT_MENU_URI = "/metrics/idempotent";

    private PlatformControlPlaneResourcePolicy() {
    }

    public static boolean isPlatformOnlyResource(Resource resource) {
        if (resource == null) {
            return false;
        }
        return isPlatformOnlyResource(resource.getName(), resource.getPermission(), resource.getUrl(), resource.getUri());
    }

    public static boolean isPlatformOnlyResource(ResourceResponseDto resource) {
        if (resource == null) {
            return false;
        }
        return isPlatformOnlyResource(resource.getName(), resource.getPermission(), resource.getUrl(), resource.getUri());
    }

    public static boolean isTenantManagementResource(Resource resource) {
        return resource != null && isTenantManagementResource(resource.getName(), resource.getPermission(), resource.getUrl(), resource.getUri());
    }

    public static boolean isIdempotentOpsResource(Resource resource) {
        return resource != null && isIdempotentOpsResource(resource.getName(), resource.getPermission(), resource.getUrl(), resource.getUri());
    }

    public static boolean isTenantManagementResource(ResourceResponseDto resource) {
        return resource != null && isTenantManagementResource(resource.getName(), resource.getPermission(), resource.getUrl(), resource.getUri());
    }

    public static boolean isIdempotentOpsResource(ResourceResponseDto resource) {
        return resource != null && isIdempotentOpsResource(resource.getName(), resource.getPermission(), resource.getUrl(), resource.getUri());
    }

    public static boolean isPlatformOnlyResource(String name, String permission, String url, String uri) {
        return isTenantManagementResource(name, permission, url, uri)
            || isIdempotentOpsResource(name, permission, url, uri);
    }

    private static boolean isTenantManagementResource(String name, String permission, String url, String uri) {
        return matches(name, TENANT_MENU_NAME)
            || matches(permission, TENANT_MENU_PERMISSION)
            || matches(url, TENANT_MENU_URL)
            || matches(uri, TENANT_MENU_URI);
    }

    private static boolean isIdempotentOpsResource(String name, String permission, String url, String uri) {
        return matches(name, IDEMPOTENT_MENU_NAME)
            || matches(permission, IDEMPOTENT_MENU_PERMISSION)
            || matches(url, IDEMPOTENT_MENU_URL)
            || matches(uri, IDEMPOTENT_MENU_URI);
    }

    private static boolean matches(String actual, String expected) {
        return StringUtils.hasText(actual) && expected.equals(actual.trim());
    }
}
