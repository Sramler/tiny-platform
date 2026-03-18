-- 插入租户数据（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `tenant` (`id`, `code`, `name`, `enabled`, `created_at`, `updated_at`) VALUES
(1, 'default', '默认租户', true, NOW(), NOW());

-- 插入用户数据（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `user` (`tenant_id`, `username`, `nickname`, `enabled`, `account_non_expired`, `account_non_locked`, `credentials_non_expired`, `failed_login_count`) VALUES
(1, 'admin', '管理员', true, true, true, true, 0),
(1, 'user', '普通用户', true, true, true, true, 0);

-- 插入角色数据（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `role` (`tenant_id`, `code`, `name`, `description`, `builtin`, `enabled`) VALUES
(1, 'ROLE_ADMIN', '系统管理员', '拥有系统所有权限的管理员角色', true, true),
(1, 'ROLE_USER', '普通用户', '普通用户角色，拥有基本权限', true, true);

-- 插入用户角色关联数据（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `user_role` (`tenant_id`, `user_id`, `role_id`) VALUES
(1, 1, 1), -- admin -> ADMIN
(1, 2, 2); -- user -> USER

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

INSERT IGNORE INTO `role_resource` (`tenant_id`, `role_id`, `resource_id`)
SELECT
  1,
  role_entity.id,
  resource_entity.id
FROM `role` role_entity
JOIN `resource` resource_entity
  ON resource_entity.tenant_id = 1
 AND resource_entity.name IN (
   'role-constraint-view-authority',
   'role-constraint-edit-authority',
   'role-constraint-violation-view-authority'
 )
WHERE role_entity.tenant_id = 1
  AND role_entity.code = 'ROLE_ADMIN';

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

-- 插入角色资源关联数据（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `role_resource` (`tenant_id`, `role_id`, `resource_id`) VALUES
-- ADMIN角色拥有所有资源（含调度中心）
(1, 1, 1), -- ADMIN -> system
(1, 1, 2), -- ADMIN -> user
(1, 1, 3), -- ADMIN -> role
(1, 1, 4), -- ADMIN -> menu
(1, 1, 5), -- ADMIN -> resource
-- USER角色只有用户管理
(1, 2, 2); -- USER -> user

-- ADMIN 拥有调度中心及子菜单（按 name 关联，避免硬编码 ID）
INSERT IGNORE INTO `role_resource` (`tenant_id`, `role_id`, `resource_id`)
SELECT 1, 1, r.id FROM `resource` r WHERE r.name IN ('scheduling', 'schedulingDag', 'schedulingTask', 'schedulingTaskType', 'schedulingDagHistory', 'schedulingAudit')
  AND NOT EXISTS (SELECT 1 FROM `role_resource` rr WHERE rr.role_id = 1 AND rr.resource_id = r.id); 

-- ADMIN 拥有租户管理菜单
INSERT IGNORE INTO `role_resource` (`tenant_id`, `role_id`, `resource_id`)
SELECT 1, 1, r.id FROM `resource` r WHERE r.name = 'tenant'
  AND NOT EXISTS (SELECT 1 FROM `role_resource` rr WHERE rr.role_id = 1 AND rr.resource_id = r.id);

-- ADMIN 拥有幂等治理菜单
INSERT IGNORE INTO `role_resource` (`tenant_id`, `role_id`, `resource_id`)
SELECT 1, 1, r.id FROM `resource` r WHERE r.name = 'idempotentOps'
  AND NOT EXISTS (SELECT 1 FROM `role_resource` rr WHERE rr.role_id = 1 AND rr.resource_id = r.id);

-- 插入用户认证方法数据
-- 为每个用户添加 LOCAL + PASSWORD 认证方法（使用 INSERT IGNORE 避免重复插入）
INSERT IGNORE INTO `user_authentication_method` 
    (`tenant_id`, `user_id`, `authentication_provider`, `authentication_type`, `authentication_configuration`, `is_primary_method`, `is_method_enabled`, `authentication_priority`, `created_at`, `updated_at`)
SELECT
    u.tenant_id,
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
WHERE u.username IN ('admin', 'user')
  AND NOT EXISTS (
      SELECT 1 FROM user_authentication_method uam 
      WHERE uam.user_id = u.id 
        AND uam.tenant_id = u.tenant_id
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
