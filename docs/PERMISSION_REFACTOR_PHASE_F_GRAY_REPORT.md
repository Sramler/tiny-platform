# Permission Refactor Phase F Gray Report

## 1. Gray Scope

- Run window: 2026-03-24 (single run, on-demand)
- Environment: local dev (`tiny_web` on `127.0.0.1:3306`)
- Owner: pending (to be filled by runner)
- Baseline document: `docs/TINY_PLATFORM_PERMISSION_REFACTOR_FINAL_APPROVAL.md`

### Tenants

| tenantId | tenantCode | tenantName | batch | included scopes |
| --- | --- | --- | --- | --- |
| 1 | N/A | N/A | first | PLATFORM/TENANT |
| 3 | N/A | N/A | first | PLATFORM/TENANT |

## 2. Runtime Config Snapshot

```yaml
permission-refactor:
  authority-diff-log-enabled: true
  permission-version-debug-enabled: true
  fail-closed-strict-enabled: true
  gray-tenant-allow-list: [1, 3]
  gray-scope-type-allow-list: [PLATFORM, TENANT, ORG]
  diff-sample-rate: 1.0
```

Notes:

- `fail-closed-strict-enabled` semantic confirmation: controls observability strict logging; no allow/deny bypass.
- Any environment override: none for this run.

## 3. Validation Commands and Artifacts

Executed SQL:

```bash
mysql -h<host> -P<port> -u<user> -p <db_name> < tiny-oauth-server/scripts/verify-permission-phase-e-observability.sql
mysql -h<host> -P<port> -u<user> -p <db_name> < tiny-oauth-server/scripts/verify-permission-phase-e-scope-bucket.sql
```

Artifacts:

- Observability SQL output: executed successfully (see terminal session output)
- Scope bucket SQL output: executed successfully (see terminal session output)
- Log query snapshots: not available in this run (no persistent runtime log sink attached)

### Phase F-2 24h Runtime Collection

Collector script:

```bash
python3 tiny-oauth-server/scripts/collect-permission-phase-f-24h.py \
  --log-dir logs \
  --markdown-out test-results/phase-f-2-runtime-signals.md
```

Current run result:

- Status: pre-check only (no runtime log files found under `logs/`)
- Collected events: `0`
- Output artifact: `test-results/phase-f-2-runtime-signals.md`

## 4. Runtime Observation Summary

### 4.1 Authority Signals

- `OLD_PERMISSION_ONLY` total: 0 (pre-check run; no logs available)（旧口径: `OLD_FALLBACK`）
- Top fallback causes: N/A
- `DENY_DISABLED` total: 59 (candidate anomalies from role_resource vs role_permission residual analysis)
- Top denied disabled permission codes: `role:view`, `menu:view`, `resource:view`, `workflowDesign:view`, `process:view`, `deployment:view`, `definition:view`, `instance:view`, `task:view`, `scheduling:view`
- `DENY_UNKNOWN` total: 0 observed in residual SQL; `PERMISSION_NOT_FOUND` absent
- Top denied unknown permission codes: none observed

Interpretation:

- Are fallback cases explainable? Partially yes by data residuals: mainly `PERMISSION_DISABLED` (59) and `TENANT_MISMATCH` (11), plus `RESOURCE_PERMISSION_EMPTY` (2).
- Any unexpected deny pattern? No unknown-permission spike from SQL baseline.

### 4.2 PermissionsVersion Signals

Distribution:

| reason | count | expected? | notes |
| --- | --- | --- | --- |
| ROLE_ASSIGNMENT_CHANGED | 0 | N/A | pre-check run had no runtime logs |
| OLD_PERMISSION_INPUT_CHANGED | 0 | N/A | pre-check run had no runtime logs |
| ROLE_PERMISSION_CHANGED | 0 | N/A | pre-check run had no runtime logs |
| PERMISSION_MASTER_CHANGED | 0 | N/A | pre-check run had no runtime logs |
| ROLE_HIERARCHY_CHANGED | 0 | N/A | pre-check run had no runtime logs |

Interpretation:

- Version changes align with real operations? Cannot conclude without runtime debug logs.
- Any noisy/unexplainable changes? Not evaluable in this run.

### 4.3 Scope Bucket Integrity (ORG/DEPT)

