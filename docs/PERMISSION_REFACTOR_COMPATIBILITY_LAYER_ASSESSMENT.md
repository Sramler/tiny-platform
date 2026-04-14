# Permission Refactor Compatibility Layer Assessment

> Status: archived compatibility assessment snapshot / historical evidence only.  
> This file records a phase-by-phase compatibility review context and must not be used as the current runtime truth source. Current runtime truth and closure should be checked in `docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md`, `docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`, `docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`, and `docs/TINY_PLATFORM_PERMISSION_REFACTOR_FINAL_APPROVAL.md`.

## Scope

- Goal: identify compatibility-layer code points and evaluate removal readiness.
- Constraint: assessment only, no direct compatibility code deletion in this task.
- Evidence inputs:
  - `docs/PERMISSION_REFACTOR_RUNTIME_SIGNAL_REVIEW.md`
  - `docs/PERMISSION_REFACTOR_GRAY_GATE_REVIEW.md`
  - `docs/PERMISSION_REFACTOR_PHASE_F_GRAY_REPORT.md`
  - `test-results/permission-runtime-signals-breakdown.json`
  - `test-results/permission-refactor-e2e-summary.json`

## Historical Compatibility Inventory (Archived Snapshot)

> Reading note: all `ACTIVE / REMOVED / CLOSED / Level A/B/... / READY / NOT_NOW` labels below are point-in-time assessment results from the review window when this file was written. They preserve historical evidence, but they do not by themselves prove today's runtime closure or current removal priority.

| compat_id | code_location | compat_type | description | current_usage | protected_by | note |
| --- | --- | --- | --- | --- | --- | --- |
| COMPAT-001 | `SecurityUserAuthorityService.chooseCandidatePermissionCodeSet()` | OLD_AUTHORITY_FALLBACK | new path not covering old set then fallback to old set | ACTIVE | ORG未全量、灰度兼容策略 | 主兼容分支 |
| COMPAT-002 | `SecurityUserAuthorityService.loadOldPermissionCodeSet()` | HISTORICAL_DATA_COMPAT | read legacy `role_resource -> resource.permission` codes | ACTIVE | 历史数据、fallback 语义 | 与 COMPAT-001 联动 |
| COMPAT-003 | `UserDetailsServiceImpl.resolveAuthorities()` | TEMPORARY_GRAY_COMPAT | authority service not injected then fallback to `SecurityUser.buildAuthoritiesFromRoles` | REMOVED | 已完成二次试删验证 | 删除后 guard/smoke/e2e/runtime 全 PASS |
| COMPAT-004 | `PermissionVersionService.buildOldPermissionInput()` | OLD_PERMISSION_VERSION_INPUT | keep old permission input in fingerprint to avoid under-refresh | ACTIVE | 不漏刷策略、灰度兼容 | 版本口径兼容项 |
| COMPAT-005 | `PermissionVersionService.resolveVersionChangeReasons()` | TEMPORARY_GRAY_COMPAT | old/new/hierarchy parallel reasons in one phase | REMOVED | 已完成单点试删并保留 | `PERMISSION_MASTER_CHANGED` 并行 reason 已收缩 |
| COMPAT-006 | `ResourceRepository.findGrantedResourcesByUsernameAndTenantId*` | MENU_OLD_PATH | historical tenant menu grant query seam (`role_resource` + legacy permission model); retained here only as archived evidence | MENU_ONLY | 菜单链路 | Level A |
| COMPAT-007 | `ResourceRepository.findGrantedPlatformResourcesByUsername*` | MENU_OLD_PATH | historical platform menu grant query seam; retained here only as archived evidence | MENU_ONLY | 菜单链路 | Level A |
| COMPAT-008 | `MenuServiceImpl.findTreeMenusForCurrentUser()` | MENU_OLD_PATH | menu tree uses repository old grant queries | MENU_ONLY | 菜单链路、DEPT后置 | Level A |
| COMPAT-009 | `PermissionRefactorObservabilityProperties` related switches | TEMPORARY_GRAY_COMPAT | gray-window/sample switches for compatibility observation | ACTIVE | 灰度策略 | 不是主逻辑兼容但属阶段性 |
| COMPAT-010 | `ActiveTenantResponseSupport.resolveSignalSourceFromRequestContext()` default | SCOPE_COMPAT | default signal source fallback for no-header requests | ACTIVE | 历史日志兼容 | 统计口径兼容项 |

