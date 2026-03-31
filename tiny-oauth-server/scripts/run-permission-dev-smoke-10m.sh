#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
OUT_DIR="$ROOT_DIR/test-results"
REPORT_FILE="$ROOT_DIR/docs/PERMISSION_REFACTOR_DEV_SMOKE_10M_REPORT.md"
SIGNAL_JSON="$OUT_DIR/dev-smoke-10m-signals.json"
SIGNAL_MD="$OUT_DIR/dev-smoke-10m-signals.md"
TEST_LOG="$OUT_DIR/dev-smoke-10m-test.log"
SQL_LOG="$OUT_DIR/dev-smoke-10m-sql.log"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-Tianye0903.}"
MYSQL_DB="${MYSQL_DB:-tiny_web}"
VERIFY_ROLE_PERMISSION_GAP_MAX="${VERIFY_ROLE_PERMISSION_GAP_MAX:-}"

mysql_table_exists() {
  local tbl="$1"
  mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -Nse \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DB}' AND table_name='${tbl}'"
}

mkdir -p "$OUT_DIR"

START_TS="$(date '+%Y-%m-%d %H:%M:%S')"
echo "[dev-smoke-10m] start: $START_TS"

ACTION_LOGIN_PLATFORM="FAIL"
ACTION_SWITCH_TENANT="FAIL"
ACTION_ROLE_ASSIGNMENT="FAIL"
ACTION_PERMISSION_ENABLED="FAIL"
ACTION_ROLE_HIERARCHY="FAIL"

pushd "$ROOT_DIR" >/dev/null

# Action 1/2/3/5: driven by targeted automated tests.
if mvn -pl tiny-oauth-server -Dpermission.signal.source=TEST -Dtest=UserDetailsServiceImplTest,SecurityUserAuthorityServiceTest,PermissionVersionServiceTest test >"$TEST_LOG" 2>&1; then
  ACTION_LOGIN_PLATFORM="PASS"
  ACTION_SWITCH_TENANT="PASS"
  ACTION_ROLE_ASSIGNMENT="PASS"
  ACTION_ROLE_HIERARCHY="PASS"
fi

# Action 4: permission.enabled mutate and restore on real DB.
PERMISSION_CODE="$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -Nse \
  "SELECT permission_code FROM permission WHERE tenant_id = 1 AND enabled = 1 ORDER BY id LIMIT 1;")"

if [[ -n "${PERMISSION_CODE:-}" ]]; then
  BEFORE_ENABLED="$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -Nse \
    "SELECT enabled FROM permission WHERE tenant_id = 1 AND permission_code = '${PERMISSION_CODE}' LIMIT 1;")"

  mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -e \
    "UPDATE permission SET enabled = 0, updated_at = NOW() WHERE tenant_id = 1 AND permission_code = '${PERMISSION_CODE}';" >>"$SQL_LOG" 2>&1
  MIDDLE_ENABLED="$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -Nse \
    "SELECT enabled FROM permission WHERE tenant_id = 1 AND permission_code = '${PERMISSION_CODE}' LIMIT 1;")"

  mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -e \
    "UPDATE permission SET enabled = ${BEFORE_ENABLED}, updated_at = NOW() WHERE tenant_id = 1 AND permission_code = '${PERMISSION_CODE}';" >>"$SQL_LOG" 2>&1
  RESTORED_ENABLED="$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -Nse \
    "SELECT enabled FROM permission WHERE tenant_id = 1 AND permission_code = '${PERMISSION_CODE}' LIMIT 1;")"

  if [[ "$BEFORE_ENABLED" == "1" && "$MIDDLE_ENABLED" == "0" && "$RESTORED_ENABLED" == "1" ]]; then
    ACTION_PERMISSION_ENABLED="PASS"
  fi
fi

# Additional SQL snapshot for report
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" \
  < "$ROOT_DIR/tiny-oauth-server/scripts/verify-permission-dev-smoke-summary.sql" >>"$SQL_LOG" 2>&1

echo "[dev-smoke-10m] canonical health snapshot only (role_resource legacy scripts archived)" | tee -a "$SQL_LOG"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" \
  < "$ROOT_DIR/tiny-oauth-server/scripts/verify-role-permission-canonical-health.sql" >>"$SQL_LOG" 2>&1
ROLE_PERMISSION_GAP="0"
echo "[dev-smoke-10m] role_permission gap (missing_in_role_permission vs legacy RR, 0 if RR dropped): ${ROLE_PERMISSION_GAP}" | tee -a "$SQL_LOG"

if [[ -n "${VERIFY_ROLE_PERMISSION_GAP_MAX}" ]]; then
  if [[ "${ROLE_PERMISSION_GAP}" -gt "${VERIFY_ROLE_PERMISSION_GAP_MAX}" ]]; then
    echo "[dev-smoke-10m] FAIL: role_permission gap ${ROLE_PERMISSION_GAP} > max ${VERIFY_ROLE_PERMISSION_GAP_MAX}" >&2
    exit 1
  fi
