#!/usr/bin/env bash
# CARD-13A：对账「GLOBAL-only」认证策略缺口（部署读侧单 scope_key 前后均可运行）。
# - 期望 gap_row_count = 0（迁移 135 已执行或数据本就不存在缺口）。
# - 退出码：0 通过；1 存在缺口；2 环境前置未满足（与仓库其它 verify 脚本口径一致）。
#
# 用法（项目根或 tiny-oauth-server 下）：
#   DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-card-13a-global-auth-scope-policy-rollout.sh
# 连接参数：VERIFY_DB_HOST / VERIFY_DB_PORT / VERIFY_DB_USER / VERIFY_DB_NAME
# 密码：VERIFY_DB_PASSWORD 或 E2E_DB_PASSWORD / E2E_MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD

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

MYSQL_BIN="${VERIFY_MYSQL_BIN:-mysql}"
VERIFY_DB_HOST="${VERIFY_DB_HOST:-127.0.0.1}"
VERIFY_DB_PORT="${VERIFY_DB_PORT:-3306}"
VERIFY_DB_USER="${VERIFY_DB_USER:-root}"
VERIFY_DB_PASSWORD="$(read_env VERIFY_DB_PASSWORD DB_PASSWORD E2E_DB_PASSWORD E2E_MYSQL_PASSWORD MYSQL_ROOT_PASSWORD || true)"
VERIFY_DB_NAME="${VERIFY_DB_NAME:-tiny_web}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$SCRIPT_DIR/sql/verify-card-13a-global-auth-scope-policy-gaps.sql"

MYSQL_CMD=("$MYSQL_BIN" -h "$VERIFY_DB_HOST" -P "$VERIFY_DB_PORT" -u "$VERIFY_DB_USER" "$VERIFY_DB_NAME" -N -s)
if [[ -n "$VERIFY_DB_PASSWORD" ]]; then
  export MYSQL_PWD="$VERIFY_DB_PASSWORD"
fi

query() { "${MYSQL_CMD[@]}" -e "$1" 2>/dev/null; }

if ! command -v "$MYSQL_BIN" >/dev/null 2>&1; then
  echo "找不到 mysql 客户端：${MYSQL_BIN}" >&2
  exit 2
fi

if ! query "SELECT 1" | grep -q 1; then
  echo "无法连接数据库：${VERIFY_DB_HOST}:${VERIFY_DB_PORT}/${VERIFY_DB_NAME}" >&2
  echo "请配置 VERIFY_DB_PASSWORD 或 DB_PASSWORD（或 E2E_DB_PASSWORD 等）。" >&2
  exit 2
fi

for tbl in user_auth_scope_policy user_auth_credential tenant_user; do
  if ! query "SELECT 1 FROM information_schema.tables WHERE table_schema='${VERIFY_DB_NAME}' AND table_name='${tbl}'" | grep -q 1; then
    echo "表 ${tbl} 不存在，跳过 CARD-13A 对账（可能尚未执行 133 迁移）。" >&2
    exit 2
  fi
done

count="$(query "$(cat "$SQL_FILE")" | tail -n 1 | tr -d '[:space:]')"
if [[ -z "${count}" ]] || ! [[ "$count" =~ ^[0-9]+$ ]]; then
  echo "无法解析 gap 计数，原始输出可能异常。" >&2
  exit 2
fi

echo "CARD-13A GLOBAL 策略缺口行数（期望 0）: ${count}"
if [[ "$count" -eq 0 ]]; then
  echo "PASS"
  exit 0
fi
echo "FAIL: 仍存在 GLOBAL 策略缺口；请先执行 Liquibase 135-duplicate-global-auth-scope-policy-card-13a 或等价数据修复。" >&2
echo "参考 SQL: tiny-oauth-server/scripts/sql/verify-card-13a-global-auth-scope-policy-gaps.sql" >&2
exit 1
