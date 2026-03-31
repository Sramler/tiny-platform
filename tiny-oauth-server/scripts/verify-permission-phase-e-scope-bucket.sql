-- Phase E scope bucket verification (TENANT / ORG / DEPT)
-- Verify role_assignment isolation by (tenant_id, scope_type, scope_id)

-- 1) active assignment count by bucket
SELECT ra.tenant_id,
       ra.scope_type,
       ra.scope_id,
       COUNT(*) AS active_assignment_count
FROM role_assignment ra
WHERE ra.status = 'ACTIVE'
  AND ra.start_time <= NOW()
  AND (ra.end_time IS NULL OR ra.end_time > NOW())
GROUP BY ra.tenant_id, ra.scope_type, ra.scope_id
ORDER BY ra.tenant_id, ra.scope_type, ra.scope_id;

-- 2) role_hierarchy edge count by tenant (hierarchy input bucket baseline)
SELECT rh.tenant_id,
       COUNT(*) AS hierarchy_edges
FROM role_hierarchy rh
GROUP BY rh.tenant_id
ORDER BY rh.tenant_id;

-- 3) detect mixed-scope assignments with null scope_id in ORG/DEPT (should be 0)
SELECT ra.tenant_id,
       ra.scope_type,
       COUNT(*) AS invalid_rows
FROM role_assignment ra
WHERE ra.scope_type IN ('ORG', 'DEPT')
  AND (ra.scope_id IS NULL OR ra.scope_id <= 0)
GROUP BY ra.tenant_id, ra.scope_type
HAVING COUNT(*) > 0;
