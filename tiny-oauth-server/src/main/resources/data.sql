-- 注意：当前仓库默认由 Liquibase 管理 schema/data 迁移；本文件仅作为参考 seed。
-- 若手工执行，默认前提是 Liquibase 114-117 等 permission 重构迁移已完成；
-- 当前角色授权关系以 role_permission -> permission 为准，不再以 role_resource 作为主关系。

-- 插入租户数据（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `tenant` (`id`, `code`, `name`, `enabled`, `created_at`, `updated_at`) VALUES
(1, 'default', '默认租户', true, NOW(), NOW());

-- 插入用户数据（tenant_id 已退场，归属以 tenant_user 为准）
INSERT IGNORE INTO `user` (`username`, `nickname`, `enabled`, `account_non_expired`, `account_non_locked`, `credentials_non_expired`, `failed_login_count`) VALUES
('admin', '管理员', true, true, true, true, 0),
('user', '普通用户', true, true, true, true, 0);

-- 插入角色数据（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `role` (`tenant_id`, `code`, `name`, `description`, `builtin`, `enabled`) VALUES
(1, 'ROLE_TENANT_ADMIN', '租户管理员', '租户管理员，负责本租户内用户、角色、组织、资源与业务配置管理', true, true),
(1, 'ROLE_USER', '普通用户', '普通用户角色，拥有基本权限', true, true);

-- 用户-租户 membership（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `tenant_user` (`tenant_id`, `user_id`, `status`, `is_default`, `joined_at`, `created_at`, `updated_at`)
SELECT
  1,
  user_entity.id,
  'ACTIVE',
  1,
  NOW(),
  NOW(),
  NOW()
FROM `user` user_entity
WHERE user_entity.username IN ('admin', 'user');

-- 用户-角色赋权（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `role_assignment`
(`principal_type`, `principal_id`, `role_id`, `tenant_id`, `scope_type`, `scope_id`, `status`, `start_time`, `granted_at`, `updated_at`)
SELECT
  'USER',
  user_entity.id,
  role_entity.id,
  1,
  'TENANT',
  1,
  'ACTIVE',
  NOW(),
  NOW(),
  NOW()
FROM `user` user_entity
JOIN (
  SELECT 'admin' AS username, 'ROLE_TENANT_ADMIN' AS role_code
  UNION ALL
  SELECT 'user', 'ROLE_USER'
) seed_mapping
  ON seed_mapping.username = user_entity.username
JOIN `role` role_entity
  ON role_entity.tenant_id = 1
 AND role_entity.code = seed_mapping.role_code;

-- ROLE_TENANT_ADMIN 默认拥有核心模块的 READ 数据范围（避免无规则时退回 SELF）
INSERT IGNORE INTO `role_data_scope`
(`tenant_id`, `role_id`, `module`, `scope_type`, `access_type`, `created_by`, `created_at`, `updated_at`)
SELECT
  1,
  role_entity.id,
  module_mapping.module,
  'ALL',
  'READ',
  1,
  NOW(),
  NOW()
FROM `role` role_entity
JOIN (
  SELECT 'user' AS module
  UNION ALL SELECT 'resource'
  UNION ALL SELECT 'menu'
  UNION ALL SELECT 'org'
  UNION ALL SELECT 'scheduling'
  UNION ALL SELECT 'export'
  UNION ALL SELECT 'dict'
) module_mapping
WHERE role_entity.tenant_id = 1
  AND role_entity.code = 'ROLE_TENANT_ADMIN';

-- 插入资源数据（包含菜单和API）
-- 注意：resource 表的 path 字段已迁移为 url 字段
-- 使用 INSERT IGNORE 避免重复插入
-- 系统管理目录
INSERT IGNORE INTO `resource` (`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`) VALUES
(1, 'system', '/system', '', '', 'SettingOutlined', 1, 1, '', '', 0, 0, '系统管理', 'system:entry:view', 0, NULL);

-- 用户管理菜单
INSERT IGNORE INTO `resource` (`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`) VALUES
(1, 'user', '/system/user', '/sys/users', 'GET', 'UserOutlined', 1, 1, '/views/user/User.vue', '', 0, 0, '用户管理', 'system:user:list', 1, 1);

-- 角色管理菜单
INSERT IGNORE INTO `resource` (`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`) VALUES
(1, 'role', '/system/role', '/sys/roles', 'GET', 'TeamOutlined', 1, 2, '/views/role/role.vue', '', 0, 0, '角色管理', 'system:role:list', 1, 1);

