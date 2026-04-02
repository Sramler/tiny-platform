-- Fix A: backfill missing permission master data from canonical carrier.permission.
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
    grouped.tenant_id,
    grouped.permission_code,
    grouped.permission_code AS permission_name,
    SUBSTRING_INDEX(grouped.permission_code, ':', 1) AS module_code,
    CASE
        WHEN INSTR(grouped.permission_code, ':') > 0 THEN SUBSTRING_INDEX(grouped.permission_code, ':', -1)
        ELSE 'view'
    END AS action_code,
    grouped.permission_type,
    grouped.enabled,
    0 AS built_in_flag,
    -999001 AS created_by,
    -999001 AS updated_by
FROM (
    SELECT
        src.tenant_id,
        src.permission_code,
        CASE
            WHEN MAX(CASE WHEN src.carrier_type = 'API' THEN 1 ELSE 0 END) = 1 THEN 'API'
            WHEN MAX(CASE WHEN src.carrier_type = 'BUTTON' THEN 1 ELSE 0 END) = 1 THEN 'BUTTON'
            WHEN MAX(CASE WHEN src.carrier_type = 'MENU' THEN 1 ELSE 0 END) = 1 THEN 'MENU'
            ELSE 'OTHER'
        END AS permission_type,
        MAX(src.enabled) AS enabled
    FROM (
        SELECT m.tenant_id, TRIM(m.permission) AS permission_code, 'MENU' AS carrier_type, m.enabled
        FROM menu m
        WHERE m.tenant_id IN (1, 3)
          AND m.permission IS NOT NULL
          AND TRIM(m.permission) <> ''
        UNION ALL
        SELECT a.tenant_id, TRIM(a.permission) AS permission_code, 'BUTTON' AS carrier_type, a.enabled
        FROM ui_action a
        WHERE a.tenant_id IN (1, 3)
          AND a.permission IS NOT NULL
          AND TRIM(a.permission) <> ''
        UNION ALL
        SELECT e.tenant_id, TRIM(e.permission) AS permission_code, 'API' AS carrier_type, e.enabled
        FROM api_endpoint e
        WHERE e.tenant_id IN (1, 3)
          AND e.permission IS NOT NULL
          AND TRIM(e.permission) <> ''
    ) src
    GROUP BY src.tenant_id, src.permission_code
) grouped
LEFT JOIN permission p
  ON p.tenant_id <=> grouped.tenant_id
 AND p.permission_code = grouped.permission_code
WHERE p.id IS NULL;
