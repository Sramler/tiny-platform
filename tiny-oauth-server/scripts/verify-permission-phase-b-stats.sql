-- PermissionRefactor Phase B statistics report (carrier-backed)

-- 1) 候选总量：carrier.permission 非空且 trim 后非空
SELECT COUNT(*) AS candidate_count
FROM (
  SELECT permission FROM menu
  UNION ALL
  SELECT permission FROM ui_action
  UNION ALL
  SELECT permission FROM api_endpoint
) carrier_permission
WHERE permission IS NOT NULL
  AND TRIM(permission) <> '';

-- 2) 去重总量：按 normalized_tenant_id + permission_code 去重
SELECT COUNT(*) AS deduplicated_count
FROM (
  SELECT
    COALESCE(src.tenant_id, 0) AS normalized_tenant_id,
    src.permission_code
  FROM (
    SELECT tenant_id, TRIM(permission) AS permission_code FROM menu
    WHERE permission IS NOT NULL AND TRIM(permission) <> ''
    UNION ALL
    SELECT tenant_id, TRIM(permission) AS permission_code FROM ui_action
    WHERE permission IS NOT NULL AND TRIM(permission) <> ''
    UNION ALL
    SELECT tenant_id, TRIM(permission) AS permission_code FROM api_endpoint
    WHERE permission IS NOT NULL AND TRIM(permission) <> ''
  ) src
  GROUP BY COALESCE(src.tenant_id, 0), src.permission_code
) t;

-- 3) 平台 / 租户分布：permission 表当前分布
SELECT
  CASE WHEN tenant_id IS NULL THEN 'PLATFORM' ELSE 'TENANT' END AS scope_kind,
  COUNT(*) AS cnt
FROM permission
GROUP BY CASE WHEN tenant_id IS NULL THEN 'PLATFORM' ELSE 'TENANT' END;

-- 4) enabled 分布：permission 表当前 enabled 分布
SELECT enabled, COUNT(*) AS cnt
FROM permission
GROUP BY enabled
ORDER BY enabled;