### COMPAT-003 Trial Removal Record and RCA

- trial date: 2026-03-24
- trial action:
  - removed null-service fallback branch in `UserDetailsServiceImpl.resolveAuthorities()`
- trial result:
  - smoke: PASS
  - e2e overall: FAIL
  - regressed suites: `suite1`, `suite2`, `suite3`, `suite6`
- decision:
  - rollback executed (per execution rule)
- rollback verification:
  - smoke PASS + e2e PASS restored
- root cause classification:
  - TEST_WIRING_INCOMPLETE
  - LEGACY_SECURITYUSER_CONSTRUCTION_DEPENDENCY
  - MOCK_OR_STUB_PATH_NOT_UPDATED
  - PARTIAL_MIGRATION_OF_AUTHORITY_ENTRY
- additional evidence:
  - `test-results/compat-003-root-cause-analysis.md`
  - `test-results/compat-003-preconditions-checklist.md`
  - `test-results/compat-003-wiring-scan.md`
  - `test-results/compat-003-guard-tests-summary.md`
- current status:
  - COMPAT-003 remains in place
  - repository scan and guard tests completed
  - recommendation updated to "enter second-trial preparation stage, no deletion in this round"

### COMPAT-003 Second Trial Removal Result

- trial date: 2026-03-24
- action:
  - removed null-service fallback branch in `UserDetailsServiceImpl.resolveAuthorities()`
- verification:
  - guard tests PASS
  - smoke PASS
  - e2e overall PASS
  - suite1/2/3/6 all PASS
  - runtime strict rows = 0
- artifacts:
  - `test-results/compat-003-removal-before.json`
  - `test-results/compat-003-removal-after.json`
  - `test-results/compat-003-removal-summary.md`
- decision:
  - keep deletion result (no rollback)

### COMPAT-003 Constructor Contract Hardening (Post-removal)

- removed legacy constructors from `UserDetailsServiceImpl`:
  - `(AuthUserResolutionService)`
  - `(AuthUserResolutionService, PermissionVersionService)`
- constructor contract now enforces explicit `SecurityUserAuthorityService` injection (fail-fast on null).
- all repository usage in scan scope remains `FULL_ARG`; no rollback to old construction path.
- evidence:
  - `test-results/user-details-service-constructor-hardening.md`

### Tenant Expansion Compatibility Observation

- next-batch tenant gray expansion added `tenantId=5` under TENANT-only scope.
- runtime strict breakdown remained `rows=0`; no new compat runtime dependency observed in this window.

### ORG Expansion Compatibility Observation

- ORG scope was added in a controlled gray round (`PLATFORM/TENANT/ORG`).
- runtime strict breakdown stayed `rows=0`; no new compat runtime dependency observed.

### DEPT Preparation Compatibility Observation

- DEPT remained out of real gray scope in this round (prep-only).
- candidate and templates were fixed; no new compat runtime dependency observed during preparation.

### DEPT Expansion Compatibility Observation

- DEPT scope was added in a controlled gray round (`PLATFORM/TENANT/ORG/DEPT`).
- runtime strict breakdown remained `rows=0`; no new compat runtime dependency observed.

## Dependency Matrix

> Table note: the matrices below continue the same archived assessment window. If a row here differs from current code or current task status, prefer `AUTHORIZATION_TASK_LIST` for completion and `AUTHORIZATION_MODEL` / `FINAL_APPROVAL` for current runtime interpretation.