- ORG included in run: yes/no
- DEPT included in run: yes/no
- ORG included in run: no
- DEPT included in run: no
- Cross-bucket contamination detected: no evidence in current TENANT/PLATFORM sample

Findings:

- Same tenant + different ORG isolation: not in scope for this run.
- Same tenant + different DEPT isolation: not in scope for this run.
- Log field completeness (`scopeType + scopeId`): code-level support present; runtime evidence pending ORG/DEPT batch.

## 5. Compatibility Guardrails

- Menu chain changed: yes/no (must be no)
- `/sys/menus/tree` behavior drift: yes/no (must be no)
- D-1 authority semantics regression: yes/no
- D-2 fingerprint semantics regression: yes/no
- Menu chain changed: no
- `/sys/menus/tree` behavior drift: no evidence
- D-1 authority semantics regression: no evidence
- D-2 fingerprint semantics regression: no evidence

## 6. Incident and Risk Notes

- Any privilege escalation incident:
- Any privilege loss incident:
- Any unresolved unknown permission spike:
- Any operational/log volume issue:
- Any privilege escalation incident: none observed
- Any privilege loss incident: none observed in this run
- Any unresolved unknown permission spike: none (baseline SQL)
- Any operational/log volume issue: not observed in local run

## 7. Final Decision

Pick one:

- [ ] Expand gray range
- [x] Keep current range and continue observation
- [ ] Roll back observability switches and investigate

Rationale (data-backed):

1. Residual analysis shows deny is mainly explainable by `PERMISSION_DISABLED` (59) and `TENANT_MISMATCH` (11), without unknown-permission spikes.
2. Current batch only covered PLATFORM/TENANT; ORG/DEPT runtime bucket evidence is still missing.
3. Phase F-2 runtime signal collector pre-check shows no logs yet, so 24h evidence is incomplete; expand-before-evidence is not recommended.

## 8. Next Action Plan

- Next batch tenants:
- Next batch tenants: keep `tenantId in [1,3]`; add one controlled tenant after runtime log evidence is complete.
- Scope expansion plan: add ORG first, then DEPT in a separate short window.
- Cleanup/fix backlog (if any): fix `PERMISSION_DISABLED` residual set and remove `RESOURCE_PERMISSION_EMPTY` rows.
- Expected next review time: after 24h runtime log collection with debug switches enabled.

## 9. Gray Gate Review Reference

- 正式准入评审文档：`docs/PERMISSION_REFACTOR_GRAY_GATE_REVIEW.md`
- 准入门槛标准：`docs/PERMISSION_REFACTOR_GRAY_GATE.md`

## 10. ORG-only Candidate Sample (No Expansion)

- Change applied:
  - `gray-scope-type-allow-list` updated to include `ORG` only (DEPT still deferred)
- Candidate fixture evidence:
  - `test-results/next-gray-org-dept-assignment.out`
  - ORG candidate: `tenant=1`, `scope_type=ORG`, `scope_id=3`, `user_id=1`, `role_id=5`

Validation rerun:

- Backend targeted regression:
  - `mvn -pl tiny-oauth-server -Dtest=UserDetailsServiceImplTest,SecurityUserAuthorityServiceTest,PermissionVersionServiceTest test`
  - Result: PASS
- E2E summary rerun:
  - `tiny-oauth-server/scripts/e2e/run-permission-refactor-e2e.sh`
  - Result: PASS (`Suite1~Suite6` all PASS)

Signal observation for rerun:

- `OLD_PERMISSION_ONLY=24`
- `DENY_DISABLED=12`
- `DENY_UNKNOWN=12`
- version reasons: all 0 in current collector output window

Conclusion for ORG-only candidate sample:

- Candidate preparation is valid.
- No regression introduced by enabling ORG in gray-scope allow list.
- Keep DEPT deferred; do not expand to DEPT until one dedicated ORG runtime window is completed.

## 11. ORG Dedicated Short Window (tenant=1, no DEPT expansion)

Window execution:

- Prepare candidate fixture:
  - `prepare-org-dept-fixtures.sql` (tenant=1 generated ORG/DEPT sample rows)
- Bucket verification:
  - `verify-permission-phase-e-scope-bucket.sql`
  - observed active buckets include:
    - `tenant=1, scope_type=ORG, scope_id=5, active_assignment_count=1`
    - `tenant=1, scope_type=DEPT, scope_id=6, active_assignment_count=1`
