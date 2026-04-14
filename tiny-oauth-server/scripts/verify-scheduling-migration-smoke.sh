#!/usr/bin/env bash
# 调度模块迁移冒烟验证脚本
# 用途：在真实 MySQL 上验证 scheduling 相关 Liquibase 迁移（当前重点 035 / 036 / 037 / 038 / 039 / 040）是否真正落库，
#      并确认默认租户可作为新租户权限模板来源。
# 使用：在项目根目录或 tiny-oauth-server 目录执行；通过环境变量覆盖数据库连接。

set -euo pipefail

MYSQL_BIN="${SCHEDULING_VERIFY_MYSQL_BIN:-mysql}"
SCHEDULING_VERIFY_DB_HOST="${SCHEDULING_VERIFY_DB_HOST:-127.0.0.1}"
SCHEDULING_VERIFY_DB_PORT="${SCHEDULING_VERIFY_DB_PORT:-3306}"
SCHEDULING_VERIFY_DB_USER="${SCHEDULING_VERIFY_DB_USER:-root}"
SCHEDULING_VERIFY_DB_PASSWORD="${SCHEDULING_VERIFY_DB_PASSWORD:-}"
SCHEDULING_VERIFY_DB_NAME="${SCHEDULING_VERIFY_DB_NAME:-tiny_web}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

MYSQL_CMD=("$MYSQL_BIN" -h "$SCHEDULING_VERIFY_DB_HOST" -P "$SCHEDULING_VERIFY_DB_PORT" -u "$SCHEDULING_VERIFY_DB_USER" "$SCHEDULING_VERIFY_DB_NAME" -N -s)
if [[ -n "$SCHEDULING_VERIFY_DB_PASSWORD" ]]; then
  export MYSQL_PWD="$SCHEDULING_VERIFY_DB_PASSWORD"
fi

echo "=== 调度模块迁移冒烟验证 ==="
echo "mysql: $MYSQL_BIN"
echo "数据库: $SCHEDULING_VERIFY_DB_HOST:$SCHEDULING_VERIFY_DB_PORT/$SCHEDULING_VERIFY_DB_NAME"
echo ""

fail() {
  echo "FAIL: $1"
  exit 1
}

query_value() {
  "${MYSQL_CMD[@]}" -e "$1" 2>/dev/null | tr -d '\r'
}

MYSQL_ERR=$(mktemp)
trap 'rm -f "$MYSQL_ERR"; unset MYSQL_PWD 2>/dev/null || true' EXIT
if ! "${MYSQL_CMD[@]}" -e "SELECT 1;" 2>"$MYSQL_ERR"; then
  echo "FAIL: 无法连接数据库（$SCHEDULING_VERIFY_DB_HOST:$SCHEDULING_VERIFY_DB_PORT，用户 $SCHEDULING_VERIFY_DB_USER，库 $SCHEDULING_VERIFY_DB_NAME）"
  [[ -s "$MYSQL_ERR" ]] && echo "mysql 错误输出:" && cat "$MYSQL_ERR"
  exit 1
fi

