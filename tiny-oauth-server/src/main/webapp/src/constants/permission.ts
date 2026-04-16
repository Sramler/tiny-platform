/**
 * 前端权限标识常量，与后端 AccessGuard 一一对应。
 *
 * 命名规范：domain:resource:action 三段式（见 92-tiny-platform-permission 规则）
 * 本文件是前端唯一真相源，禁止在 .vue / .ts 中硬编码权限字符串。
 */

// ─── 用户管理 ───
export const USER_LIST = 'system:user:list'
export const USER_VIEW = 'system:user:view'
export const USER_CREATE = 'system:user:create'
export const USER_EDIT = 'system:user:edit'
export const USER_DELETE = 'system:user:delete'
export const USER_BATCH_DELETE = 'system:user:batch-delete'
export const USER_ENABLE = 'system:user:enable'
export const USER_BATCH_ENABLE = 'system:user:batch-enable'
export const USER_DISABLE = 'system:user:disable'
export const USER_BATCH_DISABLE = 'system:user:batch-disable'
export const USER_ROLE_ASSIGN = 'system:user:role:assign'

// ─── 平台用户管理 ───
export const PLATFORM_USER_LIST = 'platform:user:list'
export const PLATFORM_USER_VIEW = 'platform:user:view'
export const PLATFORM_USER_CREATE = 'platform:user:create'
export const PLATFORM_USER_EDIT = 'platform:user:edit'
export const PLATFORM_USER_DISABLE = 'platform:user:disable'

// ─── 角色管理 ───
export const ROLE_LIST = 'system:role:list'
export const ROLE_CREATE = 'system:role:create'
export const ROLE_EDIT = 'system:role:edit'
export const ROLE_DELETE = 'system:role:delete'
export const ROLE_BATCH_DELETE = 'system:role:batch-delete'
export const ROLE_PERMISSION_ASSIGN = 'system:role:permission:assign'
export const PERMISSION_LIST = 'system:role:list'
export const PERMISSION_EDIT = 'system:role:permission:assign'

// ─── 菜单管理 ───
export const MENU_LIST = 'system:menu:list'
export const MENU_CREATE = 'system:menu:create'
export const MENU_EDIT = 'system:menu:edit'
export const MENU_DELETE = 'system:menu:delete'
export const MENU_BATCH_DELETE = 'system:menu:batch-delete'

// ─── 资源管理 ───
export const RESOURCE_LIST = 'system:resource:list'
export const RESOURCE_CREATE = 'system:resource:create'
export const RESOURCE_EDIT = 'system:resource:edit'
export const RESOURCE_DELETE = 'system:resource:delete'
export const RESOURCE_BATCH_DELETE = 'system:resource:batch-delete'

// ─── 租户管理 ───
export const TENANT_LIST = 'system:tenant:list'
export const TENANT_VIEW = 'system:tenant:view'
export const TENANT_CREATE = 'system:tenant:create'
export const TENANT_EDIT = 'system:tenant:edit'
export const TENANT_TEMPLATE_INITIALIZE = 'system:tenant:template:initialize'
export const TENANT_FREEZE = 'system:tenant:freeze'
export const TENANT_UNFREEZE = 'system:tenant:unfreeze'
export const TENANT_DECOMMISSION = 'system:tenant:decommission'
export const TENANT_DELETE = 'system:tenant:delete'

// ─── 字典管理（租户级） ───
export const DICT_TYPE_LIST = 'dict:type:list'
export const DICT_TYPE_CREATE = 'dict:type:create'
export const DICT_TYPE_EDIT = 'dict:type:edit'
export const DICT_TYPE_DELETE = 'dict:type:delete'
export const DICT_ITEM_LIST = 'dict:item:list'
export const DICT_ITEM_CREATE = 'dict:item:create'
export const DICT_ITEM_EDIT = 'dict:item:edit'
export const DICT_ITEM_DELETE = 'dict:item:delete'

// ─── 字典管理（平台级） ───
export const DICT_PLATFORM_MANAGE = 'dict:platform:manage'

// ─── 数据导出 ───
export const EXPORT_VIEW = 'system:export:view'
export const EXPORT_MANAGE = 'system:export:manage'

