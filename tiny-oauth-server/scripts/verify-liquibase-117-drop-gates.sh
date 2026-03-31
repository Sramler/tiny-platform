#!/usr/bin/env bash
# Liquibase 117 gates: pre-check (RR still present) or post-check (RR dropped).
# Usage (from repo root):
#   MYSQL_PASSWORD=... bash tiny-oauth-server/scripts/verify-liquibase-117-drop-gates.sh pre
#   MYSQL_PASSWORD=... bash tiny-oauth-server/scripts/verify-liquibase-117-drop-gates.sh post
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
MODE="${1:-pre}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${MYSQL_ROOT_PASSWORD:-}}"
MYSQL_DB="${MYSQL_DB:-tiny_web}"

if [[ -z "${MYSQL_PASSWORD}" ]]; then
  echo "Set MYSQL_PASSWORD or MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi

mysql_exec() {
  mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" "$@"
}

exists="$(mysql_exec -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DB}' AND table_name='role_resource'")"

case "$MODE" in
  pre)
    if [[ "${exists}" != "1" ]]; then
      echo "[117-gates] pre: role_resource already absent (skip pre SQL or use post mode)"
      exit 0
    fi
    echo "[117-gates] pre: role_resource present — running readiness SQL"
    mysql_exec < "$ROOT_DIR/tiny-oauth-server/scripts/verify-pre-liquibase-117-role-resource-readiness.sql"
    ;;
  post)
    echo "[117-gates] post: canonical health SQL"
    mysql_exec < "$ROOT_DIR/tiny-oauth-server/scripts/verify-post-liquibase-117-canonical-health.sql"
    still="$(mysql_exec -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DB}' AND table_name='role_resource'")"
    if [[ "${still}" != "0" ]]; then
      echo "[117-gates] FAIL: role_resource still exists (expected 0 rows in information_schema check)" >&2
      exit 1
    fi
    ;;
  *)
    echo "Usage: $0 pre|post" >&2
    exit 2
    ;;
esac

echo "[117-gates] done ($MODE)"