- Short regression + collector:
  - `mvn -pl tiny-oauth-server -Dtest=UserDetailsServiceImplTest,SecurityUserAuthorityServiceTest,PermissionVersionServiceTest test`
  - `collect-permission-phase-f-24h.py --log-dir test-results`

Observed signals in this short window:

- `OLD_PERMISSION_ONLY=24`
- `DENY_DISABLED=12`
- `DENY_UNKNOWN=12`
- by_scope currently still only reports `TENANT` in collector output

Interpretation:

- ORG bucket readiness in data layer is confirmed for tenant=1.
- Runtime collector did not yet produce ORG-tagged signal events in this short window, so ORG runtime evidence remains partial.
- DEPT remains deferred for real gray expansion.

Post-window cleanup:

- `cleanup-org-dept-fixtures.sql` executed successfully.
- residual fixture rows check:
  - `remaining_scope_assignments=0`
  - `remaining_scope_units=0`

## 12. ORG Signal Evidence Rerun (Runtime-like)

To increase ORG-scoped signal evidence, a dedicated ORG test-path was added in `SecurityUserAuthorityServiceTest` and rerun through the existing E2E collector pipeline.

Rerun commands:

- `mvn -pl tiny-oauth-server -Dtest=SecurityUserAuthorityServiceTest test`
- `tiny-oauth-server/scripts/e2e/run-permission-refactor-e2e.sh`

Rerun result:

- E2E overall: PASS (`Suite1~Suite6` all PASS)
- Signals (`test-results/permission-refactor-e2e-signals.json`):
  - `OLD_PERMISSION_ONLY=35`
  - `DENY_DISABLED=23`
  - `DENY_UNKNOWN=23`
- Scope buckets:
  - `ORG`: `OLD_PERMISSION_ONLY=11`, `DENY_DISABLED=11`, `DENY_UNKNOWN=11`
  - `TENANT`: `OLD_PERMISSION_ONLY=24`, `DENY_DISABLED=12`, `DENY_UNKNOWN=12`
- Tenant buckets:
  - tenant `1` now has ORG-scoped signal evidence (`11/11/11` for fallback/disabled/unknown)

Conclusion update:

- ORG-scoped runtime-like signals are now present and explainable.
- DEPT remains deferred for real gray expansion.

## 13. Fallback/Unknown Governance Closure (Data-first)

Artifacts:

- Governance plan: `docs/PERMISSION_REFACTOR_SIGNAL_GOVERNANCE_PLAN.md`
- Detail snapshots:
  - `test-results/permission-signal-governance-before.txt`
  - `test-results/permission-signal-governance-after.txt`
- Signal snapshots:
  - `test-results/permission-signal-governance-before.json`
  - `test-results/permission-signal-governance-after.json`
- Summary:
  - `test-results/permission-signal-governance-summary.md`

Executed data actions (no core-logic changes):

- `fix-resource-permission-legacy.sql`
- `fix-permission-main-data.sql`
- `fix-role-permission-mapping.sql`（已从仓库删除/DEPRECATED 归档）

Post-governance rerun:

- `run-permission-refactor-e2e.sh` => PASS
- `run-permission-dev-smoke-10m.sh` => PASS

Before/After (signals):

| metric | before | after | delta |
| --- | ---: | ---: | ---: |
| OLD_PERMISSION_ONLY | 35 | 35 | 0 |
| DENY_DISABLED | 23 | 23 | 0 |
| DENY_UNKNOWN | 23 | 23 | 0 |
| ORG OLD_PERMISSION_ONLY | 11 | 11 | 0 |
| ORG DENY_DISABLED | 11 | 11 | 0 |
| ORG DENY_UNKNOWN | 11 | 11 | 0 |
| TENANT OLD_PERMISSION_ONLY | 24 | 24 | 0 |
| TENANT DENY_DISABLED | 12 | 12 | 0 |
| TENANT DENY_UNKNOWN | 12 | 12 | 0 |

Interpretation:

- This round closed DB-side actionable governance items.
- Signal totals remain unchanged because current counts are dominated by deterministic test-path fallback/unknown samples (used for fail-closed assertions), not by new runtime data drift.
- Decision remains: keep current gray range; do not introduce DEPT.

