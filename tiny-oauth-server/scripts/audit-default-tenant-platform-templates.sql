-- Audit script: find potential template data in the "default" tenant
-- that now has an equivalent PLATFORM-level template (tenant_id IS NULL).
--
-- This script is **read-only**. It does NOT delete any data.
-- Use it to review candidates, then decide whether and how to clean them
-- via dedicated migration or manual operations in each environment.
--
-- 1. Find the default tenant id
SELECT id, code, name
FROM tenant
WHERE code = 'default';

-- 2. Roles in the default tenant that share the same code with a PLATFORM template role
SELECT r.*
FROM role r
JOIN tenant t ON r.tenant_id = t.id
WHERE t.code = 'default'
  AND EXISTS (
    SELECT 1
    FROM role p
    WHERE p.tenant_id IS NULL
      AND p.code = r.code
  )
ORDER BY r.code;

-- 3. Resources in the default tenant that share the same permission with a PLATFORM template resource
SELECT res.*
FROM resource res
JOIN tenant t ON res.tenant_id = t.id
WHERE t.code = 'default'
  AND EXISTS (
    SELECT 1
    FROM resource p
    WHERE p.tenant_id IS NULL
      AND p.permission = res.permission
  )
ORDER BY res.permission;

-- 4. Optional: count summary for quick overview
SELECT 'default_tenant_template_roles' AS metric, COUNT(*) AS count
FROM role r
JOIN tenant t ON r.tenant_id = t.id
WHERE t.code = 'default'
  AND EXISTS (
    SELECT 1
    FROM role p
    WHERE p.tenant_id IS NULL
      AND p.code = r.code
  )
UNION ALL
SELECT 'default_tenant_template_resources' AS metric, COUNT(*) AS count
FROM resource res
JOIN tenant t ON res.tenant_id = t.id
WHERE t.code = 'default'
  AND EXISTS (
    SELECT 1
    FROM resource p
    WHERE p.tenant_id IS NULL
      AND p.permission = res.permission
  );

