#!/usr/bin/env bash
set -euo pipefail

MYSQL_BIN="${MYSQL_BIN:-mysql}"
DB_HOST="${E2E_DB_HOST:-127.0.0.1}"
DB_PORT="${E2E_DB_PORT:-3306}"
DB_NAME="${E2E_DB_NAME:-tiny_web}"
DB_USER="${E2E_DB_USER:-root}"
DB_PASSWORD="${E2E_DB_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"

if [[ -z "${DB_PASSWORD}" ]]; then
  echo "missing E2E_DB_PASSWORD/MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

echo "rbac3 schema health target: ${DB_HOST}:${DB_PORT}/${DB_NAME} user=${DB_USER}"

table_count() {
  local table_name="$1"
  MYSQL_PWD="${DB_PASSWORD}" "${MYSQL_BIN}" -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" -N -s -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='${table_name}';"
}

role_cnt="$(table_count role)"
perm_cnt="$(table_count permission)"
role_perm_cnt="$(table_count role_permission)"

echo "table role count=${role_cnt}"
echo "table permission count=${perm_cnt}"
echo "table role_permission count=${role_perm_cnt}"

if [[ "${role_cnt}" -lt 1 || "${perm_cnt}" -lt 1 || "${role_perm_cnt}" -lt 1 ]]; then
  echo "rbac3 schema baseline is not ready" >&2
  exit 1
fi

echo "recent DATABASECHANGELOG rows:"
MYSQL_PWD="${DB_PASSWORD}" "${MYSQL_BIN}" -h "${DB_HOST}" -P "${DB_PORT}" -u "${DB_USER}" -N -s -e \
  "SELECT id, author, filename, dateexecuted FROM ${DB_NAME}.DATABASECHANGELOG ORDER BY dateexecuted DESC LIMIT 10;"
