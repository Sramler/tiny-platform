-- 分析 traceId: c49236ca0b6342e9876b571c6c0c07d7 的请求日志
-- 用途：定位系统异常根因并验证修复

USE tiny_web;

-- 1. 查询该 traceId 的所有请求日志（按时间倒序）
SELECT 
    '=== 请求日志详情 ===' as section;
SELECT 
    id,
    trace_id,
    request_id,
    service_name,
    env,
    module,
    user_id,
    client_ip,
    method,
    path_template,
    raw_path,
    query_string,
    status,
    success,
    duration_ms,
    error,
    request_at,
    created_at
FROM http_request_log
WHERE trace_id = 'c49236ca0b6342e9876b571c6c0c07d7'
ORDER BY request_at DESC;

-- 2. 查询错误请求的详细信息（包括请求体和响应体）
SELECT 
    '=== 错误请求详情 ===' as section;
SELECT 
    id,
    trace_id,
    request_id,
    method,
    path_template,
    raw_path,
    status,
    error,
    LEFT(request_body, 500) as request_body_preview,
    LEFT(response_body, 1000) as response_body_preview,
    request_at
FROM http_request_log
WHERE trace_id = 'c49236ca0b6342e9876b571c6c0c07d7'
  AND status >= 400
ORDER BY request_at DESC;

-- 3. 分析问题：检查被删除的资源是否还有角色关联
SELECT 
    '=== 问题分析：检查资源关联 ===' as section;
-- 根据日志，删除的是 /sys/menus/4，对应 resource 表的 id=4
-- 检查该资源是否还有角色关联
SELECT 
    r.id as resource_id,
    r.name as resource_name,
    r.title as resource_title,
    r.type as resource_type,
    COUNT(rr.role_id) as role_count
FROM resource r
LEFT JOIN role_resource rr ON r.id = rr.resource_id
WHERE r.id = 4
GROUP BY r.id, r.name, r.title, r.type;

-- 4. 检查该资源的所有子菜单
SELECT 
    '=== 检查子菜单 ===' as section;
SELECT 
    id,
    name,
    title,
    type,
    parent_id,
    sort
FROM resource
WHERE parent_id = 4
ORDER BY sort ASC;

-- 5. 检查子菜单是否还有角色关联
SELECT 
    '=== 检查子菜单的角色关联 ===' as section;
SELECT 
    r.id as resource_id,
    r.name as resource_name,
    r.title as resource_title,
    r.type as resource_type,
    COUNT(rr.role_id) as role_count
FROM resource r
LEFT JOIN role_resource rr ON r.id = rr.resource_id
WHERE r.parent_id = 4
GROUP BY r.id, r.name, r.title, r.type
ORDER BY r.sort ASC;

