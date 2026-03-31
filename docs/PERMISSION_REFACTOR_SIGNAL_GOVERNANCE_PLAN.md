# Permission Refactor Signal Governance Plan

## Scope and Baseline

- Goal: close fallback/unknown/disabled signals via data governance only.
- Logic constraints: no menu-chain migration, no deny semantic relaxation, no fallback removal.
- Baseline signal snapshot (`test-results/permission-signal-governance-before.json`):
  - `OLD_PERMISSION_ONLY=35` (旧口径: `OLD_FALLBACK`)
  - `DENY_DISABLED=23`
  - `DENY_UNKNOWN=23`
  - ORG bucket: `11/11/11`
  - TENANT bucket: `24/12/12`

## Signal Source Layering

- Source rules: `docs/PERMISSION_REFACTOR_SIGNAL_SOURCE_RULES.md`
- Current implementation:
  - test runners force `-Dpermission.signal.source=TEST`
  - runtime default is `RUNTIME` when no explicit marker is provided
- Collector now outputs:
  - `by_source`
  - `by_tenant`
  - `by_scope`

## Governance Detail Table

| signal_source | permission_code | scopeType | scopeId | tenantId | signal_type | hit_count | role_or_user | cause_category | action_plan | status |
| --- | --- | --- | ---: | ---: | --- | ---: | --- | --- | --- | --- |
| TEST | `org:write` | ORG | 5 | 1 | DENY_UNKNOWN | 11 | `u40/r4` (test path) | PERMISSION_MASTER_MISSING | 保持 deny，不入主数据 | WONT_FIX |
| TEST | `job:stop` | TENANT | 300 | 3 | DENY_UNKNOWN | 12 | `u30/r3` (test path) | PERMISSION_MASTER_MISSING | 保持 deny，不入主数据 | WONT_FIX |
| TEST | `org:read` | ORG | 5 | 1 | DENY_DISABLED | 11 | `u40/r4` (test path) | EXPECTED_DISABLED_DENY | 保持 deny，不入主数据 | DONE |
| TEST | `job:run` | TENANT | 300 | 3 | DENY_DISABLED | 12 | `u30/r3` (test path) | EXPECTED_DISABLED_DENY | 保持 deny，不入主数据 | DONE |
| TEST | `org:read/org:write` | ORG | 5 | 1 | OLD_PERMISSION_ONLY | 11 | `u40/r4` (test path) | EXPECTED_COMPAT_FALLBACK | 保持兼容，不处理 | DONE |
| TEST | `audit:view` | TENANT | 200 | 2 | OLD_PERMISSION_ONLY | 12 | `u20/r2` (test path) | EXPECTED_COMPAT_FALLBACK | 保持兼容，不处理 | DONE |
| TEST | `job:run/job:stop` | TENANT | 300 | 3 | OLD_PERMISSION_ONLY | 12 | `u30/r3` (test path) | ROLE_PERMISSION_MISSING | 补 role_permission 映射 | DONE |

Notes:

- `DENY_UNKNOWN` and part of `OLD_PERMISSION_ONLY` (旧口径: `OLD_FALLBACK`) come from deterministic unit-test fixtures in `SecurityUserAuthorityServiceTest`, used for fail-closed assertions.
- DB-driven governance detail (`test-results/permission-signal-governance-before.txt`) shows production-like active assignments currently dominated by `DENY_DISABLED` only, with no unresolved runtime `DENY_UNKNOWN` rows.

## Classification Closure

### DENY_UNKNOWN (A/B/C required split)

- A `应补 permission 主数据`: none in current active DB buckets after this round.
- B `历史脏数据清理`: none newly detected in active DB buckets.
- C `保持 deny`:
  - `org:write` (ORG test fixture)
  - `job:stop` (TENANT test fixture)
  - both are intentional fail-closed test samples and stay deny.

### OLD_PERMISSION_ONLY (A/B required split，旧口径: `OLD_FALLBACK`)

- A `合理兼容 fallback`:
  - `audit:view` (explicit old-path fallback test sample)
  - ORG test fallback sample (`org:*`) for scope evidence
- B `应治理缺口`:
  - `job:run/job:stop` gap classified as `ROLE_PERMISSION_MISSING`
  - mapped by data backfill script (`fix-role-permission-mapping.sql`，已从仓库删除/DEPRECATED 归档) and verified idempotently.

### DENY_DISABLED confirmation

- Active DB detail rows are all `EXPECTED_DISABLED_DENY`.
- No `MISCONFIGURED_DISABLED` item identified in this run.

## Executed Data Governance Actions

1. `tiny-oauth-server/scripts/fix-resource-permission-legacy.sql`
   - trim legacy `resource.permission`
   - convert empty-after-trim to `NULL`
2. `tiny-oauth-server/scripts/fix-permission-main-data.sql`
   - backfill missing `permission` master rows from non-empty legacy permissions
3. `tiny-oauth-server/scripts/fix-role-permission-mapping.sql`（已从仓库删除/DEPRECATED 归档）
   - backfill missing `role_permission` mappings from legacy `role_resource`
4. Detail analysis snapshots
   - before: `test-results/permission-signal-governance-before.txt`
   - after: `test-results/permission-signal-governance-after.txt`

## Post-governance Verification

- E2E rerun: `tiny-oauth-server/scripts/e2e/run-permission-refactor-e2e.sh` => PASS
- Dev smoke rerun: `tiny-oauth-server/scripts/run-permission-dev-smoke-10m.sh` => PASS
- Signal snapshots:
  - before: `test-results/permission-signal-governance-before.json`
  - after: `test-results/permission-signal-governance-after.json`
  - runtime strict breakdown: `test-results/permission-runtime-signals-breakdown.json`

## Decision Impact

- Data governance closed all currently actionable DB-side gaps in this round.
- Remaining fallback/unknown counts are still mainly test-path generated evidence, not newly surfaced runtime drift.
- Expansion decisions use `RUNTIME` signal first; `TEST` signals are regression quality evidence.
- Runtime-only detailed review is maintained in `docs/PERMISSION_REFACTOR_RUNTIME_SIGNAL_REVIEW.md`.
- Decision remains: **keep current range and continue observation**, DEPT still deferred.
