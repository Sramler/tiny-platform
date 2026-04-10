#!/usr/bin/env bash
# 清理「user_0001」～「user_9999」格式的 demo 用户及其关联数据（共享 E2E / 演示库用）
#
# 匹配规则: username REGEXP '^user_[0-9]{4}$'（不匹配内置账号 user / admin）
#
# 默认: dry-run（只统计，不改数据）
# 执行: --apply（需同时设置 VERIFY_DEMO_USERS_CLEANUP_CONFIRM=YES）
#
# 环境变量:
#   VERIFY_DB_HOST VERIFY_DB_PORT VERIFY_DB_USER VERIFY_DB_NAME VERIFY_MYSQL_BIN
#   VERIFY_DB_PASSWORD / E2E_DB_PASSWORD / E2E_MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD
#   VERIFY_DEMO_USERS_CLEANUP_CONFIRM=YES  （--apply 时必填）

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

usage() {
  cat <<'EOF'
Usage:
  bash scripts/cleanup-demo-users-user-xxxx.sh                         # dry-run
  VERIFY_DEMO_USERS_CLEANUP_CONFIRM=YES bash scripts/cleanup-demo-users-user-xxxx.sh --apply

Deletes users matching username pattern ^user_[0-9]{4}$ and related rows in:
  role_assignment, user_auth_credential/user_auth_scope_policy, user_avatar, user_unit,
  user_session, user_authentication_audit, authorization_audit_log, tenant_user, user
EOF
}

MODE="${1:-dry-run}"
if [[ "$MODE" != "dry-run" && "$MODE" != "--apply" ]]; then
  usage
  exit 2
fi

MYSQL_BIN="${VERIFY_MYSQL_BIN:-mysql}"
VERIFY_DB_HOST="${VERIFY_DB_HOST:-127.0.0.1}"
VERIFY_DB_PORT="${VERIFY_DB_PORT:-3306}"
VERIFY_DB_USER="${VERIFY_DB_USER:-root}"
VERIFY_DB_PASSWORD="$(read_env VERIFY_DB_PASSWORD E2E_DB_PASSWORD E2E_MYSQL_PASSWORD MYSQL_ROOT_PASSWORD || true)"
VERIFY_DB_NAME="${VERIFY_DB_NAME:-tiny_web}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

MYSQL_CMD=("$MYSQL_BIN" -h "$VERIFY_DB_HOST" -P "$VERIFY_DB_PORT" -u "$VERIFY_DB_USER" "$VERIFY_DB_NAME" -N -s)
if [[ -n "$VERIFY_DB_PASSWORD" ]]; then
  export MYSQL_PWD="$VERIFY_DB_PASSWORD"
fi

query() {
  "${MYSQL_CMD[@]}" -e "$1"
}

table_exists() {
  query "SELECT 1 FROM information_schema.tables WHERE table_schema='$VERIFY_DB_NAME' AND table_name='$1'" 2>/dev/null | grep -q 1
}

echo "=== 清理 demo 用户 user_NNNN 及关联数据 ==="
echo "数据库: $VERIFY_DB_HOST:$VERIFY_DB_PORT/$VERIFY_DB_NAME"
echo "模式: $MODE"
echo "匹配: username REGEXP '^user_[0-9]{4}\$'"
echo ""

if ! query "SELECT 1" | grep -q 1; then
  echo "❌ 无法连接数据库或认证失败"
  exit 2
fi

CANDIDATE_FILTER="u.username REGEXP '^user_[0-9]{4}\$'"

candidate_count="$(query "SELECT COUNT(*) FROM user u WHERE $CANDIDATE_FILTER")"
echo "候选用户数: $candidate_count"

if [[ "$candidate_count" -eq 0 ]]; then
  echo "✅ 无需清理"
  exit 0
fi

echo "示例用户（最多 20 条）:"
query "SELECT u.id, u.username, u.enabled FROM user u WHERE $CANDIDATE_FILTER ORDER BY u.username LIMIT 20" \
  | awk '{printf "  - id=%s username=%s enabled=%s\n", $1, $2, $3}'
echo ""

ra_count="$(query "SELECT COUNT(*) FROM role_assignment ra WHERE ra.principal_type='USER' AND ra.principal_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER)")"
tu_count="$(query "SELECT COUNT(*) FROM tenant_user tu WHERE tu.user_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER)")"
credential_count="$(query "SELECT COUNT(*) FROM user_auth_credential c WHERE c.user_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER)")"
scope_policy_count="$(query "SELECT COUNT(*) FROM user_auth_scope_policy p WHERE p.credential_id IN (SELECT c.id FROM user_auth_credential c WHERE c.user_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER))")"
avatar_count="$(query "SELECT COUNT(*) FROM user_avatar ua WHERE ua.user_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER)")"