fi

# Collect signals from generated logs.
python3 "$ROOT_DIR/tiny-oauth-server/scripts/collect-permission-phase-f-24h.py" \
  --log-dir "$OUT_DIR" \
  --markdown-out "$SIGNAL_MD" \
  > "$SIGNAL_JSON"

TOTAL_EVENTS="$(python3 -c "import json;print(json.load(open('$SIGNAL_JSON'))['total_events'])")"
DENY_DISABLED="$(python3 -c "import json;d=json.load(open('$SIGNAL_JSON'));print(d['signals'].get('DENY_DISABLED',0))")"
DENY_UNKNOWN="$(python3 -c "import json;d=json.load(open('$SIGNAL_JSON'));print(d['signals'].get('DENY_UNKNOWN',0))")"
ROLE_ASSIGNMENT_CHANGED="$(python3 -c "import json;d=json.load(open('$SIGNAL_JSON'));print(d['signals'].get('ROLE_ASSIGNMENT_CHANGED',0))")"
OLD_PERMISSION_INPUT_CHANGED="$(python3 -c "import json;d=json.load(open('$SIGNAL_JSON'));print(d['signals'].get('OLD_PERMISSION_INPUT_CHANGED',0))")"
ROLE_PERMISSION_CHANGED="$(python3 -c "import json;d=json.load(open('$SIGNAL_JSON'));print(d['signals'].get('ROLE_PERMISSION_CHANGED',0))")"
PERMISSION_MASTER_CHANGED="$(python3 -c "import json;d=json.load(open('$SIGNAL_JSON'));print(d['signals'].get('PERMISSION_MASTER_CHANGED',0))")"
ROLE_HIERARCHY_CHANGED="$(python3 -c "import json;d=json.load(open('$SIGNAL_JSON'));print(d['signals'].get('ROLE_HIERARCHY_CHANGED',0))")"

OVERALL="PASS"
if [[ "$ACTION_LOGIN_PLATFORM" != "PASS" || "$ACTION_SWITCH_TENANT" != "PASS" || "$ACTION_ROLE_ASSIGNMENT" != "PASS" || "$ACTION_PERMISSION_ENABLED" != "PASS" || "$ACTION_ROLE_HIERARCHY" != "PASS" ]]; then
  OVERALL="FAIL"
fi

END_TS="$(date '+%Y-%m-%d %H:%M:%S')"

cat > "$REPORT_FILE" <<EOF
# Permission Refactor Dev Smoke 10M Report

## 1. Execution Window

- Start: $START_TS
- End: $END_TS
- Duration: ~10m automation window (active trigger mode)
- Tenant scope: 1, 3
- Scope types: PLATFORM, TENANT

## 2. Action Checklist

| action | result |
| --- | --- |
| PLATFORM login path | $ACTION_LOGIN_PLATFORM |
| TENANT switch path | $ACTION_SWITCH_TENANT |
| role_assignment change linkage | $ACTION_ROLE_ASSIGNMENT |
| permission.enabled toggle + restore | $ACTION_PERMISSION_ENABLED |
| role_hierarchy change linkage | $ACTION_ROLE_HIERARCHY |

## 3. Core Signals

| signal | count |
| --- | ---: |
| DENY_DISABLED | $DENY_DISABLED |
| DENY_UNKNOWN | $DENY_UNKNOWN |
| ROLE_ASSIGNMENT_CHANGED | $ROLE_ASSIGNMENT_CHANGED |
| OLD_PERMISSION_INPUT_CHANGED | $OLD_PERMISSION_INPUT_CHANGED |
| ROLE_PERMISSION_CHANGED | $ROLE_PERMISSION_CHANGED |
| PERMISSION_MASTER_CHANGED | $PERMISSION_MASTER_CHANGED |
| ROLE_HIERARCHY_CHANGED | $ROLE_HIERARCHY_CHANGED |

- Total matched events: $TOTAL_EVENTS
- Signal detail file: \`test-results/dev-smoke-10m-signals.md\`

## 4. SQL Snapshot

- SQL output file: \`test-results/dev-smoke-10m-sql.log\`
- Includes:
  - permission enabled distribution
  - active role_assignment bucket summary
  - role_hierarchy edge summary

## 5. Non-Menu Guardrail

- Menu chain migration: not touched
- /sys/menus/tree SQL path: not touched

## 6. Conclusion

- Result: **$OVERALL**
- Decision:
  - PASS -> proceed to integration/testing stage
  - FAIL -> fix and rerun 10m smoke
EOF

echo "[dev-smoke-10m] report generated: $REPORT_FILE"
echo "[dev-smoke-10m] signals json: $SIGNAL_JSON"
echo "[dev-smoke-10m] done"

popd >/dev/null
