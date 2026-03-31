# Permission Refactor Data Governance Closure

## Scope

This closure records the two practical actions requested:

1. Govern known residual issues (`DENY_UNKNOWN`, `OLD_PERMISSION_ONLY` gaps (旧口径: `OLD_FALLBACK`), ORG/DEPT readiness)
2. Prepare next-batch gray candidates (without immediate expansion)

Date: 2026-03-24

## 1) Residual Governance (data-only, no core logic changes)

### 1.1 Baseline diagnostics (tenant 1/3)

- Residual anomaly breakdown before cleanup:
  - `PERMISSION_DISABLED`: 33
  - `TENANT_MISMATCH`: 11
  - `RESOURCE_PERMISSION_EMPTY`: 1
- Unknown permission references:
  - `unknown_refs = 0`

### 1.2 Actions executed

#### A) Fix empty `resource.permission` sample

- Target row:
  - `tenant_id=1`, `resource_id=31`, `name=exportTestData`, empty permission
- Data patch:
  - set `resource.permission = 'exportTestData:view'`
  - insert corresponding `permission` row if missing
  - backfill `role_permission` via enabled mapping insert

#### B) Fill enabled `role_permission` mapping gaps

- Executed `INSERT IGNORE` backfill for enabled permissions on tenant `1,3`.

#### C) Clean explicit tenant-projection dirty rows

- Removed `role_resource` rows where:
  - `rr.tenant_id IN (1,3)` and linked role is platform template (`role.tenant_id IS NULL`)
- This removed known `TENANT_MISMATCH` residuals from tenant-scope fallback causes.

### 1.3 Post-cleanup recheck

- Residual anomaly breakdown:
  - `PERMISSION_DISABLED`: 33
  - `TENANT_MISMATCH`: 0
  - `RESOURCE_PERMISSION_EMPTY`: 0
- Unknown references:
  - `unknown_refs = 0`

Conclusion:

- `DENY_UNKNOWN` data-side root cause in tenant `1/3` is no longer driven by missing permission rows.
- Remaining fallback surface is mainly expected disabled-permission governance (`PERMISSION_DISABLED`), not projection mismatch or empty permission code.

## 2) ORG/DEPT gray-readiness candidate preparation (not opened)

Prepared candidate fixtures (kept as candidates, not expanded to gray scope):

- candidate tenant: `tenant_id=1`
- candidate user: `user_id=1`
- candidate role: `role_id=5`
- ORG candidate:
  - `scope_type=ORG`, `scope_id=3`, `unit_code=E2E_ORG_999001`
- DEPT candidate:
  - `scope_type=DEPT`, `scope_id=4`, `unit_code=E2E_DEPT_999001`

Evidence file:

- `test-results/next-gray-org-dept-assignment.out`

## 3) Next-batch shortlist (no immediate expansion)

### 3.1 Candidate tenant

- **Primary candidate**: `tenant_id=1` (`code=default`)
  - Complexity: medium/high (role_count=9, hierarchy_edges=1)
  - Risk notes:
    - hierarchy exists (needs careful version-change observation)
    - disabled-permission residual still present (`fallback_disabled_gap_count=32`)
    - unknown gap currently `0`
    - tenant-mismatch gap cleaned in this round

### 3.2 Candidate scope

- **Next scope candidate**: `ORG` only
- `DEPT` remains deferred until one ORG gray cycle is stable.

## 4) Short-window revalidation after governance

Executed:

- `tiny-oauth-server/scripts/run-permission-dev-smoke-10m.sh`

Result:

- Report: `docs/PERMISSION_REFACTOR_DEV_SMOKE_10M_REPORT.md`
- Status: PASS
- Note: signal collector currently aggregates from cumulative test-result logs; decisioning should prioritize fresh DB anomaly checks for governance delta.

## 5) Guardrails reaffirmed

Not performed in this closure:

- no menu-chain migration
- no fallback offlining
- no immediate DEPT real gray rollout

## 6) Immediate next step

Run one focused ORG-only gray sample on candidate tenant `1` with current whitelist unchanged for production scope, then reassess:

- fallback explainability in ORG bucket
- version-change reasons in ORG bucket
- deny behavior stability without scope contamination
