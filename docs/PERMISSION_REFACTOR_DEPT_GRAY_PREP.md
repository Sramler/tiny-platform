# Permission Refactor DEPT Gray Preparation

## 1) Candidate DEPT (Locked)

DEPT candidate:

- tenantId: `1`
- orgId: `33` (`E2E_ORG_999001`)
- deptId: `34`
- deptCode/deptName: `E2E_DEPT_999001` / `E2E DEPT 999001`
- linked scoped assignment evidence:
  - `role_assignment.id=373`
  - `scope_type=DEPT`
  - `scope_id=34`
  - `role_id=5`
  - `status=ACTIVE`
  - `granted_by=-999001`

Selection reason:

- Candidate is on the same tenant/org chain already stabilized in prior gray rounds (`tenant=1`, `org=33`).
- Fixture and scope assignment are already present; no extra tenant expansion required.
- Complexity is moderate and suitable for first DEPT real gray execution.

Current risk notes:

- DEPT has higher bucket-mixing risk than ORG (DEPT<->ORG and DEPT<->TENANT boundaries must be observed simultaneously).
- Candidate is fixture-based; still needs dedicated real runtime evidence window before broadening.

## 2) DEPT Real Gray Preconditions Checklist

| 前置条件 | 状态 | 证据 | 备注 |
|---|---|---|---|
| DEPT fixture 已存在 | READY | `organization_unit.id=34, code=E2E_DEPT_999001` | `prepare-org-dept-fixtures.sql` |
| DEPT scope role_assignment 已存在 | READY | `role_assignment.id=373, scope_type=DEPT` | tenant=1, principal=1, role=5 |
| DEPT scope authority/version 测试证据可直接复用 | READY | suite4 scope bucket PASS | 当前入口可执行 |
| 当前 DEPT 未暴露 scope 串桶问题 | PARTIAL | 目前未纳入真实灰度；仅 fixture/test 证据 | 需真实窗口确认 |
| 当前 ORG/TENANT runtime 稳定 | READY | ORG/TENANT 扩灰已保留，runtime strict `0/0` | 无 blocking |
| 当前无新增 runtime blocking signal | READY | `permission-runtime-signals-breakdown.json` rows=0 | 当前窗口 |
| DEPT 不依赖菜单链路迁移 | READY | menu chain unchanged, suite5 PASS | 非菜单路径 |
| compat 侧无新增未收口风险 | READY | compatibility assessment latest | 未触发新增 runtime compat 依赖 |
| DEPT 与 ORG/TENANT 边界可单独观测 | PARTIAL | 模板已固化 | 待真实 DEPT 执行窗口 |
| DEPT 扩灰失败不影响 tenant5/ORG 结果 | READY | 回滚模板已固定为仅移除 DEPT | tenant/org 保持 |

## 3) DEPT Gray Config Diff Template (For Next Execution Card)

Keep unchanged:

- `gray-tenant-allow-list`: `[1,3,5]`
- `authority-diff-log-enabled: true`
- `permission-version-debug-enabled: true`
- `fail-closed-strict-enabled: true`
- `diff-sample-rate: 1.0`

Config diff template:

- Before:
  - `gray-scope-type-allow-list: [PLATFORM, TENANT, ORG]`
- After (DEPT real gray execution):
  - `gray-scope-type-allow-list: [PLATFORM, TENANT, ORG, DEPT]`

Hard constraints:

- DO NOT add tenant
- DO NOT remove ORG

## 4) DEPT Real Gray Verification Template

Pre-baseline:

1. focused tests
   - `mvn -pl tiny-oauth-server -Dpermission.signal.source=TEST -Dtest=UserDetailsServiceImplTest,SecurityUserAuthorityServiceTest,PermissionVersionServiceTest test`
2. smoke
   - `tiny-oauth-server/scripts/run-permission-dev-smoke-10m.sh`
3. e2e
   - `tiny-oauth-server/scripts/e2e/run-permission-refactor-e2e.sh`
4. runtime strict export
   - `python3 tiny-oauth-server/scripts/export-runtime-permission-signals.py --log-dir tiny-oauth-server/logs --json-out test-results/permission-runtime-signals-breakdown.json --markdown-out test-results/permission-runtime-signals-breakdown.md`

Post-change: run same commands in same order.

Must-observe DEPT fields:

- DEPT / `OLD_PERMISSION_ONLY` (旧口径: `OLD_FALLBACK`)
- DEPT / `DENY_UNKNOWN`
- DEPT / `DENY_DISABLED`
- DEPT-scope permissionsVersion behavior
- DEPT <-> ORG isolation
- DEPT <-> TENANT isolation
- `suite4_scope_bucket` regression

## 5) DEPT Expansion Blocking Items

D-1: DEPT inclusion introduces runtime blocking signal  
D-2: DEPT scope and ORG scope bucket contamination  
D-3: DEPT scope and TENANT scope bucket contamination  
D-4: DEPT permissionsVersion abnormal no-change or jitter  
D-5: `suite4_scope_bucket` regression  
D-6: menu drift detected (`suite5_menu_non_drift` regression)  
D-7: new compat runtime dependency exposed by DEPT traffic  
D-8: tenant/ORG-level runtime signals increase abnormally from baseline

## 6) DEPT Expansion Minimal Rollback Template

If DEPT real gray fails:

1. Remove `DEPT` from `gray-scope-type-allow-list`.
2. Keep `PLATFORM/TENANT/ORG` unchanged.
3. Keep tenant `5` expansion result.
4. Keep ORG expansion result.
5. Re-run:
   - smoke
   - e2e
   - runtime strict export
6. Confirm status restored to pre-DEPT baseline.

## 7) Formal Conclusion (This Task)

- Decision: **DEPT 已具备大部分条件，但仍需补一小批前置项**。
- Remaining small items before execution card:
  1. one dedicated DEPT runtime evidence window (not just fixture-triggered test evidence)
  2. explicit DEPT runtime signal extraction grouped by tenant=1/org=33/dept=34 in reporting pipeline

## 8) Execution Result (DEPT Real Gray Round)

- execution date: 2026-03-24
- scope change applied:
  - before: `gray-scope-type-allow-list = [PLATFORM, TENANT, ORG]`
  - after: `gray-scope-type-allow-list = [PLATFORM, TENANT, ORG, DEPT]`
- tenant list unchanged: `[1,3,5]`
- validation result:
  - focused tests PASS
  - smoke PASS
  - e2e PASS
  - suite4 PASS
  - runtime strict rows/hits = `0/0`
- DEPT blocking checks (`D-1`~`D-8`): no hit
- decision: **保留扩灰结果**

Note on fixture IDs:

- current runtime DB generated `orgId=37`, `deptId=38`, `role_assignment.id=377` for the same canonical fixture codes.
- canonical candidate identity remains `E2E_ORG_999001` + `E2E_DEPT_999001`.
