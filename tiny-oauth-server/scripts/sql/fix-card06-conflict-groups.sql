-- CARD-06 conflict cleanup for known legacy credential groups.
--
-- Scope:
-- 1) user_id=1301, LOCAL+PASSWORD
--    - GLOBAL / TENANT:3 share the same hash; TENANT:11 diverged.
--    - Canonical source: TENANT:3 row.
-- 2) user_id=1301, LOCAL+TOTP
--    - Configuration is identical across scopes; verification metadata drifted.
--    - Canonical source: TENANT:3 row.
-- 3) user_id=1481, LOCAL+PASSWORD
--    - PLATFORM row exists, but legacy TENANT:1 row has no tenant_user membership.
--    - Canonical source: PLATFORM row; TENANT:1 row is removed.
-- 4) Companion cleanup:
--    - user_id=1481, LOCAL+TOTP TENANT:1 is the same orphan cluster and is removed too.
--
-- IMPORTANT:
-- - Review precheck output before COMMIT.
-- - This script is intentionally data-specific and should only be used on the
--   environment where these exact conflict groups were audited.

-- ---------------------------------------------------------------------------
-- Precheck
-- ---------------------------------------------------------------------------
SELECT
  uam.id,
  uam.user_id,
  uam.tenant_id,
  uam.authentication_provider,
  uam.authentication_type,
  SHA2(CAST(uam.authentication_configuration AS CHAR(8192)), 256) AS config_sha256,
  CASE
    WHEN CAST(uam.last_verified_at AS CHAR) = '0000-00-00 00:00:00' THEN NULL
    ELSE uam.last_verified_at
  END AS last_verified_at_norm,
  uam.last_verified_ip,
  CASE
    WHEN CAST(uam.expires_at AS CHAR) = '0000-00-00 00:00:00' THEN NULL
    ELSE uam.expires_at
  END AS expires_at_norm,
  CASE
    WHEN uam.tenant_id IS NOT NULL THEN CONCAT('TENANT:', uam.tenant_id)
    WHEN EXISTS (
      SELECT 1
      FROM tenant_user tu
      WHERE tu.user_id = uam.user_id
        AND tu.status = 'ACTIVE'
    ) THEN 'GLOBAL'
    ELSE 'PLATFORM'
  END AS projected_scope_key
FROM user_authentication_method uam
WHERE uam.id IN (3261, 3180, 3275, 3262, 3181, 3276, 3257, 2674, 2790)
ORDER BY uam.user_id, uam.authentication_type, uam.tenant_id IS NULL DESC, uam.tenant_id;

START TRANSACTION;

-- ---------------------------------------------------------------------------
-- Canonical values
-- ---------------------------------------------------------------------------
SET @cfg_1301_password = (
  SELECT authentication_configuration
  FROM user_authentication_method
  WHERE id = 3180
);
SET @last_verified_at_1301_password = (
  SELECT last_verified_at
  FROM user_authentication_method
  WHERE id = 3180
);
SET @last_verified_ip_1301_password = (
  SELECT last_verified_ip
  FROM user_authentication_method
  WHERE id = 3180
);
SET @expires_at_1301_password = (
  SELECT expires_at
  FROM user_authentication_method
  WHERE id = 3180
);

SET @cfg_1301_totp = (
  SELECT authentication_configuration
  FROM user_authentication_method
  WHERE id = 3181
);
SET @last_verified_at_1301_totp = (
  SELECT last_verified_at
  FROM user_authentication_method
  WHERE id = 3181
);
SET @last_verified_ip_1301_totp = (
  SELECT last_verified_ip
  FROM user_authentication_method
  WHERE id = 3181
);
SET @expires_at_1301_totp = (
  SELECT expires_at
  FROM user_authentication_method
  WHERE id = 3181
);

-- ---------------------------------------------------------------------------
-- 1301 LOCAL+PASSWORD
-- Align GLOBAL / TENANT:3 / TENANT:11 to the canonical credential from TENANT:3.
-- ---------------------------------------------------------------------------
UPDATE user_authentication_method
SET authentication_configuration = @cfg_1301_password,
    last_verified_at = @last_verified_at_1301_password,
    last_verified_ip = @last_verified_ip_1301_password,
    expires_at = @expires_at_1301_password
WHERE id IN (3261, 3180, 3275)
  AND user_id = 1301
  AND authentication_provider = 'LOCAL'
  AND authentication_type = 'PASSWORD';

-- ---------------------------------------------------------------------------
-- 1301 LOCAL+TOTP
-- Configuration is already shared; normalize verification metadata too.
-- ---------------------------------------------------------------------------
UPDATE user_authentication_method
SET authentication_configuration = @cfg_1301_totp,
    last_verified_at = @last_verified_at_1301_totp,
    last_verified_ip = @last_verified_ip_1301_totp,
    expires_at = @expires_at_1301_totp
WHERE id IN (3262, 3181, 3276)
  AND user_id = 1301
  AND authentication_provider = 'LOCAL'
  AND authentication_type = 'TOTP';

-- ---------------------------------------------------------------------------
-- 1481 platform_admin orphan tenant rows
-- PLATFORM row is canonical; remove legacy TENANT:1 LOCAL rows.
-- ---------------------------------------------------------------------------
DELETE FROM user_authentication_method
WHERE id IN (2674, 2790)
  AND user_id = 1481
  AND tenant_id = 1
  AND authentication_provider = 'LOCAL'
  AND authentication_type IN ('PASSWORD', 'TOTP');

-- ---------------------------------------------------------------------------
-- Post-check
-- ---------------------------------------------------------------------------
SELECT
  uam.user_id,
  uam.authentication_provider,
  uam.authentication_type,
  COUNT(*) AS row_count,
  COUNT(DISTINCT SHA2(CAST(uam.authentication_configuration AS CHAR(8192)), 256)) AS config_variants,
  COUNT(DISTINCT COALESCE(
    CASE
      WHEN CAST(uam.last_verified_at AS CHAR) = '0000-00-00 00:00:00' THEN NULL
      ELSE CAST(uam.last_verified_at AS CHAR)
    END,
    '<NULL>'
  )) AS last_verified_at_variants,
  COUNT(DISTINCT COALESCE(uam.last_verified_ip, '<NULL>')) AS last_verified_ip_variants
FROM user_authentication_method uam
WHERE (uam.user_id = 1301 AND uam.authentication_provider = 'LOCAL' AND uam.authentication_type IN ('PASSWORD', 'TOTP'))
   OR (uam.user_id = 1481 AND uam.authentication_provider = 'LOCAL' AND uam.authentication_type IN ('PASSWORD', 'TOTP'))
GROUP BY uam.user_id, uam.authentication_provider, uam.authentication_type
ORDER BY uam.user_id, uam.authentication_type;

COMMIT;
