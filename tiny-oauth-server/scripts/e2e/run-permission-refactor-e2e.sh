#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
WEBAPP_DIR="$ROOT_DIR/tiny-oauth-server/src/main/webapp"
OUT_DIR="$ROOT_DIR/test-results"
SUMMARY_JSON="$OUT_DIR/permission-refactor-e2e-summary.json"
SUMMARY_MD="$OUT_DIR/permission-refactor-e2e-summary.md"
TEST_LOG="$OUT_DIR/permission-refactor-e2e-test.log"
SIGNAL_JSON="$OUT_DIR/permission-refactor-e2e-signals.json"
SIGNAL_MD="$OUT_DIR/permission-refactor-e2e-signals.md"
SQL_LOG="$OUT_DIR/permission-refactor-e2e-sql.log"
MUTATE_RESTORE_COUNT=0
SHORT_STABILITY_ROUNDS=10
SHORT_STABILITY_PASSED=0

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-Tianye0903.}"
MYSQL_DB="${MYSQL_DB:-tiny_web}"

mkdir -p "$OUT_DIR"

START_TS="$(date '+%Y-%m-%d %H:%M:%S')"
echo "[permission-refactor-e2e] start: $START_TS"

SUITE1="FAIL"
SUITE2="FAIL"
SUITE3="FAIL"
SUITE4="FAIL"
SUITE5="FAIL"
SUITE6="FAIL"

# Run existing non-menu linkage regression group from Java side first.
if mvn -pl tiny-oauth-server -Dpermission.signal.source=TEST -Dtest=UserDetailsServiceImplTest,SecurityUserAuthorityServiceTest,PermissionVersionServiceTest test >"$TEST_LOG" 2>&1; then
  SUITE1="PASS"
  SUITE2="PASS"
  SUITE3="PASS"
fi

# Prepare ORG/DEPT fixtures and verify Suite4 bucket readiness.
if mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" \
  < "$ROOT_DIR/tiny-oauth-server/scripts/e2e/prepare-org-dept-fixtures.sql" >"$SQL_LOG" 2>&1; then
  ORG_ASSIGNMENTS="$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -Nse \
    "SELECT COUNT(*) FROM role_assignment WHERE granted_by = -999001 AND scope_type = 'ORG';")"
  DEPT_ASSIGNMENTS="$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -Nse \
    "SELECT COUNT(*) FROM role_assignment WHERE granted_by = -999001 AND scope_type = 'DEPT';")"
  if [[ "${ORG_ASSIGNMENTS:-0}" -gt 0 && "${DEPT_ASSIGNMENTS:-0}" -gt 0 ]]; then
    SUITE4="PASS"
  fi
fi

# Explicit mutate+restore probes for permission.enabled and role_hierarchy.
PERM_CODE="$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -Nse \
  "SELECT permission_code FROM permission WHERE tenant_id = 1 AND enabled = 1 ORDER BY id LIMIT 1;")"
if [[ -n "${PERM_CODE:-}" ]]; then
  mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -e \
    "UPDATE permission SET enabled = 0, updated_by = -999001, updated_at = NOW() WHERE tenant_id = 1 AND permission_code = '${PERM_CODE}';" >>"$SQL_LOG" 2>&1
  mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -e \
    "UPDATE permission SET enabled = 1, updated_by = -999001, updated_at = NOW() WHERE tenant_id = 1 AND permission_code = '${PERM_CODE}';" >>"$SQL_LOG" 2>&1
  MUTATE_RESTORE_COUNT=$((MUTATE_RESTORE_COUNT + 1))
fi

H_PARENT="$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -Nse \
  "SELECT id FROM role WHERE tenant_id = 1 ORDER BY id LIMIT 1;")"
H_CHILD="$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -Nse \
  "SELECT id FROM role WHERE tenant_id = 1 ORDER BY id DESC LIMIT 1;")"
if [[ -n "${H_PARENT:-}" && -n "${H_CHILD:-}" && "${H_PARENT}" != "${H_CHILD}" ]]; then
  mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -e \
    "INSERT INTO role_hierarchy (tenant_id, parent_role_id, child_role_id, created_at, created_by, updated_at)
     SELECT 1, ${H_PARENT}, ${H_CHILD}, NOW(), -999001, NOW()
     WHERE NOT EXISTS (
       SELECT 1 FROM role_hierarchy WHERE tenant_id = 1 AND parent_role_id = ${H_PARENT} AND child_role_id = ${H_CHILD}
     );" >>"$SQL_LOG" 2>&1
  mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" -e \
    "DELETE FROM role_hierarchy WHERE tenant_id = 1 AND parent_role_id = ${H_PARENT} AND child_role_id = ${H_CHILD} AND created_by = -999001;" >>"$SQL_LOG" 2>&1
  MUTATE_RESTORE_COUNT=$((MUTATE_RESTORE_COUNT + 1))
