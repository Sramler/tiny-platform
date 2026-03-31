-- Dev smoke summary for 10-minute validation window

SELECT COUNT(*) AS permission_total FROM permission;

SELECT tenant_id, enabled, COUNT(*) AS cnt
FROM permission
GROUP BY tenant_id, enabled
ORDER BY tenant_id, enabled;

SELECT tenant_id, scope_type, scope_id, COUNT(*) AS active_assignments
FROM role_assignment
WHERE status = 'ACTIVE'
  AND start_time <= NOW()
  AND (end_time IS NULL OR end_time > NOW())
GROUP BY tenant_id, scope_type, scope_id
ORDER BY tenant_id, scope_type, scope_id;

SELECT tenant_id, COUNT(*) AS hierarchy_edges
FROM role_hierarchy
GROUP BY tenant_id
ORDER BY tenant_id;
