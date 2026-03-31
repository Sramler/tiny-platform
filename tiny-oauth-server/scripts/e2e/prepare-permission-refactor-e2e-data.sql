-- Prepare minimal E2E fixtures for permission refactor validation.
-- Safe to execute repeatedly on test databases.

-- 1) Ensure smoke tenants exist in assignment buckets (validation query only).
SELECT DISTINCT tenant_id, scope_type, scope_id
FROM role_assignment
WHERE status = 'ACTIVE'
  AND tenant_id IN (1, 3)
ORDER BY tenant_id, scope_type, scope_id;

-- 2) Pick one enabled permission code per tenant as mutable probe.
SELECT tenant_id, permission_code, enabled
FROM permission
WHERE tenant_id IN (1, 3)
  AND enabled = 1
ORDER BY tenant_id, id
LIMIT 10;

-- 3) Snapshot current hierarchy edges for rollback reference.
SELECT tenant_id, parent_role_id, child_role_id, updated_at
FROM role_hierarchy
WHERE tenant_id IN (1, 3)
ORDER BY tenant_id, parent_role_id, child_role_id;