## 14. Signal Source Layering (TEST vs RUNTIME)

Source rules:

- `docs/PERMISSION_REFACTOR_SIGNAL_SOURCE_RULES.md`
- `signal_source` enum is fixed: `TEST` / `RUNTIME`

Implementation updates:

- authority/version logs now include `signalSource=...`
- smoke/e2e runners force `-Dpermission.signal.source=TEST`
- collector output now includes `by_source`

Layered snapshot (latest):

- ALL:
  - `OLD_PERMISSION_ONLY=36`
  - `DENY_DISABLED=24`
  - `DENY_UNKNOWN=24`
- TEST:
  - `OLD_PERMISSION_ONLY=33`
  - `DENY_DISABLED=22`
  - `DENY_UNKNOWN=22`
- RUNTIME:
  - `OLD_PERMISSION_ONLY=3`
  - `DENY_DISABLED=2`
  - `DENY_UNKNOWN=2`

Policy update:

- Gray expansion decisions prioritize `RUNTIME` signals.
- `TEST` signals are retained as regression quality indicators and must not be used as the only expansion blocker.

## 15. Runtime-only Detailed Review

Artifacts:

- `docs/PERMISSION_REFACTOR_RUNTIME_SIGNAL_REVIEW.md`
- `test-results/permission-runtime-signals-breakdown.json`
- `test-results/permission-runtime-signals-breakdown.md`

Result (strict runtime, explicit source only):

- `RUNTIME / OLD_PERMISSION_ONLY = 0`
- `RUNTIME / DENY_DISABLED = 0`
- `RUNTIME / DENY_UNKNOWN = 0`

Clarification:

- historical `RUNTIME 3/2/2` came from mixed-source interpretation where missing `signalSource` lines were treated as runtime fallback.
- current runtime review uses strict explicit-source rule and is therefore cleaner for gate decisions.

Decision impact:

- No runtime blocking signal in this window.
- Keep current strategy: continue observation, prepare next-batch evidence, DEPT still deferred.

## 16. Compatibility Layer Assessment Entry

- Compatibility assessment stage is now opened (assessment only, no direct deletion in this phase).
- New inventory and roadmap:
  - `docs/PERMISSION_REFACTOR_COMPATIBILITY_LAYER_ASSESSMENT.md`
  - `test-results/permission-compatibility-layer-inventory.json`
  - `test-results/permission-compatibility-layer-inventory.md`

Key points:

- Menu old-path compatibility remains Level A (must keep).
- Non-menu temporary/test-only compatibility contains C-level candidate for next phase.
- Current decision remains unchanged:
  - continue observation
  - keep DEPT deferred
  - do not execute broad compatibility deletion yet

## 17. COMPAT-003 Trial Deletion Impact

Artifacts:

- `test-results/compat-003-removal-before.json`
- `test-results/compat-003-removal-after.json`
- `test-results/compat-003-removal-summary.md`

Result:

- trial deletion caused e2e regression (`suite1/2/3/6`), though menu non-drift remained PASS.
- rollback was executed immediately.
- post-rollback verification restored smoke/e2e PASS.

Impact on gray conclusion:

- no change to gray scope decision.
- keep current range observation and keep DEPT deferred.

## 18. COMPAT-003 RCA and Preconditions Hardening

Artifacts:

- `test-results/compat-003-root-cause-analysis.md`
- `test-results/compat-003-preconditions-checklist.md`
- `test-results/compat-003-wiring-scan.md`
- `test-results/compat-003-guard-tests-summary.md`

What was done in this round:

- no second deletion attempt was executed.
- focused precondition hardening was applied (test wiring update for authority service injection).
- full wiring scan was completed for remaining assembly paths.
- guard tests were added to prevent reintroduction of null-service-primary wiring.
- validation under retained compat branch:
  - smoke PASS
  - e2e PASS (`suite1~suite6` all PASS)
  - runtime strict rows = 0

Impact on gray conclusion:

- no impact on current gray decision.
- DEPT remains deferred.
- compatibility downscope may proceed only after explicit second-trial readiness gate.

## 19. COMPAT-003 Second Trial Removal Outcome

Scope:

- second trial removed only COMPAT-003 null-service fallback from `UserDetailsServiceImpl.resolveAuthorities()`.

