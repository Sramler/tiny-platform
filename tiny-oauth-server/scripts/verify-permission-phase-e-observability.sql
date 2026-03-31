-- Phase E observability verification
-- 1) authority fallback / deny / unknown count signals from auth-related audit logs (if enabled)
-- Replace table/columns if deployment uses a different log sink.

-- Example placeholder query for authorization audit extension table
-- SELECT event_type, COUNT(*) AS total
-- FROM authorization_audit_log
-- WHERE created_at >= NOW() - INTERVAL 1 DAY
--   AND event_type IN (
--     'AUTHORITY_DENY_DISABLED',
--     'AUTHORITY_DENY_UNKNOWN',
--     'PERMISSION_VERSION_CHANGED'
--   )
-- GROUP BY event_type
-- ORDER BY event_type;

-- 2) permission.enabled distribution (deny candidate baseline)
SELECT p.tenant_id,
       p.enabled,
       COUNT(*) AS total_permissions
FROM permission p
GROUP BY p.tenant_id, p.enabled
ORDER BY p.tenant_id, p.enabled;

-- 3) role_permission coverage baseline
SELECT rp.tenant_id,
       COUNT(*) AS role_permission_rows,
       COUNT(DISTINCT rp.role_id) AS role_count,
       COUNT(DISTINCT rp.permission_id) AS permission_count
FROM role_permission rp
GROUP BY rp.tenant_id
ORDER BY rp.tenant_id;