-- RBAC3 role-constraints authorities (control-plane)
SET @role_menu_id = (SELECT id FROM `resource` WHERE tenant_id = 1 AND name = 'role' LIMIT 1);
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`, `enabled`)
VALUES
(1, 'role-constraint-view-authority', '', '', '', '', 0, 801, '', '', 1, 0, 'RBAC3 角色约束查看权限', 'system:role:constraint:view', 2, @role_menu_id, 1),
(1, 'role-constraint-edit-authority', '', '', '', '', 0, 802, '', '', 1, 0, 'RBAC3 角色约束配置权限', 'system:role:constraint:edit', 2, @role_menu_id, 1),
(1, 'role-constraint-violation-view-authority', '', '', '', '', 0, 803, '', '', 1, 0, 'RBAC3 违例日志查看权限', 'system:role:constraint:violation:view', 2, @role_menu_id, 1);

INSERT IGNORE INTO `role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT
  1,
  role_entity.id,
  permission_entity.id
FROM `role` role_entity
JOIN `resource` resource_entity
  ON resource_entity.tenant_id = 1
 AND resource_entity.name IN (
   'role-constraint-view-authority',
   'role-constraint-edit-authority',
   'role-constraint-violation-view-authority'
 )
JOIN `permission` permission_entity
  ON permission_entity.tenant_id = resource_entity.tenant_id
 AND permission_entity.permission_code = resource_entity.permission
WHERE role_entity.tenant_id = 1
  AND role_entity.code = 'ROLE_TENANT_ADMIN';

-- 菜单管理菜单
INSERT IGNORE INTO `resource` (`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`) VALUES
(1, 'menu', '/system/menu', '/sys/menus', 'GET', 'MenuOutlined', 1, 3, '/views/menu/Menu.vue', '', 0, 0, '菜单管理', 'system:menu:list', 1, 1);

-- 资源管理菜单
INSERT IGNORE INTO `resource` (`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`) VALUES
(1, 'resource', '/system/resource', '/sys/resources', 'GET', 'ApiOutlined', 1, 4, '/views/resource/resource.vue', '', 0, 0, '资源管理', 'system:resource:list', 1, 1);

-- 租户管理菜单
INSERT IGNORE INTO `resource` (`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`) VALUES
(1, 'tenant', '/system/tenant', '/sys/tenants', 'GET', 'ApartmentOutlined', 1, 5, '/views/tenant/Tenant.vue', '', 0, 0, '租户管理', 'system:tenant:list', 1, 1);

-- 幂等治理菜单（平台管理员）
INSERT IGNORE INTO `resource` (`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`) VALUES
(1, 'idempotentOps', '/ops/idempotent', '/metrics/idempotent', 'GET', 'RadarChartOutlined', 0, 6, '/views/idempotent/Overview.vue', '', 0, 0, '幂等治理', 'idempotent:ops:view', 1, 1);

-- 认证审计菜单
INSERT IGNORE INTO `resource` (`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`) VALUES
(1, 'authenticationAudit', '/system/audit/authentication', '/sys/audit/authentication', 'GET', 'HistoryOutlined', 0, 7, '/views/audit/AuthenticationAudit.vue', '', 0, 0, '认证审计', 'system:audit:authentication:view', 1, 1);

-- 组织管理菜单
INSERT IGNORE INTO `resource` (`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`) VALUES
(1, 'organization', '/system/org', '/sys/org/list', 'GET', 'ApartmentOutlined', 0, 8, '/views/org/Organization.vue', '', 0, 0, '组织管理', 'system:org:list', 1, 1);

-- 数据范围菜单
INSERT IGNORE INTO `resource` (`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`) VALUES
(1, 'dataScope', '/system/datascope', '/sys/data-scope', 'GET', 'ClusterOutlined', 0, 9, '/views/datascope/DataScope.vue', '', 0, 0, '数据范围', 'system:datascope:view', 1, 1);

-- 授权审计菜单
INSERT IGNORE INTO `resource` (`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`) VALUES
(1, 'authorizationAudit', '/system/audit/authorization', '/sys/audit/authorization', 'GET', 'FileSearchOutlined', 0, 10, '/views/audit/AuthorizationAudit.vue', '', 0, 0, '授权审计', 'system:audit:auth:view', 1, 1);