| compat_id | runtime_dependency | test_dependency | menu_dependency | scope_dependency | data_dependency |
| --- | --- | --- | --- | --- | --- |
| COMPAT-001 | YES | YES | NO | YES | YES |
| COMPAT-002 | YES | YES | NO | YES | YES |
| COMPAT-003 | NO | YES | NO | NO | NO |
| COMPAT-004 | YES | YES | NO | YES | YES |
| COMPAT-005 | YES | YES | NO | YES | NO |
| COMPAT-006 | YES | YES | YES | YES | YES |
| COMPAT-007 | YES | YES | YES | NO | YES |
| COMPAT-008 | YES | YES | YES | YES | YES |
| COMPAT-009 | YES | YES | NO | YES | NO |
| COMPAT-010 | YES | YES | NO | NO | NO |

## Level Classification

| compat_id | level | reason |
| --- | --- | --- |
| COMPAT-006 | Level A | menu grant path current production behavior |
| COMPAT-007 | Level A | platform menu grant path still old chain |
| COMPAT-008 | Level A | menu service directly depends on old grant repository |
| COMPAT-001 | Level B | non-menu fallback still part of gray safety net |
| COMPAT-002 | Level B | old permission input source still needed by fallback |
| COMPAT-004 | Level B | old version input kept for no-under-refresh guarantee |
| COMPAT-005 | CLOSED | single-point downscope retained; no regression observed |
| COMPAT-009 | Level B | gray controls still required for staged rollout |
| COMPAT-003 | CLOSED | second trial deletion retained with full regression PASS |
| COMPAT-010 | Level D | evidence insufficient for stricter default without side-effect |

## B/C Preconditions and Removal Order

| compat_id | compat_type | level | preconditions | removal_order | rollback_plan | recommendation |
| --- | --- | --- | --- | --- | --- | --- |
| COMPAT-003 | TEMPORARY_GRAY_COMPAT | CLOSED | completed | done | no rollback required | 已完成第二次试删并保留删除结果 |
| COMPAT-001 | OLD_AUTHORITY_FALLBACK | B | runtime window keeps no blocking fallback; org runtime stable for >=1 cycle; menu path unchanged | Batch-2 | re-enable fallback selection branch | 待 ORG 验证后评估 |
| COMPAT-002 | HISTORICAL_DATA_COMPAT | B | COMPAT-001 preconditions met and data governance drift remains clean | Batch-2 | restore old permission load path | 待 fallback 收缩后评估 |
| COMPAT-004 | OLD_PERMISSION_VERSION_INPUT | B | confirm no under-refresh on new input only in dedicated shadow window | Batch-2 | restore old version input into fingerprint | 进入下一批下线候选 |
| COMPAT-005 | TEMPORARY_GRAY_COMPAT | B | version reason stability proven with reduced input set | Batch-2 | restore previous reason calculation matrix | 继续观察，不立即处理 |
| COMPAT-009 | TEMPORARY_GRAY_COMPAT | B | rollout reaches post-gray steady-state and no per-tenant sampling needed | Batch-3 | restore gray switches and sampling controls | 菜单迁移完成后再评估 |

## Three-Batch Roadmap

### Batch-1 (test-only non-menu compatibility)

- Target: COMPAT-003 (precondition hardening first)
- Intent: complete wiring migration evidence before second removal trial.

### Batch-2 (low-runtime non-menu compatibility)

- Target: COMPAT-001/002/004/005
- Intent: shrink fallback and old version input after one more runtime evidence cycle.

### Batch-3 (org-related and gray-control compatibility)

- Target: COMPAT-009 and org-adjacent B items
- Intent: evaluate only after org runtime window remains stable.

### Explicitly Deferred

- Menu old path compatibility: COMPAT-006/007/008 (Level A)
- DEPT-related compatibility and scope-coupled paths (until DEPT strategy changes)

## Formal Assessment Conclusion

1. Total compatibility points: **10**
2. Level A: **3**
3. Level B: **4** (remaining active B-level points: 001/002/004/009)
4. Level C: **1**
5. Level D: **1**
6. Closed/Removed: **2** (`COMPAT-003`, `COMPAT-005`)

Current downscope candidate set:

