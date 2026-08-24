#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WEBAPP_DIR="$(cd "${SCRIPT_DIR}/../src/main/webapp" && pwd)"

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

if grep -RIEq 'VITE_AUTH_SESSION_ONLY|VITE_OIDC_' \
  "${WEBAPP_DIR}/env.d.ts" \
  "${WEBAPP_DIR}/env.example" \
  "${WEBAPP_DIR}/.env.development" \
  "${WEBAPP_DIR}/.env.production" \
  "${WEBAPP_DIR}/playwright.config.ts" \
  "${WEBAPP_DIR}/playwright.real.config.ts"; then
  fail "browser authentication mode/OIDC environment switch remains"
fi

if find "${WEBAPP_DIR}/src" -type f \( -name '*.ts' -o -name '*.vue' \) \
  ! -name '*.test.ts' ! -path '*/src/test/*' -print0 \
  | xargs -0 grep -EIn 'signinSilent|oidc-client-ts|access_token|refresh_token|Authorization[^[:alnum:]]*=[^\n]*Bearer|Authorization[^\n]*Bearer[[:space:]]*\$' ; then
  fail "browser runtime still contains OAuth token or Bearer injection logic"
fi

grep -Fq "credentials: 'include'" "${WEBAPP_DIR}/src/auth/auth.ts" \
  || fail "Session requests do not explicitly include credentials"
grep -Fq 'ensureCsrfToken' "${WEBAPP_DIR}/src/auth/auth.ts" \
  || fail "Session auth wrapper does not attach CSRF"
grep -Fq 'delete config.headers.Authorization' "${WEBAPP_DIR}/src/utils/request.ts" \
  || fail "Axios boundary does not strip Authorization"

echo "[web-session-boundary] PASS"
