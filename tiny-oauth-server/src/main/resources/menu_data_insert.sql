-- 参考脚本：历史菜单初始化示例。
-- 注意：该文件不参与默认 Liquibase 初始化，仅保留为人工排查/回顾用示例；
-- URI 与 permission 需尽量对齐当前真实控制器和规范码，避免继续扩散旧契约。

-- 菜单数据插入脚本
-- 基于 menu.json 的菜单结构数据
-- 执行时间：2024-06-27

-- 清空现有菜单数据（可选，谨慎使用）
-- DELETE FROM resource WHERE type IN (0, 1); -- 0=目录，1=菜单

-- 插入顶级菜单（目录类型）
INSERT INTO resource (name, title, url, uri, method, icon, show_icon, sort, component, redirect, hidden, keep_alive, permission, type, parent_id, created_at, updated_at) VALUES
-- 工作台
('dashboard', '工作台', '/', '', '', 'HomeOutlined', true, 1, '/views/Dashboard.vue', '', false, false, 'dashboard:entry:view', 1, NULL, NOW(), NOW()),

-- 系统管理（目录）
('system', '系统管理', '/system', '', '', 'SettingOutlined', true, 2, '', '/system/role', false, false, 'system:entry:view', 0, NULL, NOW(), NOW()),

-- 个人页（目录）
('profile', '个人页', '/profile', '', '', 'UserOutlined', true, 3, '', '/profile/center', false, false, 'profile:entry:view', 0, NULL, NOW(), NOW()),

-- 异常页（目录）
('exception', '异常页', '/exception', '', '', 'WarningOutlined', true, 4, '', '/exception/403', false, false, 'exception:entry:view', 0, NULL, NOW(), NOW());

-- 获取父级菜单ID
SET @system_id = (SELECT id FROM resource WHERE name = 'system' LIMIT 1);
SET @profile_id = (SELECT id FROM resource WHERE name = 'profile' LIMIT 1);
SET @exception_id = (SELECT id FROM resource WHERE name = 'exception' LIMIT 1);

-- 插入系统管理子菜单
INSERT INTO resource (name, title, url, uri, method, icon, show_icon, sort, component, redirect, hidden, keep_alive, permission, type, parent_id, created_at, updated_at) VALUES
-- 角色管理
('role', '角色管理', '/system/role', '/sys/roles', 'GET', '', false, 1, '/views/role/role.vue', '', false, false, 'system:role:list', 1, @system_id, NOW(), NOW()),

-- 菜单管理
('menu', '菜单管理', '/system/menu', '/sys/menus', 'GET', '', false, 2, '/views/menu/Menu.vue', '', false, false, 'system:menu:list', 1, @system_id, NOW(), NOW()),

-- 资源管理
('resource', '资源管理', '/system/resource', '/sys/resources', 'GET', '', false, 3, '/views/resource/resource.vue', '', false, false, 'system:resource:list', 1, @system_id, NOW(), NOW()),

-- 用户管理
('user', '用户管理', '/system/user', '/sys/users', 'GET', '', false, 4, '/views/user/User.vue', '', false, false, 'system:user:list', 1, @system_id, NOW(), NOW()),

-- 认证审计
('authentication-audit', '认证审计', '/system/audit/authentication', '/sys/audit/authentication', 'GET', '', false, 5, '/views/audit/AuthenticationAudit.vue', '', false, false, 'system:audit:authentication:view', 1, @system_id, NOW(), NOW()),

-- 组织管理
('organization', '组织管理', '/system/org', '/sys/org/list', 'GET', '', false, 6, '/views/org/Organization.vue', '', false, false, 'system:org:list', 1, @system_id, NOW(), NOW()),

-- 数据范围
('data-scope', '数据范围', '/system/datascope', '/sys/data-scope', 'GET', '', false, 7, '/views/datascope/DataScope.vue', '', false, false, 'system:datascope:view', 1, @system_id, NOW(), NOW()),

-- 授权审计
('authorization-audit', '授权审计', '/system/audit/authorization', '/sys/audit/authorization', 'GET', '', false, 8, '/views/audit/AuthorizationAudit.vue', '', false, false, 'system:audit:auth:view', 1, @system_id, NOW(), NOW()),

-- RBAC3 约束
('role-constraint', 'RBAC3 约束', '/system/role/constraint', '/sys/role-constraints/hierarchy', 'GET', '', false, 9, '/views/constraint/RoleConstraint.vue', '', false, false, 'system:role:constraint:view', 1, @system_id, NOW(), NOW());

-- 插入个人页子菜单
INSERT INTO resource (name, title, url, uri, method, icon, show_icon, sort, component, redirect, hidden, keep_alive, permission, type, parent_id, created_at, updated_at) VALUES
-- 个人中心
('profile-center', '个人中心', '/profile/center', '/api/profile/center', 'GET', '', false, 1, '/views/Profile.vue', '', false, false, 'profile:center:view', 1, @profile_id, NOW(), NOW()),

-- 个人设置
('profile-setting', '个人设置', '/profile/setting', '/api/profile/setting', 'GET', '', false, 2, '/views/Setting.vue', '', false, false, 'profile:setting:view', 1, @profile_id, NOW(), NOW());

-- 插入异常页子菜单
INSERT INTO resource (name, title, url, uri, method, icon, show_icon, sort, component, redirect, hidden, keep_alive, permission, type, parent_id, created_at, updated_at) VALUES
-- 403页面
('exception-403', '403', '/exception/403', '', '', '', false, 1, '/views/403.vue', '', false, false, 'exception:page-403:view', 1, @exception_id, NOW(), NOW()),

-- 404页面
('exception-404', '404', '/exception/404', '', '', '', false, 2, '/views/404.vue', '', false, false, 'exception:page-404:view', 1, @exception_id, NOW(), NOW()),

-- 500页面
('exception-500', '500', '/exception/500', '', '', '', false, 3, '/views/500.vue', '', false, false, 'exception:page-500:view', 1, @exception_id, NOW(), NOW());

-- 验证插入结果
SELECT 
    id, name, title, url, type, parent_id, sort, 
    CASE type 
        WHEN 0 THEN '目录' 
        WHEN 1 THEN '菜单' 
        WHEN 2 THEN '按钮' 
        WHEN 3 THEN '接口' 
    END as type_name
FROM resource 
WHERE type IN (0, 1) 
ORDER BY sort, id; 
