#!/usr/bin/env bash
# 校验 RBAC3 集成测试账号前缀是否命中受控白名单
# 用法：
#   bash tiny-oauth-server/scripts/verify-rbac3-test-user-prefix-whitelist.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TARGET_DIR="$REPO_ROOT/tiny-oauth-server/src/test/java"

if [[ ! -d "$TARGET_DIR" ]]; then
  echo "❌ 未找到测试目录: $TARGET_DIR"
  exit 2
fi

# 受控前缀白名单（必须与 cleanup/verify 脚本保持一致）
ALLOWED_PREFIXES=(
  "rbac3_dryrun_user_"
  "rbac3_enforce_user_"
  "rbac3_violation_obs_user_"
  "rbac3_enforce_obs_user_"
  "rbac3_enforce_allowlist_user_"
)

RAW_MATCHES="$(git -C "$REPO_ROOT" grep -n -E 'setUsername\("rbac3_[^"]+"' -- 'tiny-oauth-server/src/test/java/**/*.java' || true)"

if [[ -z "${RAW_MATCHES//[[:space:]]/}" ]]; then
  echo "✅ 未发现 rbac3_* 测试账号命名。"
  exit 0
fi

PREFIX_LIST="$(printf '%s\n' "$RAW_MATCHES" | sed -E 's/.*setUsername\("([^"]+)".*/\1/' | sort -u)"

echo "=== RBAC3 测试账号前缀白名单校验 ==="
echo "扫描目录: $TARGET_DIR"
echo ""

unknown_count=0

while IFS= read -r found_prefix; do
  [[ -z "$found_prefix" ]] && continue
  example_line="$(printf '%s\n' "$RAW_MATCHES" | grep "setUsername(\"$found_prefix\"" | head -n 1 || true)"
  allowed="false"
  for whitelist_prefix in "${ALLOWED_PREFIXES[@]}"; do
    if [[ "$found_prefix" == "$whitelist_prefix"* ]]; then
      allowed="true"
      break
    fi
  done

  if [[ "$allowed" != "true" ]]; then
    unknown_count=$((unknown_count + 1))
    echo "❌ 未命中白名单前缀: $found_prefix"
    echo "   示例: $example_line"
  fi

  if [[ "$found_prefix" != *"_user_"* ]]; then
    unknown_count=$((unknown_count + 1))
    echo "❌ 不满足 _user_ 结构锚点: $found_prefix"
    echo "   示例: $example_line"
  fi
done <<< "$PREFIX_LIST"

echo ""
if [[ "$unknown_count" -gt 0 ]]; then
  echo "❌ 发现 $unknown_count 处账号命名不符合白名单/结构约束。"
  echo "   请同步更新："
  echo "   1) 测试命名前缀"
  echo "   2) scripts/cleanup-rbac3-test-residual-users.sh"
  echo "   3) scripts/verify-authorization-model-rollout.sh"
  echo "   4) docs/TINY_PLATFORM_TEST_ACCOUNT_NAMING_AND_CLEANUP_RULES.md"
  exit 1
fi

echo "✅ 校验通过：所有 rbac3_* 测试账号命名前缀均在白名单内且满足 _user_ 结构约束。"
