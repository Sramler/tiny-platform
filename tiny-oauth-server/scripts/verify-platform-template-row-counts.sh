#!/usr/bin/env bash
# 查询平台模板行数：role 与 split carrier（menu / ui_action / api_endpoint）
# 在 tenant_id IS NULL 下的模板记录数。
# 与 ensure-platform-admin.sh 使用相同的数据库连接环境变量。
#
# 用法（仓库根目录）:
#   DB_PASSWORD='…' bash tiny-oauth-server/scripts/verify-platform-template-row-counts.sh
#
# 可选：若同时设置 VERIFY_PLATFORM_TEMPLATE_MIN_ROWS=1，则在 role 或 carrier 总量任一计数为 0 时以非 0 退出
#（表示平台模板未回填或半截数据，需重启 dev 触发自动回填或手工修复）。
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
  exit 1
fi

# MYSQL_PWD：避免 -p 在命令行上触发「Using a password on the command line...」刷屏（本地脚本用）
line="$(
  env MYSQL_PWD="${DB_PASSWORD}" mysql \
    -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -D "${DB_NAME}" -N -B <<'SQL'
SELECT
  (SELECT COUNT(*) FROM role WHERE tenant_id IS NULL),
  (SELECT COUNT(*) FROM menu WHERE tenant_id IS NULL),
  (SELECT COUNT(*) FROM ui_action WHERE tenant_id IS NULL),
  (SELECT COUNT(*) FROM api_endpoint WHERE tenant_id IS NULL);
SQL
)"
role_cnt="$(echo "${line}" | awk '{print $1}')"
menu_cnt="$(echo "${line}" | awk '{print $2}')"
ui_action_cnt="$(echo "${line}" | awk '{print $3}')"
api_endpoint_cnt="$(echo "${line}" | awk '{print $4}')"
carrier_cnt=$((menu_cnt + ui_action_cnt + api_endpoint_cnt))

if ! [[ "${role_cnt}" =~ ^[0-9]+$ && "${menu_cnt}" =~ ^[0-9]+$ && "${ui_action_cnt}" =~ ^[0-9]+$ && "${api_endpoint_cnt}" =~ ^[0-9]+$ ]]; then
  echo "Unexpected mysql output (expected four integers): ${line}" >&2
  exit 1
fi

echo "==> platform template row counts (tenant_id IS NULL)"
echo "    role:     ${role_cnt}"
echo "    menu:     ${menu_cnt}"
echo "    ui_action:${ui_action_cnt}"
echo "    api_ep:   ${api_endpoint_cnt}"
echo "    carrier:  ${carrier_cnt}"

if [[ "${VERIFY_PLATFORM_TEMPLATE_MIN_ROWS:-}" == "1" ]]; then
  if [[ "${role_cnt}" -eq 0 || "${carrier_cnt}" -eq 0 ]]; then
    echo "VERIFY_PLATFORM_TEMPLATE_MIN_ROWS=1: 期望 role 与 carrier 平台模板行数均 > 0，当前不满足。" >&2
    exit 2
  fi
fi
