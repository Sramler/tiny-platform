#!/usr/bin/env bash
# 真实 MySQL 下比较 Spring MVC Controller 映射与 api_endpoint 载体，并检查：
#   1) 经过 ApiEndpointRequirementFilter 且未精确豁免的 method+template 均有载体；
#   2) 同一 scope 下不存在运行时等价模板重复；
#   3) 命中的 enabled 载体具备 required_permission_id 与 requirement 行。
#
# 退出码：0 通过；1 漂移/测试失败；2 环境前置不足。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WEBAPP_ENV_FILE="${ROOT_DIR}/tiny-oauth-server/src/main/webapp/.env.e2e.local"
cd "${ROOT_DIR}"

if [[ -f "${WEBAPP_ENV_FILE}" && -z "${E2E_DB_PASSWORD:-}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${WEBAPP_ENV_FILE}"
  set +a
fi

required_env=(E2E_DB_HOST E2E_DB_PORT E2E_DB_NAME E2E_DB_USER E2E_DB_PASSWORD)
missing_env=()
for name in "${required_env[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    missing_env+=("${name}")
  fi
done
if (( ${#missing_env[@]} > 0 )); then
  echo "verify-api-endpoint-controller-drift: missing ${missing_env[*]} -> exit 2" >&2
  exit 2
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "verify-api-endpoint-controller-drift: mvn unavailable -> exit 2" >&2
  exit 2
fi

echo "==> Controller/api_endpoint real-DB drift gate"
mvn -pl tiny-oauth-server \
  -Dtest=com.tiny.platform.core.oauth.security.ApiEndpointControllerMappingDriftIT \
  -DfailIfNoTests=true \
  test

echo "==> verify-api-endpoint-controller-drift: OK"
