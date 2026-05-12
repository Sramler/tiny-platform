#!/usr/bin/env bash
# Verifies the runtime menu version behavior against the local E2E MySQL schema.
#
# This is intentionally a behavior proof, not a schema-only check:
#   1. Reads MENU_CONFIG / PLATFORM runtime_version_signal before the test.
#   2. Runs MenuRuntimeVersionBehaviorE2eTest against MySQL.
#   3. Prints the proof file containing seq/version/ETag before and after bump.
#   4. Reads MENU_CONFIG / PLATFORM runtime_version_signal after the test.
#
# Exit codes:
#   0 - behavior verified.
#   1 - prerequisites are present, but verification failed.
#   2 - local environment prerequisite missing.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT_DIR}"

LOCAL_ENV_SHELL="${LOCAL_ENV_SHELL:-${SHELL:-/bin/zsh}}"
LOAD_LOGIN_SHELL_ENV="${LOAD_LOGIN_SHELL_ENV:-1}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
DB_HOST="${DB_HOST:-${E2E_DB_HOST:-127.0.0.1}}"
DB_PORT="${DB_PORT:-${E2E_DB_PORT:-3306}}"
DB_NAME="${DB_NAME:-${E2E_DB_NAME:-tiny_web}}"
DB_USER="${DB_USER:-${E2E_DB_USER:-root}}"

read_login_shell_env() {
  local name="$1"
  [[ "$name" =~ ^[A-Z0-9_]+$ ]] || return 1
  TP_ENV_NAME="${name}" "${LOCAL_ENV_SHELL}" -lc 'printenv "$TP_ENV_NAME"' 2>/dev/null | head -n 1
}

hydrate_env_from_login_shell() {
  if [[ "${LOAD_LOGIN_SHELL_ENV}" != "1" ]]; then
    return 0
  fi
  local name value
  local -a names=(
    DB_HOST
    DB_PORT
    DB_NAME
    DB_USER
    DB_PASSWORD
    E2E_DB_HOST
    E2E_DB_PORT
    E2E_DB_NAME
    E2E_DB_USER
    E2E_DB_PASSWORD
    MYSQL_BIN
  )
  for name in "${names[@]}"; do
    if [[ -z "${!name:-}" ]]; then
      value="$(read_login_shell_env "${name}" || true)"
      if [[ -n "${value}" ]]; then
        export "${name}=${value}"
      fi
    fi
  done
}

hydrate_env_from_login_shell

DB_HOST="${DB_HOST:-${E2E_DB_HOST:-127.0.0.1}}"
DB_PORT="${DB_PORT:-${E2E_DB_PORT:-3306}}"
DB_NAME="${DB_NAME:-${E2E_DB_NAME:-tiny_web}}"
DB_USER="${DB_USER:-${E2E_DB_USER:-root}}"
DB_PASSWORD="${DB_PASSWORD:-${E2E_DB_PASSWORD:-}}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"

if [[ -z "${DB_PASSWORD}" ]]; then
  echo "verify-menu-runtime-version-behavior: missing DB_PASSWORD or E2E_DB_PASSWORD" >&2
  exit 2
fi
if ! command -v "${MYSQL_BIN}" >/dev/null 2>&1; then
  echo "verify-menu-runtime-version-behavior: mysql client not found: ${MYSQL_BIN}" >&2
  exit 2
fi

export E2E_DB_HOST="${E2E_DB_HOST:-${DB_HOST}}"
export E2E_DB_PORT="${E2E_DB_PORT:-${DB_PORT}}"
export E2E_DB_NAME="${E2E_DB_NAME:-${DB_NAME}}"
export E2E_DB_USER="${E2E_DB_USER:-${DB_USER}}"
export E2E_DB_PASSWORD="${E2E_DB_PASSWORD:-${DB_PASSWORD}}"

mysql_exec() {
  env MYSQL_PWD="${DB_PASSWORD}" "${MYSQL_BIN}" \
    -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -D"${DB_NAME}" \
    --connect-timeout=5 -N -B "$@"
}

print_platform_menu_runtime_version_row() {
  local rows
  rows="$(mysql_exec <<'SQL'
SELECT
  id,
  version_seq,
  version_value,
  reason,
  updated_at
FROM runtime_version_signal
WHERE version_domain = 'MENU_CONFIG'
  AND normalized_tenant_id = 0
  AND scope_type = 'PLATFORM'
  AND normalized_scope_id = 0;
SQL
)"
  if [[ -z "${rows}" ]]; then
    echo "(no MENU_CONFIG / PLATFORM runtime_version_signal row)"
  else
    printf '%s\n' "${rows}"
  fi
}

if ! mysql_exec -e "SELECT 1" >/dev/null 2>&1; then
  echo "verify-menu-runtime-version-behavior: cannot connect to MySQL ${DB_HOST}:${DB_PORT}/${DB_NAME}" >&2
  exit 2
fi

echo "== runtime_version_signal before =="
print_platform_menu_runtime_version_row

rm -f tiny-oauth-server/target/menu-runtime-version-behavior-proof.json

mvn -pl tiny-oauth-server -Dtest=MenuRuntimeVersionBehaviorE2eTest test

echo "== behavior proof =="
cat tiny-oauth-server/target/menu-runtime-version-behavior-proof.json

echo
echo "== runtime_version_signal after =="
print_platform_menu_runtime_version_row

echo "verify-menu-runtime-version-behavior: OK"