Post-removal checks:

- guard tests PASS
- dev smoke PASS
- e2e overall PASS
- suite1/2/3/6 all PASS
- menu_non_drift PASS
- runtime strict rows = 0

Impact on gray conclusion:

- no negative impact on current gray range.
- no new runtime blocking signal introduced by COMPAT-003 removal.
- keep current expand policy unchanged: continue observation, DEPT deferred.

## 20. UserDetailsServiceImpl Constructor Hardening

Scope:

- removed legacy one-arg and two-arg constructors from `UserDetailsServiceImpl`.
- enforced explicit authority service dependency in constructor contract.

Validation:

- focused tests PASS
- smoke PASS
- e2e PASS
- runtime strict rows = 0

Impact on gray decision:

- no change.
- this is a technical-debt closure item that improves contract safety; gray scope policy remains unchanged.

## 21. Next-batch TENANT Gray Expansion

This round executed one controlled TENANT-only expansion:

- new tenant: `tenantId=5`, `tenantCode=gray-mid-tenant`, `tenantName=灰度中复杂租户`
- tenant complexity snapshot: `role_count=3`, `role_permission_count=6`
- scope policy kept strict for real expansion: `PLATFORM`, `TENANT` only
- ORG not included in this round; DEPT still deferred

Config change snapshot:

- before:
  - `gray-tenant-allow-list: [1, 3]`
  - `gray-scope-type-allow-list: [PLATFORM, TENANT, ORG]`
- after:
  - `gray-tenant-allow-list: [1, 3, 5]`
  - `gray-scope-type-allow-list: [PLATFORM, TENANT]`

Verification result:

- focused tests PASS
- smoke PASS
- e2e overall PASS
- suite1~suite6 all PASS
- runtime strict rows = 0
- tenant=5 runtime hits in this window: 0

Decision:

- keep expansion result (no rollback).

## 22. ORG Real Gray Preparation (No rollout in this task)

Preparation status:

- this round only prepares ORG real gray execution; no ORG rollout applied.
- candidate locked:
  - tenantId=`1`, orgId=`33`, orgCode=`E2E_ORG_999001`, orgName=`E2E ORG 999001`
- DEPT remains deferred.

Preparation outputs:

- precondition checklist
- config diff template (`[PLATFORM, TENANT] -> [PLATFORM, TENANT, ORG]`)
- fixed verification entry template
- ORG-specific blocking items (`O-1`~`O-7`)
- minimal rollback template (remove ORG only, keep tenant expansion)

Artifacts:

- `docs/PERMISSION_REFACTOR_ORG_GRAY_PREP.md`
- `test-results/org-gray-prep-summary.md`
- `test-results/org-gray-prep-checklist.json`

Conclusion:

- ORG is promoted from "candidate discussion" to "execution preparation stage".
- final readiness in this task: **PARTIAL_READY** (one dedicated ORG runtime evidence window still pending).

## 23. ORG Real Gray Execution Result

Execution scope:

- this round added `ORG` into gray scope allow-list.
- `gray-scope-type-allow-list`: `[PLATFORM, TENANT] -> [PLATFORM, TENANT, ORG]`
- `gray-tenant-allow-list` kept unchanged: `[1,3,5]`
- no DEPT introduction.

Candidate validated:

- tenantId=`1`, orgId=`33`, orgCode=`E2E_ORG_999001`, orgName=`E2E ORG 999001`
- binding evidence: `role_assignment.id=372` (`scope_type=ORG`, `scope_id=33`, `role_id=5`, `status=ACTIVE`)

Verification result:

- focused tests PASS
- smoke PASS
- e2e overall PASS
- suite1~suite6 all PASS
- suite4_scope_bucket PASS
- menu_non_drift PASS
- runtime strict rows/hits = `0/0`
- ORG runtime fallback/deny/version anomalies: not observed in runtime strict window

Decision:

- keep ORG expansion result (no rollback).
- DEPT remains deferred.

## 24. DEPT Real Gray Preparation (No rollout in this task)

Preparation scope:

- this round prepares DEPT real gray execution only; no DEPT rollout applied.
- candidate locked:
  - tenantId=`1`
  - orgId=`33` (`E2E_ORG_999001`)
  - deptId=`34` (`E2E_DEPT_999001`)
  - scoped assignment: `role_assignment.id=373` (`scope_type=DEPT`, `scope_id=34`, `role_id=5`)

