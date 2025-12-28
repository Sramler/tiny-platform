-- 验证菜单删除修复是否生效
-- 用途：检查删除菜单时是否正确清理了角色关联关系

USE tiny_web;

-- 1. 检查资源 ID=4 及其子菜单是否还存在
SELECT 
    '=== 检查资源是否存在 ===' as section;
SELECT 
    id,
    name,
    title,
    type,
    parent_id,
    CASE 
        WHEN id = 4 THEN '父菜单（异常页）'
        WHEN parent_id = 4 THEN '子菜单'
        ELSE '其他'
    END as menu_type
FROM resource
WHERE id IN (4, 9, 10, 11)
ORDER BY id;

-- 2. 检查这些资源是否还有角色关联（应该返回空，表示已正确清理）
SELECT 
    '=== 检查角色关联（应该为空） ===' as section;
SELECT 
    r.id as resource_id,
    r.name as resource_name,
    r.title as resource_title,
    COUNT(rr.role_id) as role_count,
    GROUP_CONCAT(rr.role_id) as role_ids
FROM resource r
LEFT JOIN role_resource rr ON r.id = rr.resource_id
WHERE r.id IN (4, 9, 10, 11)
GROUP BY r.id, r.name, r.title
HAVING role_count > 0;

-- 3. 如果资源已删除，检查 role_resource 表中是否还有残留的关联记录
SELECT 
    '=== 检查残留的角色关联记录 ===' as section;
SELECT 
    rr.role_id,
    rr.resource_id,
    r.name as resource_name,
    r.title as resource_title
FROM role_resource rr
LEFT JOIN resource r ON rr.resource_id = r.id
WHERE rr.resource_id IN (4, 9, 10, 11)
   OR r.id IS NULL;  -- 查找指向已删除资源的关联记录

-- 4. 统计信息
SELECT 
    '=== 统计信息 ===' as section;
SELECT 
    COUNT(*) as total_resources,
    SUM(CASE WHEN id IN (4, 9, 10, 11) THEN 1 ELSE 0 END) as target_resources,
    (SELECT COUNT(*) FROM role_resource WHERE resource_id IN (4, 9, 10, 11)) as remaining_associations
FROM resource;

