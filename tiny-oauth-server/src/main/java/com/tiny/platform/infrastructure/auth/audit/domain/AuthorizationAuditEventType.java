package com.tiny.platform.infrastructure.auth.audit.domain;

/**
 * 授权审计事件类型常量。
 */
public final class AuthorizationAuditEventType {

    private AuthorizationAuditEventType() {}

    // 角色赋权
    public static final String ROLE_ASSIGNMENT_GRANT = "ROLE_ASSIGNMENT_GRANT";
    public static final String ROLE_ASSIGNMENT_REVOKE = "ROLE_ASSIGNMENT_REVOKE";
    public static final String ROLE_ASSIGNMENT_REPLACE = "ROLE_ASSIGNMENT_REPLACE";

    // 数据范围
    public static final String DATA_SCOPE_UPSERT = "DATA_SCOPE_UPSERT";
    public static final String DATA_SCOPE_DELETE = "DATA_SCOPE_DELETE";
    public static final String DATA_SCOPE_ITEM_REPLACE = "DATA_SCOPE_ITEM_REPLACE";

    // RBAC3 约束规则
    public static final String CONSTRAINT_RULE_UPSERT = "CONSTRAINT_RULE_UPSERT";
    public static final String CONSTRAINT_RULE_DELETE = "CONSTRAINT_RULE_DELETE";

    // RBAC3 约束违例
    public static final String CONSTRAINT_VIOLATION = "CONSTRAINT_VIOLATION";

    // 组织/部门
    public static final String ORG_UNIT_CREATE = "ORG_UNIT_CREATE";
    public static final String ORG_UNIT_UPDATE = "ORG_UNIT_UPDATE";
    public static final String ORG_UNIT_DELETE = "ORG_UNIT_DELETE";

    // 用户归属
    public static final String USER_UNIT_ASSIGN = "USER_UNIT_ASSIGN";
    public static final String USER_UNIT_REMOVE = "USER_UNIT_REMOVE";
    public static final String USER_UNIT_SET_PRIMARY = "USER_UNIT_SET_PRIMARY";

    // 角色资源分配
    public static final String ROLE_RESOURCE_ASSIGN = "ROLE_RESOURCE_ASSIGN";

    // 租户治理
    public static final String TENANT_CREATE = "TENANT_CREATE";
    public static final String TENANT_UPDATE = "TENANT_UPDATE";
    public static final String TENANT_FREEZE = "TENANT_FREEZE";
    public static final String TENANT_UNFREEZE = "TENANT_UNFREEZE";
    public static final String TENANT_DECOMMISSION = "TENANT_DECOMMISSION";
    public static final String TENANT_DELETE = "TENANT_DELETE";
    public static final String PLATFORM_TEMPLATE_INITIALIZE = "PLATFORM_TEMPLATE_INITIALIZE";
    public static final String PLATFORM_TEMPLATE_DIFF = "PLATFORM_TEMPLATE_DIFF";
    public static final String TENANT_LIFECYCLE_ALLOWLIST_ACCESS = "TENANT_LIFECYCLE_ALLOWLIST_ACCESS";

    // requirement-aware 授权审计（权限要求求值可追踪）
    public static final String REQUIREMENT_AWARE_ACCESS = "REQUIREMENT_AWARE_ACCESS";
}
