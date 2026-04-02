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

-- 3. Carrier rows in the default tenant that share the same permission with a PLATFORM template carrier
SELECT res.*
FROM (
  SELECT 'menu' AS carrier_type, m.id, m.tenant_id, m.name, m.permission
  FROM menu m
  UNION ALL
  SELECT 'ui_action' AS carrier_type, a.id, a.tenant_id, a.name, a.permission
  FROM ui_action a
  UNION ALL
  SELECT 'api_endpoint' AS carrier_type, e.id, e.tenant_id, e.name, e.permission
  FROM api_endpoint e
) res
JOIN tenant t ON res.tenant_id = t.id
WHERE t.code = 'default'
  AND EXISTS (
    SELECT 1
    FROM (
      SELECT m.permission FROM menu m WHERE m.tenant_id IS NULL
      UNION ALL
      SELECT a.permission FROM ui_action a WHERE a.tenant_id IS NULL
      UNION ALL
      SELECT e.permission FROM api_endpoint e WHERE e.tenant_id IS NULL
    ) p
    WHERE p.permission = res.permission
  )
ORDER BY res.permission, res.carrier_type;

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
SELECT 'default_tenant_template_carriers' AS metric, COUNT(*) AS count
FROM (
  SELECT m.tenant_id, m.permission FROM menu m
  UNION ALL
  SELECT a.tenant_id, a.permission FROM ui_action a
  UNION ALL
  SELECT e.tenant_id, e.permission FROM api_endpoint e
) res
JOIN tenant t ON res.tenant_id = t.id
WHERE t.code = 'default'
  AND EXISTS (
    SELECT 1
    FROM (
      SELECT m.permission FROM menu m WHERE m.tenant_id IS NULL
      UNION ALL
      SELECT a.permission FROM ui_action a WHERE a.tenant_id IS NULL
      UNION ALL
      SELECT e.permission FROM api_endpoint e WHERE e.tenant_id IS NULL
    ) p
    WHERE p.permission = res.permission
  );
