-- CARD-06: user_authentication_method -> user_auth_credential/user_auth_scope_policy
-- 说明：
-- 1) 本脚本仅做回填与对账，不删除旧表，不关闭 fallback。
-- 2) scope_key 规则必须与 UserAuthenticationBridgeWriter.buildScopeKey(...) 保持一致：
--    GLOBAL / PLATFORM / TENANT:{id}
-- 3) tenant_id IS NULL 的平台/全局区分口径：
--    - 若用户存在 ACTIVE tenant_user 归属 -> GLOBAL
--    - 否则 -> PLATFORM
-- 4) 部署 CARD-13A（读侧仅单 scope_key）前/后：必须执行 Liquibase
--    `135-duplicate-global-auth-scope-policy-card-13a`，或运行对账
--    `scripts/verify-card-13a-global-auth-scope-policy-rollout.sh` 证明 gap=0。

-- 凭证层回填（user + provider + type 唯一）
INSERT INTO user_auth_credential (
  user_id,
  authentication_provider,
  authentication_type,
  authentication_configuration,
  last_verified_at,
  last_verified_ip,
  expires_at,
  created_at,
  updated_at
)
SELECT
  uam.user_id,
  uam.authentication_provider,
  uam.authentication_type,
  ANY_VALUE(uam.authentication_configuration) AS authentication_configuration,
  MAX(
    CASE
      WHEN CAST(uam.last_verified_at AS CHAR) = '0000-00-00 00:00:00' THEN NULL
      ELSE uam.last_verified_at
    END
  ) AS last_verified_at,
  ANY_VALUE(uam.last_verified_ip) AS last_verified_ip,
  NULLIF(
    MAX(
      NULLIF(CAST(uam.expires_at AS CHAR), '0000-00-00 00:00:00')
    ),
    '0000-00-00 00:00:00'
  ) AS expires_at,
  MIN(
    COALESCE(
      CASE
        WHEN CAST(uam.created_at AS CHAR) = '0000-00-00 00:00:00' THEN NULL
        ELSE uam.created_at
      END,
      NOW()
    )
  ) AS created_at,
  MAX(
    COALESCE(
      CASE
        WHEN CAST(uam.updated_at AS CHAR) = '0000-00-00 00:00:00' THEN NULL
        ELSE uam.updated_at
      END,
      CASE
        WHEN CAST(uam.created_at AS CHAR) = '0000-00-00 00:00:00' THEN NULL
        ELSE uam.created_at
      END,
      NOW()
    )
  ) AS updated_at
FROM user_authentication_method uam
GROUP BY
  uam.user_id,
  uam.authentication_provider,
  uam.authentication_type
ON DUPLICATE KEY UPDATE
  authentication_configuration = VALUES(authentication_configuration),
  last_verified_at = VALUES(last_verified_at),
  last_verified_ip = VALUES(last_verified_ip),
  expires_at = VALUES(expires_at),
  updated_at = VALUES(updated_at);

-- 作用域策略层回填（credential + scope_key 唯一）
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
  c.id AS credential_id,
  CASE
    WHEN uam.tenant_id IS NOT NULL THEN 'TENANT'
    WHEN EXISTS (
      SELECT 1
      FROM tenant_user tu
      WHERE tu.user_id = uam.user_id
        AND tu.status = 'ACTIVE'
    ) THEN 'GLOBAL'
    ELSE 'PLATFORM'
  END AS scope_type,
  CASE
    WHEN uam.tenant_id IS NOT NULL THEN uam.tenant_id
    ELSE NULL
  END AS scope_id,
  CASE
    WHEN uam.tenant_id IS NOT NULL THEN CONCAT('TENANT:', uam.tenant_id)
    WHEN EXISTS (
      SELECT 1
      FROM tenant_user tu
      WHERE tu.user_id = uam.user_id
        AND tu.status = 'ACTIVE'
    ) THEN 'GLOBAL'
    ELSE 'PLATFORM'
  END AS scope_key,
  COALESCE(uam.is_primary_method, 0) AS is_primary_method,
  COALESCE(uam.is_method_enabled, 1) AS is_method_enabled,
  COALESCE(uam.authentication_priority, 0) AS authentication_priority,
  COALESCE(
    CASE
      WHEN CAST(uam.created_at AS CHAR) = '0000-00-00 00:00:00' THEN NULL
      ELSE uam.created_at
    END,
    NOW()
  ) AS created_at,
  COALESCE(
    CASE
      WHEN CAST(uam.updated_at AS CHAR) = '0000-00-00 00:00:00' THEN NULL
      ELSE uam.updated_at
    END,
    CASE
      WHEN CAST(uam.created_at AS CHAR) = '0000-00-00 00:00:00' THEN NULL
      ELSE uam.created_at
    END,
    NOW()
  ) AS updated_at
FROM user_authentication_method uam
JOIN user_auth_credential c
  ON c.user_id = uam.user_id
 AND c.authentication_provider = uam.authentication_provider
 AND c.authentication_type = uam.authentication_type
ON DUPLICATE KEY UPDATE
  is_primary_method = VALUES(is_primary_method),
  is_method_enabled = VALUES(is_method_enabled),
  authentication_priority = VALUES(authentication_priority),
  updated_at = VALUES(updated_at);
