#!/usr/bin/env bash
# 统一审计/治理 tiny-platform 测试数据库残留：
# - RBAC3 集成测试临时用户
# - RBAC3 孤儿测试角色及其约束表
# - real-link E2E 派生租户 / e2e_init_* / stale membership
#
# 默认：audit only（不改数据）
# 可选：
#   --apply
#   --fail-on-stale
#   --include-active
#   --target-generated-tenant-codes code1,code2

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  bash tiny-oauth-server/scripts/verify-test-db-residuals.sh
  bash tiny-oauth-server/scripts/verify-test-db-residuals.sh --apply
  bash tiny-oauth-server/scripts/verify-test-db-residuals.sh --fail-on-stale
  bash tiny-oauth-server/scripts/verify-test-db-residuals.sh --apply --fail-on-stale
  bash tiny-oauth-server/scripts/verify-test-db-residuals.sh --apply --include-active
  bash tiny-oauth-server/scripts/verify-test-db-residuals.sh --apply --target-generated-tenant-codes bench-1m-t
EOF
}

APPLY_CHANGES="false"
FAIL_ON_STALE="false"
INCLUDE_ACTIVE="false"
TARGET_GENERATED_TENANT_CODES=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply )
      APPLY_CHANGES="true"
      shift
      ;;
    --fail-on-stale )
      FAIL_ON_STALE="true"
      shift
      ;;
    --include-active )
      INCLUDE_ACTIVE="true"
      shift
      ;;
    --target-generated-tenant-codes )
      if [[ $# -lt 2 ]]; then
        usage
        exit 2
      fi
      TARGET_GENERATED_TENANT_CODES="$2"
      shift 2
      ;;
    * )
      usage
      exit 2
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

RBAC3_USER_SCRIPT="$SCRIPT_DIR/cleanup-rbac3-test-residual-users.sh"
RBAC3_ROLE_SCRIPT="$SCRIPT_DIR/cleanup-rbac3-test-residual-roles.sh"
REAL_E2E_SCRIPT="$SCRIPT_DIR/verify-real-e2e-derived-assets.sh"

if [[ ! -x "$RBAC3_USER_SCRIPT" ]]; then
  chmod +x "$RBAC3_USER_SCRIPT"
fi
if [[ ! -x "$RBAC3_ROLE_SCRIPT" ]]; then
  chmod +x "$RBAC3_ROLE_SCRIPT"
fi
if [[ ! -x "$REAL_E2E_SCRIPT" ]]; then
  chmod +x "$REAL_E2E_SCRIPT"
fi

MODULE_RESULTS=()
HAS_STALE="false"
HAS_ENV_GAP="false"
HAS_RUNTIME_ERROR="false"

run_module() {
  local label="$1"
  shift
  local rc

  echo ""
  echo ">>> ${label}"
  set +e
  "$@"
  rc=$?
  set -e

  MODULE_RESULTS+=("${label}:${rc}")
  case "$rc" in
    0 )
      ;;
    1 )
      HAS_STALE="true"
      ;;
    2 )
      HAS_ENV_GAP="true"
      ;;
    * )
      HAS_RUNTIME_ERROR="true"
      ;;
  esac
}

rbac3_user_args=()
rbac3_role_args=()
real_e2e_args=()
if [[ "$APPLY_CHANGES" == "true" ]]; then
  rbac3_user_args+=("--apply")
  rbac3_role_args+=("--apply")
  real_e2e_args+=("--apply")
fi
if [[ "$FAIL_ON_STALE" == "true" ]]; then
  rbac3_user_args+=("--fail-on-stale")
  rbac3_role_args+=("--fail-on-stale")
  real_e2e_args+=("--fail-on-stale")
fi
if [[ "$INCLUDE_ACTIVE" == "true" ]]; then
  rbac3_user_args+=("--include-active")
fi
if [[ -n "${TARGET_GENERATED_TENANT_CODES:-}" ]]; then
  real_e2e_args+=("--target-generated-tenant-codes" "$TARGET_GENERATED_TENANT_CODES")
fi

echo "=== 测试数据库残留治理基线 ==="
echo "工作目录: $ROOT_DIR"
echo "apply: $APPLY_CHANGES"
echo "fail-on-stale: $FAIL_ON_STALE"
echo "include-active: $INCLUDE_ACTIVE"
if [[ -n "${TARGET_GENERATED_TENANT_CODES:-}" ]]; then
  echo "target-generated-tenant-codes: $TARGET_GENERATED_TENANT_CODES"
fi

if [[ "${#rbac3_user_args[@]}" -gt 0 ]]; then
  run_module "RBAC3 测试残留用户" bash "$RBAC3_USER_SCRIPT" "${rbac3_user_args[@]}"
else
  run_module "RBAC3 测试残留用户" bash "$RBAC3_USER_SCRIPT"
fi

if [[ "${#rbac3_role_args[@]}" -gt 0 ]]; then
  run_module "RBAC3 测试残留角色" bash "$RBAC3_ROLE_SCRIPT" "${rbac3_role_args[@]}"
else
  run_module "RBAC3 测试残留角色" bash "$RBAC3_ROLE_SCRIPT"
fi

if [[ "${#real_e2e_args[@]}" -gt 0 ]]; then
  run_module "real-link 派生资产" bash "$REAL_E2E_SCRIPT" "${real_e2e_args[@]}"
else
  run_module "real-link 派生资产" bash "$REAL_E2E_SCRIPT"
fi

echo ""
echo "=== 汇总 ==="
for result in "${MODULE_RESULTS[@]}"; do
  printf '  - %s\n' "$result"
done

if [[ "$HAS_RUNTIME_ERROR" == "true" ]]; then
  echo "FAIL: 存在脚本执行错误"
  exit 1
fi

if [[ "$HAS_ENV_GAP" == "true" ]]; then
  echo "FAIL: 存在环境前置缺口（例如数据库连接/凭证缺失）"
  exit 2
fi

if [[ "$HAS_STALE" == "true" ]]; then
  echo "FAIL: 仍存在测试数据库残留"
  exit 1
fi

echo "✅ 测试数据库残留与治理基线一致"