-- RBAC3 约束菜单
INSERT IGNORE INTO `resource` (`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`) VALUES
(1, 'roleConstraint', '/system/role/constraint', '/sys/role-constraints/hierarchy', 'GET', 'TeamOutlined', 0, 11, '/views/constraint/RoleConstraint.vue', '', 0, 0, 'RBAC3 约束', 'system:role:constraint:view', 1, 1);

-- 调度中心目录
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`)
VALUES
(1, 'scheduling', '/scheduling', '', '', 'ClusterOutlined', 1, 10, '', '/scheduling/dag', 0, 0, '调度中心', 'scheduling:entry:view', 0, NULL);
SET @scheduling_dir_id = (SELECT id FROM `resource` WHERE `name` = 'scheduling' LIMIT 1);

-- 调度中心 - DAG 管理
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`)
VALUES
(1, 'schedulingDag', '/scheduling/dag', '/scheduling/dag/list', 'GET', 'BranchesOutlined', 1, 11, '/views/scheduling/Dag.vue', '', 0, 0, 'DAG 管理', 'scheduling:console:view', 1, @scheduling_dir_id);

-- 调度中心 - 任务管理
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`)
VALUES
(1, 'schedulingTask', '/scheduling/task', '/scheduling/task/list', 'GET', 'ProfileOutlined', 1, 12, '/views/scheduling/Task.vue', '', 0, 0, '任务管理', 'scheduling:console:view', 1, @scheduling_dir_id);

-- 调度中心 - 任务类型
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`)
VALUES
(1, 'schedulingTaskType', '/scheduling/task-type', '/scheduling/task-type/list', 'GET', 'DatabaseOutlined', 1, 13, '/views/scheduling/TaskType.vue', '', 0, 0, '任务类型', 'scheduling:console:view', 1, @scheduling_dir_id);

-- 调度中心 - 运行历史
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`)
VALUES
(1, 'schedulingDagHistory', '/scheduling/dag-history', '/scheduling/dag/run/list', 'GET', 'HistoryOutlined', 1, 14, '/views/scheduling/DagHistory.vue', '', 0, 0, '运行历史', 'scheduling:console:view', 1, @scheduling_dir_id);

-- 调度中心 - 审计日志
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`)
VALUES
(1, 'schedulingAudit', '/scheduling/audit', '/scheduling/audit/list', 'GET', 'SecurityScanOutlined', 1, 15, '/views/scheduling/Audit.vue', '', 0, 0, '审计日志', 'scheduling:audit:view', 1, @scheduling_dir_id);

-- ========================================================================
-- 细粒度操作权限（type=2 按钮/权限标识，hidden=1 不在菜单树显示）
-- 规范：domain:resource:action 三段式；四段式仅子资源需独立授权时使用
-- ========================================================================

-- --- 用户管理操作权限 ---
SET @user_menu_id = (SELECT id FROM `resource` WHERE tenant_id = 1 AND name = 'user' LIMIT 1);
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`, `enabled`)
VALUES
(1, 'user-view-authority',         '', '', '', '', 0, 101, '', '', 1, 0, '用户详情查看',     'system:user:view',         2, @user_menu_id, 1),
(1, 'user-create-authority',       '', '', '', '', 0, 102, '', '', 1, 0, '用户新增',         'system:user:create',       2, @user_menu_id, 1),
(1, 'user-edit-authority',         '', '', '', '', 0, 103, '', '', 1, 0, '用户编辑',         'system:user:edit',         2, @user_menu_id, 1),
(1, 'user-delete-authority',       '', '', '', '', 0, 104, '', '', 1, 0, '用户删除',         'system:user:delete',       2, @user_menu_id, 1),
(1, 'user-batch-delete-authority', '', '', '', '', 0, 105, '', '', 1, 0, '用户批量删除',     'system:user:batch-delete', 2, @user_menu_id, 1),
(1, 'user-enable-authority',       '', '', '', '', 0, 106, '', '', 1, 0, '用户启用',         'system:user:enable',       2, @user_menu_id, 1),
(1, 'user-batch-enable-authority', '', '', '', '', 0, 107, '', '', 1, 0, '用户批量启用',     'system:user:batch-enable', 2, @user_menu_id, 1),
(1, 'user-disable-authority',      '', '', '', '', 0, 108, '', '', 1, 0, '用户禁用',         'system:user:disable',      2, @user_menu_id, 1),
(1, 'user-batch-disable-authority','', '', '', '', 0, 109, '', '', 1, 0, '用户批量禁用',     'system:user:batch-disable',2, @user_menu_id, 1),
(1, 'user-role-assign-authority',  '', '', '', '', 0, 110, '', '', 1, 0, '用户角色分配',     'system:user:role:assign',  2, @user_menu_id, 1);

-- --- 角色管理操作权限 ---
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`, `enabled`)
VALUES
(1, 'role-create-authority',            '', '', '', '', 0, 201, '', '', 1, 0, '角色新增',         'system:role:create',            2, @role_menu_id, 1),
(1, 'role-edit-authority',              '', '', '', '', 0, 202, '', '', 1, 0, '角色编辑',         'system:role:edit',              2, @role_menu_id, 1),
(1, 'role-delete-authority',            '', '', '', '', 0, 203, '', '', 1, 0, '角色删除',         'system:role:delete',            2, @role_menu_id, 1),
(1, 'role-batch-delete-authority',      '', '', '', '', 0, 204, '', '', 1, 0, '角色批量删除',     'system:role:batch-delete',      2, @role_menu_id, 1),
(1, 'role-permission-assign-authority', '', '', '', '', 0, 205, '', '', 1, 0, '角色权限分配',     'system:role:permission:assign', 2, @role_menu_id, 1);