- Enter removal-candidate pool now: **none directly executable**
- Keep but prepare candidate docs: **COMPAT-001/002/004/009 (B)**
- Must retain now: **COMPAT-006/007/008 (A)**
- Removed and retained deletion: **COMPAT-003**

Stage conclusion:

- Compatibility downscope **assessment phase**: **YES**
- Compatibility executable **deletion phase**: **PARTIAL YES** (`COMPAT-003` completed), overall staged.

## Accelerated Round: Multi-point Parallel Evaluation + Single-point Serial Trial Policy

Round intent:

- evaluate remaining B-level points in parallel
- keep deletion serial and at most one point per round

Parallel evaluation result (remaining B-level):

| compat_id | code_location | runtime_dependency | menu_dependency | scope_dependency | data_dependency | shadow_evidence | recommendation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| COMPAT-001 | `SecurityUserAuthorityService.chooseCandidatePermissionCodeSet()` | HIGH | NO | HIGH | HIGH | PARTIAL | OBSERVE |
| COMPAT-002 | `SecurityUserAuthorityService.loadOldPermissionCodeSet()` | HIGH | NO | HIGH | HIGH | PARTIAL | NOT_NOW |
| COMPAT-004 | `PermissionVersionService.buildOldPermissionInput()` | MEDIUM | NO | MEDIUM | HIGH | PARTIAL | OBSERVE |
| COMPAT-009 | `PermissionRefactorObservabilityProperties` related switches | MEDIUM | NO | MEDIUM | LOW | READY | NOT_NOW |

Selection result:

- TRY_NOW candidates in remaining B-level: none
- this accelerated round executes **evaluation only** (no new trial deletion)

Rationale:

- `COMPAT-001/002` still function as fallback + historical data compatibility mainline.
- `COMPAT-004` still lacks dedicated READY-level under-refresh shadow evidence for old-input removal.
- `COMPAT-009` remains essential for current gray governance control.

## COMPAT-004 Dedicated Shadow Evidence (Current Round)

Target confirmation:

- compat_id: `COMPAT-004`
- code_location: `PermissionVersionService.buildOldPermissionInput()`
- level: `B`
- current recommendation before this round: `OBSERVE`

Evidence implementation:

- 当前代码已停止生成 COMPAT-004 dedicated shadow evidence：
  - `PermissionVersionService` 已移除 `compat=COMPAT-004` shadow compare snapshot 与对应证据快照/单测证据生成入口
- `permissionsVersion` 正式指纹路径保持不变（fingerprint 逻辑未改）
- shadow 输出仅作为历史阶段观测/对比记录存在，不再在本阶段继续补证据

Artifacts:

- `test-results/compat-004-shadow-evidence.md`
- `test-results/compat-004-shadow-summary.json`

Shadow findings（历史证据）:

- scenarios covered: role assignment change (TENANT), role permission change (TENANT), permission master change (ORG), role hierarchy change (DEPT)
- leak refresh risk: not observed (`version_changed_when_expected=true` in all scenarios)
- reason equivalence: not yet equivalent in current comparison output (`reason_not_equivalent_found=true`)

Assessment conclusion for COMPAT-004:

- remain `OBSERVE` (not promoted to `TRY_NOW` in this round)
- blocker is explainability equivalence gap, not refresh-leak risk.

## COMPAT-004 Reason-Equivalence Rule-based Evidence

Artifacts:

- `test-results/compat-004-reason-equivalence.md`
- `test-results/compat-004-reason-equivalence-summary.json`

Rule set used:

- CATEGORY_A_ALIAS_ONLY => acceptable
- CATEGORY_B_SUBSUMED_BY_HIGHER_LEVEL_REASON => acceptable
- CATEGORY_C_MISSING_EXPLANATION => unacceptable
- CATEGORY_D_EXTRA_EXPLANATION_ONLY => acceptable
- CATEGORY_E_UNKNOWN_DIFF => TBD

Current round classification result:

