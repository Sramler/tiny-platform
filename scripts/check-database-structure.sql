-- 数据库结构检查脚本
-- 用途：检查当前数据库的表结构，用于与 schema.sql 对比

USE tiny_web;

-- 1. 列出所有表
SELECT 
    '=== 所有表 ===' as section;
SELECT 
    TABLE_NAME as '表名',
    TABLE_ROWS as '行数',
    ROUND(((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024), 2) as '大小(MB)',
    TABLE_COMMENT as '注释'
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = 'tiny_web' 
  AND TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;

-- 2. 检查 user 表结构（确认是否包含 password 字段）
SELECT 
    '=== user 表结构 ===' as section;
SELECT 
    COLUMN_NAME as '字段名',
    DATA_TYPE as '数据类型',
    IS_NULLABLE as '可空',
    COLUMN_DEFAULT as '默认值',
    COLUMN_COMMENT as '注释',
    EXTRA as '额外信息'
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'tiny_web' 
  AND TABLE_NAME = 'user'
ORDER BY ORDINAL_POSITION;

-- 3. 检查 resource 表结构（确认字段名是 path 还是 url）
SELECT 
    '=== resource 表结构 ===' as section;
SELECT 
    COLUMN_NAME as '字段名',
    DATA_TYPE as '数据类型',
    IS_NULLABLE as '可空',
    COLUMN_DEFAULT as '默认值',
    COLUMN_COMMENT as '注释'
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'tiny_web' 
  AND TABLE_NAME = 'resource'
ORDER BY ORDINAL_POSITION;

-- 4. 检查 user_avatar 表的 CHECK 约束
SELECT 
    '=== user_avatar 表约束 ===' as section;
SELECT 
    CONSTRAINT_NAME as '约束名',
    CONSTRAINT_TYPE as '约束类型',
    TABLE_NAME as '表名'
FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = 'tiny_web' 
  AND TABLE_NAME = 'user_avatar';

-- 5. 检查所有表的字段统计
SELECT 
    '=== 字段统计 ===' as section;
SELECT 
    TABLE_NAME as '表名',
    COUNT(*) as '字段数'
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'tiny_web'
GROUP BY TABLE_NAME
ORDER BY TABLE_NAME;

-- 6. 检查初始数据
SELECT 
    '=== 初始数据检查 ===' as section;

SELECT 'user 表数据' as table_name, COUNT(*) as count FROM user
UNION ALL
SELECT 'role 表数据', COUNT(*) FROM role
UNION ALL
SELECT 'tenant_user 表数据', COUNT(*) FROM tenant_user
UNION ALL
SELECT 'role_assignment 表数据', COUNT(*) FROM role_assignment
UNION ALL
SELECT 'resource 表数据', COUNT(*) FROM resource
UNION ALL
SELECT 'user_authentication_method 表数据', COUNT(*) FROM user_authentication_method;

-- 7. 检查关键数据
SELECT 
    '=== 关键数据示例 ===' as section;

SELECT 'user 表' as table_name, id, username, nickname FROM user ORDER BY id LIMIT 5;
SELECT 'role 表' as table_name, id, code, name FROM role ORDER BY id LIMIT 5;
SELECT 'resource 表' as table_name, id, name, url FROM resource ORDER BY id LIMIT 5;

