#!/usr/bin/env bash
# 清理共享库中 RBAC3 集成测试残留角色（默认仅处理“孤儿测试角色”）
# 角色命名来源：
#   ROLE_RBAC3_*
#   RBAC3 *
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
  bash scripts/cleanup-rbac3-test-residual-roles.sh            # dry-run
  bash scripts/cleanup-rbac3-test-residual-roles.sh --apply    # apply cleanup
  bash scripts/cleanup-rbac3-test-residual-roles.sh --fail-on-stale
EOF
}

MODE="dry-run"
FAIL_ON_STALE="false"
while [[ $# -gt 0 ]]; do
  case "$1" in
    dry-run|--apply )
      MODE="$1"
      shift
      ;;
    --fail-on-stale )
      FAIL_ON_STALE="true"
      shift
      ;;
    * )
      usage
      exit 2
      ;;
  esac
done

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

sql_list_from_csv() {
  local csv="$1"
  local item
  local result=""
  local -a items=()
  if [[ -z "${csv//[[:space:]]/}" ]]; then
    printf "'__never__'"
    return 0
  fi
  IFS=',' read -r -a items <<<"$csv"
  for item in "${items[@]:-}"; do
    item="${item#"${item%%[![:space:]]*}"}"
    item="${item%"${item##*[![:space:]]}"}"
    if [[ -z "$item" ]]; then
      continue
    fi
    if [[ -z "$result" ]]; then
      result="$item"
    else
      result="${result}, $item"
    fi
  done
  printf '%s' "${result:-'__never__'}"
}

echo "=== RBAC3 测试残留角色清理 ==="
echo "数据库: $VERIFY_DB_HOST:$VERIFY_DB_PORT/$VERIFY_DB_NAME"
echo "模式: $MODE"
echo ""

if ! query "SELECT 1" | grep -q 1; then
  echo "❌ 无法连接数据库或认证失败"
  exit 2
fi

CANDIDATE_FILTER="
  (
       r.code LIKE 'ROLE_RBAC3_%'
    OR r.name LIKE 'RBAC3 %'
  )
  AND r.builtin = 0
  AND NOT EXISTS (
    SELECT 1
    FROM role_assignment ra
    WHERE ra.role_id = r.id
  )
  AND NOT EXISTS (
    SELECT 1
    FROM role_data_scope rds
    WHERE rds.role_id = r.id
  )
  AND NOT EXISTS (
    SELECT 1
    FROM role_permission rp
    WHERE rp.role_id = r.id
  )
"

candidate_count="$(query "SELECT COUNT(*) FROM role r WHERE $CANDIDATE_FILTER")"
echo "候选角色数: $candidate_count"

if [[ "$candidate_count" -eq 0 ]]; then
  echo "✅ 无需清理"
  exit 0
fi

echo "示例角色（最多 20 条）:"
query "
  SELECT r.id, t.code AS tenant_code, r.code, r.name
  FROM role r
  JOIN tenant t ON t.id = r.tenant_id
  WHERE $CANDIDATE_FILTER
  ORDER BY r.created_at DESC
  LIMIT 20
" | awk '{
  printf "  - id=%s tenant=%s code=%s", $1, $2, $3;
  $1=""; $2=""; $3="";
  sub(/^   */, "", $0);
  printf " name=%s\n", $0;
}'
echo ""

hierarchy_count="$(query "SELECT COUNT(*) FROM role_hierarchy h WHERE h.child_role_id IN (SELECT r.id FROM role r WHERE $CANDIDATE_FILTER) OR h.parent_role_id IN (SELECT r.id FROM role r WHERE $CANDIDATE_FILTER)")"
mutex_count="$(query "SELECT COUNT(*) FROM role_mutex m WHERE m.left_role_id IN (SELECT r.id FROM role r WHERE $CANDIDATE_FILTER) OR m.right_role_id IN (SELECT r.id FROM role r WHERE $CANDIDATE_FILTER)")"
cardinality_count="$(query "SELECT COUNT(*) FROM role_cardinality c WHERE c.role_id IN (SELECT r.id FROM role r WHERE $CANDIDATE_FILTER)")"
prerequisite_count="$(query "SELECT COUNT(*) FROM role_prerequisite p WHERE p.role_id IN (SELECT r.id FROM role r WHERE $CANDIDATE_FILTER) OR p.required_role_id IN (SELECT r.id FROM role r WHERE $CANDIDATE_FILTER)")"
candidate_role_ids_csv="$(query "SELECT COALESCE(GROUP_CONCAT(r.id ORDER BY r.id SEPARATOR ','), '') FROM role r WHERE $CANDIDATE_FILTER")"
candidate_role_ids_sql="$(sql_list_from_csv "$candidate_role_ids_csv")"

echo "关联规则数据统计（将一并清理）:"
echo "  role_hierarchy: $hierarchy_count"
echo "  role_mutex: $mutex_count"
echo "  role_cardinality: $cardinality_count"
echo "  role_prerequisite: $prerequisite_count"
echo ""

if [[ "$MODE" != "--apply" ]]; then
  echo "dry-run 模式未修改数据。"
  echo "如需执行，请运行:"
  echo "  VERIFY_DB_PASSWORD='***' bash tiny-oauth-server/scripts/cleanup-rbac3-test-residual-roles.sh --apply"
  if [[ "$FAIL_ON_STALE" == "true" ]]; then
    echo "FAIL: 发现 RBAC3 测试残留角色"
    exit 1
  fi
  exit 0
fi

query "
  START TRANSACTION;
  DELETE FROM role_hierarchy
   WHERE child_role_id IN (${candidate_role_ids_sql})
      OR parent_role_id IN (${candidate_role_ids_sql});
  DELETE FROM role_mutex
   WHERE left_role_id IN (${candidate_role_ids_sql})
      OR right_role_id IN (${candidate_role_ids_sql});
  DELETE FROM role_prerequisite
   WHERE role_id IN (${candidate_role_ids_sql})
      OR required_role_id IN (${candidate_role_ids_sql});
  DELETE FROM role_cardinality
   WHERE role_id IN (${candidate_role_ids_sql});
  DELETE FROM role
   WHERE id IN (${candidate_role_ids_sql});
  COMMIT;
"

remaining="$(query "SELECT COUNT(*) FROM role r WHERE $CANDIDATE_FILTER")"
echo "清理完成。剩余候选角色数: $remaining"

if [[ "$remaining" -eq 0 ]]; then
  echo "✅ RBAC3 测试残留角色已清理"
else
  echo "⚠️ 仍有 $remaining 条未清理，请检查外键/并发写入"
  if [[ "$FAIL_ON_STALE" == "true" ]]; then
    exit 1
  fi
fi
