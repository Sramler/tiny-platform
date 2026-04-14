-- CARD-13B：补全 RBAC3 角色约束控制面 api_endpoint 登记，避免统一守卫对未登记接口 fail-closed。
-- 幂等：按 (tenant_id, uri, method) 去重插入；requirement 行 INSERT IGNORE。

INSERT INTO api_endpoint (
  tenant_id, resource_level, name, title, uri, method, permission, required_permission_id, enabled, created_at, updated_at
)
SELECT 1, 'TENANT', 'role-constraint-hierarchy-post', 'RBAC3 层级约束写入', '/sys/role-constraints/hierarchy', 'POST',
       'system:role:constraint:edit', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM permission p
WHERE p.tenant_id = 1 AND p.permission_code = 'system:role:constraint:edit'
  AND NOT EXISTS (SELECT 1 FROM api_endpoint e WHERE e.tenant_id = 1 AND e.uri = '/sys/role-constraints/hierarchy' AND e.method = 'POST');

INSERT INTO api_endpoint (
  tenant_id, resource_level, name, title, uri, method, permission, required_permission_id, enabled, created_at, updated_at
)
SELECT 1, 'TENANT', 'role-constraint-mutex-post', 'RBAC3 互斥约束写入', '/sys/role-constraints/mutex', 'POST',
       'system:role:constraint:edit', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM permission p
WHERE p.tenant_id = 1 AND p.permission_code = 'system:role:constraint:edit'
  AND NOT EXISTS (SELECT 1 FROM api_endpoint e WHERE e.tenant_id = 1 AND e.uri = '/sys/role-constraints/mutex' AND e.method = 'POST');

INSERT INTO api_endpoint (
  tenant_id, resource_level, name, title, uri, method, permission, required_permission_id, enabled, created_at, updated_at
)
SELECT 1, 'TENANT', 'role-constraint-prerequisite-post', 'RBAC3 前置约束写入', '/sys/role-constraints/prerequisite', 'POST',
       'system:role:constraint:edit', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM permission p
WHERE p.tenant_id = 1 AND p.permission_code = 'system:role:constraint:edit'
  AND NOT EXISTS (SELECT 1 FROM api_endpoint e WHERE e.tenant_id = 1 AND e.uri = '/sys/role-constraints/prerequisite' AND e.method = 'POST');

INSERT INTO api_endpoint (
  tenant_id, resource_level, name, title, uri, method, permission, required_permission_id, enabled, created_at, updated_at
)
SELECT 1, 'TENANT', 'role-constraint-cardinality-post', 'RBAC3 基数约束写入', '/sys/role-constraints/cardinality', 'POST',
       'system:role:constraint:edit', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM permission p
WHERE p.tenant_id = 1 AND p.permission_code = 'system:role:constraint:edit'
  AND NOT EXISTS (SELECT 1 FROM api_endpoint e WHERE e.tenant_id = 1 AND e.uri = '/sys/role-constraints/cardinality' AND e.method = 'POST');

INSERT INTO api_endpoint (
  tenant_id, resource_level, name, title, uri, method, permission, required_permission_id, enabled, created_at, updated_at
)
SELECT 1, 'TENANT', 'role-constraint-hierarchy-get', 'RBAC3 层级约束查询', '/sys/role-constraints/hierarchy', 'GET',
       'system:role:constraint:view', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM permission p
WHERE p.tenant_id = 1 AND p.permission_code = 'system:role:constraint:view'
  AND NOT EXISTS (SELECT 1 FROM api_endpoint e WHERE e.tenant_id = 1 AND e.uri = '/sys/role-constraints/hierarchy' AND e.method = 'GET');

INSERT INTO api_endpoint (
  tenant_id, resource_level, name, title, uri, method, permission, required_permission_id, enabled, created_at, updated_at
)
SELECT 1, 'TENANT', 'role-constraint-mutex-get', 'RBAC3 互斥约束查询', '/sys/role-constraints/mutex', 'GET',
       'system:role:constraint:view', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM permission p
WHERE p.tenant_id = 1 AND p.permission_code = 'system:role:constraint:view'
  AND NOT EXISTS (SELECT 1 FROM api_endpoint e WHERE e.tenant_id = 1 AND e.uri = '/sys/role-constraints/mutex' AND e.method = 'GET');

INSERT INTO api_endpoint (
  tenant_id, resource_level, name, title, uri, method, permission, required_permission_id, enabled, created_at, updated_at
)
SELECT 1, 'TENANT', 'role-constraint-prerequisite-get', 'RBAC3 前置约束查询', '/sys/role-constraints/prerequisite', 'GET',
       'system:role:constraint:view', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM permission p
WHERE p.tenant_id = 1 AND p.permission_code = 'system:role:constraint:view'
  AND NOT EXISTS (SELECT 1 FROM api_endpoint e WHERE e.tenant_id = 1 AND e.uri = '/sys/role-constraints/prerequisite' AND e.method = 'GET');

INSERT INTO api_endpoint (
  tenant_id, resource_level, name, title, uri, method, permission, required_permission_id, enabled, created_at, updated_at
)
SELECT 1, 'TENANT', 'role-constraint-cardinality-get', 'RBAC3 基数约束查询', '/sys/role-constraints/cardinality', 'GET',
       'system:role:constraint:view', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM permission p
WHERE p.tenant_id = 1 AND p.permission_code = 'system:role:constraint:view'
  AND NOT EXISTS (SELECT 1 FROM api_endpoint e WHERE e.tenant_id = 1 AND e.uri = '/sys/role-constraints/cardinality' AND e.method = 'GET');

INSERT INTO api_endpoint (
  tenant_id, resource_level, name, title, uri, method, permission, required_permission_id, enabled, created_at, updated_at
)
SELECT 1, 'TENANT', 'role-constraint-violations-get', 'RBAC3 约束违例日志查询', '/sys/role-constraints/violations', 'GET',
       'system:role:constraint:violation:view', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM permission p
WHERE p.tenant_id = 1 AND p.permission_code = 'system:role:constraint:violation:view'
  AND NOT EXISTS (SELECT 1 FROM api_endpoint e WHERE e.tenant_id = 1 AND e.uri = '/sys/role-constraints/violations' AND e.method = 'GET');

INSERT IGNORE INTO api_endpoint_permission_requirement (
  tenant_id, api_endpoint_id, requirement_group, sort_order, permission_id, negated, created_at, updated_at
)
SELECT
  e.tenant_id,
  e.id,
  0,
  1,
  e.required_permission_id,
  0,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM api_endpoint e
WHERE e.tenant_id = 1
  AND e.uri IN (
    '/sys/role-constraints/hierarchy',
    '/sys/role-constraints/mutex',
    '/sys/role-constraints/prerequisite',
    '/sys/role-constraints/cardinality',
    '/sys/role-constraints/violations'
  )
  AND e.method IN ('GET', 'POST')
  AND e.required_permission_id IS NOT NULL;
