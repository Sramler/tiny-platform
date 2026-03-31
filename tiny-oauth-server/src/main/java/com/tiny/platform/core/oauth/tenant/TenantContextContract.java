package com.tiny.platform.core.oauth.tenant;

/**
 * 活动租户与作用域在 token / session 中的标准契约。
 *
 * <p>与 docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md 运行态最小授权上下文一致：
 * activeTenantId、activeScopeType（PLATFORM/TENANT）为可选/必填依据作用域区分。</p>
 */
public final class TenantContextContract {

    public static final String ACTIVE_TENANT_ID_HEADER = "X-Active-Tenant-Id";
    public static final String SIGNAL_SOURCE_HEADER = "X-Permission-Signal-Source";
    public static final String ACTIVE_TENANT_ID_CLAIM = "activeTenantId";
    public static final String SESSION_ACTIVE_TENANT_ID_KEY = "AUTH_ACTIVE_TENANT_ID";

    /** JWT/Session 中当前授权作用域：PLATFORM 或 TENANT。 */
    public static final String ACTIVE_SCOPE_TYPE_CLAIM = "activeScopeType";
    /** JWT/Session 中当前授权作用域 ID（TENANT=tenantId，ORG/DEPT=unitId）。 */
    public static final String ACTIVE_SCOPE_ID_CLAIM = "activeScopeId";
    /** Session 中存储的 activeScopeType，与 ACTIVE_SCOPE_TYPE_CLAIM 语义一致。 */
    public static final String SESSION_ACTIVE_SCOPE_TYPE_KEY = "AUTH_ACTIVE_SCOPE_TYPE";
    /** Session 中存储的 activeScopeId，与 ACTIVE_SCOPE_ID_CLAIM 语义一致。 */
    public static final String SESSION_ACTIVE_SCOPE_ID_KEY = "AUTH_ACTIVE_SCOPE_ID";

    /** 作用域枚举：平台级。 */
    public static final String SCOPE_TYPE_PLATFORM = "PLATFORM";
    /** 作用域枚举：租户级。 */
    public static final String SCOPE_TYPE_TENANT = "TENANT";
    /** 作用域枚举：组织级。 */
    public static final String SCOPE_TYPE_ORG = "ORG";
    /** 作用域枚举：部门级。 */
    public static final String SCOPE_TYPE_DEPT = "DEPT";

    /** 权限信号来源：测试驱动流量。 */
    public static final String SIGNAL_SOURCE_TEST = "TEST";
    /** 权限信号来源：真实运行期流量。 */
    public static final String SIGNAL_SOURCE_RUNTIME = "RUNTIME";

    /**
     * ORG/DEPT active scope 校验失败时的统一错误码（Filter JSON / 与文档对齐）。
     * Session 路径：{@code 403}；Bearer 路径：{@code 401} + {@code WWW-Authenticate}。
     */
    public static final String ERROR_INVALID_ACTIVE_SCOPE = "invalid_active_scope";

    /**
     * 历史错误码：曾用于拒绝一切 Bearer 写路径；M4 写路径已支持，保留常量仅兼容旧客户端解析。
     * 写成功时以响应字段 {@code tokenRefreshRequired} / {@code newActiveScopeType} / {@code newActiveScopeId} 为准。
     */
    public static final String ERROR_ACTIVE_SCOPE_SWITCH_REQUIRES_SESSION_PRINCIPAL =
        "active_scope_switch_requires_session_principal";

    /** JWT {@code userId} claim 与 {@code sub} 对应用户主键不一致。 */
    public static final String ERROR_BEARER_SUBJECT_USER_MISMATCH = "bearer_subject_user_mismatch";

    private TenantContextContract() {
    }
}
