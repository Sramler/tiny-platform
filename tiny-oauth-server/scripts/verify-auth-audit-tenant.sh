#!/usr/bin/env bash
# 认证审计租户链路验证脚本
# 用途：验证 user_authentication_audit 的 tenant_id / tenant_resolution_code / tenant_resolution_source 是否按预期落库。
# 说明：默认只输出统计与异常；可通过 AUTH_AUDIT_VERIFY_STRICT=1 开启严格失败模式。

set -euo pipefail

MYSQL_BIN="${AUTH_AUDIT_VERIFY_MYSQL_BIN:-mysql}"
AUTH_AUDIT_VERIFY_DB_HOST="${AUTH_AUDIT_VERIFY_DB_HOST:-127.0.0.1}"
AUTH_AUDIT_VERIFY_DB_PORT="${AUTH_AUDIT_VERIFY_DB_PORT:-3306}"
AUTH_AUDIT_VERIFY_DB_USER="${AUTH_AUDIT_VERIFY_DB_USER:-root}"
AUTH_AUDIT_VERIFY_DB_PASSWORD="${AUTH_AUDIT_VERIFY_DB_PASSWORD:-}"
AUTH_AUDIT_VERIFY_DB_NAME="${AUTH_AUDIT_VERIFY_DB_NAME:-tiny_web}"
AUTH_AUDIT_VERIFY_MINUTES="${AUTH_AUDIT_VERIFY_MINUTES:-120}"
AUTH_AUDIT_VERIFY_STRICT="${AUTH_AUDIT_VERIFY_STRICT:-0}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

MYSQL_CMD=("$MYSQL_BIN" -h "$AUTH_AUDIT_VERIFY_DB_HOST" -P "$AUTH_AUDIT_VERIFY_DB_PORT" -u "$AUTH_AUDIT_VERIFY_DB_USER" "$AUTH_AUDIT_VERIFY_DB_NAME" -N -s)
if [[ -n "$AUTH_AUDIT_VERIFY_DB_PASSWORD" ]]; then
  export MYSQL_PWD="$AUTH_AUDIT_VERIFY_DB_PASSWORD"
fi

cleanup() {
  unset MYSQL_PWD 2>/dev/null || true
}
trap cleanup EXIT

die() {
  echo "FAIL: $1" >&2
  exit 1
}

query() {
  "${MYSQL_CMD[@]}" -e "$1"
}

echo "=== 认证审计租户链路验证 ==="
echo "mysql: $MYSQL_BIN"
echo "database: $AUTH_AUDIT_VERIFY_DB_HOST:$AUTH_AUDIT_VERIFY_DB_PORT/$AUTH_AUDIT_VERIFY_DB_NAME"
echo "window: 最近 $AUTH_AUDIT_VERIFY_MINUTES 分钟"
echo ""

# 1) 基础连通性
if ! query "SELECT 1;" >/dev/null 2>&1; then
  die "数据库连接失败，请检查连接参数"
fi

# 2) 表与列存在性
table_exists="$(query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${AUTH_AUDIT_VERIFY_DB_NAME}' AND table_name='user_authentication_audit';")"
[[ "${table_exists:-0}" -eq 1 ]] || die "表 user_authentication_audit 不存在"

missing_columns="$(query "
SELECT COUNT(*)
FROM (
  SELECT 'tenant_id' AS c
  UNION ALL SELECT 'tenant_resolution_code'
  UNION ALL SELECT 'tenant_resolution_source'
) expected
LEFT JOIN information_schema.columns col
  ON col.table_schema='${AUTH_AUDIT_VERIFY_DB_NAME}'
 AND col.table_name='user_authentication_audit'
 AND col.column_name=expected.c
WHERE col.column_name IS NULL;
")"
[[ "${missing_columns:-0}" -eq 0 ]] || die "缺少 tenant 审计字段（tenant_id/tenant_resolution_code/tenant_resolution_source）"

echo "[OK] 表与字段检查通过"
echo ""

# 3) 总览统计
echo "--- 总量统计 ---"
query "
SELECT
  CONCAT('total=', COUNT(*)),
  CONCAT('login=', SUM(CASE WHEN event_type='LOGIN' THEN 1 ELSE 0 END)),
  CONCAT('token_issue=', SUM(CASE WHEN event_type='TOKEN_ISSUE' THEN 1 ELSE 0 END))
FROM user_authentication_audit;
"
echo ""

echo "--- 最近 ${AUTH_AUDIT_VERIFY_MINUTES} 分钟：tenant_resolution_code 分布 ---"
query "
SELECT COALESCE(tenant_resolution_code, '<NULL>') AS code, COUNT(*) AS cnt
FROM user_authentication_audit
WHERE created_at >= DATE_SUB(NOW(), INTERVAL ${AUTH_AUDIT_VERIFY_MINUTES} MINUTE)
GROUP BY COALESCE(tenant_resolution_code, '<NULL>')
ORDER BY cnt DESC, code;
"
echo ""

echo "--- 最近 ${AUTH_AUDIT_VERIFY_MINUTES} 分钟：tenant_resolution_source 分布 ---"
query "
SELECT COALESCE(tenant_resolution_source, '<NULL>') AS source, COUNT(*) AS cnt
FROM user_authentication_audit
WHERE created_at >= DATE_SUB(NOW(), INTERVAL ${AUTH_AUDIT_VERIFY_MINUTES} MINUTE)
GROUP BY COALESCE(tenant_resolution_source, '<NULL>')
ORDER BY cnt DESC, source;
"
echo ""

# 4) 异常扫描
invalid_rows="$(query "
SELECT COUNT(*)
FROM user_authentication_audit
WHERE created_at >= DATE_SUB(NOW(), INTERVAL ${AUTH_AUDIT_VERIFY_MINUTES} MINUTE)
  AND (
    (tenant_resolution_code = 'resolved' AND tenant_id IS NULL)
    OR (tenant_resolution_code = 'tenant_context_missing' AND tenant_id IS NOT NULL)
    OR tenant_resolution_code IS NULL
    OR tenant_resolution_code = ''
    OR tenant_resolution_source IS NULL
    OR tenant_resolution_source = ''
    OR tenant_resolution_source NOT IN ('token', 'session', 'login_param', 'unknown')
  );
")"

echo "--- 最近 ${AUTH_AUDIT_VERIFY_MINUTES} 分钟：异常记录数 ---"
echo "invalid_rows=${invalid_rows:-0}"
echo ""

echo "--- 最近 20 条认证审计（用于排障）---"
query "
SELECT
  DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
  event_type,
  success,
  username,
  tenant_id,
  tenant_resolution_code,
  tenant_resolution_source
FROM user_authentication_audit
ORDER BY id DESC
LIMIT 20;
"
echo ""

if [[ "${invalid_rows:-0}" -gt 0 ]]; then
  if [[ "$AUTH_AUDIT_VERIFY_STRICT" == "1" ]]; then
    die "检测到异常审计记录（strict 模式）"
  fi
  echo "WARN: 检测到异常审计记录，请根据上方明细排查。"
  exit 0
fi

echo "=== PASS: 租户审计字段写入正常 ==="
