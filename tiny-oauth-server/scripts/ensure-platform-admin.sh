#!/usr/bin/env bash
set -euo pipefail

# Ensure a dedicated platform admin account bound to platform tenant.
# Idempotent by design: can run repeatedly without duplicating records.

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-tiny_web}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"

PLATFORM_TENANT_CODE="${PLATFORM_TENANT_CODE:-default}"
PLATFORM_ADMIN_USERNAME="${PLATFORM_ADMIN_USERNAME:-platform_admin}"
PLATFORM_ADMIN_NICKNAME="${PLATFORM_ADMIN_NICKNAME:-平台管理员}"
PLATFORM_ADMIN_ROLE_CODE="${PLATFORM_ADMIN_ROLE_CODE:-ROLE_PLATFORM_ADMIN}"
# DelegatingPasswordEncoder string: default {noop}admin (plaintext "admin"). Dev convenience only; override with PLATFORM_ADMIN_PASSWORD_BCRYPT for bcrypt or other {prefix} forms.
PLATFORM_ADMIN_PASSWORD_BCRYPT="${PLATFORM_ADMIN_PASSWORD_BCRYPT:-}"
if [[ -z "${PLATFORM_ADMIN_PASSWORD_BCRYPT}" ]]; then
  PLATFORM_ADMIN_PASSWORD_BCRYPT='{noop}admin'
fi
# Metadata only; set to bcrypt when PLATFORM_ADMIN_PASSWORD_BCRYPT is a {bcrypt}... string.
PLATFORM_ADMIN_HASH_ALGORITHM="${PLATFORM_ADMIN_HASH_ALGORITHM:-noop}"

if [[ -z "${DB_PASSWORD}" ]]; then
  echo "Missing DB_PASSWORD env var." >&2
  exit 1
fi

mysql_cmd=(
  env MYSQL_PWD="${DB_PASSWORD}" mysql
  -h"${DB_HOST}"
  -P"${DB_PORT}"
  -u"${DB_USER}"
  -D "${DB_NAME}"
)

"${mysql_cmd[@]}" <<SQL
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @platform_tenant_code := '${PLATFORM_TENANT_CODE}';
SET @platform_admin_username := '${PLATFORM_ADMIN_USERNAME}';
SET @platform_admin_nickname := '${PLATFORM_ADMIN_NICKNAME}';
SET @platform_admin_password_hash := '${PLATFORM_ADMIN_PASSWORD_BCRYPT}';
SET @platform_admin_hash_algorithm := '${PLATFORM_ADMIN_HASH_ALGORITHM}';
SET @platform_admin_role_code := '${PLATFORM_ADMIN_ROLE_CODE}';

SELECT id INTO @platform_tenant_id
FROM tenant
WHERE code = @platform_tenant_code COLLATE utf8mb4_unicode_ci
LIMIT 1;

-- Fail fast if platform tenant is absent.
SELECT IF(@platform_tenant_id IS NULL, CAST('MISSING_PLATFORM_TENANT' AS SIGNED), @platform_tenant_id) INTO @platform_tenant_check;

INSERT INTO user (
  username, nickname, enabled, account_non_expired, account_non_locked, credentials_non_expired, failed_login_count
)
SELECT
  @platform_admin_username, @platform_admin_nickname, 1, 1, 1, 1, 0
WHERE NOT EXISTS (
  SELECT 1 FROM user u WHERE u.username = @platform_admin_username COLLATE utf8mb4_unicode_ci
);

SELECT id INTO @platform_admin_user_id
FROM user
WHERE username = @platform_admin_username COLLATE utf8mb4_unicode_ci
LIMIT 1;

UPDATE user
SET
  nickname = @platform_admin_nickname,
  enabled = 1,
  account_non_expired = 1,
  account_non_locked = 1,
  credentials_non_expired = 1,
  failed_login_count = 0,
  last_failed_login_at = NULL
WHERE id = @platform_admin_user_id;

-- PLATFORM 作用域账号不绑定租户 membership（避免语义混淆为租户管理员）。
DELETE FROM tenant_user
WHERE user_id = @platform_admin_user_id;

-- 平台登录权限严格绑定平台模板 ROLE_PLATFORM_ADMIN（role.tenant_id IS NULL）。
-- 不再做 ROLE_ADMIN 兼容回退，避免历史别名继续扩散。
SELECT id INTO @platform_admin_role_id
FROM role
WHERE tenant_id IS NULL
  AND code = @platform_admin_role_code COLLATE utf8mb4_unicode_ci
LIMIT 1;

SET @platform_admin_role_code_resolved := (
  SELECT code FROM role WHERE id = @platform_admin_role_id LIMIT 1
);

DELETE FROM role_assignment
WHERE principal_type = 'USER'
  AND principal_id = @platform_admin_user_id
  AND scope_type = 'TENANT';

