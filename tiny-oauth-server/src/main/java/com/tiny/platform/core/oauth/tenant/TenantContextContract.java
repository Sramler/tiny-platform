package com.tiny.platform.core.oauth.tenant;

/**
 * 活动租户与作用域在 token / session 中的标准契约。
 *
 * <p>与 docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md 运行态最小授权上下文一致：
 * activeTenantId、activeScopeType（PLATFORM/TENANT）为可选/必填依据作用域区分。</p>
 */
public final class TenantContextContract {

    public static final String ACTIVE_TENANT_ID_HEADER = "X-Active-Tenant-Id";
    public static final String ACTIVE_TENANT_ID_CLAIM = "activeTenantId";
    public static final String SESSION_ACTIVE_TENANT_ID_KEY = "AUTH_ACTIVE_TENANT_ID";

    /** JWT/Session 中当前授权作用域：PLATFORM 或 TENANT。 */
    public static final String ACTIVE_SCOPE_TYPE_CLAIM = "activeScopeType";
    /** Session 中存储的 activeScopeType，与 ACTIVE_SCOPE_TYPE_CLAIM 语义一致。 */
    public static final String SESSION_ACTIVE_SCOPE_TYPE_KEY = "AUTH_ACTIVE_SCOPE_TYPE";

    /** 作用域枚举：平台级。 */
    public static final String SCOPE_TYPE_PLATFORM = "PLATFORM";
    /** 作用域枚举：租户级。 */
    public static final String SCOPE_TYPE_TENANT = "TENANT";

    private TenantContextContract() {
    }
}
