#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/sql/backfill-user-auth-new-model.sql"

MODE="dry-run"
if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  echo "Usage: bash tiny-oauth-server/scripts/backfill-user-auth-new-model.sh [--apply]"
  echo
  echo "Modes:"
  echo "  (default) dry-run : only project/write-count/reconcile evidence, no SQL apply"
  echo "  --apply           : execute backfill SQL, then print reconcile evidence"
  exit 0
fi
if [[ "${1:-}" == "--apply" ]]; then
  MODE="apply"
fi

DB_HOST="${DB_HOST:-${MYSQL_HOST:-127.0.0.1}}"
DB_PORT="${DB_PORT:-${MYSQL_PORT:-3306}}"
DB_USER="${DB_USER:-${MYSQL_USER:-root}}"
DB_PASSWORD="${DB_PASSWORD:-${MYSQL_PWD:-}}"
DB_NAME="${DB_NAME:-${MYSQL_DATABASE:-tiny_web}}"

if [[ -z "${DB_PASSWORD}" ]]; then
  echo "[card-06] missing DB_PASSWORD/MYSQL_PWD"
  exit 2
fi

if [[ ! -f "${SQL_FILE}" ]]; then
  echo "[card-06] missing sql file: ${SQL_FILE}"
  exit 2
fi

MYSQL_ARGS=(
  -h"${DB_HOST}"
  -P"${DB_PORT}"
  -u"${DB_USER}"
  "${DB_NAME}"
  --default-character-set=utf8mb4
  --batch
  --raw
)

run_query() {
  local sql="$1"
  MYSQL_PWD="${DB_PASSWORD}" mysql "${MYSQL_ARGS[@]}" -e "${sql}"
}

run_scalar_query() {
  local sql="$1"
  run_query "${sql}" | tail -n 1
}

check_table_exists() {
  local table_name="$1"
  local count
  count="$(run_scalar_query "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}' AND table_name='${table_name}';")"
  [[ "${count}" == "1" ]]
}

print_scope_projection_rule() {
  echo "[card-06] scope projection rule (temporary historical mapping for backfill only):"
  echo "  - tenant_id IS NOT NULL           -> TENANT:{tenant_id}"
  echo "  - tenant_id IS NULL + ACTIVE membership exists -> GLOBAL"
  echo "  - tenant_id IS NULL + no ACTIVE membership     -> PLATFORM"
  echo "  note: this is backfill-time legacy projection, not final runtime semantics"
}

credential_conflict_count() {
  run_scalar_query "
SELECT COUNT(*)
FROM (
  SELECT
    uam.user_id,
    uam.authentication_provider,
    uam.authentication_type
  FROM user_authentication_method uam
  GROUP BY
    uam.user_id,
    uam.authentication_provider,
    uam.authentication_type
  HAVING
    COUNT(DISTINCT SHA2(COALESCE(CAST(uam.authentication_configuration AS CHAR(8192)), 'NULL'), 256)) > 1
    OR COUNT(DISTINCT COALESCE(NULLIF(CAST(uam.last_verified_at AS CHAR), '0000-00-00 00:00:00'), 'NULL')) > 1
    OR COUNT(DISTINCT COALESCE(uam.last_verified_ip, 'NULL')) > 1
    OR COUNT(DISTINCT COALESCE(NULLIF(CAST(uam.expires_at AS CHAR), '0000-00-00 00:00:00'), 'NULL')) > 1
) conflicts;
"
}

