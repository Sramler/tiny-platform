-- PermissionRefactor Phase B statistics report

-- 1) 候选总量：resource.permission 非空且 trim 后非空
SELECT COUNT(*) AS candidate_count
FROM resource
WHERE permission IS NOT NULL
  AND TRIM(permission) <> '';

-- 2) 去重总量：按 normalized_tenant_id + permission_code 去重
SELECT COUNT(*) AS deduplicated_count
FROM (
  SELECT
    COALESCE(tenant_id, 0) AS normalized_tenant_id,
    TRIM(permission) AS permission_code
  FROM resource
  WHERE permission IS NOT NULL
    AND TRIM(permission) <> ''
  GROUP BY COALESCE(tenant_id, 0), TRIM(permission)
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
