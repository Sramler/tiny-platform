# Permission Refactor Runtime Signal Review

## Review Scope

- Focus only on `signal_source = RUNTIME`
- Target signal types:
  - `OLD_PERMISSION_ONLY` (旧口径: `OLD_FALLBACK`)
  - `DENY_DISABLED`
  - `DENY_UNKNOWN`

## Inputs

- Layered aggregate snapshot:
  - `test-results/permission-signal-governance-after.json`
- Runtime-only strict breakdown:
  - `test-results/permission-runtime-signals-breakdown.json`
  - `test-results/permission-runtime-signals-breakdown.md`

## Runtime Breakdown (strict, explicit-source only)

Current extraction rule:

- only count lines with explicit `signalSource=RUNTIME`
- ignore lines without `signalSource` to avoid historical mixed-source ambiguity

Result:

- total runtime rows: `0`
- total runtime hits: `0`

## Why this differs from prior `RUNTIME 3/2/2`

Prior layered totals treated missing `signalSource` as `RUNTIME` fallback in collector logic.
After signal-source rollout, test runners now force `-Dpermission.signal.source=TEST`.

Therefore:

- current explicit runtime review sees no natural-runtime hit in this short window
- previous `3/2/2` is classified as historical mixed-source artifact, not stable runtime evidence

## Runtime Decision Table

| permission_code | signal_type | tenantId | scopeType | scopeId | hit_count | cause_category | action_plan | impact_on_expand | owner | status |
| --- | --- | ---: | --- | --- | ---: | --- | --- | --- | --- | --- |
| (none) | (none) | - | - | - | 0 | (none) | 继续观察，不立即处理 | NON_BLOCKING | auth-permission | DONE |

## Blocking Assessment

- runtime blocking item exists: **NO**
- unresolved runtime unknown exists: **NO**
- unresolved runtime fallback exists: **NO**

## Expansion Impact

- Can prepare next-batch expansion evidence: **YES (prepare only)**
- Immediate expansion to DEPT: **NO (still deferred by strategy)**
- Suggested order remains:
  1. keep current tenant/scope observation
  2. if runtime keeps clean, consider controlled next-batch tenant/ORG step

