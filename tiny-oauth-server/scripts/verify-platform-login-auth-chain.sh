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
#
# 当 E2E_DB_PASSWORD 未导出时，若存在 Playwright 的 .env.e2e.local，则自动 source，
# 以便 VERIFY_PLATFORM_LOGIN_E2E=1 的 Tier2 与 real-link 共用同一套本地身份，无需手工 export。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

WEBAPP_E2E_LOCAL="${ROOT_DIR}/tiny-oauth-server/src/main/webapp/.env.e2e.local"
if [[ -z "${E2E_DB_PASSWORD:-}" ]] && [[ -f "${WEBAPP_E2E_LOCAL}" ]]; then
  # shellcheck disable=SC1090
  set -a
  source "${WEBAPP_E2E_LOCAL}"
  set +a
fi

TIER1_TESTS="MultiAuthenticationProviderTest,PartialMfaFormLoginIntegrationTest,TenantContextFilterTest,AuthUserResolutionServiceTest"

echo "==> Tier 1: unit / sliced integration (no external DB): ${TIER1_TESTS}"
mvn -q -pl tiny-oauth-server test -Dtest="${TIER1_TESTS}"

if [[ "${VERIFY_PLATFORM_LOGIN_E2E:-}" == "1" ]] && [[ -n "${E2E_DB_PASSWORD:-}" ]]; then
  ENSURE_SCRIPT="${ROOT_DIR}/tiny-oauth-server/scripts/e2e/ensure-scheduling-e2e-auth.sh"
  if [[ -f "${ENSURE_SCRIPT}" ]] && [[ -n "${E2E_TENANT_CODE:-}" ]] && [[ -n "${E2E_USERNAME:-}" ]] && [[ -n "${E2E_PASSWORD:-}" ]] && [[ -n "${E2E_TOTP_SECRET:-}" ]]; then
    echo "==> Tier 2 prep: ensure-scheduling-e2e-auth.sh（将 .env 中的主身份口令/TOTP 幂等写入 DB）"
    bash "${ENSURE_SCRIPT}"
  else
    echo "==> Tier 2 prep: skip ensure（缺少 E2E_TENANT_CODE/USERNAME/PASSWORD/TOTP_SECRET 或脚本不存在）"
  fi
  echo "==> Tier 2: VERIFY_PLATFORM_LOGIN_E2E=1 and E2E_DB_PASSWORD set; running AuthenticationFlowE2eProfileIntegrationTest"
  mvn -q -pl tiny-oauth-server test -Dtest=AuthenticationFlowE2eProfileIntegrationTest
else
  echo "==> Tier 2: skipped (set VERIFY_PLATFORM_LOGIN_E2E=1 and E2E_DB_PASSWORD for full MockMvc e2e profile chain)"
fi

echo "==> verify-platform-login-auth-chain: OK"
