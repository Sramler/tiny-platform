-- Backfill missing tenant_id=3 standard tenant roles (7 roles) from tenant_id=1.
-- Assumption: tenant_id=1 already has correct roles + role_permission bindings for standard tenant roles.
-- Action:
-- 1) Create missing role rows for tenant_id=3 by copying code/name/description/builtin/enabled/role_level from tenant_id=1.
-- 2) Copy role_permission by mapping permission_id via permission_code + normalized_tenant_id（与 TenantBootstrap / RoleRepository 口径一致）。
-- 3) Copy role_data_scope rows 1:1 for those role codes.

START TRANSACTION;

SET @src_tenant_id := 1;
SET @tgt_tenant_id := 3;

-- Standard tenant role codes to ensure
-- NOTE: ROLE_TENANT_ADMIN already exists in your DB (tenant_id=3), so we only backfill the missing 6 roles.
SET @tenant_role_codes := 'ROLE_USER_MANAGER,ROLE_OPERATOR,ROLE_VIEWER,ROLE_DEVELOPER,ROLE_GUEST,ROLE_USER';

-- 1) Insert missing role rows (copy from src tenant)
INSERT IGNORE INTO `role` (`tenant_id`, `code`, `name`, `description`, `builtin`, `enabled`, `role_level`)
SELECT
  @tgt_tenant_id,
  r_src.`code`,
  -- role.name is globally unique in this DB; add a tenant suffix to avoid collisions
  CONCAT(r_src.`name`, ' (tenant-', @tgt_tenant_id, ')'),
  r_src.`description`,
  r_src.`builtin`,
  r_src.`enabled`,
  r_src.`role_level`
FROM `role` r_src
WHERE r_src.`tenant_id` = @src_tenant_id
  AND r_src.`code` IN ('ROLE_USER_MANAGER','ROLE_OPERATOR','ROLE_VIEWER','ROLE_DEVELOPER','ROLE_GUEST','ROLE_USER')
  AND NOT EXISTS (
    SELECT 1
    FROM `role` r_tgt
    WHERE r_tgt.`tenant_id` = @tgt_tenant_id
      AND r_tgt.`code` = r_src.`code`
  );

-- 2) Copy role_permission (map permission by permission_code within each tenant’s normalized_tenant_id)
INSERT IGNORE INTO `role_permission` (`tenant_id`, `role_id`, `permission_id`)
SELECT
  @tgt_tenant_id,
  r_tgt.id,
  p_tgt.id
FROM `role_permission` rp_src
JOIN `role` r_src
  ON r_src.id = rp_src.`role_id`
 AND r_src.`tenant_id` = @src_tenant_id
 AND r_src.`code` IN ('ROLE_USER_MANAGER','ROLE_OPERATOR','ROLE_VIEWER','ROLE_DEVELOPER','ROLE_GUEST','ROLE_USER')
 AND rp_src.`normalized_tenant_id` = IFNULL(r_src.`tenant_id`, 0)
JOIN `permission` p_src
  ON p_src.id = rp_src.`permission_id`
 AND p_src.`normalized_tenant_id` = rp_src.`normalized_tenant_id`
JOIN `role` r_tgt
  ON r_tgt.`tenant_id` = @tgt_tenant_id
 AND r_tgt.`code` = r_src.`code`
JOIN `permission` p_tgt
  ON p_tgt.`permission_code` = p_src.`permission_code`
 AND p_tgt.`normalized_tenant_id` = IFNULL(@tgt_tenant_id, 0)
 AND p_tgt.`enabled` = 1;

-- 3) Copy role_data_scope
INSERT IGNORE INTO `role_data_scope`
(`tenant_id`, `role_id`, `module`, `scope_type`, `access_type`, `created_by`, `created_at`, `updated_at`)
SELECT
  @tgt_tenant_id,
  r_tgt.id AS tgt_role_id,
  rds.`module`,
  rds.`scope_type`,
  rds.`access_type`,
  rds.`created_by`,
  rds.`created_at`,
  rds.`updated_at`
FROM `role_data_scope` rds
JOIN `role` r_src
  ON r_src.id = rds.`role_id`
 AND r_src.`tenant_id` = @src_tenant_id
 AND r_src.`code` IN ('ROLE_USER_MANAGER','ROLE_OPERATOR','ROLE_VIEWER','ROLE_DEVELOPER','ROLE_GUEST','ROLE_USER')
JOIN `role` r_tgt
  ON r_tgt.`tenant_id` = @tgt_tenant_id
 AND r_tgt.`code` = r_src.`code`;

-- Verification: role coverage for tenant_id=3
SELECT
  expected.code AS expected_role_code,
  r.id AS role_id,
  r.enabled,
  COUNT(DISTINCT rp.permission_id) AS rp_cnt,
  COUNT(DISTINCT rds.module)    AS rds_module_cnt
FROM (
  SELECT 'ROLE_TENANT_ADMIN' AS code UNION ALL
  SELECT 'ROLE_USER_MANAGER' UNION ALL
  SELECT 'ROLE_OPERATOR' UNION ALL
  SELECT 'ROLE_VIEWER' UNION ALL
  SELECT 'ROLE_DEVELOPER' UNION ALL
  SELECT 'ROLE_GUEST' UNION ALL
  SELECT 'ROLE_USER'
) expected
LEFT JOIN `role` r
  ON r.`tenant_id` = @tgt_tenant_id
 AND r.`code` = expected.code
LEFT JOIN `role_permission` rp
  ON rp.`role_id` = r.id
  -- don't filter tenant_id here; role_id is a PK and uniquely identifies bindings
LEFT JOIN `role_data_scope` rds
  ON rds.`role_id` = r.id
  -- don't filter tenant_id here; role_id is a PK and uniquely identifies bindings
GROUP BY expected.code, r.id, r.enabled
ORDER BY expected.code;

COMMIT;