# 035: uk_scheduling_dag_version_dag_version
index_count=$(query_value "
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'scheduling_dag_version'
    AND index_name = 'uk_scheduling_dag_version_dag_version';
")
[[ "${index_count:-0}" -ge 1 ]] || fail "缺少唯一索引 uk_scheduling_dag_version_dag_version"
echo "  [OK] 035: 唯一索引 uk_scheduling_dag_version_dag_version 已存在"

duplicate_count=$(query_value "
  SELECT COUNT(*) FROM (
    SELECT dag_id, version_no, COUNT(*) AS cnt
    FROM scheduling_dag_version
    GROUP BY dag_id, version_no
    HAVING COUNT(*) > 1
  ) t;
")
[[ "${duplicate_count:-0}" -eq 0 ]] || fail "scheduling_dag_version 仍存在重复 (dag_id, version_no) 数据: ${duplicate_count}"
echo "  [OK] 035: scheduling_dag_version 不存在重复 (dag_id, version_no)"

check_column_not_null() {
  local table_name="$1"
  local column_name="$2"
  local nullable
  nullable=$(query_value "
    SELECT IS_NULLABLE
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = '${table_name}'
      AND column_name = '${column_name}'
    LIMIT 1;
  ")
  [[ "$nullable" == "NO" ]] || fail "${table_name}.${column_name} 不是 NOT NULL"
}

check_null_rows() {
  local table_name="$1"
  local column_name="$2"
  local null_count
  null_count=$(query_value "SELECT COUNT(*) FROM \`${table_name}\` WHERE \`${column_name}\` IS NULL;")
  [[ "${null_count:-0}" -eq 0 ]] || fail "${table_name}.${column_name} 仍存在 NULL 数据: ${null_count}"
}

check_index_exists() {
  local table_name="$1"
  local index_name="$2"
  local count
  count=$(query_value "
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = '${table_name}'
      AND index_name = '${index_name}';
  ")
  [[ "${count:-0}" -ge 1 ]] || fail "${table_name} 缺少索引 ${index_name}"
}

# 036: tenant_id + 回填 + 索引
for table_name in scheduling_dag_version scheduling_dag_task scheduling_dag_edge; do
  check_column_not_null "$table_name" "tenant_id"
  check_null_rows "$table_name" "tenant_id"
  echo "  [OK] 036: ${table_name}.tenant_id 为 NOT NULL 且无脏数据"
done

check_index_exists "scheduling_dag_version" "idx_scheduling_dag_version_tenant_dag"
check_index_exists "scheduling_dag_task" "idx_scheduling_dag_task_tenant_version_node"
check_index_exists "scheduling_dag_edge" "idx_scheduling_dag_edge_tenant_version_from_to"
echo "  [OK] 036: 租户相关索引已存在"

default_tenant_id=$(query_value "SELECT id FROM tenant WHERE code = 'default' LIMIT 1;")
[[ -n "${default_tenant_id}" ]] || fail "缺少默认租户 default，无法作为新租户 bootstrap 模板来源"
echo "  [OK] bootstrap: 默认租户 default 已存在（tenant_id=${default_tenant_id}）"

default_admin_role_count=$(query_value "
  SELECT COUNT(*)
  FROM role
  WHERE tenant_id = ${default_tenant_id}
    AND code = 'ROLE_TENANT_ADMIN';
")
[[ "${default_admin_role_count:-0}" -ge 1 ]] || fail "默认租户缺少 ROLE_TENANT_ADMIN，无法提供权限模板"
echo "  [OK] bootstrap: 默认租户存在 ROLE_TENANT_ADMIN"

default_legacy_admin_count=$(query_value "
  SELECT COUNT(*)
  FROM role
  WHERE tenant_id = ${default_tenant_id}
    AND code = 'ROLE_ADMIN';
")
[[ "${default_legacy_admin_count:-0}" -eq 0 ]] || fail "默认租户仍存在 ROLE_ADMIN（应已严格收口到 ROLE_TENANT_ADMIN）"
echo "  [OK] bootstrap: 默认租户无 ROLE_ADMIN 残留"

for permission in \
  'scheduling:console:view' \
  'scheduling:console:config' \
  'scheduling:run:control' \
  'scheduling:audit:view' \
  'scheduling:cluster:view' \
  'scheduling:*'
do
  permission_count=$(query_value "
    SELECT COUNT(*)
    FROM permission
    WHERE tenant_id = ${default_tenant_id}
      AND permission_code = '${permission}';
  ")
  [[ "${permission_count:-0}" -ge 1 ]] || fail "默认租户缺少调度 authority 权限 ${permission}"
done
echo "  [OK] 037: 默认租户调度 authority 权限已存在"

wildcard_binding_count=$(query_value "
  SELECT COUNT(*)
  FROM role_permission rp
  JOIN role role_entity
    ON role_entity.id = rp.role_id
   AND role_entity.tenant_id = rp.tenant_id
  JOIN permission perm
    ON perm.id = rp.permission_id
   AND perm.normalized_tenant_id = rp.normalized_tenant_id
  WHERE rp.tenant_id = ${default_tenant_id}
    AND role_entity.code = 'ROLE_TENANT_ADMIN'
    AND perm.permission_code = 'scheduling:*';
")
[[ "${wildcard_binding_count:-0}" -ge 1 ]] || fail "默认租户 ROLE_TENANT_ADMIN 未绑定 scheduling:*，新租户 bootstrap 模板不完整"
echo "  [OK] 037: 默认租户 ROLE_TENANT_ADMIN 已绑定 scheduling:*"

non_default_platform_menu_count=$(query_value "
  SELECT COUNT(*)
  FROM (
    SELECT tenant_id, name, permission, path AS route_value, '' AS uri_value
    FROM menu
    UNION ALL
    SELECT tenant_id, name, permission, page_path AS route_value, '' AS uri_value
    FROM ui_action
    UNION ALL
    SELECT tenant_id, name, permission, '' AS route_value, uri AS uri_value
    FROM api_endpoint
  ) carrier
  JOIN tenant tenant_entity
    ON tenant_entity.id = carrier.tenant_id
  WHERE tenant_entity.code <> 'default'
    AND (
      carrier.name IN ('tenant', 'idempotentOps')
      OR carrier.permission IN ('system:tenant:list', 'idempotent:ops:view')
      OR carrier.route_value IN ('/system/tenant', '/ops/idempotent')
      OR carrier.uri_value IN ('/sys/tenants', '/metrics/idempotent')
    );
")
[[ "${non_default_platform_menu_count:-0}" -eq 0 ]] || fail "非默认租户仍存在平台级菜单资源，038 清理未生效: ${non_default_platform_menu_count}"
echo "  [OK] 038: 非默认租户不存在 tenant / idempotentOps 平台菜单"

# 039 迁移后：下列 IN(...) 中的字符串均为**禁止残留的历史 permission_code**（含 scheduling:read 等旧码），
# 断言 COUNT(*) 必须为 0；并非脚本“仍使用旧码作为期望值”。规范码以 039 changelog 与 scheduling:console:view 等为准。
legacy_scheduling_permission_count=$(query_value "
  SELECT COUNT(*)
  FROM permission
  WHERE permission_code IN (
    'scheduling:dag:list',
    'scheduling:task:list',
    'scheduling:task-type:list',
    'scheduling:dag-run:list',
    'scheduling:audit:list',
    'scheduling:read',
    'scheduling:manage-config',
    'scheduling:operate-run',
    'scheduling:view-audit',
    'scheduling:view-cluster-status'
  );
")
[[ "${legacy_scheduling_permission_count:-0}" -eq 0 ]] || fail "permission 表中仍残留历史调度权限码，039 规范化未生效: ${legacy_scheduling_permission_count}"
echo "  [OK] 039: 历史调度权限码已统一迁移为规范码"

check_permission_by_name() {
  local resource_name="$1"
  local expected_permission="$2"
  local actual_permission
  actual_permission=$(query_value "
    SELECT permission
    FROM menu
    WHERE tenant_id = ${default_tenant_id}
      AND name = '${resource_name}'
    LIMIT 1;
  ")
  [[ "$actual_permission" == "$expected_permission" ]] || fail "默认租户资源 ${resource_name} 权限码异常，期望 ${expected_permission}，实际 ${actual_permission:-<empty>}"
}

for resource_name in schedulingDag schedulingTask schedulingTaskType schedulingDagHistory; do
  check_permission_by_name "$resource_name" "scheduling:console:view"
done
check_permission_by_name "schedulingAudit" "scheduling:audit:view"
echo "  [OK] 039: 默认租户调度菜单载体已使用规范权限码"

legacy_control_plane_uri_count=$(query_value "
  SELECT COUNT(*)
  FROM api_endpoint
  WHERE uri IN ('/api/users', '/api/roles', '/api/resources/menus', '/api/resources');
")
[[ "${legacy_control_plane_uri_count:-0}" -eq 0 ]] || fail "api_endpoint 表中仍残留历史控制面 URI，040 规范化未生效: ${legacy_control_plane_uri_count}"

# 040 最初针对 legacy resource(type=1) 的 uri；125 拆分后控制面「菜单」落在 menu 表（无 uri 列），
# api_endpoint 仅承载 type=3 资源，种子中未必存在 name=user/role 等与菜单同名的行。
# 因此不再按菜单名断言 api_endpoint；保留上方对历史 /api/* 残留否证即可。
echo "  [OK] 040: api_endpoint 无历史控制面 /api/* URI（拆分载体后与菜单同名 endpoint 不作强制）"

echo ""
echo "=== 全部检查通过：035/036/037/038/039/040 调度迁移、默认租户 bootstrap 模板、平台菜单清理、权限与 URI 规范化已在真实 MySQL 上落库 ==="
