#!/usr/bin/env bash
# 审计 real-link E2E 在本地/共享库中留下的“派生资产”：
# - 通过 /sys/tenants 动态创建的派生租户（name = E2E租户(...)）
# - 为这些派生租户生成的初始管理员（username = e2e_init_*）
# - 超出当前 keep-set 的 e2e_* 长期账号/跨租户 membership（仅提示，不自动删除）
#
# 默认：audit only，exit 0
# 可选：
#   --apply                  受控清理“生成型租户 + e2e_init_* + keep 用户挂在生成型租户上的 membership”
#   --fail-on-stale          若仍存在 stale 派生资产则 exit 1，便于本地门禁/CI 校验
#   --target-generated-tenant-codes code1,code2
#                            将这些 code 对应的生成型租户视为本轮治理目标；即使在 keep-set 中，也会参与清理
#
# 连接参数：
#   VERIFY_DB_HOST VERIFY_DB_PORT VERIFY_DB_USER VERIFY_DB_NAME VERIFY_MYSQL_BIN
#   VERIFY_DB_PASSWORD / DB_PASSWORD / E2E_DB_PASSWORD / E2E_MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD

set -euo pipefail

read_env() {
  local name
  local value
  for name in "$@"; do
    value="${!name-}"
    if [[ -n "${value//[[:space:]]/}" ]]; then
      printf '%s' "$value"
      return 0
    fi
  done
  return 1
}

strip_wrapping_quotes() {
  local value="$1"
  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "$value"
}

read_env_or_local_file() {
  local value
  local name
  local line
  local raw
  if value="$(read_env "$@" 2>/dev/null)"; then
    printf '%s' "$value"
    return 0
  fi
  if [[ ! -f "${E2E_LOCAL_ENV_FILE:-}" ]]; then
    return 1
  fi
  for name in "$@"; do
    line="$(grep -E "^${name}=" "$E2E_LOCAL_ENV_FILE" | tail -n 1 || true)"
    if [[ -z "$line" ]]; then
      continue
    fi
    raw="${line#*=}"
    raw="${raw%$'\r'}"
    raw="$(strip_wrapping_quotes "$raw")"
    if [[ -n "${raw//[[:space:]]/}" ]]; then
      printf '%s' "$raw"
      return 0
    fi
  done
  return 1
}

contains_csv() {
  local csv="$1"
  local target="$2"
  local item
  IFS=',' read -r -a items <<<"$csv"
  for item in "${items[@]}"; do
    if [[ "$item" == "$target" ]]; then
      return 0
    fi
  done
  return 1
}

append_csv_unique() {
  local value="$1"
  if [[ -z "${value//[[:space:]]/}" ]]; then
    return 0
  fi
  if [[ -z "${KEEP_VALUES:-}" ]]; then
    KEEP_VALUES="$value"
    return 0
  fi
  if ! contains_csv "$KEEP_VALUES" "$value"; then
    KEEP_VALUES="${KEEP_VALUES},${value}"
  fi
}