// ─── 幂等治理 ───
export const IDEMPOTENT_OPS_VIEW = 'idempotent:ops:view'

// ─── 调度中心 ───
export const SCHEDULING_CONSOLE_VIEW = 'scheduling:console:view'
export const SCHEDULING_CONSOLE_CONFIG = 'scheduling:console:config'
export const SCHEDULING_RUN_CONTROL = 'scheduling:run:control'
export const SCHEDULING_AUDIT_VIEW = 'scheduling:audit:view'
export const SCHEDULING_CLUSTER_VIEW = 'scheduling:cluster:view'
export const SCHEDULING_WILDCARD = 'scheduling:*'

// ─── 工作流 ───
export const WORKFLOW_CONSOLE_VIEW = 'workflow:console:view'
export const WORKFLOW_CONSOLE_CONFIG = 'workflow:console:config'
export const WORKFLOW_INSTANCE_CONTROL = 'workflow:instance:control'
export const WORKFLOW_TENANT_MANAGE = 'workflow:tenant:manage'

// ─── RBAC3 约束控制面 ───
export const ROLE_CONSTRAINT_VIEW = 'system:role:constraint:view'
export const ROLE_CONSTRAINT_EDIT = 'system:role:constraint:edit'
export const ROLE_CONSTRAINT_VIOLATION_VIEW = 'system:role:constraint:violation:view'

// ─── 数据范围管理 ───
export const DATASCOPE_VIEW = 'system:datascope:view'
export const DATASCOPE_EDIT = 'system:datascope:edit'

// ─── 授权审计 ───
export const AUDIT_AUTH_VIEW = 'system:audit:auth:view'
export const AUDIT_AUTH_EXPORT = 'system:audit:auth:export'
export const AUDIT_AUTH_PURGE = 'system:audit:auth:purge'

// ─── 认证审计 ───
export const AUDIT_AUTHENTICATION_VIEW = 'system:audit:authentication:view'
export const AUDIT_AUTHENTICATION_EXPORT = 'system:audit:authentication:export'

// ─── 组织/部门管理 ───
export const ORG_LIST = 'system:org:list'
export const ORG_VIEW = 'system:org:view'
export const ORG_CREATE = 'system:org:create'
export const ORG_EDIT = 'system:org:edit'
export const ORG_DELETE = 'system:org:delete'
export const ORG_USER_ASSIGN = 'system:org:user:assign'
export const ORG_USER_REMOVE = 'system:org:user:remove'

// ═══════════════════════════════════════════════════════════════
// 按模块聚合的权限数组（供 Vue 组件中 hasAnyAuthority 使用）
// ═══════════════════════════════════════════════════════════════

export const USER_MANAGEMENT_READ_AUTHORITIES = [USER_LIST]
export const USER_MANAGEMENT_CREATE_AUTHORITIES = [USER_CREATE]
export const USER_MANAGEMENT_UPDATE_AUTHORITIES = [USER_EDIT, USER_ROLE_ASSIGN]
export const USER_MANAGEMENT_DELETE_AUTHORITIES = [USER_DELETE, USER_BATCH_DELETE]
export const USER_MANAGEMENT_ENABLE_AUTHORITIES = [USER_BATCH_ENABLE, USER_ENABLE]
export const USER_MANAGEMENT_DISABLE_AUTHORITIES = [USER_BATCH_DISABLE, USER_DISABLE]

export const PLATFORM_USER_MANAGEMENT_READ_AUTHORITIES = [PLATFORM_USER_LIST, PLATFORM_USER_VIEW]
export const PLATFORM_USER_MANAGEMENT_CREATE_AUTHORITIES = [PLATFORM_USER_CREATE]
export const PLATFORM_USER_MANAGEMENT_UPDATE_AUTHORITIES = [PLATFORM_USER_EDIT, PLATFORM_USER_DISABLE]