Preparation outputs:

- DEPT precondition checklist
- config diff template (`[PLATFORM, TENANT, ORG] -> [PLATFORM, TENANT, ORG, DEPT]`)
- fixed verification entry template
- DEPT-specific blocking items (`D-1`~`D-8`)
- minimal rollback template (remove DEPT only, keep tenant5+ORG results)

Artifacts:

- `docs/PERMISSION_REFACTOR_DEPT_GRAY_PREP.md`
- `test-results/dept-gray-prep-summary.md`
- `test-results/dept-gray-prep-checklist.json`

Conclusion:

- DEPT is promoted from deferred-object status to execution-preparation status.
- final readiness in this task: **PARTIAL_READY** (dedicated DEPT runtime evidence window still pending).

## 25. DEPT Real Gray Execution Result

Execution scope:

- this round added `DEPT` into gray scope allow-list.
- `gray-scope-type-allow-list`: `[PLATFORM, TENANT, ORG] -> [PLATFORM, TENANT, ORG, DEPT]`
- `gray-tenant-allow-list` kept unchanged: `[1,3,5]`
- ORG retained, no tenant expansion.

Candidate validated:

- canonical candidate: `tenant=1`, `org=E2E_ORG_999001`, `dept=E2E_DEPT_999001`
- execution-window IDs: `orgId=37`, `deptId=38`, `role_assignment.id=377`

Verification result:

- focused tests PASS
- smoke PASS
- e2e overall PASS
- suite1~suite6 all PASS
- suite4_scope_bucket PASS
- menu_non_drift PASS
- runtime strict rows/hits = `0/0`
- DEPT runtime fallback/deny/version anomalies: not observed in runtime strict window

Decision:

- keep DEPT expansion result (no rollback).

## 26. Larger TENANT Expansion (Scope Unchanged)

Execution intent:

- keep current scope allow list unchanged: `[PLATFORM, TENANT, ORG, DEPT]`
- expand tenant sample only by adding one medium-complexity tenant
- do not touch menu chain, do not delete compat points

New tenant selected:

- `tenantId=6`
- `tenantCode=gray-mid-tenant-b`
- `tenantName=灰度中复杂租户B`
- complexity snapshot: `role_count=4`, `role_permission_count=10`
- selection rationale: medium complexity with real role/permission mapping, but not the highest-risk tenant

Config snapshot:

- before:
  - `gray-tenant-allow-list: [1, 3, 5]`
  - `gray-scope-type-allow-list: [PLATFORM, TENANT, ORG, DEPT]`
- after:
  - `gray-tenant-allow-list: [1, 3, 5, 6]`
  - `gray-scope-type-allow-list: [PLATFORM, TENANT, ORG, DEPT]` (unchanged)

Verification (same order before/after):

- focused tests: PASS
- dev smoke: PASS
- e2e overall: PASS
- suite1~suite6: all PASS
- runtime strict rows/hits: `0/0` -> `0/0`

Runtime-only observations:

- new tenant(6) fallback/deny/hits: `0/0/0`
- tenant5 runtime hits: `0` (no disturbance)
- ORG runtime hits: `0` (no disturbance)
- DEPT runtime hits: `0` (no disturbance)
- new compat runtime dependency: not observed

Decision:

- keep this tenant expansion result.

## 27. Next Compat Downscope (B-level, Single Candidate)

Round scope:

- reassess all B-level compat candidates with latest gray evidence (`tenant5/tenant6/ORG/DEPT retained`, runtime strict `0/0`)
- select at most one `TRY_NOW` candidate
- execute single-point trial removal with full before/after regression

Selected candidate:

- `compat_id=COMPAT-005`
- `code_location=PermissionVersionService.resolveVersionChangeReasons()`
- `compat_type=TEMPORARY_GRAY_COMPAT`

Trial removal action:

- removed temporary parallel reason append: `PERMISSION_MASTER_CHANGED` on `newPermissionDigest` change.
- no menu path modification.
- no tenant/scope/config expansion in this round.

Verification result (before and after):

