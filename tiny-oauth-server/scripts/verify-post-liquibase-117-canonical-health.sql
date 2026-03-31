-- POST_LIQUIBASE_117: Expect role_resource table absent; canonical row counts for sanity.

SELECT COUNT(*) AS role_resource_table_exists
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'role_resource';

SELECT COUNT(*) AS role_permission_rows FROM role_permission;
SELECT COUNT(*) AS permission_rows FROM permission;
