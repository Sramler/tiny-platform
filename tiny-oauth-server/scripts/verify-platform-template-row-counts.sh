#!/usr/bin/env bash
# 查询平台模板行数：role、split carrier（menu / ui_action / api_endpoint）、
# 平台管理员资源管理运行时操作闭包，以及平台工作流模板业务权限资产。
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
      AND p.permission_code LIKE 'workflow:platform:%'),
  (SELECT COUNT(*)
     FROM ui_action ua
     JOIN menu m
       ON m.id = ua.parent_menu_id
      AND m.tenant_id <=> ua.tenant_id
    WHERE ua.tenant_id IS NULL
      AND ua.resource_level = 'PLATFORM'
      AND ua.enabled = 1
      AND m.path = '/system/resource'
      AND ua.permission IN (
        'system:resource:create',
        'system:resource:edit',
        'system:resource:delete',
        'system:resource:batch-delete'
      )),
  (SELECT COUNT(*)
     FROM ui_action ua
     JOIN menu m
       ON m.id = ua.parent_menu_id
      AND m.tenant_id <=> ua.tenant_id
     JOIN ui_action_permission_requirement requirement
       ON requirement.ui_action_id = ua.id
      AND requirement.tenant_id <=> ua.tenant_id
     JOIN permission p
       ON p.id = requirement.permission_id
      AND p.tenant_id IS NULL
      AND p.permission_code = ua.permission
      AND p.enabled = 1
    WHERE ua.tenant_id IS NULL
      AND ua.resource_level = 'PLATFORM'
      AND ua.enabled = 1
      AND requirement.requirement_group = 0
      AND requirement.sort_order = 1
      AND requirement.negated = 0
      AND ua.required_permission_id = p.id
      AND 1 = (
        SELECT COUNT(*)
        FROM ui_action_permission_requirement exact_requirement
        WHERE exact_requirement.ui_action_id = ua.id
          AND exact_requirement.tenant_id <=> ua.tenant_id
      )
      AND m.path = '/system/resource'
      AND ua.permission IN (
        'system:resource:create',
        'system:resource:edit',
        'system:resource:delete',
        'system:resource:batch-delete'
      )),
  (SELECT COUNT(DISTINCT p.permission_code)
     FROM role r
     JOIN role_permission rp
       ON rp.role_id = r.id
      AND rp.tenant_id IS NULL
     JOIN permission p
       ON p.id = rp.permission_id
      AND p.tenant_id IS NULL
      AND p.enabled = 1
    WHERE r.tenant_id IS NULL
      AND r.role_level = 'PLATFORM'
      AND r.code = 'ROLE_PLATFORM_ADMIN'
      AND r.enabled = 1
      AND p.permission_code IN (
        'system:resource:create',
        'system:resource:edit',
        'system:resource:delete',
        'system:resource:batch-delete'
      )),
  (SELECT COUNT(*)
     FROM api_endpoint endpoint
     JOIN (
       SELECT 'POST' method, '/sys/resources' uri, 'system:resource:create' permission_code
       UNION ALL SELECT 'PUT', '/sys/resources/{id}', 'system:resource:edit'
       UNION ALL SELECT 'PUT', '/sys/resources/{id}/sort', 'system:resource:edit'
       UNION ALL SELECT 'DELETE', '/sys/resources/{id}', 'system:resource:delete'
       UNION ALL SELECT 'POST', '/sys/resources/batch/delete', 'system:resource:batch-delete'
     ) route
       ON route.method = endpoint.method
      AND route.uri = endpoint.uri
     JOIN permission p
       ON p.id = endpoint.required_permission_id
      AND p.tenant_id IS NULL
      AND p.permission_code = route.permission_code
      AND p.enabled = 1
    WHERE endpoint.tenant_id IS NULL
      AND endpoint.resource_level = 'PLATFORM'
      AND endpoint.enabled = 1
      AND endpoint.permission = route.permission_code),
  (SELECT COUNT(*)
     FROM api_endpoint endpoint
     JOIN (
       SELECT 'POST' method, '/sys/resources' uri, 'system:resource:create' permission_code
       UNION ALL SELECT 'PUT', '/sys/resources/{id}', 'system:resource:edit'
       UNION ALL SELECT 'PUT', '/sys/resources/{id}/sort', 'system:resource:edit'
       UNION ALL SELECT 'DELETE', '/sys/resources/{id}', 'system:resource:delete'
       UNION ALL SELECT 'POST', '/sys/resources/batch/delete', 'system:resource:batch-delete'
     ) route
       ON route.method = endpoint.method
      AND route.uri = endpoint.uri
     JOIN api_endpoint_permission_requirement requirement
       ON requirement.api_endpoint_id = endpoint.id
      AND requirement.tenant_id <=> endpoint.tenant_id
      AND requirement.requirement_group = 0
      AND requirement.sort_order = 1
      AND requirement.negated = 0
     JOIN permission p
       ON p.id = requirement.permission_id
      AND p.id = endpoint.required_permission_id
      AND p.tenant_id IS NULL
      AND p.permission_code = route.permission_code
      AND p.enabled = 1
    WHERE endpoint.tenant_id IS NULL
      AND endpoint.resource_level = 'PLATFORM'
      AND endpoint.enabled = 1
      AND endpoint.permission = route.permission_code
      AND 1 = (
        SELECT COUNT(*)
        FROM api_endpoint_permission_requirement exact_requirement
        WHERE exact_requirement.api_endpoint_id = endpoint.id
          AND exact_requirement.tenant_id <=> endpoint.tenant_id
      )),
  (SELECT COUNT(*)
     FROM (
       SELECT 'POST' method, '/sys/resources' uri, 0 template_kind
       UNION ALL SELECT 'PUT', '/sys/resources/{id}', 1
       UNION ALL SELECT 'PUT', '/sys/resources/{id}/sort', 2
       UNION ALL SELECT 'DELETE', '/sys/resources/{id}', 1
       UNION ALL SELECT 'POST', '/sys/resources/batch/delete', 0
     ) route
    WHERE 1 = (
      SELECT COUNT(*)
      FROM api_endpoint equivalent_endpoint
      WHERE equivalent_endpoint.tenant_id IS NULL
        AND UPPER(TRIM(equivalent_endpoint.method)) = route.method
        AND (
          (
            route.template_kind = 0
            AND CASE
              WHEN CHAR_LENGTH(TRIM(equivalent_endpoint.uri)) > 1
               AND RIGHT(TRIM(equivalent_endpoint.uri), 1) = '/'
              THEN LEFT(TRIM(equivalent_endpoint.uri), CHAR_LENGTH(TRIM(equivalent_endpoint.uri)) - 1)
              ELSE TRIM(equivalent_endpoint.uri)
            END = route.uri
          )
          OR
          (
            route.template_kind = 1
            AND CASE
              WHEN CHAR_LENGTH(TRIM(equivalent_endpoint.uri)) > 1
               AND RIGHT(TRIM(equivalent_endpoint.uri), 1) = '/'
              THEN LEFT(TRIM(equivalent_endpoint.uri), CHAR_LENGTH(TRIM(equivalent_endpoint.uri)) - 1)
              ELSE TRIM(equivalent_endpoint.uri)
            END REGEXP '^/sys/resources/[{][^/{}]+[}]$'
          )
          OR
          (
            route.template_kind = 2
            AND CASE
              WHEN CHAR_LENGTH(TRIM(equivalent_endpoint.uri)) > 1
               AND RIGHT(TRIM(equivalent_endpoint.uri), 1) = '/'
              THEN LEFT(TRIM(equivalent_endpoint.uri), CHAR_LENGTH(TRIM(equivalent_endpoint.uri)) - 1)
              ELSE TRIM(equivalent_endpoint.uri)
            END REGEXP '^/sys/resources/[{][^/{}]+[}]/sort$'
          )
        )
    )),
  (SELECT 5),
  (SELECT COUNT(*)
     FROM api_endpoint endpoint
     JOIN (
       SELECT 'GET' method, '/sys/audit/authentication' uri, 'system:audit:authentication:view' permission_code
       UNION ALL SELECT 'GET', '/sys/audit/authentication/summary', 'system:audit:authentication:view'
       UNION ALL SELECT 'GET', '/sys/audit/authentication/export', 'system:audit:authentication:export'
       UNION ALL SELECT 'GET', '/sys/audit/authorization', 'system:audit:auth:view'
       UNION ALL SELECT 'GET', '/sys/audit/authorization/summary', 'system:audit:auth:view'
       UNION ALL SELECT 'GET', '/sys/audit/authorization/export', 'system:audit:auth:export'
       UNION ALL SELECT 'GET', '/sys/audit/authorization/by-event-type', 'system:audit:auth:view'
       UNION ALL SELECT 'GET', '/sys/audit/authorization/by-user/{userId}', 'system:audit:auth:view'
       UNION ALL SELECT 'DELETE', '/sys/audit/authorization/purge', 'system:audit:auth:purge'
     ) route
       ON route.method = endpoint.method
      AND route.uri = endpoint.uri
     JOIN permission p
       ON p.id = endpoint.required_permission_id
      AND p.tenant_id <=> endpoint.tenant_id
      AND p.permission_code = route.permission_code
      AND p.enabled = 1
    WHERE endpoint.resource_level = CASE WHEN endpoint.tenant_id IS NULL THEN 'PLATFORM' ELSE 'TENANT' END
      AND endpoint.enabled = 1
      AND endpoint.permission = route.permission_code),
  (SELECT COUNT(*)
     FROM api_endpoint endpoint
     JOIN (
       SELECT 'GET' method, '/sys/audit/authentication' uri, 'system:audit:authentication:view' permission_code
       UNION ALL SELECT 'GET', '/sys/audit/authentication/summary', 'system:audit:authentication:view'
       UNION ALL SELECT 'GET', '/sys/audit/authentication/export', 'system:audit:authentication:export'
       UNION ALL SELECT 'GET', '/sys/audit/authorization', 'system:audit:auth:view'
       UNION ALL SELECT 'GET', '/sys/audit/authorization/summary', 'system:audit:auth:view'
       UNION ALL SELECT 'GET', '/sys/audit/authorization/export', 'system:audit:auth:export'
       UNION ALL SELECT 'GET', '/sys/audit/authorization/by-event-type', 'system:audit:auth:view'
       UNION ALL SELECT 'GET', '/sys/audit/authorization/by-user/{userId}', 'system:audit:auth:view'
       UNION ALL SELECT 'DELETE', '/sys/audit/authorization/purge', 'system:audit:auth:purge'
     ) route
       ON route.method = endpoint.method
      AND route.uri = endpoint.uri
     JOIN api_endpoint_permission_requirement requirement
       ON requirement.api_endpoint_id = endpoint.id
      AND requirement.tenant_id <=> endpoint.tenant_id
      AND requirement.requirement_group = 0
      AND requirement.sort_order = 1
      AND requirement.negated = 0
     JOIN permission p
       ON p.id = requirement.permission_id
      AND p.id = endpoint.required_permission_id
      AND p.tenant_id <=> endpoint.tenant_id
      AND p.permission_code = route.permission_code
      AND p.enabled = 1
    WHERE endpoint.resource_level = CASE WHEN endpoint.tenant_id IS NULL THEN 'PLATFORM' ELSE 'TENANT' END
      AND endpoint.enabled = 1
      AND endpoint.permission = route.permission_code
      AND 1 = (
        SELECT COUNT(*)
        FROM api_endpoint_permission_requirement exact_requirement
        WHERE exact_requirement.api_endpoint_id = endpoint.id
          AND exact_requirement.tenant_id <=> endpoint.tenant_id
      )),
  (SELECT COUNT(*)
     FROM (
       SELECT NULL AS tenant_id, 'PLATFORM' AS resource_level
       UNION ALL
       SELECT tenant_entry.id, 'TENANT'
       FROM tenant tenant_entry
     ) target_scope
     CROSS JOIN (
       SELECT 'GET' method, '/sys/audit/authentication' uri, 0 placeholder_template
       UNION ALL SELECT 'GET', '/sys/audit/authentication/summary', 0
       UNION ALL SELECT 'GET', '/sys/audit/authentication/export', 0
       UNION ALL SELECT 'GET', '/sys/audit/authorization', 0
       UNION ALL SELECT 'GET', '/sys/audit/authorization/summary', 0
       UNION ALL SELECT 'GET', '/sys/audit/authorization/export', 0
       UNION ALL SELECT 'GET', '/sys/audit/authorization/by-event-type', 0
       UNION ALL SELECT 'GET', '/sys/audit/authorization/by-user/{userId}', 1
       UNION ALL SELECT 'DELETE', '/sys/audit/authorization/purge', 0
     ) route
    WHERE 1 = (
      SELECT COUNT(*)
      FROM api_endpoint equivalent_endpoint
      WHERE equivalent_endpoint.tenant_id <=> target_scope.tenant_id
        AND UPPER(TRIM(equivalent_endpoint.method)) = route.method
        AND (
          (
            route.placeholder_template = 0
            AND CASE
              WHEN CHAR_LENGTH(TRIM(equivalent_endpoint.uri)) > 1
               AND RIGHT(TRIM(equivalent_endpoint.uri), 1) = '/'
              THEN LEFT(TRIM(equivalent_endpoint.uri), CHAR_LENGTH(TRIM(equivalent_endpoint.uri)) - 1)
              ELSE TRIM(equivalent_endpoint.uri)
            END = route.uri
          )
          OR
          (
            route.placeholder_template = 1
            AND CASE
              WHEN CHAR_LENGTH(TRIM(equivalent_endpoint.uri)) > 1
               AND RIGHT(TRIM(equivalent_endpoint.uri), 1) = '/'
              THEN LEFT(TRIM(equivalent_endpoint.uri), CHAR_LENGTH(TRIM(equivalent_endpoint.uri)) - 1)
              ELSE TRIM(equivalent_endpoint.uri)
            END REGEXP '^/sys/audit/authorization/by-user/[{][^/{}]+[}]$'
          )
        )
    )),
  (SELECT (COUNT(*) + 1) * 9 FROM tenant);