DELETE FROM role_assignment
WHERE principal_type = 'USER'
  AND principal_id = @platform_admin_user_id
  AND scope_type = 'PLATFORM';

INSERT INTO role_assignment (
  principal_type, principal_id, role_id, tenant_id, scope_type, scope_id, status, start_time, end_time, granted_by, granted_at, updated_at
)
SELECT
  'USER', @platform_admin_user_id, @platform_admin_role_id, NULL, 'PLATFORM', NULL,
  'ACTIVE', NOW(), NULL, NULL, NOW(), NOW()
WHERE @platform_admin_role_id IS NOT NULL;

SET @platform_admin_password_json := JSON_OBJECT(
  'password', @platform_admin_password_hash,
  'password_changed_at', DATE_FORMAT(NOW(), '%Y-%m-%dT%H:%i:%sZ'),
  'hash_algorithm', @platform_admin_hash_algorithm,
  'password_version', 1,
  'created_by', 'ensure-platform-admin.sh'
);

-- 平台账号密码挂在 tenant_id NULL（用户级全局），任意租户/PLATFORM 登录上下文均可通过回退命中；与 uk_user_auth_method_scope(auth_scope_key=0) 一致
INSERT INTO user_authentication_method (
  tenant_id, user_id, authentication_provider, authentication_type, authentication_configuration,
  is_primary_method, is_method_enabled, authentication_priority, created_at, updated_at
)
VALUES (
  NULL, @platform_admin_user_id, 'LOCAL', 'PASSWORD', @platform_admin_password_json,
  1, 1, 0, NOW(), NOW()
)
ON DUPLICATE KEY UPDATE
  authentication_configuration = VALUES(authentication_configuration),
  is_primary_method = 1,
  is_method_enabled = 1,
  authentication_priority = 0,
  updated_at = NOW();

SELECT
  @platform_tenant_id AS platform_tenant_id,
  @platform_admin_user_id AS user_id,
  @platform_admin_username AS username,
  @platform_admin_role_id AS role_admin_id,
  @platform_admin_role_code_resolved AS role_admin_code,
  'PLATFORM scope uses activeTenantId=NULL and role_assignment(scope_type=PLATFORM)' AS note;
SQL

bind_count=$(
  "${mysql_cmd[@]}" -N -e "
    SELECT COUNT(*)
    FROM role_assignment ra
    INNER JOIN role r ON r.id = ra.role_id
    WHERE ra.principal_type = 'USER'
      AND ra.principal_id = (SELECT id FROM user WHERE username = '${PLATFORM_ADMIN_USERNAME}' COLLATE utf8mb4_unicode_ci LIMIT 1)
      AND ra.scope_type = 'PLATFORM'
      AND ra.tenant_id IS NULL
      AND ra.scope_id IS NULL
      AND ra.status = 'ACTIVE'
      AND r.tenant_id IS NULL
      AND r.code = '${PLATFORM_ADMIN_ROLE_CODE}' COLLATE utf8mb4_unicode_ci;
  " 2>/dev/null || echo "0"
)
if [[ "${bind_count}" != "1" ]]; then
  echo "ERROR: ${PLATFORM_ADMIN_USERNAME} 必须有一条 PLATFORM 赋权且指向平台模板 ${PLATFORM_ADMIN_ROLE_CODE}（role.tenant_id IS NULL）。当前匹配行数=${bind_count}。" >&2
  echo "说明：本脚本不再兼容 ROLE_ADMIN。请先创建/初始化平台模板 ${PLATFORM_ADMIN_ROLE_CODE} 后重试。" >&2
  echo "处理：1) 使用 spring.profiles.active=dev 启动 oauth-server 一次（application-dev.yaml 已默认开启 tiny.platform.tenant.auto-initialize-platform-template-if-missing），或 2) 用平台身份 POST /sys/tenants/platform-template/initialize，然后重新执行本脚本。" >&2
  exit 1
fi

tenant_membership_count=$(
  "${mysql_cmd[@]}" -N -e "
    SELECT COUNT(*)
    FROM tenant_user
    WHERE user_id = (SELECT id FROM user WHERE username = '${PLATFORM_ADMIN_USERNAME}' COLLATE utf8mb4_unicode_ci LIMIT 1);
  " 2>/dev/null || echo "0"
)
if [[ "${tenant_membership_count}" != "0" ]]; then
  echo "ERROR: ${PLATFORM_ADMIN_USERNAME} 作为 PLATFORM 作用域账号不应存在 tenant_user 绑定。当前数量=${tenant_membership_count}。" >&2
  echo "处理：重新执行本脚本（会自动清理 tenant_user），或手动删除 tenant_user 绑定后再校验。" >&2
  exit 1
fi

echo "ensure-platform-admin completed."