- total scenarios: 4
- acceptable: 0
- unacceptable: 4
- TBD: 0
- normalizedEquivalent true: 0
- normalizedEquivalent false: 4
- dominant diff class: `CATEGORY_C_MISSING_EXPLANATION`

Updated recommendation for COMPAT-004:

- move from `OBSERVE` to `NOT_NOW` in this round.
- rationale: reason-equivalence is not closed under current rule set, and diff falls into unacceptable category.

## Phase Closure Baseline (Sealed)

Sealed facts:

- `COMPAT-003` removed and retained.
- `COMPAT-005` removed and retained.
- `COMPAT-004` is `NOT_NOW` after reason-equivalence rule evidence.
- `tenant5/tenant6/ORG/DEPT` gray results retained.
- runtime strict latest window is `0/0`.
- no new compat runtime blocking dependency observed.

Compat buckets in closure stage:

- Bucket A (removed and retained): `COMPAT-003`, `COMPAT-005`
- Bucket B (freeze in current phase): `COMPAT-001`, `COMPAT-002`, `COMPAT-004`, `COMPAT-009`
- Bucket C (next-phase only): menu-chain related compat (`COMPAT-006/007/008`) and other high-risk items

Closure hard constraints:

- stop adding new compat downscope actions in this phase.
- Bucket B enters freeze observation and is no longer a continuous-delivery target in this phase.
- Bucket C cannot start in this phase.

Next phase single mainline (fixed):

- 菜单链路迁移前评估

## Next Mainline Kickoff Status

Kickoff artifacts completed for menu pre-migration assessment:

- `test-results/permission-refactor-menu-pre-migration-dependency-inventory.md`
- `test-results/permission-refactor-menu-pre-migration-risk-matrix.md`
- `test-results/permission-refactor-menu-pre-migration-readiness-checklist.md`
- `test-results/permission-refactor-menu-pre-migration-rollback-rehearsal-plan.md`

Current decision:

- keep compat freeze policy unchanged in this stage.
- proceed with menu-chain pre-migration evaluation only.

## Next Round B-level Reassessment and Single-point Downscope

Latest factual inputs used:

- tenant expansion retained: `tenant5` + `tenant6`
- scope expansion retained: `ORG` + `DEPT`
- runtime strict breakdown: `rows/hits = 0/0`
- no new compat runtime dependency observed in latest window

B-level reassessment table:

| compat_id | code_location | runtime_dependency | menu_dependency | scope_dependency | data_dependency | recommendation |
| --- | --- | --- | --- | --- | --- | --- |
| COMPAT-001 | `SecurityUserAuthorityService.chooseCandidatePermissionCodeSet()` | high | no | high | high | OBSERVE |
| COMPAT-002 | `SecurityUserAuthorityService.loadOldPermissionCodeSet()` | high | no | high | high | NOT_NOW |
| COMPAT-004 | `PermissionVersionService.buildOldPermissionInput()` | medium | no | medium | high | OBSERVE |
| COMPAT-005 | `PermissionVersionService.resolveVersionChangeReasons()` | low | no | low | low | TRY_NOW |
| COMPAT-009 | `PermissionRefactorObservabilityProperties` switches | medium | no | medium | low | NOT_NOW |

Selected single candidate in this round:

- compat_id: `COMPAT-005`
- code_location: `PermissionVersionService.resolveVersionChangeReasons()`
- compat_type: `TEMPORARY_GRAY_COMPAT`
- reason:
  - non-menu
  - does not change authority/version main decision semantics
  - only shrinks temporary parallel reason output in observability lane

Trial action:

- removed `PERMISSION_MASTER_CHANGED` parallel reason add-on when `newPermissionDigest` changes.

Verification result (same baseline and post-check flow):

- focused tests: PASS
- smoke: PASS
- e2e overall: PASS
- suite1~suite6: all PASS
- runtime strict rows/hits: `0/0` -> `0/0`
- tenant5/tenant6/ORG/DEPT runtime hits: unchanged (`0`)

Round decision:

- keep deletion result for `COMPAT-005` trial downscope.
