-- Fix A: backfill missing permission master data from legacy resource.permission.
-- Safety: only for non-empty permission_code in tenant 1/3 and only when missing.

INSERT INTO permission (
    tenant_id,
    permission_code,
    permission_name,
    module_code,
    action_code,
    permission_type,
    enabled,
    built_in_flag,
    created_by,
    updated_by
)
SELECT
    r.tenant_id,
    TRIM(r.permission) AS permission_code,
    TRIM(r.permission) AS permission_name,
    SUBSTRING_INDEX(TRIM(r.permission), ':', 1) AS module_code,
    CASE
        WHEN INSTR(TRIM(r.permission), ':') > 0 THEN SUBSTRING_INDEX(TRIM(r.permission), ':', -1)
        ELSE 'view'
    END AS action_code,
    CASE
        WHEN TRIM(r.permission) LIKE '%:%' THEN 'ACTION'
        ELSE 'CUSTOM'
    END AS permission_type,
    1 AS enabled,
    0 AS built_in_flag,
    -999001 AS created_by,
    -999001 AS updated_by
FROM resource r
LEFT JOIN permission p
  ON p.tenant_id = r.tenant_id
 AND p.permission_code = TRIM(r.permission)
WHERE r.tenant_id IN (1, 3)
  AND r.permission IS NOT NULL
  AND TRIM(r.permission) <> ''
  AND p.id IS NULL;