-- --- 菜单管理操作权限 ---
SET @menu_menu_id = (SELECT id FROM `resource` WHERE tenant_id = 1 AND name = 'menu' LIMIT 1);
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`, `enabled`)
VALUES
(1, 'menu-create-authority',       '', '', '', '', 0, 301, '', '', 1, 0, '菜单新增',         'system:menu:create',       2, @menu_menu_id, 1),
(1, 'menu-edit-authority',         '', '', '', '', 0, 302, '', '', 1, 0, '菜单编辑',         'system:menu:edit',         2, @menu_menu_id, 1),
(1, 'menu-delete-authority',       '', '', '', '', 0, 303, '', '', 1, 0, '菜单删除',         'system:menu:delete',       2, @menu_menu_id, 1),
(1, 'menu-batch-delete-authority', '', '', '', '', 0, 304, '', '', 1, 0, '菜单批量删除',     'system:menu:batch-delete', 2, @menu_menu_id, 1);

-- --- 资源管理操作权限 ---
SET @resource_menu_id = (SELECT id FROM `resource` WHERE tenant_id = 1 AND name = 'resource' LIMIT 1);
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`, `enabled`)
VALUES
(1, 'resource-create-authority',       '', '', '', '', 0, 401, '', '', 1, 0, '资源新增',         'system:resource:create',       2, @resource_menu_id, 1),
(1, 'resource-edit-authority',         '', '', '', '', 0, 402, '', '', 1, 0, '资源编辑',         'system:resource:edit',         2, @resource_menu_id, 1),
(1, 'resource-delete-authority',       '', '', '', '', 0, 403, '', '', 1, 0, '资源删除',         'system:resource:delete',       2, @resource_menu_id, 1),
(1, 'resource-batch-delete-authority', '', '', '', '', 0, 404, '', '', 1, 0, '资源批量删除',     'system:resource:batch-delete', 2, @resource_menu_id, 1);