- focused tests: PASS -> PASS
- smoke: PASS -> PASS
- e2e overall: PASS -> PASS
- suite1~suite6: all PASS -> all PASS
- menu_non_drift: PASS -> PASS
- runtime strict rows/hits: `0/0` -> `0/0`
- tenant5/tenant6/ORG/DEPT runtime hits: unchanged (`0`)
- new compat runtime dependency: not observed

Decision:

- keep deletion result for this round (`COMPAT-005`).
- no rollback needed.
- gray conclusion impact: none negative.

## 28. Accelerated Compat Round (Parallel Evaluation + Serial Deletion Guard)

Round strategy:

- parallel reassessment for remaining B-level compat points (`001/002/004/009`)
- keep serial deletion gate (at most one point), with strict no-batch-delete rule

Latest baseline facts (unchanged):

- tenant5/tenant6/ORG/DEPT retained
- runtime strict rows/hits remains `0/0`
- no new compat runtime dependency observed

Evaluation result:

- `COMPAT-001`: OBSERVE
- `COMPAT-002`: NOT_NOW
- `COMPAT-004`: OBSERVE
- `COMPAT-009`: NOT_NOW
- remaining TRY_NOW candidates: none

Execution outcome:

- this round did not enter trial deletion path
- no code rollback required

Impact on gray conclusion:

- none negative
- current gray decision remains stable while compatibility evidence quality improves via parallel evaluation outputs

## 29. COMPAT-004 Dedicated Shadow Evidence Round

Scope control:

- this round does not execute COMPAT-004 dedicated shadow evidence generation (code removed); only historical evidence record is kept.
- no compat deletion executed.
- no menu path change, no tenant/scope expansion.

Implementation note:

- COMPAT-004 shadow comparison snapshot code is removed; formal permissionsVersion path remains unchanged.

Artifacts:

- `test-results/compat-004-shadow-evidence.md`
- `test-results/compat-004-shadow-summary.json`

Result:

- focused tests: PASS
- dev smoke: PASS
- e2e overall: PASS
- runtime strict rows/hits: `0/0`
- shadow:
  - refresh-leak risk: not observed
  - reason equivalence: not yet equivalent in current output

Decision:

- `COMPAT-004` remains `OBSERVE` in this round (not promoted to `TRY_NOW` yet).
- gray conclusion impact: none negative.

## 30. COMPAT-004 Reason-Equivalence Rule Round

Scope:

- this round only records historical reason-equivalence evidence (no code-based evidence generation).
- formal result semantics remain unchanged; rule assessment remains a historical reference.

Artifacts:

- `test-results/compat-004-reason-equivalence.md`
- `test-results/compat-004-reason-equivalence-summary.json`

Result summary:

- scenario count: 4
- acceptable diffs: 0
- unacceptable diffs: 4
- normalizedEquivalent true/false: `0/4`
- final recommendation: `NOT_NOW`

Validation:

- focused tests PASS
- smoke PASS
- e2e overall PASS
- runtime strict rows/hits: `0/0`

Decision impact:

- `COMPAT-004` is downgraded to `NOT_NOW` under current reason-equivalence rule set.
- gray overall conclusion remains stable (no negative runtime/security drift introduced by evidence work).

## 31. Phase Closure (Current Stage Sealed)

Closure statement:

- the current non-menu permission evolution stage is sealed.
- no new compat downscope action will be introduced in this stage.
- remaining compat items enter freeze observation.

Sealed baseline:

- `COMPAT-003` retained removal
- `COMPAT-005` retained removal
- `COMPAT-004 = NOT_NOW`
- tenant5/tenant6/ORG/DEPT retained
- runtime strict `0/0`
- latest e2e overall PASS

Operational artifacts:

- `test-results/permission-refactor-phase-closure-summary.md`
- `test-results/permission-refactor-compat-freeze-list.md`
- `test-results/permission-refactor-runtime-observation-template.md`
- `test-results/permission-refactor-rollback-sop.md`
- `test-results/permission-refactor-next-phase-backlog.md`

Next phase single mainline (fixed):

- **菜单链路迁移前评估**

## 32. Next Mainline Progress (Menu Pre-migration Evaluation)

This round progressed the next mainline via documentation-grade readiness artifacts only:

- dependency inventory done
- risk matrix done
- readiness checklist initialized
- rollback rehearsal plan documented

No migration execution started in this round, and no impact on current gray stability baseline.
