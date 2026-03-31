-- PRE_LIQUIBASE_117_ONLY: Run before applying 117-drop-role-resource-legacy (while role_resource still exists).
-- Fails fast in application terms if gap > 0; execute manually and review output.

SELECT COUNT(*) AS role_permission_rows FROM role_permission;
SELECT COUNT(*) AS permission_rows FROM permission;

WITH rr_perm AS (
  SELECT DISTINCT
    IFNULL(rr.tenant_id, 0) AS normalized_tenant_id,
    rr.role_id,
    TRIM(r.permission) AS permission_code
  FROM role_resource rr
  JOIN resource r ON r.id = rr.resource_id
  WHERE TRIM(IFNULL(r.permission, '')) <> ''
),
rp_perm AS (
  SELECT DISTINCT
    IFNULL(rp.tenant_id, 0) AS normalized_tenant_id,
    rp.role_id,
    TRIM(p.permission_code) AS permission_code
  FROM role_permission rp
  JOIN permission p ON p.id = rp.permission_id
  WHERE TRIM(IFNULL(p.permission_code, '')) <> ''
)
SELECT COUNT(*) AS missing_in_role_permission
FROM rr_perm rr
LEFT JOIN rp_perm rp
  ON rp.normalized_tenant_id = rr.normalized_tenant_id
 AND rp.role_id = rr.role_id
 AND rp.permission_code = rr.permission_code
WHERE rp.role_id IS NULL;
