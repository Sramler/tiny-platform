#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

CONFIG_FILE="tiny-oauth-server/src/main/resources/application.yaml"
REGISTERED_CLIENT_CONFIG="tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/RegisteredClientConfig.java"

if grep -Eiq 'vue-client|silent-renew|localhost:5173/callback' \
  "${CONFIG_FILE}" "${REGISTERED_CLIENT_CONFIG}"; then
  echo "[oauth2-client-boundary] FAIL: Vue browser OAuth client wiring remains" >&2
  exit 1
fi

grep -Fq 'client-id: tiny-public-client' "${CONFIG_FILE}" \
  || { echo "[oauth2-client-boundary] FAIL: public PKCE client is missing" >&2; exit 1; }
grep -Fq 'client-id: tiny-service-client' "${CONFIG_FILE}" \
  || { echo "[oauth2-client-boundary] FAIL: service client is missing" >&2; exit 1; }
grep -Fq -- '- client_credentials' "${CONFIG_FILE}" \
  || { echo "[oauth2-client-boundary] FAIL: client_credentials grant is missing" >&2; exit 1; }
grep -Fq 'client-secret: ${TINY_SERVICE_CLIENT_SECRET:}' "${CONFIG_FILE}" \
  || { echo "[oauth2-client-boundary] FAIL: service client must be disabled when its secret is not injected" >&2; exit 1; }
if grep -Eq 'tiny-service-dev-secret|\{noop\}.*clientSecret' "${CONFIG_FILE}" "${REGISTERED_CLIENT_CONFIG}"; then
  echo "[oauth2-client-boundary] FAIL: hard-coded or noop service client secret remains" >&2
  exit 1
fi

mvn -q -pl tiny-oauth-server test \
  -Dtest=RegisteredClientConfigTest,AuthorizationServerUserInfoIntegrationTest,JwtTokenCustomizerTest,TenantContextFilterTest

echo "[oauth2-client-boundary] PASS"