SQL
)"
role_cnt="$(echo "${line}" | awk '{print $1}')"
menu_cnt="$(echo "${line}" | awk '{print $2}')"
ui_action_cnt="$(echo "${line}" | awk '{print $3}')"
api_endpoint_cnt="$(echo "${line}" | awk '{print $4}')"
workflow_role_cnt="$(echo "${line}" | awk '{print $5}')"
workflow_permission_cnt="$(echo "${line}" | awk '{print $6}')"
workflow_binding_cnt="$(echo "${line}" | awk '{print $7}')"
resource_action_cnt="$(echo "${line}" | awk '{print $8}')"
resource_action_requirement_cnt="$(echo "${line}" | awk '{print $9}')"
platform_admin_resource_action_binding_cnt="$(echo "${line}" | awk '{print $10}')"
resource_api_endpoint_cnt="$(echo "${line}" | awk '{print $11}')"
resource_api_requirement_cnt="$(echo "${line}" | awk '{print $12}')"
resource_api_runtime_unique_cnt="$(echo "${line}" | awk '{print $13}')"
resource_api_expected_cnt="$(echo "${line}" | awk '{print $14}')"
audit_api_endpoint_cnt="$(echo "${line}" | awk '{print $15}')"
audit_api_requirement_cnt="$(echo "${line}" | awk '{print $16}')"
audit_api_runtime_unique_cnt="$(echo "${line}" | awk '{print $17}')"
audit_api_expected_cnt="$(echo "${line}" | awk '{print $18}')"
carrier_cnt=$((menu_cnt + ui_action_cnt + api_endpoint_cnt))