print_credential_conflicts() {
  run_query "
SELECT
  uam.user_id,
  uam.authentication_provider,
  uam.authentication_type,
  COUNT(*) AS legacy_rows,
  GROUP_CONCAT(
    DISTINCT CASE
      WHEN uam.tenant_id IS NOT NULL THEN CONCAT('TENANT:', uam.tenant_id)
      WHEN EXISTS (
        SELECT 1 FROM tenant_user tu
        WHERE tu.user_id = uam.user_id AND tu.status = 'ACTIVE'
      ) THEN 'GLOBAL'
      ELSE 'PLATFORM'
    END
    ORDER BY CASE
      WHEN uam.tenant_id IS NOT NULL THEN CONCAT('TENANT:', uam.tenant_id)
      WHEN EXISTS (
        SELECT 1 FROM tenant_user tu
        WHERE tu.user_id = uam.user_id AND tu.status = 'ACTIVE'
      ) THEN 'GLOBAL'
      ELSE 'PLATFORM'
    END
    SEPARATOR ', '
  ) AS projected_scope_keys,
  COUNT(DISTINCT SHA2(COALESCE(CAST(uam.authentication_configuration AS CHAR(8192)), 'NULL'), 256)) AS configuration_variants,
  COUNT(DISTINCT COALESCE(NULLIF(CAST(uam.last_verified_at AS CHAR), '0000-00-00 00:00:00'), 'NULL')) AS last_verified_at_variants,
  COUNT(DISTINCT COALESCE(uam.last_verified_ip, 'NULL')) AS last_verified_ip_variants,
  COUNT(DISTINCT COALESCE(NULLIF(CAST(uam.expires_at AS CHAR), '0000-00-00 00:00:00'), 'NULL')) AS expires_at_variants
FROM user_authentication_method uam
GROUP BY
  uam.user_id,
  uam.authentication_provider,
  uam.authentication_type
HAVING
  configuration_variants > 1
  OR last_verified_at_variants > 1
  OR last_verified_ip_variants > 1
  OR expires_at_variants > 1
ORDER BY uam.user_id, uam.authentication_provider, uam.authentication_type
LIMIT 20;
"
}

echo "[card-06] mode: ${MODE}"
echo "[card-06] db: ${DB_HOST}:${DB_PORT}/${DB_NAME} as ${DB_USER}"

if ! check_table_exists "user_authentication_method"; then
  echo "[card-06] missing required legacy table: user_authentication_method"
  echo "[card-06] result: environment/schema precondition missing (not script logic failure)"
  exit 2
fi
if ! check_table_exists "tenant_user"; then
  echo "[card-06] missing required table for GLOBAL/PLATFORM projection: tenant_user"
  echo "[card-06] result: environment/schema precondition missing (not script logic failure)"
  exit 2
fi

echo
print_scope_projection_rule

echo
echo "[card-06] projected legacy scope summary (old table -> new scope semantics)"
run_query "
SELECT
  CASE
    WHEN uam.tenant_id IS NOT NULL THEN 'TENANT'
    WHEN EXISTS (
      SELECT 1 FROM tenant_user tu
      WHERE tu.user_id = uam.user_id AND tu.status = 'ACTIVE'
    ) THEN 'GLOBAL'
    ELSE 'PLATFORM'
  END AS projected_scope_type,
  CASE
    WHEN uam.tenant_id IS NOT NULL THEN CONCAT('TENANT:', uam.tenant_id)
    WHEN EXISTS (
      SELECT 1 FROM tenant_user tu
      WHERE tu.user_id = uam.user_id AND tu.status = 'ACTIVE'
    ) THEN 'GLOBAL'
    ELSE 'PLATFORM'
  END AS projected_scope_key,
  uam.authentication_provider,
  uam.authentication_type,
  COUNT(*) AS legacy_rows
FROM user_authentication_method uam
GROUP BY
  projected_scope_type,
  projected_scope_key,
  uam.authentication_provider,
  uam.authentication_type
ORDER BY projected_scope_type, projected_scope_key, authentication_provider, authentication_type;
"

echo
echo "[card-06] projected write volumes"
run_query "
SELECT
  (SELECT COUNT(*) FROM (
    SELECT user_id, authentication_provider, authentication_type
    FROM user_authentication_method
    GROUP BY user_id, authentication_provider, authentication_type
  ) t) AS projected_credential_upserts,
  (SELECT COUNT(*) FROM user_authentication_method) AS projected_scope_policy_upserts;
"

echo
echo "[card-06] credential conflict summary (legacy rows collapsing into one credential)"
CONFLICT_COUNT="$(credential_conflict_count)"
if [[ "${CONFLICT_COUNT}" == "0" ]]; then
  echo "conflict_groups"
  echo "0"
  echo "[card-06] no divergent credential groups detected"
else
  echo "conflict_groups"
  echo "${CONFLICT_COUNT}"
  echo "[card-06] divergent legacy credential groups detected; sample follows"
  print_credential_conflicts
  echo "[card-06] apply mode will fail-fast until these groups are reconciled or an explicit merge rule is approved"