append_csv_values() {
  local csv="$1"
  local item
  local -a items=()
  IFS=',' read -r -a items <<<"$csv"
  for item in "${items[@]:-}"; do
    item="${item#"${item%%[![:space:]]*}"}"
    item="${item%"${item##*[![:space:]]}"}"
    if [[ -n "$item" ]]; then
      append_csv_unique "$item"
    fi
  done
}

trim_csv_spaces() {
  local csv="$1"
  local item
  local result=""
  local -a items=()
  IFS=',' read -r -a items <<<"$csv"
  for item in "${items[@]:-}"; do
    item="${item#"${item%%[![:space:]]*}"}"
    item="${item%"${item##*[![:space:]]}"}"
    if [[ -n "$item" ]]; then
      if [[ -z "$result" ]]; then
        result="$item"
      else
        result="${result},${item}"
      fi
    fi
  done
  printf '%s' "$result"
}

sql_list_from_csv() {
  local csv
  local item
  local result=""
  local -a items=()
  csv="$(trim_csv_spaces "$1")"
  if [[ -z "$csv" ]]; then
    printf "'__never__'"
    return 0
  fi
  IFS=',' read -r -a items <<<"$csv"
  for item in "${items[@]:-}"; do
    item="${item//\'/\'\'}"
    if [[ -z "$result" ]]; then
      result="'$item'"
    else
      result="${result}, '$item'"
    fi
  done
  printf '%s' "$result"
}

usage() {
  cat <<'EOF'
Usage:
  bash tiny-oauth-server/scripts/verify-real-e2e-derived-assets.sh
  bash tiny-oauth-server/scripts/verify-real-e2e-derived-assets.sh --apply
  bash tiny-oauth-server/scripts/verify-real-e2e-derived-assets.sh --fail-on-stale
  bash tiny-oauth-server/scripts/verify-real-e2e-derived-assets.sh --apply --target-generated-tenant-codes bench-1m-t
EOF
}

APPLY_CHANGES="false"
FAIL_ON_STALE="false"
TARGET_GENERATED_TENANT_CODES=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply )
      APPLY_CHANGES="true"
      shift
      ;;
    --fail-on-stale )
      FAIL_ON_STALE="true"
      shift
      ;;
    --target-generated-tenant-codes )
      if [[ $# -lt 2 ]]; then
        usage
        exit 2
      fi
      TARGET_GENERATED_TENANT_CODES="$(trim_csv_spaces "$2")"
      shift 2
      ;;
    * )
      usage
      exit 2
      ;;
  esac
done

MYSQL_BIN="${VERIFY_MYSQL_BIN:-mysql}"
VERIFY_DB_HOST="${VERIFY_DB_HOST:-${DB_HOST:-127.0.0.1}}"
VERIFY_DB_PORT="${VERIFY_DB_PORT:-${DB_PORT:-3306}}"
VERIFY_DB_USER="${VERIFY_DB_USER:-${DB_USER:-root}}"
VERIFY_DB_NAME="${VERIFY_DB_NAME:-${DB_NAME:-tiny_web}}"
VERIFY_DB_PASSWORD="$(read_env VERIFY_DB_PASSWORD DB_PASSWORD E2E_DB_PASSWORD E2E_MYSQL_PASSWORD MYSQL_ROOT_PASSWORD || true)"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."
E2E_LOCAL_ENV_FILE="${E2E_LOCAL_ENV_FILE:-${PWD}/src/main/webapp/.env.e2e.local}"

MYSQL_CMD=("$MYSQL_BIN" -h "$VERIFY_DB_HOST" -P "$VERIFY_DB_PORT" -u "$VERIFY_DB_USER" "$VERIFY_DB_NAME" -N -s)
if [[ -n "$VERIFY_DB_PASSWORD" ]]; then
  export MYSQL_PWD="$VERIFY_DB_PASSWORD"
fi

query() {
  "${MYSQL_CMD[@]}" -e "$1"
}

execute_sql() {
  printf '%s\n' "$1" | "${MYSQL_CMD[@]}"
}

derive_tenant_scope_code() {
  local primary="$1"
  local platform="$2"
  local p
  local plat
  p="$(printf '%s' "$primary" | tr '[:upper:]' '[:lower:]')"
  plat="$(printf '%s' "$platform" | tr '[:upper:]' '[:lower:]')"
  if [[ "$p" != "$plat" ]]; then
    printf '%s' "$primary"
    return 0
  fi
  local candidate="${p}-t"
  if (( ${#candidate} > 32 )); then
    candidate="${candidate:0:32}"
  fi
  printf '%s' "$candidate"
}

echo "=== real-link E2E 派生资产审计 ==="
echo "数据库: $VERIFY_DB_HOST:$VERIFY_DB_PORT/$VERIFY_DB_NAME"
echo ""

if ! query "SELECT 1" | grep -q 1; then
  echo "FAIL: 无法连接数据库或认证失败"
  exit 2
fi

PRIMARY_TENANT_CODE="$(read_env_or_local_file E2E_TENANT_CODE || true)"
SECONDARY_TENANT_CODE="$(read_env_or_local_file E2E_TENANT_CODE_B || true)"
READONLY_TENANT_CODE="$(read_env_or_local_file E2E_TENANT_CODE_READONLY || true)"
BIND_TENANT_CODE="$(read_env_or_local_file E2E_TENANT_CODE_BIND || true)"
PLATFORM_TENANT_CODE="$(read_env_or_local_file E2E_PLATFORM_TENANT_CODE || true)"
PLATFORM_TENANT_CODE="${PLATFORM_TENANT_CODE:-default}"

PRIMARY_USERNAME="$(read_env_or_local_file E2E_USERNAME || true)"
SECONDARY_USERNAME="$(read_env_or_local_file E2E_USERNAME_B || true)"
READONLY_USERNAME="$(read_env_or_local_file E2E_USERNAME_READONLY || true)"
BIND_USERNAME="$(read_env_or_local_file E2E_USERNAME_BIND || true)"
PLATFORM_USERNAME="$(read_env_or_local_file E2E_PLATFORM_USERNAME || true)"
EXTRA_KEEP_USERS_RAW="$(read_env_or_local_file E2E_KEEP_USERS_EXTRA || true)"
DEFAULT_COMPAT_KEEP_USERS="e2e_admin_b,e2e_platform_admin,e2e_scheduling_readonly"

KEEP_VALUES=""
append_csv_unique "default"
append_csv_unique "$PRIMARY_TENANT_CODE"
append_csv_unique "$SECONDARY_TENANT_CODE"
append_csv_unique "$READONLY_TENANT_CODE"
append_csv_unique "$BIND_TENANT_CODE"
append_csv_unique "$PLATFORM_TENANT_CODE"
if [[ -n "${PRIMARY_TENANT_CODE:-}" ]]; then
  append_csv_unique "$(derive_tenant_scope_code "$PRIMARY_TENANT_CODE" "$PLATFORM_TENANT_CODE")"
fi
KEEP_TENANTS="$(trim_csv_spaces "$KEEP_VALUES")"

KEEP_VALUES=""
append_csv_unique "$PRIMARY_USERNAME"
append_csv_unique "$SECONDARY_USERNAME"
append_csv_unique "$READONLY_USERNAME"
append_csv_unique "$BIND_USERNAME"
append_csv_unique "$PLATFORM_USERNAME"
append_csv_values "$DEFAULT_COMPAT_KEEP_USERS"
append_csv_values "$EXTRA_KEEP_USERS_RAW"
KEEP_USERS="$(trim_csv_spaces "$KEEP_VALUES")"

keep_tenant_sql="$(sql_list_from_csv "$KEEP_TENANTS")"
keep_user_sql="$(sql_list_from_csv "$KEEP_USERS")"
target_generated_tenant_sql="$(sql_list_from_csv "$TARGET_GENERATED_TENANT_CODES")"
cleanup_generated_tenant_condition="
  name LIKE 'E2E租户(%'
  AND (
    code NOT IN (${keep_tenant_sql})
    OR code IN (${target_generated_tenant_sql})
  )
"

echo "keep tenants: ${KEEP_TENANTS:-<none>}"
echo "keep users:   ${KEEP_USERS:-<none>}"
if [[ -n "${EXTRA_KEEP_USERS_RAW:-}" ]]; then
  echo "extra keep users from env: ${EXTRA_KEEP_USERS_RAW}"
fi
if [[ -n "${TARGET_GENERATED_TENANT_CODES:-}" ]]; then
  echo "target generated tenants: ${TARGET_GENERATED_TENANT_CODES}"
fi
echo ""

generated_tenants_total="$(query "SELECT COUNT(*) FROM tenant WHERE name LIKE 'E2E租户(%';")"
stale_generated_tenant_count="$(query "
  SELECT COUNT(*)
  FROM tenant
  WHERE ${cleanup_generated_tenant_condition};
")"
stale_init_user_count="$(query "
  SELECT COUNT(*)
  FROM user
  WHERE username LIKE 'e2e_init_%'
    AND username NOT IN (${keep_user_sql});
")"
extra_e2e_user_count="$(query "
  SELECT COUNT(*)
  FROM user
  WHERE username LIKE 'e2e_%'
    AND username NOT LIKE 'e2e_init_%'
    AND username NOT IN (${keep_user_sql});
")"
keep_user_stale_membership_count="$(query "
  SELECT COUNT(*)
  FROM tenant_user tu
  JOIN user u ON u.id = tu.user_id
  JOIN tenant t ON t.id = tu.tenant_id
  WHERE u.username IN (${keep_user_sql})
    AND ${cleanup_generated_tenant_condition//code/t.code};
")"

cleanup_generated_tenant_codes_csv="$(query "
  SELECT COALESCE(GROUP_CONCAT(code ORDER BY code SEPARATOR ','), '')
  FROM tenant
  WHERE ${cleanup_generated_tenant_condition};
")"

apply_cleanup() {
  local table
  local table_names
  local generated_cleanup_count
  local init_cleanup_count
  local keep_membership_cleanup_count
  local delete_sql

  generated_cleanup_count="$(query "
    SELECT COUNT(*)
    FROM tenant
    WHERE ${cleanup_generated_tenant_condition};
  ")"
  init_cleanup_count="$(query "
    SELECT COUNT(*)
    FROM user
    WHERE username LIKE 'e2e_init_%'
      AND username NOT IN (${keep_user_sql});
  ")"
  keep_membership_cleanup_count="$(query "
    SELECT COUNT(*)
    FROM tenant_user tu
    JOIN user u ON u.id = tu.user_id
    JOIN tenant t ON t.id = tu.tenant_id
    WHERE u.username IN (${keep_user_sql})
      AND ${cleanup_generated_tenant_condition//code/t.code};
  ")"

  if [[ "${generated_cleanup_count:-0}" -eq 0 \
     && "${init_cleanup_count:-0}" -eq 0 \
     && "${keep_membership_cleanup_count:-0}" -eq 0 ]]; then
    echo "apply: 无需清理的生成型租户派生资产"
    echo ""
    return 0
  fi

  echo "apply: 开始清理生成型租户派生资产"
  echo "  目标生成型租户: ${generated_cleanup_count:-0}"
  echo "  目标 e2e_init_*: ${init_cleanup_count:-0}"
  echo "  keep 用户 stale membership: ${keep_membership_cleanup_count:-0}"

  table_names="$(query "
    SELECT TABLE_NAME
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND COLUMN_NAME = 'tenant_id'
      AND TABLE_NAME <> 'tenant'
    GROUP BY TABLE_NAME
    ORDER BY TABLE_NAME;
  ")"

  delete_sql="SET FOREIGN_KEY_CHECKS=0;
"
  while IFS= read -r table; do
    if [[ -z "${table:-}" ]]; then
      continue
    fi
    delete_sql="${delete_sql}
      DELETE FROM \`${table}\`
      WHERE tenant_id IN (
        SELECT id
        FROM (
          SELECT id
          FROM tenant
          WHERE ${cleanup_generated_tenant_condition}
        ) cleanup_generated_tenants
      );
"
  done <<<"$table_names"

  delete_sql="${delete_sql}
    DELETE FROM user
    WHERE username LIKE 'e2e_init_%'
      AND username NOT IN (${keep_user_sql});
"

  delete_sql="${delete_sql}
    DELETE FROM tenant
    WHERE id IN (
      SELECT id
      FROM (
        SELECT id
        FROM tenant
        WHERE ${cleanup_generated_tenant_condition}
        ) cleanup_generated_tenants
    );
SET FOREIGN_KEY_CHECKS=1;
"

  execute_sql "$delete_sql"

  echo "apply: 清理完成"
  echo ""
}

echo "统计摘要:"
echo "  生成型 E2E 租户总数: ${generated_tenants_total:-0}"
echo "  stale 生成型租户:   ${stale_generated_tenant_count:-0}"
echo "  stale e2e_init_*:   ${stale_init_user_count:-0}"
echo "  额外 e2e_* 用户:    ${extra_e2e_user_count:-0}"
echo "  keep 用户挂在 stale 租户的 membership: ${keep_user_stale_membership_count:-0}"
echo ""

if [[ "$APPLY_CHANGES" == "true" ]]; then
  apply_cleanup

  stale_generated_tenant_count="$(query "
    SELECT COUNT(*)
    FROM tenant
    WHERE ${cleanup_generated_tenant_condition};
  ")"
  stale_init_user_count="$(query "
    SELECT COUNT(*)
    FROM user
    WHERE username LIKE 'e2e_init_%'
      AND username NOT IN (${keep_user_sql});
  ")"
  keep_user_stale_membership_count="$(query "
    SELECT COUNT(*)
    FROM tenant_user tu
    JOIN user u ON u.id = tu.user_id
    JOIN tenant t ON t.id = tu.tenant_id
    WHERE u.username IN (${keep_user_sql})
      AND ${cleanup_generated_tenant_condition//code/t.code};
  ")"
fi

if [[ "${stale_generated_tenant_count:-0}" -gt 0 ]]; then
  echo "stale 生成型租户（超出 keep-set 或被本轮 target 标记，但仍未清理完成）:"
  query "
    SELECT CONCAT('  - tenant_id=', id, ' code=', code, ' name=', name)
    FROM tenant
    WHERE ${cleanup_generated_tenant_condition}
    ORDER BY code;
  "
  echo ""
fi

if [[ "${stale_init_user_count:-0}" -gt 0 ]]; then
  echo "stale e2e_init_* 用户（由 /sys/tenants 派生，通常不应长期保留）:"
  query "
    SELECT CONCAT(
      '  - username=', u.username,
      ' tenant_codes=', IFNULL(GROUP_CONCAT(DISTINCT t.code ORDER BY t.code SEPARATOR ','), '<none>')
    )
    FROM user u
    LEFT JOIN tenant_user tu ON tu.user_id = u.id
    LEFT JOIN tenant t ON t.id = tu.tenant_id
    WHERE u.username LIKE 'e2e_init_%'
      AND u.username NOT IN (${keep_user_sql})
    GROUP BY u.id, u.username
    ORDER BY u.username;
  "
  echo ""
fi

if [[ "${extra_e2e_user_count:-0}" -gt 0 ]]; then
  echo "额外 e2e_* 用户（不在 keep-set / documented allowlist 中，建议人工确认是否仍被当前配置使用）:"
  query "
    SELECT CONCAT(
      '  - username=', u.username,
      ' tenant_codes=', IFNULL(GROUP_CONCAT(DISTINCT t.code ORDER BY t.code SEPARATOR ','), '<none>'),
      ' auth_credentials=', COUNT(DISTINCT cred.id)
    )
    FROM user u
    LEFT JOIN tenant_user tu ON tu.user_id = u.id
    LEFT JOIN tenant t ON t.id = tu.tenant_id
    LEFT JOIN user_auth_credential cred ON cred.user_id = u.id
    LEFT JOIN user_auth_scope_policy policy ON policy.credential_id = cred.id
    WHERE u.username LIKE 'e2e_%'
      AND u.username NOT LIKE 'e2e_init_%'
      AND u.username NOT IN (${keep_user_sql})
    GROUP BY u.id, u.username
    ORDER BY u.username;
  "
  echo ""
fi

if [[ "${keep_user_stale_membership_count:-0}" -gt 0 ]]; then
  echo "当前 keep 用户仍挂在 stale 生成型租户上的 membership（建议清理）:"
  query "
    SELECT CONCAT(
      '  - username=', u.username,
      ' tenant=', t.code,
      ' is_default=', tu.is_default,
      ' status=', tu.status
    )
    FROM tenant_user tu
    JOIN user u ON u.id = tu.user_id
    JOIN tenant t ON t.id = tu.tenant_id
    WHERE u.username IN (${keep_user_sql})
      AND t.name LIKE 'E2E租户(%'
      AND t.code NOT IN (${keep_tenant_sql})
    ORDER BY u.username, t.code;
  "
  echo ""
fi

if [[ "${stale_generated_tenant_count:-0}" -eq 0 \
   && "${stale_init_user_count:-0}" -eq 0 \
   && "${extra_e2e_user_count:-0}" -eq 0 \
   && "${keep_user_stale_membership_count:-0}" -eq 0 ]]; then
  echo "✅ 当前 real-link 派生资产与 keep-set 一致，无额外残留"
  exit 0
fi

echo "结论:"
echo "  - 固定 real-link 身份（keep users）允许长期保留，不纳入自动删除。"
echo "  - 当前仓库的长期自动化身份兼容 allowlist 默认包含：e2e_admin_b、e2e_platform_admin、e2e_scheduling_readonly；必要时可用 E2E_KEEP_USERS_EXTRA 扩展。"
echo "  - 生成型租户 E2E租户(...)、e2e_init_*、以及 keep 用户挂在生成型租户上的 stale membership，属于自动治理范围。"
echo "  - 额外 e2e_* 用户（如不在 keep-set）默认仅审计提示，不自动删除。"

if [[ "$FAIL_ON_STALE" == "true" && (
  "${stale_generated_tenant_count:-0}" -gt 0 \
  || "${stale_init_user_count:-0}" -gt 0 \
  || "${keep_user_stale_membership_count:-0}" -gt 0
) ]]; then
  exit 1
fi

exit 0