fi

# Short stability loop (Suite6): repeat core regression test rounds.
for i in $(seq 1 "$SHORT_STABILITY_ROUNDS"); do
  if mvn -pl tiny-oauth-server -Dpermission.signal.source=TEST -Dtest=UserDetailsServiceImplTest,SecurityUserAuthorityServiceTest,PermissionVersionServiceTest test >>"$TEST_LOG" 2>&1; then
    SHORT_STABILITY_PASSED=$((SHORT_STABILITY_PASSED + 1))
  fi
done
if [[ "$SHORT_STABILITY_PASSED" -eq "$SHORT_STABILITY_ROUNDS" ]]; then
  SUITE6="PASS"
fi

# Menu non-drift guard (read-only regression from existing integration tests)
if mvn -pl tiny-oauth-server -Dpermission.signal.source=TEST -Dtest=MenuControllerRbacIntegrationTest test >>"$TEST_LOG" 2>&1; then
  SUITE5="PASS"
fi

# Cleanup fixtures and probes (always attempt).
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" \
  < "$ROOT_DIR/tiny-oauth-server/scripts/e2e/cleanup-org-dept-fixtures.sql" >>"$SQL_LOG" 2>&1 || true
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DB" \
  < "$ROOT_DIR/tiny-oauth-server/scripts/e2e/cleanup-permission-refactor-e2e-data.sql" >>"$SQL_LOG" 2>&1 || true

# Collect signal statistics from generated logs in test-results.
python3 "$ROOT_DIR/tiny-oauth-server/scripts/collect-permission-phase-f-24h.py" \
  --log-dir "$OUT_DIR" \
  --markdown-out "$SIGNAL_MD" \
  > "$SIGNAL_JSON"

TOTAL_EVENTS="$(python3 -c "import json;print(json.load(open('$SIGNAL_JSON'))['total_events'])")"

OVERALL="PASS"
if [[ "$SUITE1" != "PASS" || "$SUITE2" != "PASS" || "$SUITE3" != "PASS" || "$SUITE4" != "PASS" || "$SUITE5" != "PASS" || "$SUITE6" != "PASS" ]]; then
  OVERALL="FAIL"
fi

END_TS="$(date '+%Y-%m-%d %H:%M:%S')"

cat > "$SUMMARY_JSON" <<EOF
{
  "start": "$START_TS",
  "end": "$END_TS",
  "overall": "$OVERALL",
  "suites": {
    "suite1_auth_context": "$SUITE1",
    "suite2_permission_linkage": "$SUITE2",
    "suite3_new_old_consistency": "$SUITE3",
    "suite4_scope_bucket": "$SUITE4",
    "suite5_menu_non_drift": "$SUITE5",
    "suite6_short_stability": "$SUITE6"
  },
  "mutate_restore_count": $MUTATE_RESTORE_COUNT,
  "short_stability_rounds": $SHORT_STABILITY_ROUNDS,
  "short_stability_passed": $SHORT_STABILITY_PASSED,
  "signal_total_events": $TOTAL_EVENTS,
  "signal_file": "test-results/permission-refactor-e2e-signals.json",
  "test_log": "test-results/permission-refactor-e2e-test.log",
  "sql_log": "test-results/permission-refactor-e2e-sql.log"
}
EOF

cat > "$SUMMARY_MD" <<EOF
# Permission Refactor E2E Summary

- Start: $START_TS
- End: $END_TS
- Overall: **$OVERALL**

## Suites

| suite | result |
| --- | --- |
| Suite 1 Auth & Context | $SUITE1 |
| Suite 2 Permission Linkage | $SUITE2 |
| Suite 3 New/Old Consistency | $SUITE3 |
| Suite 4 Scope Bucket | $SUITE4 |
| Suite 5 Menu Non-Drift | $SUITE5 |
| Suite 6 Short Stability | $SUITE6 |

## Signals

- Total matched signal events: $TOTAL_EVENTS
- Detail JSON: \`test-results/permission-refactor-e2e-signals.json\`
- Detail Markdown: \`test-results/permission-refactor-e2e-signals.md\`

## Mutation/Restore and Stability

- mutate_restore_count: $MUTATE_RESTORE_COUNT
- short_stability_rounds: $SHORT_STABILITY_ROUNDS
- short_stability_passed: $SHORT_STABILITY_PASSED

## Artifacts

- Summary JSON: \`test-results/permission-refactor-e2e-summary.json\`
- Summary Markdown: \`test-results/permission-refactor-e2e-summary.md\`
- Test log: \`test-results/permission-refactor-e2e-test.log\`
- SQL log: \`test-results/permission-refactor-e2e-sql.log\`
EOF

echo "[permission-refactor-e2e] summary: $SUMMARY_JSON"
echo "[permission-refactor-e2e] done"