fi

if [[ "${MODE}" == "apply" ]]; then
  if ! check_table_exists "user_auth_credential" || ! check_table_exists "user_auth_scope_policy"; then
    echo
    echo "[card-06] cannot apply: CARD-02 schema baseline missing (user_auth_credential/user_auth_scope_policy)"
    echo "[card-06] result: environment/schema precondition missing (not script logic failure)"
    exit 2
  fi
  if [[ "${CONFLICT_COUNT}" != "0" ]]; then
    echo
    echo "[card-06] cannot apply: divergent legacy credential groups detected"
    echo "[card-06] result: data-quality/merge-rule precondition missing (not backfill script crash)"
    echo "[card-06] review the conflict summary above and reconcile legacy scoped rows before apply"
    exit 1
  fi
  echo
  echo "[card-06] apply backfill sql"
  MYSQL_PWD="${DB_PASSWORD}" mysql "${MYSQL_ARGS[@]}" < "${SQL_FILE}"
fi

if ! check_table_exists "user_auth_credential" || ! check_table_exists "user_auth_scope_policy"; then
  echo
  echo "[card-06] stop before new-model reconcile: CARD-02 schema baseline missing"
  echo "[card-06] missing table(s): user_auth_credential and/or user_auth_scope_policy"
  echo "[card-06] interpretation: precondition gap, not backfill script crash"
  echo "[card-06] next:"
  echo "  1) ensure CARD-02 baseline tables exist in ${DB_NAME}"
  echo "  2) rerun dry-run:  bash tiny-oauth-server/scripts/backfill-user-auth-new-model.sh"
  echo "  3) apply backfill: bash tiny-oauth-server/scripts/backfill-user-auth-new-model.sh --apply"
  echo "  4) rerun dry-run to capture reconcile evidence"
  exit 2
fi

echo
echo "[card-06] new-model row counts"
run_query "
SELECT 'user_auth_credential' AS table_name, COUNT(*) AS row_count
FROM user_auth_credential
UNION ALL
SELECT 'user_auth_scope_policy' AS table_name, COUNT(*) AS row_count
FROM user_auth_scope_policy;
"

echo
echo "[card-06] legacy vs new-model diff by scope/provider/type"
run_query "
SELECT
  merged.scope_key,
  merged.authentication_provider,
  merged.authentication_type,
  SUM(merged.legacy_count) AS legacy_count,
  SUM(merged.new_count) AS new_count,
  SUM(merged.new_count) - SUM(merged.legacy_count) AS delta
FROM (
  SELECT
    CASE
      WHEN uam.tenant_id IS NOT NULL THEN CONCAT('TENANT:', uam.tenant_id)
      WHEN EXISTS (
        SELECT 1 FROM tenant_user tu
        WHERE tu.user_id = uam.user_id AND tu.status = 'ACTIVE'
      ) THEN 'GLOBAL'
      ELSE 'PLATFORM'
    END AS scope_key,
    uam.authentication_provider,
    uam.authentication_type,
    COUNT(*) AS legacy_count,
    0 AS new_count
  FROM user_authentication_method uam
  GROUP BY scope_key, uam.authentication_provider, uam.authentication_type

  UNION ALL

  SELECT
    p.scope_key,
    c.authentication_provider,
    c.authentication_type,
    0 AS legacy_count,
    COUNT(*) AS new_count
  FROM user_auth_scope_policy p
  JOIN user_auth_credential c ON c.id = p.credential_id
  GROUP BY p.scope_key, c.authentication_provider, c.authentication_type
) merged
GROUP BY merged.scope_key, merged.authentication_provider, merged.authentication_type
HAVING legacy_count <> new_count
ORDER BY merged.scope_key, merged.authentication_provider, merged.authentication_type;
"

echo
if [[ "${MODE}" == "dry-run" ]]; then
  echo "[card-06] dry-run complete (no writes applied)"
else
  echo "[card-06] apply complete"
fi
echo "[card-06] CARD-07 readiness evidence criteria:"
echo "  - projected_credential_upserts / projected_scope_policy_upserts are available"
echo "  - new-model row counts are available"
echo "  - reconcile diff output is empty (or deltas are explicitly explained and accepted)"