if ! [[ "${role_cnt}" =~ ^[0-9]+$ && "${menu_cnt}" =~ ^[0-9]+$ && "${ui_action_cnt}" =~ ^[0-9]+$ && "${api_endpoint_cnt}" =~ ^[0-9]+$ && "${workflow_role_cnt}" =~ ^[0-9]+$ && "${workflow_permission_cnt}" =~ ^[0-9]+$ && "${workflow_binding_cnt}" =~ ^[0-9]+$ && "${resource_action_cnt}" =~ ^[0-9]+$ && "${resource_action_requirement_cnt}" =~ ^[0-9]+$ && "${platform_admin_resource_action_binding_cnt}" =~ ^[0-9]+$ && "${resource_api_endpoint_cnt}" =~ ^[0-9]+$ && "${resource_api_requirement_cnt}" =~ ^[0-9]+$ && "${resource_api_runtime_unique_cnt}" =~ ^[0-9]+$ && "${resource_api_expected_cnt}" =~ ^[0-9]+$ && "${audit_api_endpoint_cnt}" =~ ^[0-9]+$ && "${audit_api_requirement_cnt}" =~ ^[0-9]+$ && "${audit_api_runtime_unique_cnt}" =~ ^[0-9]+$ && "${audit_api_expected_cnt}" =~ ^[0-9]+$ ]]; then
  echo "Unexpected mysql output (expected eighteen integers): ${line}" >&2
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
echo "    resource_actions:    ${resource_action_cnt}/4"
echo "    resource_require:    ${resource_action_requirement_cnt}/4"
echo "    platform_admin_bind: ${platform_admin_resource_action_binding_cnt}/4"
echo "    resource_api:        ${resource_api_endpoint_cnt}/5"
echo "    resource_api_require:${resource_api_requirement_cnt}/5"
echo "    resource_runtime_unique:${resource_api_runtime_unique_cnt}/${resource_api_expected_cnt}"
echo "    audit_api:           ${audit_api_endpoint_cnt}/${audit_api_expected_cnt}"
echo "    audit_api_require:   ${audit_api_requirement_cnt}/${audit_api_expected_cnt}"
echo "    audit_runtime_unique:${audit_api_runtime_unique_cnt}/${audit_api_expected_cnt}"

