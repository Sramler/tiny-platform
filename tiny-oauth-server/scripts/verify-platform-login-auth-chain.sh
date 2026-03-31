#!/usr/bin/env bash
# 平台登录 / 租户上下文 / MultiAuthenticationProvider 相关自动化回归（不依赖本地 MySQL）。
#
# 用途：
# - 在修改 PLATFORM 登录、PlatformTenantResolver、TenantContextFilter、密码校验链路后，
#   先跑本脚本再下结论；避免仅凭“代码看起来对”推断浏览器结果。
#
# 可选 Tier2：需同时设置 VERIFY_PLATFORM_LOGIN_E2E=1 与 E2E_DB_PASSWORD（与
#   AuthenticationFlowE2eProfileIntegrationTest 一致）。避免因环境里残留 E2E_DB_PASSWORD
#   而误跑全量 e2e、把“本地库状态”当成脚本失败。
#
# 用法（仓库根目录）:
#   bash tiny-oauth-server/scripts/verify-platform-login-auth-chain.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

TIER1_TESTS="MultiAuthenticationProviderTest,PartialMfaFormLoginIntegrationTest,TenantContextFilterTest,AuthUserResolutionServiceTest"

echo "==> Tier 1: unit / sliced integration (no external DB): ${TIER1_TESTS}"
mvn -q -pl tiny-oauth-server test -Dtest="${TIER1_TESTS}"

if [[ "${VERIFY_PLATFORM_LOGIN_E2E:-}" == "1" ]] && [[ -n "${E2E_DB_PASSWORD:-}" ]]; then
  echo "==> Tier 2: VERIFY_PLATFORM_LOGIN_E2E=1 and E2E_DB_PASSWORD set; running AuthenticationFlowE2eProfileIntegrationTest"
  mvn -q -pl tiny-oauth-server test -Dtest=AuthenticationFlowE2eProfileIntegrationTest
else
  echo "==> Tier 2: skipped (set VERIFY_PLATFORM_LOGIN_E2E=1 and E2E_DB_PASSWORD for full MockMvc e2e profile chain)"
fi

echo "==> verify-platform-login-auth-chain: OK"