export const ROLE_MANAGEMENT_READ_AUTHORITIES = [ROLE_LIST]
export const ROLE_MANAGEMENT_CREATE_AUTHORITIES = [ROLE_CREATE]
export const ROLE_MANAGEMENT_UPDATE_AUTHORITIES = [ROLE_EDIT]
export const ROLE_MANAGEMENT_DELETE_AUTHORITIES = [ROLE_DELETE, ROLE_BATCH_DELETE]
export const ROLE_ASSIGN_USER_AUTHORITIES = [USER_ROLE_ASSIGN]
export const ROLE_ASSIGN_PERMISSION_AUTHORITIES = [ROLE_PERMISSION_ASSIGN]
export const PERMISSION_MANAGEMENT_READ_AUTHORITIES = [PERMISSION_LIST, ROLE_PERMISSION_ASSIGN]
export const PERMISSION_MANAGEMENT_UPDATE_AUTHORITIES = [PERMISSION_EDIT]

// 迁移兼容：少量控制面页面仍允许管理员角色码作为只读兜底（后续按治理计划收口）。

export const MENU_MANAGEMENT_READ_AUTHORITIES = [MENU_LIST]
export const MENU_MANAGEMENT_CREATE_AUTHORITIES = [MENU_CREATE]
export const MENU_MANAGEMENT_UPDATE_AUTHORITIES = [MENU_EDIT]
export const MENU_MANAGEMENT_DELETE_AUTHORITIES = [MENU_DELETE, MENU_BATCH_DELETE]

export const RESOURCE_MANAGEMENT_READ_AUTHORITIES = [RESOURCE_LIST]
export const RESOURCE_MANAGEMENT_CREATE_AUTHORITIES = [RESOURCE_CREATE]
export const RESOURCE_MANAGEMENT_UPDATE_AUTHORITIES = [RESOURCE_EDIT]
export const RESOURCE_MANAGEMENT_DELETE_AUTHORITIES = [RESOURCE_DELETE, RESOURCE_BATCH_DELETE]

export const TENANT_MANAGEMENT_READ_AUTHORITIES = [TENANT_LIST, TENANT_VIEW]
export const TENANT_MANAGEMENT_CREATE_AUTHORITIES = [TENANT_CREATE]
export const TENANT_MANAGEMENT_UPDATE_AUTHORITIES = [TENANT_EDIT]
export const TENANT_MANAGEMENT_TEMPLATE_INITIALIZE_AUTHORITIES = [TENANT_TEMPLATE_INITIALIZE]
export const TENANT_MANAGEMENT_FREEZE_AUTHORITIES = [TENANT_FREEZE]
export const TENANT_MANAGEMENT_UNFREEZE_AUTHORITIES = [TENANT_UNFREEZE]
export const TENANT_MANAGEMENT_DECOMMISSION_AUTHORITIES = [TENANT_DECOMMISSION]
export const TENANT_MANAGEMENT_DELETE_AUTHORITIES = [TENANT_DELETE]

export const ORG_MANAGEMENT_READ_AUTHORITIES = [ORG_LIST, ORG_VIEW]
export const ORG_MANAGEMENT_CREATE_AUTHORITIES = [ORG_CREATE]
export const ORG_MANAGEMENT_UPDATE_AUTHORITIES = [ORG_EDIT]
export const ORG_MANAGEMENT_DELETE_AUTHORITIES = [ORG_DELETE]
export const ORG_MANAGEMENT_USER_ASSIGN_AUTHORITIES = [ORG_USER_ASSIGN]
export const ORG_MANAGEMENT_USER_REMOVE_AUTHORITIES = [ORG_USER_REMOVE]

export const DATASCOPE_VIEW_AUTHORITIES = [DATASCOPE_VIEW]
export const DATASCOPE_EDIT_AUTHORITIES = [DATASCOPE_EDIT]

export const AUDIT_AUTH_VIEW_AUTHORITIES = [AUDIT_AUTH_VIEW]
export const AUDIT_AUTH_EXPORT_AUTHORITIES = [AUDIT_AUTH_EXPORT]
export const AUDIT_AUTH_PURGE_AUTHORITIES = [AUDIT_AUTH_PURGE]
export const AUDIT_AUTHENTICATION_VIEW_AUTHORITIES = [AUDIT_AUTHENTICATION_VIEW]
export const AUDIT_AUTHENTICATION_EXPORT_AUTHORITIES = [AUDIT_AUTHENTICATION_EXPORT]