-- --- 租户管理操作权限 ---
SET @tenant_menu_id = (SELECT id FROM `resource` WHERE tenant_id = 1 AND name = 'tenant' LIMIT 1);
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`, `enabled`)
VALUES
(1, 'tenant-view-authority',   '', '', '', '', 0, 501, '', '', 1, 0, '租户详情查看', 'system:tenant:view',   2, @tenant_menu_id, 1),
(1, 'tenant-create-authority', '', '', '', '', 0, 502, '', '', 1, 0, '租户新增',     'system:tenant:create', 2, @tenant_menu_id, 1),
(1, 'tenant-edit-authority',   '', '', '', '', 0, 503, '', '', 1, 0, '租户编辑',     'system:tenant:edit',   2, @tenant_menu_id, 1),
(1, 'tenant-template-initialize-authority', '', '', '', '', 0, 504, '', '', 1, 0, '平台模板初始化', 'system:tenant:template:initialize', 2, @tenant_menu_id, 1),
(1, 'tenant-delete-authority', '', '', '', '', 0, 505, '', '', 1, 0, '租户删除',     'system:tenant:delete', 2, @tenant_menu_id, 1),
(1, 'tenant-freeze-authority', '', '', '', '', 0, 506, '', '', 1, 0, '租户冻结',     'system:tenant:freeze', 2, @tenant_menu_id, 1),
(1, 'tenant-unfreeze-authority', '', '', '', '', 0, 507, '', '', 1, 0, '租户解冻',   'system:tenant:unfreeze', 2, @tenant_menu_id, 1),
(1, 'tenant-decommission-authority', '', '', '', '', 0, 508, '', '', 1, 0, '租户下线', 'system:tenant:decommission', 2, @tenant_menu_id, 1);

-- --- 字典管理操作权限（租户级 + 平台级） ---
SET @system_dir_id = (SELECT id FROM `resource` WHERE tenant_id = 1 AND name = 'system' LIMIT 1);
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`, `enabled`)
VALUES
(1, 'dict-type-list-authority',    '', '', '', '', 0, 601, '', '', 1, 0, '字典类型查看',     'dict:type:list',       2, @system_dir_id, 1),
(1, 'dict-type-create-authority',  '', '', '', '', 0, 602, '', '', 1, 0, '字典类型新增',     'dict:type:create',     2, @system_dir_id, 1),
(1, 'dict-type-edit-authority',    '', '', '', '', 0, 603, '', '', 1, 0, '字典类型编辑',     'dict:type:edit',       2, @system_dir_id, 1),
(1, 'dict-type-delete-authority',  '', '', '', '', 0, 604, '', '', 1, 0, '字典类型删除',     'dict:type:delete',     2, @system_dir_id, 1),
(1, 'dict-item-list-authority',    '', '', '', '', 0, 605, '', '', 1, 0, '字典项查看',       'dict:item:list',       2, @system_dir_id, 1),
(1, 'dict-item-create-authority',  '', '', '', '', 0, 606, '', '', 1, 0, '字典项新增',       'dict:item:create',     2, @system_dir_id, 1),
(1, 'dict-item-edit-authority',    '', '', '', '', 0, 607, '', '', 1, 0, '字典项编辑',       'dict:item:edit',       2, @system_dir_id, 1),
(1, 'dict-item-delete-authority',  '', '', '', '', 0, 608, '', '', 1, 0, '字典项删除',       'dict:item:delete',     2, @system_dir_id, 1),
(1, 'dict-platform-manage-authority', '', '', '', '', 0, 609, '', '', 1, 0, '平台字典管理', 'dict:platform:manage', 2, @system_dir_id, 1);

-- --- 调度中心操作权限 ---
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`, `enabled`)
VALUES
(1, 'scheduling-config-authority',  '', '', '', '', 0, 701, '', '', 1, 0, '调度配置管理',     'scheduling:console:config', 2, @scheduling_dir_id, 1),
(1, 'scheduling-run-authority',     '', '', '', '', 0, 702, '', '', 1, 0, '调度运行控制',     'scheduling:run:control',    2, @scheduling_dir_id, 1),
(1, 'scheduling-cluster-authority', '', '', '', '', 0, 703, '', '', 1, 0, '调度集群状态',     'scheduling:cluster:view',   2, @scheduling_dir_id, 1);

-- --- 工作流操作权限 ---
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`, `enabled`)
VALUES
(1, 'workflow-view-authority',       '', '', '', '', 0, 901, '', '', 1, 0, '工作流查看',       'workflow:console:view',     2, @system_dir_id, 1),
(1, 'workflow-config-authority',     '', '', '', '', 0, 902, '', '', 1, 0, '工作流配置管理',   'workflow:console:config',   2, @system_dir_id, 1),
(1, 'workflow-instance-authority',   '', '', '', '', 0, 903, '', '', 1, 0, '工作流实例控制',   'workflow:instance:control', 2, @system_dir_id, 1),
(1, 'workflow-tenant-authority',     '', '', '', '', 0, 904, '', '', 1, 0, '工作流租户管理',   'workflow:tenant:manage',    2, @system_dir_id, 1);

