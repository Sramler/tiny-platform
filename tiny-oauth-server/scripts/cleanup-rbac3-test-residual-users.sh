#!/usr/bin/env bash
# 清理共享库中 RBAC3 集成测试残留用户（默认仅处理无 ACTIVE membership 的测试账号）
# 用户名前缀覆盖：dryrun/enforce 主用例、violation_obs、enforce_obs、enforce_allowlist 等
#
# 默认: dry-run（只统计，不改数据）
# 执行: --apply（执行清理）
#
# 可覆盖的连接参数:
#   VERIFY_DB_HOST VERIFY_DB_PORT VERIFY_DB_USER VERIFY_DB_NAME VERIFY_MYSQL_BIN
#   VERIFY_DB_PASSWORD / E2E_DB_PASSWORD / E2E_MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD

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
  bash scripts/cleanup-rbac3-test-residual-users.sh            # dry-run
  bash scripts/cleanup-rbac3-test-residual-users.sh --apply    # apply cleanup
  bash scripts/cleanup-rbac3-test-residual-users.sh --apply --include-active  # include even if tenant_user.status='ACTIVE'
EOF
}

MODE="${1:-dry-run}"
if [[ "$MODE" != "dry-run" && "$MODE" != "--apply" ]]; then
  usage
  exit 2
fi

INCLUDE_ACTIVE="false"
if [[ "${2:-}" == "--include-active" || "${3:-}" == "--include-active" ]]; then
  INCLUDE_ACTIVE="true"
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

echo "=== RBAC3 测试残留用户清理 ==="
echo "数据库: $VERIFY_DB_HOST:$VERIFY_DB_PORT/$VERIFY_DB_NAME"
echo "模式: $MODE"
echo ""

if ! query "SELECT 1" | grep -q 1; then
  echo "❌ 无法连接数据库或认证失败"
  exit 2
fi

# 覆盖 dry-run/enforce 集成测试与 observability / allowlist 等用例的用户名前缀
BASE_USERNAME_FILTER="
  (
       u.username LIKE 'rbac3_dryrun_user_%'
    OR u.username LIKE 'rbac3_enforce_user_%'
    OR u.username LIKE 'rbac3_violation_obs_user_%'
    OR u.username LIKE 'rbac3_enforce_obs_user_%'
    OR u.username LIKE 'rbac3_enforce_allowlist_user_%'
  )
"

ACTIVE_MEMBERSHIP_FILTER=""
if [[ "$INCLUDE_ACTIVE" != "true" ]]; then
  # safe mode: only delete accounts that have no ACTIVE membership
  ACTIVE_MEMBERSHIP_FILTER="
    AND NOT EXISTS (
      SELECT 1
      FROM tenant_user tu
      WHERE tu.user_id = u.id
        AND tu.status = 'ACTIVE'
    )"
fi

CANDIDATE_FILTER="
  $BASE_USERNAME_FILTER
  $ACTIVE_MEMBERSHIP_FILTER
"

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
uam_count="$(query "SELECT COUNT(*) FROM user_authentication_method uam WHERE uam.user_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER)")"
avatar_count="$(query "SELECT COUNT(*) FROM user_avatar ua WHERE ua.user_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER)")"
rvl_count="$(query "SELECT COUNT(*) FROM role_constraint_violation_log rvl WHERE rvl.principal_type='USER' AND rvl.principal_id IN (SELECT u.id FROM user u WHERE $CANDIDATE_FILTER)")"

echo "关联数据统计（将一并清理）:"
echo "  role_assignment: $ra_count"
echo "  tenant_user: $tu_count"
echo "  user_authentication_method: $uam_count"
echo "  user_avatar: $avatar_count"
echo "  role_constraint_violation_log: $rvl_count"
echo ""

if [[ "$MODE" != "--apply" ]]; then
  echo "dry-run 模式未修改数据。"
  echo "如需执行，请运行:"
  echo "  VERIFY_DB_PASSWORD='***' bash tiny-oauth-server/scripts/cleanup-rbac3-test-residual-users.sh --apply"
  exit 0
fi

query "
  DROP TEMPORARY TABLE IF EXISTS tmp_rbac3_cleanup_user_ids;
  CREATE TEMPORARY TABLE tmp_rbac3_cleanup_user_ids (
    id BIGINT PRIMARY KEY
  );
  INSERT INTO tmp_rbac3_cleanup_user_ids (id)
  SELECT u.id
  FROM user u
  WHERE $CANDIDATE_FILTER;

  START TRANSACTION;
  DELETE FROM role_assignment
   WHERE principal_type='USER' AND principal_id IN (SELECT id FROM tmp_rbac3_cleanup_user_ids);
  DELETE FROM role_constraint_violation_log
   WHERE principal_type='USER' AND principal_id IN (SELECT id FROM tmp_rbac3_cleanup_user_ids);
  DELETE FROM user_authentication_method
   WHERE user_id IN (SELECT id FROM tmp_rbac3_cleanup_user_ids);
  DELETE FROM user_avatar
   WHERE user_id IN (SELECT id FROM tmp_rbac3_cleanup_user_ids);
  DELETE FROM tenant_user
   WHERE user_id IN (SELECT id FROM tmp_rbac3_cleanup_user_ids);
  DELETE FROM user
   WHERE id IN (SELECT id FROM tmp_rbac3_cleanup_user_ids);
  COMMIT;

  DROP TEMPORARY TABLE IF EXISTS tmp_rbac3_cleanup_user_ids;
"

remaining="$(query "SELECT COUNT(*) FROM user u WHERE $CANDIDATE_FILTER")"
echo "清理完成。剩余候选用户数: $remaining"

if [[ "$remaining" -eq 0 ]]; then
  echo "✅ RBAC3 测试残留用户已清理"
else
  echo "⚠️ 仍有 $remaining 条未清理，请检查外键/并发写入"
fi
