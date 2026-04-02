-- PermissionRefactor Phase B anomaly report

SELECT
  src.tenant_id,
  COALESCE(src.tenant_id, 0) AS normalized_tenant_id,
  src.carrier_type,
  src.carrier_id,
  src.carrier_name,
  src.raw_permission,
  CASE
    WHEN src.permission_code IS NULL OR src.permission_code = '' THEN 'EMPTY_PERMISSION'
    WHEN src.permission_code NOT REGEXP '^[^:[:space:]]+(:[^:[:space:]]+){2,}$' THEN 'INVALID_FORMAT'
    WHEN SUBSTRING_INDEX(src.permission_code, ':', 1) = ''
      OR SUBSTRING_INDEX(src.permission_code, ':', -1) = '' THEN 'PARSE_FAILED'
    ELSE 'UNKNOWN_PATTERN'
  END AS abnormal_reason,
  CASE
    WHEN src.permission_code IS NULL OR src.permission_code = '' THEN 'fill or remove empty permission'
    WHEN src.permission_code NOT REGEXP '^[^:[:space:]]+(:[^:[:space:]]+){2,}$' THEN 'normalize to project permission naming spec'
    WHEN SUBSTRING_INDEX(src.permission_code, ':', 1) = ''
      OR SUBSTRING_INDEX(src.permission_code, ':', -1) = '' THEN 'repair domain/action segments'
    ELSE 'manual review'
  END AS suggested_action
FROM (
  SELECT
    'menu' AS carrier_type,
    m.id AS carrier_id,
    m.tenant_id,
    m.name AS carrier_name,
    m.permission AS raw_permission,
    TRIM(m.permission) AS permission_code
  FROM `menu` m
  WHERE m.permission IS NOT NULL
  UNION ALL
  SELECT
    'ui_action' AS carrier_type,
    a.id AS carrier_id,
    a.tenant_id,
    a.name AS carrier_name,
    a.permission AS raw_permission,
    TRIM(a.permission) AS permission_code
  FROM `ui_action` a
  WHERE a.permission IS NOT NULL
  UNION ALL
  SELECT
    'api_endpoint' AS carrier_type,
    e.id AS carrier_id,
    e.tenant_id,
    e.name AS carrier_name,
    e.permission AS raw_permission,
    TRIM(e.permission) AS permission_code
  FROM `api_endpoint` e
  WHERE e.permission IS NOT NULL
) src
WHERE src.permission_code = ''
   OR src.permission_code NOT REGEXP '^[^:[:space:]]+(:[^:[:space:]]+){2,}$'
   OR SUBSTRING_INDEX(src.permission_code, ':', 1) = ''
   OR SUBSTRING_INDEX(src.permission_code, ':', -1) = ''
ORDER BY src.tenant_id ASC, src.carrier_type ASC, src.carrier_id ASC;