-- --- 组织/部门管理操作权限 ---
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`, `enabled`)
VALUES
(1, 'org-list-authority',        '', '', '', '', 0, 1001, '', '', 1, 0, '组织列表查看',       'system:org:list',         2, @system_dir_id, 1),
(1, 'org-view-authority',        '', '', '', '', 0, 1002, '', '', 1, 0, '组织详情查看',       'system:org:view',         2, @system_dir_id, 1),
(1, 'org-create-authority',      '', '', '', '', 0, 1003, '', '', 1, 0, '组织/部门新增',      'system:org:create',       2, @system_dir_id, 1),
(1, 'org-edit-authority',        '', '', '', '', 0, 1004, '', '', 1, 0, '组织/部门编辑',      'system:org:edit',         2, @system_dir_id, 1),
(1, 'org-delete-authority',      '', '', '', '', 0, 1005, '', '', 1, 0, '组织/部门删除',      'system:org:delete',       2, @system_dir_id, 1),
(1, 'org-user-assign-authority', '', '', '', '', 0, 1006, '', '', 1, 0, '组织成员分配',       'system:org:user:assign',  2, @system_dir_id, 1),
(1, 'org-user-remove-authority', '', '', '', '', 0, 1007, '', '', 1, 0, '组织成员移除',       'system:org:user:remove',  2, @system_dir_id, 1);

-- --- 数据范围管理操作权限 ---
INSERT IGNORE INTO `resource`
(`tenant_id`, `name`, `url`, `uri`, `method`, `icon`, `show_icon`, `sort`, `component`, `redirect`, `hidden`, `keep_alive`, `title`, `permission`, `type`, `parent_id`, `enabled`)
VALUES
(1, 'datascope-view-authority', '', '', '', '', 0, 1101, '', '', 1, 0, '数据范围查看', 'system:datascope:view', 2, @system_dir_id, 1),
(1, 'datascope-edit-authority', '', '', '', '', 0, 1102, '', '', 1, 0, '数据范围编辑', 'system:datascope:edit', 2, @system_dir_id, 1),
(1, 'audit-auth-view-authority', '', '', '', '', 0, 1201, '', '', 1, 0, '授权审计查看', 'system:audit:auth:view', 2, @system_dir_id, 1),
(1, 'audit-auth-purge-authority', '', '', '', '', 0, 1202, '', '', 1, 0, '授权审计清理', 'system:audit:auth:purge', 2, @system_dir_id, 1),
(1, 'audit-auth-export-authority', '', '', '', '', 0, 1203, '', '', 1, 0, '授权审计导出', 'system:audit:auth:export', 2, @system_dir_id, 1),
(1, 'audit-authentication-export-authority', '', '', '', '', 0, 1204, '', '', 1, 0, '认证审计导出', 'system:audit:authentication:export', 2, @system_dir_id, 1),
(1, 'export-view-authority', '', '', '', '', 0, 1301, '', '', 1, 0, '数据导出', 'system:export:view', 2, @system_dir_id, 1),
(1, 'export-manage-authority', '', '', '', '', 0, 1302, '', '', 1, 0, '导出管理（查看全部任务）', 'system:export:manage', 2, @system_dir_id, 1);

UPDATE `resource`
SET `created_by` = 1
WHERE `tenant_id` = 1
  AND `created_by` IS NULL;

-- ========================================================================
-- 角色权限关联数据（禁止依赖固定角色主键）
-- ========================================================================
INSERT IGNORE INTO `role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT
  1,
  role_entity.id,
  permission_entity.id
FROM `role` role_entity
JOIN `resource` resource_entity
  ON resource_entity.tenant_id = 1
JOIN `permission` permission_entity
  ON permission_entity.tenant_id = resource_entity.tenant_id
 AND permission_entity.permission_code = resource_entity.permission
WHERE role_entity.tenant_id = 1
  AND role_entity.code = 'ROLE_TENANT_ADMIN';

