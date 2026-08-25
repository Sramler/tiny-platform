#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEBAPP_DIR="$(cd "${SCRIPT_DIR}/../src/main/webapp" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

fail() {
  echo "[web-session-boundary] FAIL: $*" >&2
  exit 1
}

for removed in \
  "${WEBAPP_DIR}/silent-renew.html" \
  "${WEBAPP_DIR}/src/auth/oidc.ts" \
  "${WEBAPP_DIR}/src/auth/silent-renew.ts" \
  "${WEBAPP_DIR}/src/views/OidcCallback.vue"; do
  [[ ! -e "${removed}" ]] || fail "obsolete browser OAuth artifact remains: ${removed#"${WEBAPP_DIR}/"}"
done

if grep -Eq 'oidc-client-ts|"jose"|jwt-decode' "${WEBAPP_DIR}/package.json"; then
  fail "browser OIDC/JWT client dependency remains in frontend dependencies"
fi

if [[ -f "${WEBAPP_DIR}/package-lock.json" ]] \
  && grep -Eq 'oidc-client-ts|"jose"|jwt-decode' "${WEBAPP_DIR}/package-lock.json"; then
  fail "local package-lock still contains removed browser OIDC/JWT dependencies"
fi

for stale_module in oidc-client-ts jose jwt-decode; do
  [[ ! -d "${WEBAPP_DIR}/node_modules/${stale_module}" ]] \
    || fail "installed node_modules still contains removed dependency: ${stale_module}"
done

if grep -RIEq 'VITE_AUTH_SESSION_ONLY|VITE_OIDC_' \
  "${WEBAPP_DIR}/env.d.ts" \
  "${WEBAPP_DIR}/env.example" \
  "${WEBAPP_DIR}/.env.development" \
  "${WEBAPP_DIR}/.env.production" \
  "${WEBAPP_DIR}/playwright.config.ts" \
  "${WEBAPP_DIR}/playwright.real.config.ts"; then
  fail "browser authentication mode/OIDC environment switch remains"
fi

if grep -RIEq 'VITE_AUTH_SESSION_ONLY|VITE_OIDC_|VITE_ENABLE_OIDC_TRACE|E2E_OIDC_CLIENT_ID|E2E_ENABLE_OIDC_TRACE' \
  "${ROOT_DIR}/.github/workflows" \
  "${WEBAPP_DIR}/.env.e2e.example"; then
  fail "workflow or E2E example still injects a removed Vue authentication/OIDC switch"
fi

if find "${WEBAPP_DIR}/src" -type f \( -name '*.ts' -o -name '*.vue' \) \
  ! -name '*.test.ts' ! -path '*/src/test/*' -print0 \
  | xargs -0 grep -EIn 'signinSilent|oidc-client-ts|access[_A-Za-z]*token|refresh[_A-Za-z]*token|oidc[A-Za-z]*authority|Authorization[^[:alnum:]]*=[^\n]*Bearer|Authorization[^\n]*Bearer[[:space:]]*\$' ; then
  fail "browser runtime still contains OAuth token or Bearer injection logic"
fi

if find "${WEBAPP_DIR}/src" -type f -name '*.test.ts' \
  ! -name 'realGlobalSetup.test.ts' -print0 \
  | xargs -0 grep -EIn 'access_token|refresh_token|oidc\.user:' ; then
  fail "component/unit tests still model the Vue user as a browser token holder"
fi

if find "${WEBAPP_DIR}/e2e" -maxdepth 1 -type f -name '*.spec.ts' -print0 \
  | xargs -0 grep -EIn 'access_token|refresh_token|oidc\.user:|buildFakeAccessToken' ; then
  fail "mock browser E2E still seeds OAuth tokens instead of a Session principal response"
fi

grep -Fq "credentials: 'include'" "${WEBAPP_DIR}/src/auth/auth.ts" \
  || fail "Session requests do not explicitly include credentials"
grep -Fq 'ensureCsrfToken' "${WEBAPP_DIR}/src/auth/auth.ts" \
  || fail "Session auth wrapper does not attach CSRF"
grep -Fq 'delete config.headers.Authorization' "${WEBAPP_DIR}/src/utils/request.ts" \
  || fail "Axios boundary does not strip Authorization"

if grep -RIEq 'vue-client|silent-renew|localhost:5173/callback' \
  "${WEBAPP_DIR}/../../main/resources/application.yaml" \
  "${WEBAPP_DIR}/../../main/java/com/tiny/platform/core/oauth/config/RegisteredClientConfig.java"; then
  fail "backend still registers Vue as an OAuth client or synthesizes browser OAuth callbacks"
fi

echo "[web-session-boundary] PASS"