if [[ "${VERIFY_PLATFORM_TEMPLATE_MIN_ROWS:-}" == "1" ]]; then
  if [[ "${role_cnt}" -eq 0 || "${carrier_cnt}" -eq 0 || "${workflow_role_cnt}" -lt 3 || "${workflow_permission_cnt}" -lt 24 || "${workflow_binding_cnt}" -eq 0 || "${resource_action_cnt}" -ne 4 || "${resource_action_requirement_cnt}" -ne 4 || "${platform_admin_resource_action_binding_cnt}" -ne 4 || "${resource_api_endpoint_cnt}" -ne 5 || "${resource_api_requirement_cnt}" -ne 5 || "${resource_api_runtime_unique_cnt}" -ne "${resource_api_expected_cnt}" || "${audit_api_endpoint_cnt}" -ne "${audit_api_expected_cnt}" || "${audit_api_requirement_cnt}" -ne "${audit_api_expected_cnt}" || "${audit_api_runtime_unique_cnt}" -ne "${audit_api_expected_cnt}" ]]; then
    echo "VERIFY_PLATFORM_TEMPLATE_MIN_ROWS=1: 期望 role/carrier、平台管理员资源操作闭包与工作流模板业务资产均已回填，当前不满足。" >&2
    exit 1
  fi
fi