-- ROLE_USER 保留最小用户管理菜单
INSERT IGNORE INTO `role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT
  1,
  role_entity.id,
  permission_entity.id
FROM `role` role_entity
JOIN `resource` resource_entity
  ON resource_entity.tenant_id = 1
 AND resource_entity.name = 'user'
JOIN `permission` permission_entity
  ON permission_entity.tenant_id = resource_entity.tenant_id
 AND permission_entity.permission_code = resource_entity.permission
WHERE role_entity.tenant_id = 1
  AND role_entity.code = 'ROLE_USER';

-- 插入用户认证方法数据
-- 为每个用户添加 LOCAL + PASSWORD 认证方法（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `user_authentication_method` 
    (`tenant_id`, `user_id`, `authentication_provider`, `authentication_type`, `authentication_configuration`, `is_primary_method`, `is_method_enabled`, `authentication_priority`, `created_at`, `updated_at`)
SELECT
    COALESCE(default_membership.tenant_id, 1),
    u.id,
    'LOCAL',
    'PASSWORD',
    JSON_OBJECT(
        'password', CASE WHEN u.username = 'admin'
                        THEN '{bcrypt}$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa'
                        ELSE '{bcrypt}$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDa'
                   END,
        'password_changed_at', DATE_FORMAT(NOW(), '%Y-%m-%dT%H:%i:%sZ'),
        'hash_algorithm', 'bcrypt',
        'password_version', 1,
        'created_by', 'data.sql'
    ),
    true,
    true,
    0,
    NOW(),
    NOW()
FROM user u
LEFT JOIN (
    SELECT
        tu.user_id,
        COALESCE(
            MAX(CASE WHEN tu.status = 'ACTIVE' AND tu.is_default = 1 THEN tu.tenant_id END),
            MIN(CASE WHEN tu.status = 'ACTIVE' THEN tu.tenant_id END)
        ) AS tenant_id
    FROM `tenant_user` tu
    GROUP BY tu.user_id
) default_membership ON default_membership.user_id = u.id
WHERE u.username IN ('admin', 'user')
  AND NOT EXISTS (
      SELECT 1 FROM user_authentication_method uam 
      WHERE uam.user_id = u.id 
        AND uam.tenant_id = COALESCE(default_membership.tenant_id, 1)
        AND uam.authentication_provider = 'LOCAL' 
        AND uam.authentication_type = 'PASSWORD'
  );

-- 导出教学示例：用量/账单型数据（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `demo_export_usage`
(`tenant_id`, `usage_date`, `product_code`, `product_name`, `plan_tier`, `region`, `usage_qty`, `unit`, `unit_price`, `amount`, `currency`, `tax_rate`, `is_billable`, `status`, `metadata`, `created_at`)
VALUES
(1, CURDATE() - INTERVAL 3 DAY, 'cdn', 'CDN 流量', 'standard', 'cn-north-1', 120.5678, 'GB', 0.1200, 14.4681, 'CNY', 0.0600, TRUE, 'UNBILLED', JSON_OBJECT('tag','marketing','project','site-a'), NOW() - INTERVAL 3 DAY),
(1, CURDATE() - INTERVAL 2 DAY, 'oss', '对象存储', 'pro', 'cn-north-1', 980.0000, 'GB', 0.0800, 78.4000, 'CNY', 0.0600, TRUE, 'BILLED', JSON_OBJECT('bucket','assets','storage_class','standard'), NOW() - INTERVAL 2 DAY),
(2, CURDATE() - INTERVAL 1 DAY, 'api', 'API 调用', 'standard', 'ap-southeast-1', 150000, 'req', 0.0008, 120.0000, 'CNY', 0.0000, TRUE, 'UNBILLED', JSON_OBJECT('endpoint','/v1/search'), NOW() - INTERVAL 1 DAY),
(2, CURDATE() - INTERVAL 7 DAY, 'mq', '消息队列', 'basic', 'ap-southeast-1', 52000, 'msg', 0.0005, 26.0000, 'CNY', 0.0000, FALSE, 'ADJUSTED', JSON_OBJECT('note','free tier promo'), NOW() - INTERVAL 7 DAY),
(3, CURDATE(), 'db', '托管数据库', 'enterprise', 'us-west-1', 36.5000, 'hours', 2.8000, 102.2000, 'USD', 0.0725, TRUE, 'UNBILLED', JSON_OBJECT('engine','postgres','version','14'), NOW());

-- ========================================================================
-- 调度中心示例数据（DAG 管理 → 任务类型 → 任务 → 计划/版本/节点 → 运行历史）
-- 用于前端：任务类型 / 任务管理 / DAG 管理 / 运行历史 页面可正常查看与流转
-- 使用 INSERT IGNORE 避免重复插入；依赖顺序：task_type → task → dag → dag_version → dag_task → dag_edge → dag_run → task_instance → task_history
-- 重要：node_a、node_b 在下方 dag_task 中定义；若未执行本段，节点不存在，触发 DAG 会失败。
-- ========================================================================

-- 1) 任务类型（示例：Shell 执行器）
INSERT IGNORE INTO `scheduling_task_type`
(`id`, `tenant_id`, `code`, `name`, `description`, `executor`, `default_timeout_sec`, `default_max_retry`, `enabled`, `created_at`, `updated_at`)
VALUES
(1, 1, 'DEMO_SHELL', '示例Shell任务', '用于示例的 Shell 执行器，便于 DAG 计划与运行历史验证', 'shellExecutor', 300, 2, 1, NOW(), NOW());

-- 2) 任务定义（两个任务：准备 → 执行）
INSERT IGNORE INTO `scheduling_task`
(`id`, `tenant_id`, `type_id`, `code`, `name`, `description`, `enabled`, `created_at`, `updated_at`)
VALUES
(1, 1, 1, 'demo_task_a', '示例任务A-准备', '示例流程第一步：准备', 1, NOW(), NOW()),
(2, 1, 1, 'demo_task_b', '示例任务B-执行', '示例流程第二步：执行', 1, NOW(), NOW());

-- 3) DAG 主表（示例计划）
INSERT IGNORE INTO `scheduling_dag`
(`id`, `tenant_id`, `code`, `name`, `description`, `enabled`, `created_at`, `updated_at`)
VALUES
(1, 1, 'demo_dag', '示例DAG计划', 'DAG 管理 → 任务类型 → 任务 → 运行历史 示例流转，用于前端验证', 1, NOW(), NOW());

-- 4) DAG 版本（v1，ACTIVE）
INSERT IGNORE INTO `scheduling_dag_version`
(`id`, `dag_id`, `version_no`, `status`, `created_at`)
VALUES
(1, 1, 1, 'ACTIVE', NOW());

-- 5) DAG 节点（node_a → node_b）
INSERT IGNORE INTO `scheduling_dag_task`
(`id`, `dag_version_id`, `node_code`, `task_id`, `name`, `created_at`)
VALUES
(1, 1, 'node_a', 1, '准备节点', NOW()),
(2, 1, 'node_b', 2, '执行节点', NOW());

-- 6) DAG 边（node_a → node_b）
INSERT IGNORE INTO `scheduling_dag_edge`
(`id`, `dag_version_id`, `from_node_code`, `to_node_code`, `created_at`)
VALUES
(1, 1, 'node_a', 'node_b', NOW());

-- 7) DAG 运行实例（一条已成功结束的运行记录，供运行历史页查看）
INSERT IGNORE INTO `scheduling_dag_run`
(`id`, `dag_id`, `dag_version_id`, `run_no`, `tenant_id`, `trigger_type`, `triggered_by`, `status`, `start_time`, `end_time`, `created_at`)
VALUES
(1, 1, 1, 'RUN-DEMO-001', 1, 'MANUAL', 'admin', 'SUCCESS', NOW() - INTERVAL 1 HOUR, NOW() - INTERVAL 1 HOUR + INTERVAL 5 MINUTE, NOW() - INTERVAL 1 HOUR);

-- 8) 任务实例（该次运行的两个节点执行记录）
INSERT IGNORE INTO `scheduling_task_instance`
(`id`, `dag_run_id`, `dag_id`, `dag_version_id`, `node_code`, `task_id`, `tenant_id`, `attempt_no`, `status`, `scheduled_at`, `params`, `result`, `created_at`, `updated_at`)
VALUES
(1, 1, 1, 1, 'node_a', 1, 1, 1, 'SUCCESS', NOW() - INTERVAL 1 HOUR, NULL, '{"ok":true}', NOW() - INTERVAL 1 HOUR, NOW()),
(2, 1, 1, 1, 'node_b', 2, 1, 1, 'SUCCESS', NOW() - INTERVAL 1 HOUR + INTERVAL 2 MINUTE, NULL, '{"ok":true}', NOW() - INTERVAL 1 HOUR, NOW());

-- 9) 任务执行历史（与实例对应，便于节点记录/日志查看）
INSERT IGNORE INTO `scheduling_task_history`
(`id`, `task_instance_id`, `dag_run_id`, `dag_id`, `node_code`, `task_id`, `tenant_id`, `attempt_no`, `status`, `start_time`, `end_time`, `duration_ms`, `created_at`)
VALUES
(1, 1, 1, 1, 'node_a', 1, 1, 1, 'SUCCESS', NOW() - INTERVAL 1 HOUR, NOW() - INTERVAL 1 HOUR + INTERVAL 1 MINUTE, 60000, NOW()),
(2, 2, 1, 1, 'node_b', 2, 1, 1, 'SUCCESS', NOW() - INTERVAL 1 HOUR + INTERVAL 2 MINUTE, NOW() - INTERVAL 1 HOUR + INTERVAL 5 MINUTE, 180000, NOW());
