-- Cleanup RBAC3 dry-run violation logs.
--
-- Suggested policy:
-- - Keep recent 30 days for troubleshooting and rollout analysis.
-- - Run daily during off-peak hours.
--
-- Notes:
-- - This is a safe delete (no business source of truth depends on it).
-- - If you want to keep longer for compliance, adjust RETENTION_DAYS.

SET @RETENTION_DAYS = 30;

DELETE FROM role_constraint_violation_log
WHERE created_at < DATE_SUB(NOW(), INTERVAL @RETENTION_DAYS DAY);

