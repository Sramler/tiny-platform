-- CARD-13A / migration 135:
-- Backfill (CARD-06) projected tenant-neutral rows as scope_key=GLOBAL when user had ACTIVE tenant_user.
-- Runtime read (CARD-13A) uses only the active scope_key (TENANT:{id} / PLATFORM / GLOBAL), without merging GLOBAL.
-- This script duplicates those GLOBAL policies so tenant login and platform login still resolve credentials.
--
-- 1) For each GLOBAL policy, insert TENANT:{tenant_id} for every ACTIVE tenant_user row for that user (idempotent).
-- 2) For each GLOBAL policy without an existing PLATFORM row for the same credential, insert PLATFORM.

INSERT INTO user_auth_scope_policy (
  credential_id,
  scope_type,
  scope_id,
  scope_key,
  is_primary_method,
  is_method_enabled,
  authentication_priority,
  created_at,
  updated_at
)
SELECT
  g.credential_id,
  'TENANT' AS scope_type,
  tu.tenant_id AS scope_id,
  CONCAT('TENANT:', tu.tenant_id) AS scope_key,
  g.is_primary_method,
  g.is_method_enabled,
  g.authentication_priority,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM user_auth_scope_policy g
INNER JOIN user_auth_credential c ON c.id = g.credential_id
INNER JOIN tenant_user tu ON tu.user_id = c.user_id AND tu.status = 'ACTIVE'
WHERE g.scope_key = 'GLOBAL'
  AND NOT EXISTS (
    SELECT 1
    FROM user_auth_scope_policy p2
    WHERE p2.credential_id = g.credential_id
      AND p2.scope_key = CONCAT('TENANT:', tu.tenant_id)
  );

INSERT INTO user_auth_scope_policy (
  credential_id,
  scope_type,
  scope_id,
  scope_key,
  is_primary_method,
  is_method_enabled,
  authentication_priority,
  created_at,
  updated_at
)
SELECT
  g.credential_id,
  'PLATFORM' AS scope_type,
  NULL AS scope_id,
  'PLATFORM' AS scope_key,
  g.is_primary_method,
  g.is_method_enabled,
  g.authentication_priority,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
FROM user_auth_scope_policy g
WHERE g.scope_key = 'GLOBAL'
  AND NOT EXISTS (
    SELECT 1
    FROM user_auth_scope_policy p2
    WHERE p2.credential_id = g.credential_id
      AND p2.scope_key = 'PLATFORM'
  );
