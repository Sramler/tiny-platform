# Permission Refactor ORG Gray Preparation

## 1) Candidate ORG (Locked)

ORG candidate:

- tenantId: `1`
- orgId: `33`
- orgCode/orgName: `E2E_ORG_999001` / `E2E ORG 999001`
- linked test principal: `user_id=1`
- scoped role assignment evidence:
  - `role_assignment.id=372`, `scope_type=ORG`, `scope_id=33`, `role_id=5`, `status=ACTIVE`, `granted_by=-999001`

Selection reason:

- Candidate is already backed by stable ORG fixture and scope assignment.
- Candidate is under tenant `1`, which is already in gray allow-list and has existing operational evidence.
- Complexity is controlled (not the most complex org graph), suitable for first real ORG expansion.

Current risk notes:

- Fixture-based candidate (`created_by=-999001`), not organic production org.
- Needs runtime-window validation after real ORG inclusion.
- DEPT remains explicitly deferred.

## 2) ORG Real Gray Preconditions Checklist

| 前置条件 | 状态 | 证据 | 备注 |
|---|---|---|---|
| ORG fixture 已存在 | READY | `organization_unit.id=33, code=E2E_ORG_999001` | 来自 prepare-org-dept-fixtures |
| ORG scope role_assignment 已存在 | READY | `role_assignment.id=372, scope_type=ORG` | tenant=1, user=1, role=5 |
| ORG scope authority / version 测试证据已存在 | READY | Suite4 scope bucket PASS | 当前 E2E 总入口稳定 |
| 当前 ORG 未暴露串桶 | READY | 当前 gate/reports 未出现 ORG<->TENANT 串桶项 | 仍需真实窗口复核 |
| 当前 tenant 级 runtime 仍稳定 | READY | runtime strict rows/hits = 0/0 | 现窗口无 blocking |
| 当前无新增 runtime blocking | READY | `test-results/permission-runtime-signals-breakdown.json` | rows=0 |
| ORG 不依赖菜单迁移 | READY | 菜单链路未变更、suite5 PASS | 非菜单路径 |
| compat 侧无新增运行依赖 | READY | compatibility assessment 最新记录 | 本轮未删 compat |

## 3) ORG Gray Config Diff Template (For Next Execution Card)

Keep unchanged:

- `gray-tenant-allow-list`: keep current tenant list (currently `[1,3,5]`)
- `authority-diff-log-enabled: true`
- `permission-version-debug-enabled: true`
- `fail-closed-strict-enabled: true`
- `diff-sample-rate: 1.0`

Config diff template:

- Before:
  - `gray-scope-type-allow-list: [PLATFORM, TENANT]`
- After (ORG real gray execution):
  - `gray-scope-type-allow-list: [PLATFORM, TENANT, ORG]`

Hard constraints:

- DO NOT add `DEPT`
- DO NOT add extra tenants in same round

## 4) ORG Real Gray Verification Template

Pre-baseline (before ORG include):

1. focused tests (recommended):
   - `mvn -pl tiny-oauth-server -Dpermission.signal.source=TEST -Dtest=UserDetailsServiceImplTest,SecurityUserAuthorityServiceTest,PermissionVersionServiceTest test`
2. smoke:
   - `tiny-oauth-server/scripts/run-permission-dev-smoke-10m.sh`
3. e2e:
   - `tiny-oauth-server/scripts/e2e/run-permission-refactor-e2e.sh`
4. runtime strict export:
   - `python3 tiny-oauth-server/scripts/export-runtime-permission-signals.py --log-dir tiny-oauth-server/logs --json-out test-results/permission-runtime-signals-breakdown.json --markdown-out test-results/permission-runtime-signals-breakdown.md`

Post-change (after ORG include) run same commands in same order.

Must-observe fields:

- ORG / `OLD_PERMISSION_ONLY` (旧口径: `OLD_FALLBACK`)
- ORG / `DENY_UNKNOWN`
- ORG / `DENY_DISABLED`
- ORG-scope `permissionsVersion` change behavior
- ORG <-> TENANT bucket isolation
- suite4 scope bucket regression

## 5) ORG Expansion Blocking Items

O-1: ORG inclusion introduces runtime blocking signal  
O-2: ORG scope and TENANT scope bucket contamination  
O-3: ORG permissionsVersion abnormal no-change or jitter  
O-4: `suite4_scope_bucket` regression  
O-5: menu drift detected (`suite5_menu_non_drift` regression)  
O-6: new compat runtime dependency exposed by ORG traffic  
O-7: tenant-level runtime signals increase abnormally from current baseline

## 6) ORG Expansion Minimal Rollback Template

If ORG real gray fails:

1. Remove `ORG` from `gray-scope-type-allow-list`.
2. Keep `PLATFORM/TENANT` as-is.
3. Keep tenant `5` expansion result (do not rollback tenant list).
4. Re-run:
   - smoke
   - e2e
   - runtime strict export
5. Confirm metrics and suite status restored to pre-ORG baseline.

## 7) Formal Conclusion (This Task)

- Decision: **ORG 已具备大部分条件，但仍需补一小批前置项**。
- Missing small items before execution card:
  1. one dedicated real ORG runtime window evidence (not only fixture-triggered tests)
  2. explicit ORG runtime-signal extraction grouped by tenant=1/org=33 in report pipeline

## 8) Execution Result (ORG Real Gray Round)

- execution date: 2026-03-24
- scope change applied:
  - before: `gray-scope-type-allow-list = [PLATFORM, TENANT]`
  - after: `gray-scope-type-allow-list = [PLATFORM, TENANT, ORG]`
- tenant list unchanged: `[1,3,5]`
- validation result:
  - focused tests PASS
  - smoke PASS
  - e2e PASS
  - suite4 PASS
  - runtime strict rows/hits = `0/0`
- ORG blocking checks (`O-1`~`O-7`): no hit
- decision: **保留扩灰结果**
