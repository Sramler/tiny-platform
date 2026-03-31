-- Post role_resource drop: canonical authorization row counts (no legacy table required).

SELECT COUNT(*) AS role_permission_rows FROM role_permission;
SELECT COUNT(*) AS permission_rows FROM permission;