uu_count=0
if table_exists "user_unit"; then
  uu_count="$(query "SELECT COUNT(*) FROM user_unit uu WHERE uu.user_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER)")"
fi
us_count=0
if table_exists "user_session"; then
  us_count="$(query "SELECT COUNT(*) FROM user_session us WHERE us.user_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER)")"
fi
uaa_count=0
if table_exists "user_authentication_audit"; then
  uaa_count="$(query "SELECT COUNT(*) FROM user_authentication_audit uaa WHERE uaa.user_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER)")"
fi
aal_count=0
if table_exists "authorization_audit_log"; then
  aal_count="$(query "SELECT COUNT(*) FROM authorization_audit_log aal WHERE aal.actor_user_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER) OR aal.target_user_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER)")"
fi

echo "关联数据统计（将一并清理）:"
echo "  role_assignment: $ra_count"
echo "  tenant_user: $tu_count"
echo "  user_auth_credential: $credential_count"
echo "  user_auth_scope_policy: $scope_policy_count"
echo "  user_avatar: $avatar_count"
echo "  user_unit: $uu_count"
echo "  user_session: $us_count"
echo "  user_authentication_audit: $uaa_count"
echo "  authorization_audit_log: $aal_count"
echo ""

if [[ "$MODE" != "--apply" ]]; then
  echo "dry-run 模式未修改数据。"
  echo "如需执行（破坏性），请运行:"
  echo "  VERIFY_DB_PASSWORD='***' VERIFY_DEMO_USERS_CLEANUP_CONFIRM=YES bash tiny-oauth-server/scripts/cleanup-demo-users-user-xxxx.sh --apply"
  exit 0
fi

if [[ "${VERIFY_DEMO_USERS_CLEANUP_CONFIRM:-}" != "YES" ]]; then
  echo "❌ --apply 需要设置 VERIFY_DEMO_USERS_CLEANUP_CONFIRM=YES 以防止误删生产数据"
  exit 3
fi

# 单次连接执行：保证 TEMPORARY TABLE 与事务在同一 session 内有效
CLEANUP_SQL="
  DROP TEMPORARY TABLE IF EXISTS tmp_demo_user_ids;
  CREATE TEMPORARY TABLE tmp_demo_user_ids (
    id BIGINT PRIMARY KEY
  );
  INSERT INTO tmp_demo_user_ids (id)
  SELECT u.id
  FROM user u
  WHERE $CANDIDATE_FILTER;

  START TRANSACTION;
  DELETE ra FROM role_assignment ra
   INNER JOIN tmp_demo_user_ids t ON ra.principal_type='USER' AND ra.principal_id = t.id;
  DELETE p FROM user_auth_scope_policy p
   INNER JOIN user_auth_credential c ON c.id = p.credential_id
   INNER JOIN tmp_demo_user_ids t ON c.user_id = t.id;
  DELETE c FROM user_auth_credential c
   INNER JOIN tmp_demo_user_ids t ON c.user_id = t.id;
  DELETE ua FROM user_avatar ua
   INNER JOIN tmp_demo_user_ids t ON ua.user_id = t.id;
"

if table_exists "user_unit"; then
  CLEANUP_SQL+="
  DELETE uu FROM user_unit uu INNER JOIN tmp_demo_user_ids t ON uu.user_id = t.id;
"
fi
if table_exists "user_session"; then
  CLEANUP_SQL+="
  DELETE us FROM user_session us INNER JOIN tmp_demo_user_ids t ON us.user_id = t.id;
"
fi
if table_exists "user_authentication_audit"; then
  CLEANUP_SQL+="
  DELETE uaa FROM user_authentication_audit uaa INNER JOIN tmp_demo_user_ids t ON uaa.user_id = t.id;
"
fi
if table_exists "authorization_audit_log"; then
  CLEANUP_SQL+="
  DELETE aal FROM authorization_audit_log aal INNER JOIN tmp_demo_user_ids t ON aal.actor_user_id = t.id;
  DELETE aal FROM authorization_audit_log aal INNER JOIN tmp_demo_user_ids t ON aal.target_user_id = t.id;
"
fi

CLEANUP_SQL+="
  DELETE tu FROM tenant_user tu INNER JOIN tmp_demo_user_ids t ON tu.user_id = t.id;
  DELETE u FROM user u INNER JOIN tmp_demo_user_ids t ON u.id = t.id;
  COMMIT;

  DROP TEMPORARY TABLE IF EXISTS tmp_demo_user_ids;
"

query "$CLEANUP_SQL"

remaining="$(query "SELECT COUNT(*) FROM user u WHERE $CANDIDATE_FILTER")"
echo "清理完成。剩余候选用户数: $remaining"

if [[ "$remaining" -eq 0 ]]; then
  echo "✅ demo 用户 user_NNNN 及关联数据已清理"
else
  echo "⚠️ 仍有 $remaining 条未清理，请检查外键/并发写入"
fi
