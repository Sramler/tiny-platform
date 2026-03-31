#!/usr/bin/env bash
# 授权模型灰度验证脚本
# 用途：在真实 MySQL 上验证授权模型迁移后的数据一致性，
#      确认 role_assignment / tenant_user 已回填且运行态可用，
#      user_role 已下线，RBAC3 约束表已就绪。
# 使用：在 tiny-oauth-server 目录执行；通过环境变量覆盖数据库连接。

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
VERIFY_DB_PASSWORD="$(read_env VERIFY_DB_PASSWORD E2E_DB_PASSWORD E2E_MYSQL_PASSWORD MYSQL_ROOT_PASSWORD || true)"
VERIFY_DB_NAME="${VERIFY_DB_NAME:-tiny_web}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

MYSQL_CMD=("$MYSQL_BIN" -h "$VERIFY_DB_HOST" -P "$VERIFY_DB_PORT" -u "$VERIFY_DB_USER" "$VERIFY_DB_NAME" -N -s)
if [[ -n "$VERIFY_DB_PASSWORD" ]]; then
  export MYSQL_PWD="$VERIFY_DB_PASSWORD"
fi

PASS=0
FAIL=0
WARN=0

pass() { echo "  PASS: $1"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL: $1"; FAIL=$((FAIL + 1)); }
warn() { echo "  WARN: $1"; WARN=$((WARN + 1)); }
query() { "${MYSQL_CMD[@]}" -e "$1" 2>/dev/null; }
table_exists() {
  query "SELECT 1 FROM information_schema.tables WHERE table_schema='$VERIFY_DB_NAME' AND table_name='$1'" | grep -q 1
}
column_exists() {
  query "SELECT 1 FROM information_schema.columns WHERE table_schema='$VERIFY_DB_NAME' AND table_name='$1' AND column_name='$2'" | grep -q 1
}
index_exists() {
  query "SELECT 1 FROM information_schema.statistics WHERE table_schema='$VERIFY_DB_NAME' AND table_name='$1' AND index_name='$2'" | grep -q 1
}

echo "=== 授权模型灰度验证 ==="
echo "数据库: $VERIFY_DB_HOST:$VERIFY_DB_PORT/$VERIFY_DB_NAME"
echo ""

if ! query "SELECT 1" | grep -q 1; then
  echo "❌ 无法连接数据库或认证失败：$VERIFY_DB_HOST:$VERIFY_DB_PORT/$VERIFY_DB_NAME"
  echo "   请确认 VERIFY_DB_USER / VERIFY_DB_PASSWORD（或 E2E_DB_PASSWORD / E2E_MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD）配置正确"
  exit 2
fi

# ─── 1. 表结构检查 ───
echo "── 1. 表结构检查 ──"

if query "SELECT 1 FROM information_schema.tables WHERE table_schema='$VERIFY_DB_NAME' AND table_name='user_role'" | grep -q 1; then
  fail "user_role 表仍存在（043/047 迁移未执行）"
else
  pass "user_role 表已下线"
fi

if query "SELECT 1 FROM information_schema.tables WHERE table_schema='$VERIFY_DB_NAME' AND table_name='user_role_legacy'" | grep -q 1; then
  fail "user_role_legacy 表仍存在（047-drop 未执行）"
else
  pass "user_role_legacy 已清理"
fi

for tbl in tenant_user role_assignment role_hierarchy role_mutex role_prerequisite role_cardinality \
           role_constraint_violation_log organization_unit user_unit role_data_scope role_data_scope_item \
           authorization_audit_log user_session; do
  if query "SELECT 1 FROM information_schema.tables WHERE table_schema='$VERIFY_DB_NAME' AND table_name='$tbl'" | grep -q 1; then
    pass "$tbl 表已创建"
  else
    fail "$tbl 表不存在"
  fi
done

echo ""

# ─── 2. 数据一致性检查 ───
echo "── 2. 数据一致性 ──"

if table_exists "user" && table_exists "tenant_user" && table_exists "role_assignment"; then
  user_count=$(query "SELECT COUNT(*) FROM user")
  tu_user_count=$(query "SELECT COUNT(DISTINCT user_id) FROM tenant_user WHERE status='ACTIVE'")
  ra_user_count=$(query "SELECT COUNT(DISTINCT principal_id) FROM role_assignment WHERE principal_type='USER' AND status='ACTIVE'")

  echo "  用户总数: $user_count"
  echo "  tenant_user 活跃用户: $tu_user_count"
  echo "  role_assignment 有赋权用户: $ra_user_count"

  if [[ "$tu_user_count" -ge "$user_count" ]]; then
    pass "tenant_user 覆盖率 >= 用户总数"
  else
    orphan_count=$((user_count - tu_user_count))
    rbac3_orphan_count=$(query "
      SELECT COUNT(*)
      FROM user u
      WHERE u.id NOT IN (
        SELECT tu.user_id
        FROM tenant_user tu
        WHERE tu.status='ACTIVE'
      )
        AND (
             u.username LIKE 'rbac3_dryrun_user_%'
          OR u.username LIKE 'rbac3_enforce_user_%'
          OR u.username LIKE 'rbac3_violation_obs_user_%'
          OR u.username LIKE 'rbac3_enforce_obs_user_%'
          OR u.username LIKE 'rbac3_enforce_allowlist_user_%'
        )
    ")
    if [[ "$rbac3_orphan_count" -eq "$orphan_count" && "$orphan_count" -gt 0 ]]; then
      warn "有 $orphan_count 个用户无 tenant_user membership，均为共享 E2E 库中的 RBAC3 集成测试残留用户"
      echo "    -> 建议执行: VERIFY_DB_PASSWORD='***' bash tiny-oauth-server/scripts/cleanup-rbac3-test-residual-users.sh --apply"
    elif [[ "$rbac3_orphan_count" -gt 0 ]]; then
      non_test_orphan_count=$((orphan_count - rbac3_orphan_count))
      warn "有 $orphan_count 个用户无 tenant_user membership，其中 $rbac3_orphan_count 个为 RBAC3 集成测试残留，另有 $non_test_orphan_count 个需人工排查"
      echo "    -> 可先清理测试残留: VERIFY_DB_PASSWORD='***' bash tiny-oauth-server/scripts/cleanup-rbac3-test-residual-users.sh --apply"
    else
      warn "有 $orphan_count 个用户无 tenant_user membership（历史用户或已退出）"
    fi
  fi

  orphan_membership=$(query "
    SELECT COUNT(*)
    FROM tenant_user tu
    WHERE tu.status='ACTIVE'
      AND NOT EXISTS (
        SELECT 1
        FROM role_assignment ra
        WHERE ra.principal_type='USER'
          AND ra.principal_id=tu.user_id
          AND ra.tenant_id=tu.tenant_id
          AND ra.status='ACTIVE'
      )
  ")
  if [[ "$orphan_membership" -eq 0 ]]; then
    pass "所有活跃 membership 均有 role_assignment"
  else
    demo_membership_without_role=$(query "
      SELECT COUNT(*)
      FROM tenant_user tu
      JOIN user u ON u.id = tu.user_id
      WHERE tu.status='ACTIVE'
        AND tu.tenant_id = 1
        AND u.username REGEXP '^user_[0-9]{4}$'
        AND NOT EXISTS (
          SELECT 1
          FROM role_assignment ra
          WHERE ra.principal_type='USER'
            AND ra.principal_id=tu.user_id
            AND ra.tenant_id=tu.tenant_id
            AND ra.status='ACTIVE'
        )
    ")
    if [[ "$demo_membership_without_role" -eq "$orphan_membership" && "$orphan_membership" -gt 0 ]]; then
      warn "$orphan_membership 个活跃 membership 无 role_assignment，均为默认租户 demo 用户（user_0001~）的零角色运营态"
      echo "    -> 如需整批移除 user_NNNN demo 账号及关联: VERIFY_DB_PASSWORD='***' VERIFY_DEMO_USERS_CLEANUP_CONFIRM=YES bash tiny-oauth-server/scripts/cleanup-demo-users-user-xxxx.sh --apply"
    elif [[ "$demo_membership_without_role" -gt 0 ]]; then
      non_demo_membership_without_role=$((orphan_membership - demo_membership_without_role))
      warn "$orphan_membership 个活跃 membership 无 role_assignment，其中 $demo_membership_without_role 个为默认租户 demo 用户，另有 $non_demo_membership_without_role 个需人工排查"
      echo "    -> demo 部分可清理: VERIFY_DEMO_USERS_CLEANUP_CONFIRM=YES bash tiny-oauth-server/scripts/cleanup-demo-users-user-xxxx.sh --apply"
    else
      warn "$orphan_membership 个 membership 无 role_assignment（新用户/待分配角色）"
    fi
  fi
else
  warn "tenant_user / role_assignment 相关表未就绪，跳过数据一致性检查"
fi

echo ""

# ─── 2.5 平台登录作用域检查 ───
echo "── 2.5 平台作用域赋权 ──"

if table_exists "user" && table_exists "role_assignment"; then
  platform_admin_id=$(query "SELECT id FROM user WHERE username='platform_admin' LIMIT 1")
  if [[ -z "${platform_admin_id:-}" ]]; then
    warn "platform_admin 不存在，跳过平台作用域赋权检查"
  else
    platform_tenant_membership_count=$(query "SELECT COUNT(*) FROM tenant_user WHERE user_id=${platform_admin_id}" 2>/dev/null || echo "0")
    platform_scope_assignment_count=$(query "
      SELECT COUNT(*)
      FROM role_assignment ra
      WHERE ra.principal_type='USER'
        AND ra.principal_id=${platform_admin_id}
        AND ra.scope_type='PLATFORM'
        AND ra.tenant_id IS NULL
        AND ra.scope_id IS NULL
        AND ra.status='ACTIVE'
    ")

    if [[ "${platform_scope_assignment_count:-0}" -gt 0 ]]; then
      pass "platform_admin 具备 PLATFORM scope 有效赋权"
    else
      fail "platform_admin 缺少 PLATFORM scope 有效赋权"
    fi

    if [[ "${platform_tenant_membership_count:-0}" -eq 0 ]]; then
      pass "platform_admin 未绑定 tenant_user membership（符合平台无租户上下文）"
    else
      fail "platform_admin 仍绑定 ${platform_tenant_membership_count} 条 tenant_user membership（平台账号必须去租户化）"
    fi

    if table_exists "role"; then
      wrong_platform_role_bind=$(query "
        SELECT COUNT(*)
        FROM role_assignment ra
        INNER JOIN role r ON r.id = ra.role_id
        WHERE ra.principal_type='USER'
          AND ra.principal_id=${platform_admin_id}
          AND ra.scope_type='PLATFORM'
          AND ra.tenant_id IS NULL
          AND ra.status='ACTIVE'
          AND r.tenant_id IS NOT NULL
      ")
      if [[ "${wrong_platform_role_bind:-0}" -eq 0 ]]; then
        pass "platform_admin 的 PLATFORM 赋权未指向租户级 role 行（与平台模板解析一致）"
      else
        fail "platform_admin 存在 ${wrong_platform_role_bind} 条 PLATFORM 赋权指向租户级 role（应改为平台模板 ROLE_PLATFORM_ADMIN，见 ensure-platform-admin.sh）"
      fi

      strict_platform_admin_bind=$(query "
        SELECT COUNT(*)
        FROM role_assignment ra
        INNER JOIN role r ON r.id = ra.role_id
        WHERE ra.principal_type='USER'
          AND ra.principal_id=${platform_admin_id}
          AND ra.scope_type='PLATFORM'
          AND ra.tenant_id IS NULL
          AND ra.scope_id IS NULL
          AND ra.status='ACTIVE'
          AND r.tenant_id IS NULL
          AND r.code='ROLE_PLATFORM_ADMIN'
      ")
      if [[ "${strict_platform_admin_bind:-0}" -eq 1 ]]; then
        pass "platform_admin 严格绑定平台模板 ROLE_PLATFORM_ADMIN"
      else
        fail "platform_admin 未严格绑定平台模板 ROLE_PLATFORM_ADMIN（当前命中=${strict_platform_admin_bind:-0}）"
      fi
    fi
  fi
else
  warn "user / role_assignment 表未就绪，跳过平台作用域赋权检查"
fi

echo ""

# ─── 3. 角色收口（严格前推） ───
echo "── 3. 角色收口（严格前推） ──"

if table_exists "role"; then
  tenant_legacy_admin_count=$(query "
    SELECT COUNT(*)
    FROM role
    WHERE tenant_id IS NOT NULL
      AND code = 'ROLE_ADMIN'
  ")
  if [[ "${tenant_legacy_admin_count:-0}" -eq 0 ]]; then
    pass "tenant 侧已无 ROLE_ADMIN 残留（已统一到 ROLE_TENANT_ADMIN）"
  else
    fail "tenant 侧仍存在 ${tenant_legacy_admin_count} 条 ROLE_ADMIN 残留（违反严格收口）"
  fi

  tenant_admin_count=$(query "
    SELECT COUNT(*)
    FROM role
    WHERE tenant_id IS NOT NULL
      AND code = 'ROLE_TENANT_ADMIN'
  ")
  if [[ "${tenant_admin_count:-0}" -ge 1 ]]; then
    pass "tenant 侧 ROLE_TENANT_ADMIN 已存在"
  else
    fail "tenant 侧缺少 ROLE_TENANT_ADMIN"
  fi
else
  warn "role 表未就绪，跳过角色收口检查"
fi

echo ""

# ─── 4. 字典平台语义检查 ───
echo "── 4. 字典平台语义 ──"

if table_exists "dict_type"; then
  dict_type_zero=$(query "SELECT COUNT(*) FROM dict_type WHERE tenant_id = 0")
  if [[ "$dict_type_zero" -eq 0 ]]; then
    pass "dict_type 无 tenant_id=0 残留（已迁移到 NULL）"
  else
    fail "dict_type 仍有 $dict_type_zero 条 tenant_id=0 记录"
  fi
else
  warn "dict_type 表不存在，跳过 tenant_id=0 检查"
fi

if table_exists "dict_item"; then
  dict_item_zero=$(query "SELECT COUNT(*) FROM dict_item WHERE tenant_id = 0")
  if [[ "$dict_item_zero" -eq 0 ]]; then
    pass "dict_item 无 tenant_id=0 残留"
  else
    fail "dict_item 仍有 $dict_item_zero 条 tenant_id=0 记录"
  fi
else
  warn "dict_item 表不存在，跳过 tenant_id=0 检查"
fi

echo ""

# ─── 5. RBAC3 约束表数据 ───
echo "── 5. RBAC3 约束表 ──"

for constraint_tbl in role_hierarchy role_mutex role_prerequisite role_cardinality; do
  if table_exists "$constraint_tbl"; then
    cnt=$(query "SELECT COUNT(*) FROM $constraint_tbl" 2>/dev/null || echo "N/A")
    echo "  $constraint_tbl: $cnt 条规则"
  else
    echo "  $constraint_tbl: N/A（表不存在）"
  fi
done

if table_exists "role_constraint_violation_log"; then
  violation_count=$(query "SELECT COUNT(*) FROM role_constraint_violation_log" 2>/dev/null || echo "N/A")
  echo "  violation_log: $violation_count 条记录"
else
  echo "  violation_log: N/A（表不存在）"
fi

echo ""

# ─── 6. 管理端数据范围与新增列检查 ───
echo "── 6. 数据范围 / 会话 / 导出扩展 ──"

if table_exists "user_session"; then
  if index_exists "user_session" "uk_user_session_session_id"; then
    pass "user_session 唯一索引 uk_user_session_session_id 已存在"
  else
    fail "user_session 缺少唯一索引 uk_user_session_session_id"
  fi

  if index_exists "user_session" "idx_user_session_last_seen"; then
    pass "user_session 最近活跃索引已存在"
  else
    fail "user_session 缺少 idx_user_session_last_seen"
  fi
else
  warn "user_session 表不存在，跳过索引检查"
fi

if table_exists "export_task"; then
  column_exists "export_task" "tenant_id" && pass "export_task.tenant_id 已存在" || fail "export_task 缺少 tenant_id"
  column_exists "export_task" "file_size_bytes" && pass "export_task.file_size_bytes 已存在" || fail "export_task 缺少 file_size_bytes"
  index_exists "export_task" "idx_export_task_tenant" && pass "export_task tenant 索引已存在" || fail "export_task 缺少 idx_export_task_tenant"
else
  warn "export_task 表不存在，跳过导出扩展检查"
fi

if table_exists "role_data_scope" && table_exists "role"; then
  admin_data_scope_modules=$(query "
    SELECT COUNT(DISTINCT module)
    FROM role_data_scope rds
    JOIN role role_entity
      ON role_entity.id = rds.role_id
     AND role_entity.tenant_id = rds.tenant_id
    WHERE role_entity.tenant_id = 1
      AND role_entity.code = 'ROLE_TENANT_ADMIN'
      AND rds.access_type = 'READ'
      AND rds.scope_type = 'ALL'
      AND rds.module IN ('user', 'resource', 'menu', 'org', 'scheduling', 'export', 'dict');
  ")
  if [[ "${admin_data_scope_modules:-0}" -ge 7 ]]; then
    pass "ROLE_TENANT_ADMIN 已回填核心模块 READ=ALL 数据范围"
  else
    warn "ROLE_TENANT_ADMIN READ=ALL 数据范围模块数不足 7，当前为 ${admin_data_scope_modules:-0}"
  fi
else
  warn "role_data_scope 尚未就绪，跳过 ROLE_TENANT_ADMIN 数据范围 seed 检查"
fi

echo ""

# ─── 7. 权限码 seed 完整性 ───
echo "── 7. 权限码 seed ──"

if table_exists "resource" && table_exists "role" && table_exists "role_permission" && table_exists "permission"; then
  resource_permission_count=$(query "SELECT COUNT(DISTINCT permission) FROM resource WHERE tenant_id = 1 AND permission IS NOT NULL AND permission != ''")
  role_admin_permission_count=$(query "SELECT COUNT(DISTINCT p.permission_code) FROM role_permission rp JOIN permission p ON p.id = rp.permission_id AND p.normalized_tenant_id = rp.normalized_tenant_id JOIN role rl ON rl.id = rp.role_id AND rl.tenant_id = 1 AND rl.tenant_id <=> rp.tenant_id WHERE rl.code='ROLE_TENANT_ADMIN' AND rp.tenant_id = 1 AND p.permission_code IS NOT NULL AND p.permission_code != ''")

  echo "  系统权限码总数: $resource_permission_count"
  echo "  ROLE_TENANT_ADMIN 已授予: $role_admin_permission_count"

  if [[ "$role_admin_permission_count" -ge "$resource_permission_count" ]]; then
    pass "ROLE_TENANT_ADMIN 已覆盖全部权限码"
  else
    gap=$((resource_permission_count - role_admin_permission_count))
    warn "ROLE_TENANT_ADMIN 缺少 $gap 个权限码（可能是新增未 seed）"
  fi

  if column_exists "resource" "required_permission_id"; then
    unbound_resource_permission_count=$(query "
      SELECT COUNT(*)
      FROM resource
      WHERE permission IS NOT NULL
        AND TRIM(permission) <> ''
        AND required_permission_id IS NULL
    ")
    if [[ "${unbound_resource_permission_count:-0}" -eq 0 ]]; then
      pass "resource.required_permission_id 已覆盖全部非空 permission 载体"
    else
      fail "仍有 ${unbound_resource_permission_count:-0} 条 resource.permission 未绑定 required_permission_id"
    fi
  else
    fail "resource.required_permission_id 缺失，当前库未完成 permission 载体显式绑定"
  fi

  if table_exists "menu" && table_exists "ui_action" && table_exists "api_endpoint"; then
    menu_projection_count=$(query "SELECT COUNT(*) FROM menu")
    menu_resource_projection_count=$(query "SELECT COUNT(*) FROM resource WHERE type IN (0, 1)")
    ui_action_projection_count=$(query "SELECT COUNT(*) FROM ui_action")
    ui_action_resource_projection_count=$(query "SELECT COUNT(*) FROM resource WHERE type = 2")
    api_endpoint_projection_count=$(query "SELECT COUNT(*) FROM api_endpoint")
    api_endpoint_resource_projection_count=$(query "SELECT COUNT(*) FROM resource WHERE type = 3")

    if [[ "${menu_projection_count:-0}" -eq "${menu_resource_projection_count:-0}" ]]; then
      pass "menu 载体表已与 resource(type=目录/菜单) 对齐"
    else
      fail "menu 载体表与 resource(type=目录/菜单) 数量不一致（menu=${menu_projection_count:-0}, resource=${menu_resource_projection_count:-0}）"
    fi

    if [[ "${ui_action_projection_count:-0}" -eq "${ui_action_resource_projection_count:-0}" ]]; then
      pass "ui_action 载体表已与 resource(type=按钮) 对齐"
    else
      fail "ui_action 载体表与 resource(type=按钮) 数量不一致（ui_action=${ui_action_projection_count:-0}, resource=${ui_action_resource_projection_count:-0}）"
    fi

    if [[ "${api_endpoint_projection_count:-0}" -eq "${api_endpoint_resource_projection_count:-0}" ]]; then
      pass "api_endpoint 载体表已与 resource(type=接口) 对齐"
    else
      fail "api_endpoint 载体表与 resource(type=接口) 数量不一致（api_endpoint=${api_endpoint_projection_count:-0}, resource=${api_endpoint_resource_projection_count:-0}）"
    fi

    unbound_menu_permission_count=$(query "
      SELECT COUNT(*)
      FROM menu
      WHERE permission IS NOT NULL
        AND TRIM(permission) <> ''
        AND required_permission_id IS NULL
    ")
    unbound_ui_action_permission_count=$(query "
      SELECT COUNT(*)
      FROM ui_action
      WHERE permission IS NOT NULL
        AND TRIM(permission) <> ''
        AND required_permission_id IS NULL
    ")
    unbound_api_endpoint_permission_count=$(query "
      SELECT COUNT(*)
      FROM api_endpoint
      WHERE permission IS NOT NULL
        AND TRIM(permission) <> ''
        AND required_permission_id IS NULL
    ")

    if [[ "${unbound_menu_permission_count:-0}" -eq 0 && "${unbound_ui_action_permission_count:-0}" -eq 0 && "${unbound_api_endpoint_permission_count:-0}" -eq 0 ]]; then
      pass "split carrier tables 已覆盖全部非空 permission 载体绑定"
    else
      fail "split carrier tables 仍有未绑定 required_permission_id 的载体（menu=${unbound_menu_permission_count:-0}, ui_action=${unbound_ui_action_permission_count:-0}, api_endpoint=${unbound_api_endpoint_permission_count:-0}）"
    fi

    if table_exists "menu_permission_requirement" && table_exists "ui_action_permission_requirement" && table_exists "api_endpoint_permission_requirement"; then
      missing_menu_compatibility_requirement_count=$(query "
        SELECT COUNT(*)
        FROM menu m
        LEFT JOIN menu_permission_requirement r
          ON r.menu_id = m.id
         AND r.requirement_group = 0
         AND r.negated = 0
         AND r.permission_id = m.required_permission_id
        WHERE m.required_permission_id IS NOT NULL
          AND r.id IS NULL
      ")
      missing_ui_action_compatibility_requirement_count=$(query "
        SELECT COUNT(*)
        FROM ui_action a
        LEFT JOIN ui_action_permission_requirement r
          ON r.ui_action_id = a.id
         AND r.requirement_group = 0
         AND r.negated = 0
         AND r.permission_id = a.required_permission_id
        WHERE a.required_permission_id IS NOT NULL
          AND r.id IS NULL
      ")
      missing_api_endpoint_compatibility_requirement_count=$(query "
        SELECT COUNT(*)
        FROM api_endpoint e
        LEFT JOIN api_endpoint_permission_requirement r
          ON r.api_endpoint_id = e.id
         AND r.requirement_group = 0
         AND r.negated = 0
         AND r.permission_id = e.required_permission_id
        WHERE e.required_permission_id IS NOT NULL
          AND r.id IS NULL
      ")

      if [[ "${missing_menu_compatibility_requirement_count:-0}" -eq 0 && "${missing_ui_action_compatibility_requirement_count:-0}" -eq 0 && "${missing_api_endpoint_compatibility_requirement_count:-0}" -eq 0 ]]; then
        pass "carrier requirement 兼容组已与 required_permission_id 对齐"
      else
        fail "carrier requirement 兼容组存在缺口（menu=${missing_menu_compatibility_requirement_count:-0}, ui_action=${missing_ui_action_compatibility_requirement_count:-0}, api_endpoint=${missing_api_endpoint_compatibility_requirement_count:-0}）"
      fi
    else
      fail "carrier requirement tables 未全部就绪（menu/ui_action/api_endpoint requirement）"
    fi
  else
    warn "menu/ui_action/api_endpoint 载体表未全部就绪，跳过 split carrier rollout 检查"
  fi
else
  warn "resource / role / role_permission / permission 未就绪；当前 canonical rollout 检查不再接受 role_resource 作为权限主关系"
fi

echo ""

# ─── 8. user.tenant_id 退场检查 ───
echo "── 8. user.tenant_id 退场 ──"

if table_exists "user"; then
  user_with_tenant_id=$(query "SELECT COUNT(*) FROM user WHERE tenant_id IS NOT NULL")
  orphan_user_tenant_id=$(query "
    SELECT COUNT(*)
    FROM user u
    WHERE u.tenant_id IS NOT NULL
      AND NOT EXISTS (
        SELECT 1
        FROM tenant_user tu
        WHERE tu.user_id = u.id
          AND tu.tenant_id = u.tenant_id
          AND tu.status = 'ACTIVE'
      )
  ")
  echo "  兼容 tenant_id 非空用户: $user_with_tenant_id"
  if [[ "$orphan_user_tenant_id" -eq 0 ]]; then
    pass "user.tenant_id 兼容值已与 active membership 对齐"
  else
    warn "有 $orphan_user_tenant_id 条 user.tenant_id 兼容值未匹配 active membership（历史脏数据待清理）"
  fi
else
  warn "user 表不存在，跳过 tenant_id 退场检查"
fi

echo ""

# ─── 8. Liquibase changelog 完整性 ───
echo "── 8. 关键迁移检查 ──"

if table_exists "DATABASECHANGELOG"; then
  for changeset_id in add-tenant-user-and-role-assignment \
                       043-rename-user-role-legacy 047-drop-user-role-legacy \
                       061-organization-unit 062-user-unit 063-role-assignment-extend-org-dept-scope \
                       081-authorization-audit-log 082-create-user-session 083-seed-admin-data-scope \
                       101-dict-platform-tenant-null 104-export-task-tenant-and-file-size \
                       106-repair-default-tenant-builtins-role-assignment \
                       105-repair-default-tenant-role-admin-and-legacy-tenant-id \
                       123-add-resource-required-permission-id \
                       124-backfill-missing-resource-permission-bindings \
                       125-split-resource-carrier-tables \
                       126-carrier-permission-requirement-tables; do
    if query "SELECT 1 FROM DATABASECHANGELOG WHERE id='$changeset_id'" 2>/dev/null | grep -q 1; then
      pass "迁移 $changeset_id 已执行"
    else
      warn "迁移 $changeset_id 未找到（可能 ID 不完全匹配）"
    fi
  done
else
  warn "DATABASECHANGELOG 表不存在，跳过关键迁移检查"
fi

echo ""
echo "─────────────────────────────────────"
echo "结果: PASS=$PASS  FAIL=$FAIL  WARN=$WARN"
if [[ "$FAIL" -gt 0 ]]; then
  echo "❌ 有 $FAIL 项失败，请修复后重试"
  exit 1
elif [[ "$WARN" -gt 0 ]]; then
  echo "⚠️ 有 $WARN 项警告，建议关注"
  exit 0
else
  echo "✅ 全部通过"
  exit 0
fi
