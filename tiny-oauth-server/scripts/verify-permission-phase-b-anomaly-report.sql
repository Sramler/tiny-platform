-- PermissionRefactor Phase B anomaly report

SELECT
  src.tenant_id,
  COALESCE(src.tenant_id, 0) AS normalized_tenant_id,
  src.resource_id,
  src.resource_name,
  src.resource_type,
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
    r.id AS resource_id,
    r.tenant_id,
    r.name AS resource_name,
    r.type AS resource_type,
    r.permission AS raw_permission,
    TRIM(r.permission) AS permission_code
  FROM `resource` r
  WHERE r.permission IS NOT NULL
) src
WHERE src.permission_code = ''
   OR src.permission_code NOT REGEXP '^[^:[:space:]]+(:[^:[:space:]]+){2,}$'
   OR SUBSTRING_INDEX(src.permission_code, ':', 1) = ''
   OR SUBSTRING_INDEX(src.permission_code, ':', -1) = ''
ORDER BY src.tenant_id ASC, src.resource_id ASC;
