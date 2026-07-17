#!/usr/bin/env bash
# 查询平台模板行数：role、split carrier（menu / ui_action / api_endpoint）
# 以及平台工作流模板业务权限资产在 tenant_id IS NULL 下的记录数。
# 与 ensure-platform-admin.sh 使用相同的数据库连接环境变量。
#
# 用法（仓库根目录）:
#   DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-template-row-counts.sh
#
# 可选：
#   MYSQL_BIN=/Users/bliu/software/mysql/3306/bin/mysql DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-template-row-counts.sh
#
# 可选：若同时设置 VERIFY_PLATFORM_TEMPLATE_MIN_ROWS=1，则在 role、carrier 或工作流模板业务资产
# 任一关键计数不足时以非 0 退出（表示平台模板未回填或半截数据，需重启 dev 触发自动回填或手工修复）。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-tiny_web}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-}"

if [[ -z "${DB_PASSWORD}" ]]; then
  echo "Missing DB_PASSWORD env var." >&2
  exit 2
fi

resolve_mysql() {
  if [[ -n "${MYSQL_BIN:-}" ]]; then
    echo "${MYSQL_BIN}"
    return
  fi
  if command -v mysql >/dev/null 2>&1; then
    command -v mysql
    return
  fi
  for candidate in \
    /Users/bliu/software/mysql/3306/bin/mysql \
    /opt/homebrew/bin/mysql \
    /usr/local/bin/mysql \
    /usr/bin/mysql
  do
    if [[ -x "${candidate}" ]]; then
      echo "${candidate}"
      return
    fi
  done
}

MYSQL_CMD="$(resolve_mysql)"
if [[ -z "${MYSQL_CMD}" || ! -x "${MYSQL_CMD}" ]]; then
  echo "Missing mysql client. Set MYSQL_BIN=/path/to/mysql or add mysql to PATH." >&2
  exit 2
fi

# MYSQL_PWD：避免 -p 在命令行上触发「Using a password on the command line...」刷屏（本地脚本用）
line="$(
  env MYSQL_PWD="${DB_PASSWORD}" "${MYSQL_CMD}" \
    -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -D "${DB_NAME}" -N -B <<'SQL'
SELECT
  (SELECT COUNT(*) FROM role WHERE tenant_id IS NULL),
  (SELECT COUNT(*) FROM menu WHERE tenant_id IS NULL),
  (SELECT COUNT(*) FROM ui_action WHERE tenant_id IS NULL),
  (SELECT COUNT(*) FROM api_endpoint WHERE tenant_id IS NULL),
  (SELECT COUNT(*) FROM role WHERE tenant_id IS NULL AND code IN ('ROLE_PLATFORM_PRODUCT', 'ROLE_PLATFORM_OPS', 'ROLE_PLATFORM_SECURITY')),
  (SELECT COUNT(*) FROM permission WHERE tenant_id IS NULL AND permission_code LIKE 'workflow:platform:%'),
  (SELECT COUNT(*)
     FROM role_permission rp
     JOIN permission p ON p.id = rp.permission_id
    WHERE rp.tenant_id IS NULL
      AND p.tenant_id IS NULL
      AND p.permission_code LIKE 'workflow:platform:%');
SQL
)"
role_cnt="$(echo "${line}" | awk '{print $1}')"
menu_cnt="$(echo "${line}" | awk '{print $2}')"
ui_action_cnt="$(echo "${line}" | awk '{print $3}')"
api_endpoint_cnt="$(echo "${line}" | awk '{print $4}')"
workflow_role_cnt="$(echo "${line}" | awk '{print $5}')"
workflow_permission_cnt="$(echo "${line}" | awk '{print $6}')"
workflow_binding_cnt="$(echo "${line}" | awk '{print $7}')"
carrier_cnt=$((menu_cnt + ui_action_cnt + api_endpoint_cnt))

if ! [[ "${role_cnt}" =~ ^[0-9]+$ && "${menu_cnt}" =~ ^[0-9]+$ && "${ui_action_cnt}" =~ ^[0-9]+$ && "${api_endpoint_cnt}" =~ ^[0-9]+$ && "${workflow_role_cnt}" =~ ^[0-9]+$ && "${workflow_permission_cnt}" =~ ^[0-9]+$ && "${workflow_binding_cnt}" =~ ^[0-9]+$ ]]; then
  echo "Unexpected mysql output (expected seven integers): ${line}" >&2
  exit 1
fi

echo "==> platform template row counts (tenant_id IS NULL)"
echo "    role:                ${role_cnt}"
echo "    menu:                ${menu_cnt}"
echo "    ui_action:           ${ui_action_cnt}"
echo "    api_ep:              ${api_endpoint_cnt}"
echo "    carrier:             ${carrier_cnt}"
echo "    workflow_role:       ${workflow_role_cnt}"
echo "    workflow_permission: ${workflow_permission_cnt}"
echo "    workflow_binding:    ${workflow_binding_cnt}"

if [[ "${VERIFY_PLATFORM_TEMPLATE_MIN_ROWS:-}" == "1" ]]; then
  if [[ "${role_cnt}" -eq 0 || "${carrier_cnt}" -eq 0 || "${workflow_role_cnt}" -lt 3 || "${workflow_permission_cnt}" -lt 24 || "${workflow_binding_cnt}" -eq 0 ]]; then
    echo "VERIFY_PLATFORM_TEMPLATE_MIN_ROWS=1: 期望 role/carrier 平台模板与工作流模板业务资产均已回填，当前不满足。" >&2
    exit 2
  fi
fi
