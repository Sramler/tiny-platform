# Permission Refactor Gray Gate Review (Go / No-Go)

## 1) 当前评审范围

- tenant: `1`, `3`
- scope: `PLATFORM`, `TENANT`（配置已允许 ORG 候选样本）
- diff-sample-rate: `1.0`
- ORG/DEPT 是否纳入: ORG 已做候选短窗口样本，DEPT 未纳入
- 评审时间窗口: 2026-03-24（基于最新脚本执行与当前测试库快照）

## 2) 配置快照

来源：`tiny-oauth-server/src/main/resources/application-dev.yaml`

```yaml
permission-refactor:
  authority-diff-log-enabled: true
  permission-version-debug-enabled: true
  fail-closed-strict-enabled: true
  gray-tenant-allow-list: [1, 3]
  gray-scope-type-allow-list: [PLATFORM, TENANT, ORG]
  diff-sample-rate: 1.0
```

## 3) 证据输入清单

- 数据层
  - `test-results/gray-gate-observability.out`
  - `test-results/gray-gate-scope-bucket.out`
- 运行时/阶段验收
  - `docs/PERMISSION_REFACTOR_PHASE_F_GRAY_REPORT.md`
  - `docs/PERMISSION_REFACTOR_DEV_SMOKE_10M_REPORT.md`
- 自动化验证
  - `test-results/permission-refactor-e2e-summary.json`
  - `test-results/permission-refactor-e2e-summary.md`
  - `test-results/permission-refactor-e2e-signals.json`
  - `test-results/permission-refactor-e2e-signals.md`

## 4) A~F 检查项逐项结论

| 检查项 | 状态 | 证据 | 备注 |
| --- | --- | --- | --- |
| A. 数据与结构基础 | 满足 | `gray-gate-observability.out` | `permission/role_permission` 均有有效数据，平台/租户投影成立 |
| B. 运行时主链路 | 满足 | `permission-refactor-e2e-summary.json` | D-1/D-2 相关能力已在 E2E 总入口中稳定通过 |
| C. 观测与可解释性 | 部分满足 | `permission-refactor-e2e-signals.json` + `PERMISSION_REFACTOR_PHASE_F_GRAY_REPORT.md` | fallback/deny 可观测；permissionsVersion reason 在当前窗口未命中 |
| D. E2E 与联调验证 | 满足 | `permission-refactor-e2e-summary.json` | Suite1~6 全 PASS，pending 已收口，短稳 10/10 通过 |
| E. 灰度风险控制 | 满足 | `application-dev.yaml` + `PERMISSION_REFACTOR_PHASE_F_GRAY_REPORT.md` | 白名单、scope 白名单、采样率、报告工件均已具备 |
| F. 阻断项检查 | 未命中阻断 | 见下节 | 当前无硬阻断命中 |

## 5) 关键指标汇总

来源：`test-results/permission-refactor-e2e-signals.json`（最新 ORG 证据复跑）

- OLD_PERMISSION_ONLY: `35`（旧口径: `OLD_FALLBACK`）
- DENY_DISABLED: `23`
- DENY_UNKNOWN: `23`
- ROLE_ASSIGNMENT_CHANGED: `0`
- OLD_PERMISSION_INPUT_CHANGED: `0`
- ROLE_PERMISSION_CHANGED: `0`
- PERMISSION_MASTER_CHANGED: `0`
- ROLE_HIERARCHY_CHANGED: `0`
- 是否存在 scope 串桶: 未见证据（当前信号已包含 ORG/TENANT 双桶，未见串桶）
- 菜单是否稳定: 稳定（Suite5 PASS，未迁菜单链路）
- E2E 总结果: PASS（含 Suite4/6 收口）
- mutate/restore 自动恢复: 成立（summary: `mutate_restore_count=2`, `short_stability_passed=10/10`）

补充：  
`gray-gate-observability.out` 显示 `permission.enabled` 在平台/租户均存在 0/1 分布，`role_permission` 在平台与租户均有数据；  
`gray-gate-scope-bucket.out` 显示当前活跃 assignment 桶包含 `PLATFORM(NULL)`、`TENANT(1)`、`TENANT(3)`；在 ORG 候选窗口中额外出现 `ORG(tenant=1,scope_id=5)` 样本桶。  
最新 collector 结果已出现 ORG 运行信号桶：`ORG -> OLD_PERMISSION_ONLY=11, DENY_DISABLED=11, DENY_UNKNOWN=11`。

## 6) 阻断项检查（F-1 ~ F-8）

- F-1 `DENY_UNKNOWN` 明显上升且原因不明：**否**
- F-2 `OLD_PERMISSION_ONLY` 大量出现且无法解释：**否（当前可解释为测试窗口的已知回归样本）**
- F-3 `role_hierarchy` 变化后 version 无反应：**否（变更联动回归已通过）**
- F-4 `permission.enabled = 0` 后仍继续放权：**否**
- F-5 tenant/scope 串用：**否**
- F-6 菜单明显漂移：**否**
- F-7 E2E 自动恢复失败、存在脏状态残留：**否**
- F-8 ORG/DEPT 已纳入灰度但分桶不成立：**否（ORG 仅候选样本，未见分桶异常；DEPT 未纳入）**

结论：

- 是否命中阻断项：**否**
- 命中项编号：**无**
- 是否允许继续扩围：**谨慎可扩（建议先保持一轮观察后再扩大）**

## 7) 正式结论（三选一）

最终结论：

- 判定：**保持当前范围继续观察**
- 理由 1：非菜单链路与 E2E 主干/收口项均通过，结构稳定性已建立。
- 理由 2：fallback/deny 当前可解释，但 `permissionsVersion` reason 在当前窗口样本仍偏少。
- 理由 3：ORG/DEPT 已具备测试可执行能力，但尚未进入真实灰度样本窗口。

## 8) 下一批范围建议

- tenant: 保持 `1,3` 再观察一轮；随后新增 1 个复杂度中等租户（单租户、非关键业务）
- scope: 下一批保持 `PLATFORM/TENANT`；下一阶段单独引入 `ORG`，`DEPT` 再后置
- 观察窗口: 建议再执行 1 轮 24h 信号采集（含 reason 分布）
- 是否纳入 ORG: 下一阶段可纳入（单独批次）
- 是否纳入 DEPT: 暂不纳入（待 ORG 样本稳定后）
- 回滚方式:
  1. 从 `gray-tenant-allow-list` 移除新增租户
  2. 关闭 `permission-version-debug-enabled` 与 `authority-diff-log-enabled`（如日志压力过大）
  3. 保持 D-1/D-2 核心逻辑不回退
  4. 不变更菜单链路

## 9) ORG-only 候选样本更新

- 范围控制：
  - 已将 `ORG` 纳入灰度 scope allow-list 用于候选样本验证。
  - `DEPT` 仍未纳入。
- 候选对象：
  - `tenant=1`，ORG 候选桶已准备（fixture 与候选映射已导出）。
- 复验结果：
  - 定向后端回归：PASS
  - E2E 总入口（Suite1~6）：PASS
- 信号结果（本轮）：
  - `OLD_PERMISSION_ONLY=35`
  - `DENY_DISABLED=23`
  - `DENY_UNKNOWN=23`
  - ORG 分桶已命中：`OLD_PERMISSION_ONLY=11`, `DENY_DISABLED=11`, `DENY_UNKNOWN=11`
  - 未出现新增阻断项

结论保持不变：

- **保持当前范围继续观察**。
- 下一步仍为：先执行 ORG 独立运行窗口，再评估是否放开 DEPT。

## 10) 评审结论摘要

本次评审已完成 A~F 全量检查与证据归档；当前建议不是“立即大幅扩围”，而是“保持范围 + 再观察一轮 + 分阶段引入 ORG/DEPT”，以确保放量决策持续由证据驱动。

## 11) Fallback/Unknown 治理收口更新

新增证据：

- `docs/PERMISSION_REFACTOR_SIGNAL_GOVERNANCE_PLAN.md`
- `test-results/permission-signal-governance-before.json`
- `test-results/permission-signal-governance-after.json`
- `test-results/permission-signal-governance-summary.md`

治理动作：

- 已执行主数据/映射/旧值清理（仅数据治理）：
  - `fix-resource-permission-legacy.sql`
  - `fix-permission-main-data.sql`
  - `fix-role-permission-mapping.sql`（已从仓库删除/DEPRECATED 归档）
- 未修改菜单链路、未修改 deny/fallback 主逻辑语义。

治理后结论：

- 阻断项状态未恶化，仍无新增硬阻断命中。
- `DENY_UNKNOWN` / `OLD_PERMISSION_ONLY` 当前口径下已可归因（主要为测试路径样本），不存在“原因不明”项。
- 聚合计数未下降（35/23/23 -> 35/23/23），但未出现扩散迹象，且 DB 侧可治理项已完成本轮收口。

对放量结论影响：

- 维持原结论：**保持当前范围继续观察**。
- 维持策略：ORG 可继续观察，DEPT 继续后置。

## 12) Signal Source 分层口径更新（TEST vs RUNTIME）

新增口径规则：

- `signal_source` 固定枚举：`TEST` / `RUNTIME`
- 扩围评审优先看 `RUNTIME`，`TEST` 作为回归断言质量参考

最新分层结果（collector）：

- ALL：`OLD_PERMISSION_ONLY=36`，`DENY_DISABLED=24`，`DENY_UNKNOWN=24`
- TEST：`OLD_PERMISSION_ONLY=33`，`DENY_DISABLED=22`，`DENY_UNKNOWN=22`
- RUNTIME：`OLD_PERMISSION_ONLY=3`，`DENY_DISABLED=2`，`DENY_UNKNOWN=2`

评审含义：

- 历史“混合口径”中的高计数主要由 TEST 驱动。
- 在 RUNTIME 口径下，当前阻断项检查更可操作，避免被测试断言流量误伤。
- 当前三选一结论维持不变：**保持当前范围继续观察**（不直接扩围，不引入 DEPT）。

## 13) RUNTIME 明细观察结论（只看真实运行信号）

证据：

- `docs/PERMISSION_REFACTOR_RUNTIME_SIGNAL_REVIEW.md`
- `test-results/permission-runtime-signals-breakdown.json`

结论：

- 在“显式 `signalSource=RUNTIME`”口径下，本窗口 runtime 明细命中为 `0`。
- 当前未发现 runtime blocking 项。
- 放量评审仍按策略谨慎推进：可准备下一批证据，但不直接引入 DEPT。

## 14) Compatibility Layer Assessment 引用结论

新增评估工件：

- `docs/PERMISSION_REFACTOR_COMPATIBILITY_LAYER_ASSESSMENT.md`
- `test-results/permission-compatibility-layer-inventory.json`
- `test-results/permission-compatibility-layer-inventory.md`

结论摘要：

- 当前已进入“兼容层剔除评估阶段”：**是**
- 当前已进入“兼容层可执行删除阶段”：**部分是（仅 C 级候选）**
- 菜单链路兼容点（Level A）当前明确不可动。
- DEPT 相关与菜单旧路径继续后置，维持现有灰度策略不变。

## 15) COMPAT-003 试删执行结果

执行记录：

- before baseline: smoke PASS / e2e PASS
- trial deletion target: `UserDetailsServiceImpl.resolveAuthorities()` null-service fallback branch
- trial result:
  - smoke PASS
  - e2e overall FAIL
  - failed suites: `suite1`, `suite2`, `suite3`, `suite6`
- menu_non_drift: PASS

结论：

- 按执行规则已执行最小回滚，并完成回滚后 smoke+e2e 复验（均 PASS）。
- 当前不保留删除结果；COMPAT-003 维持保留，待前置条件补齐后再试删。

## 16) COMPAT-003 根因与前置条件补齐结果

新增证据：

- `test-results/compat-003-root-cause-analysis.md`
- `test-results/compat-003-preconditions-checklist.md`
- `test-results/compat-003-wiring-scan.md`
- `test-results/compat-003-guard-tests-summary.md`

根因分类结论：

- TEST_WIRING_INCOMPLETE
- LEGACY_SECURITYUSER_CONSTRUCTION_DEPENDENCY
- MOCK_OR_STUB_PATH_NOT_UPDATED
- PARTIAL_MIGRATION_OF_AUTHORITY_ENTRY

本轮补齐动作：

- `UserDetailsServiceImplTest` 统一注入 `SecurityUserAuthorityService` mock，消除关键测试链路的 null 装配依赖。
- 完成仓库范围装配路径扫描，未发现新增 HIGH 风险测试/替身装配路径。
- 新增 guard tests，固化 authority service 作为主入口的测试约束。
- 保留 compat 分支前提下重新验证：smoke PASS + e2e PASS（suite1~suite6 全 PASS）。

阶段结论：

- 根因已查清。
- 前置条件已补齐关键项，剩余为补充型 guard（非阻断）。
- 可进入“第二次试删准备阶段”，但本轮不执行第二次试删。

## 17) COMPAT-003 第二次试删结果（加强门禁）

执行范围：

- 仅删除 `UserDetailsServiceImpl.resolveAuthorities()` 的 null-service fallback 分支。
- 未触碰菜单链路、未触碰其他 compat 点。

删除后验证（同口径）：

- guard tests: PASS
- smoke: PASS
- e2e overall: PASS
- suite1_auth_context: PASS
- suite2_permission_linkage: PASS
- suite3_new_old_consistency: PASS
- suite6_short_stability: PASS
- suite5_menu_non_drift: PASS
- runtime strict rows: 0

结论：

- 本轮结论为：**保留删除结果**。
- 对灰度准入判断无负向影响，继续保持“当前范围观察、DEPT 后置”的主结论。

## 18) UserDetailsServiceImpl 构造契约收尾结果

本轮收尾动作：

- 删除 `UserDetailsServiceImpl` 的一参/两参旧构造器。
- 保留并强化完整构造器（显式依赖 `SecurityUserAuthorityService`）。
- 增加 fail-fast guard，阻止构造无效对象。
- 新增 guard test 防止旧构造方式回流。

验证结果：

- focused tests PASS
- smoke PASS
- e2e PASS（suite1~suite6 全 PASS）
- runtime strict rows = 0

评审结论：

- 该收尾仅减少技术债，不改变灰度结论。
- 对兼容层后续收口是正向信号，但不构成菜单链路/DEPT 放量前提变更。

## 19) 下一批 TENANT 扩灰执行结果

本轮扩灰动作：

- 新增 `tenantId=5 (gray-mid-tenant)` 进入 `gray-tenant-allow-list`
- 扩灰范围保持非菜单链路，并将 real gray scope 固定为 `PLATFORM/TENANT`
- 未引入 ORG 真实扩灰，DEPT 继续后置

扩灰后结果：

- focused tests PASS
- smoke PASS
- e2e overall PASS
- suite1/2/3/6 全 PASS
- suite5_menu_non_drift PASS
- runtime strict rows = 0
- new tenant runtime hits = 0（当前观察窗口）

对后续放量判断影响：

- 本轮可保留扩灰结果。
- 可以继续保持“单 tenant 逐步扩围 + ORG/DEPT 后置”的策略，不建议跨级放量。

## 20) ORG 真实扩灰准备推进结果

本轮动作：

- 仅完成 ORG 真实扩灰准备，不立即放量。
- 锁定首个 ORG 候选：`tenant=1`, `orgId=33 (E2E_ORG_999001)`。
- 固化配置差异模板、验证入口模板、阻断项与最小回滚模板。

关键口径：

- 当前 tenant 扩灰结果保持稳定（tenant 5 保留）。
- ORG 未加入真实 gray scope 白名单（本轮无放量动作）。
- DEPT 继续后置，不纳入本轮准备结论。

阶段结论：

- ORG 已从“候选讨论阶段”推进到“可执行准备阶段”。
- readiness judgement: **PARTIAL_READY**（尚需一轮 dedicated ORG runtime window 证据）。

## 21) ORG 真实扩灰执行结果

本轮执行动作：

- 将 `ORG` 纳入 gray scope allow-list（未引入 DEPT）。
- tenant 列表保持 `1,3,5` 不扩张。

执行结果：

- focused tests PASS
- smoke PASS
- e2e overall PASS
- suite1/2/3/4/5/6 全 PASS
- menu_non_drift PASS
- runtime strict rows/hits = `0/0`
- ORG blocking items O-1~O-7: 未命中

结论：

- ORG 扩灰结果：**保留**
- 当前 gate 结论：可进入 ORG 稳定观察窗口；DEPT 仍后置，不进入本轮。

## 22) DEPT 真实扩灰准备推进结果

本轮动作：

- 仅完成 DEPT 真实扩灰准备，不立即放量。
- 锁定首个 DEPT 候选：`tenant=1`, `orgId=33`, `deptId=34 (E2E_DEPT_999001)`。
- 固化配置差异模板、验证入口模板、阻断项（D-1~D-8）与最小回滚模板。

关键口径：

- tenant 5 扩灰结果保持保留。
- ORG 扩灰结果保持保留。
- DEPT 未加入真实 gray scope allow-list（本轮无放量动作）。

阶段结论：

- DEPT 已从“后置对象”推进到“可执行准备阶段”。
- readiness judgement: **PARTIAL_READY**（仍需一轮 dedicated DEPT runtime evidence）。

## 23) DEPT 真实扩灰执行结果

本轮执行动作：

- 将 `DEPT` 纳入 gray scope allow-list（不新增 tenant，不移除 ORG）。
- scope 变更：`[PLATFORM, TENANT, ORG] -> [PLATFORM, TENANT, ORG, DEPT]`。

执行结果：

- focused tests PASS
- smoke PASS
- e2e overall PASS
- suite1/2/3/4/5/6 全 PASS
- menu_non_drift PASS
- runtime strict rows/hits = `0/0`
- DEPT blocking items D-1~D-8: 未命中

结论：

- DEPT 扩灰结果：**保留**
- 当前 gate 建议：进入更大范围前仍保持一轮稳定观察，不做跨级放量。

## 24) 更大范围 TENANT 扩围评审结果（scope 不变）

执行范围控制：

- 仅扩围 tenant 样本，不新增 scope 类型。
- `gray-scope-type-allow-list` 保持 `[PLATFORM, TENANT, ORG, DEPT]` 不变。
- 菜单链路未改动，compat 点未删减。

本轮新增 tenant：

- `tenantId=6`, `tenantCode=gray-mid-tenant-b`, `tenantName=灰度中复杂租户B`
- complexity: `role_count=4`, `role_permission_count=10`

扩围后同口径结果：

- focused tests: PASS
- smoke: PASS
- e2e overall: PASS
- suite1/2/3/4/5/6: all PASS
- runtime strict rows/hits: `0/0`
- new tenant runtime-only fallback/deny/hits: `0/0/0`
- tenant5/ORG/DEPT runtime hits: all unchanged (`0`)

阻断项检查（T-1~T-8）：

- none hit in this window.

对后续 compat 收口影响：

- 本轮新增 tenant 未暴露新的 compat runtime 依赖。
- 可将本轮结果作为“tenant 维度样本增强证据”，支持后续 compat 收口评审；
  但仍建议保持“逐步收口 + 每步同口径回归”的节奏，不建议一次性批量删除 compat。

正式结论（三选一）：

- **保留扩围结果**。

## 25) 下一批 compat 收口（B 级单点）执行结果

本轮执行策略：

- 先重评全部 B 级候选，再仅选择 1 个 `TRY_NOW`。
- 严格保持：不碰菜单链路、不新增 tenant/scope、不批量删除。

重评结论：

- `COMPAT-001`: OBSERVE
- `COMPAT-002`: NOT_NOW
- `COMPAT-004`: OBSERVE
- `COMPAT-005`: TRY_NOW（唯一入选）
- `COMPAT-009`: NOT_NOW

本轮试删点：

- `compat_id=COMPAT-005`
- `code_location=PermissionVersionService.resolveVersionChangeReasons()`
- 变更内容：移除 `PERMISSION_MASTER_CHANGED` 并行 reason 兼容输出（仅观测解释层，不改版本主判定）

删除后同口径结果：

- focused tests PASS
- smoke PASS
- e2e overall PASS
- suite1/2/3/4/5/6 all PASS
- menu_non_drift PASS
- runtime strict rows/hits 维持 `0/0`
- tenant5/tenant6/ORG/DEPT runtime hits 均未扰动
- 未暴露新的 compat runtime 依赖

结论（三选一）：

- **保留删除结果**

对后续顺序影响：

- 仍建议保持“单点收口 -> 同口径回归 -> 再下一个候选”的节奏。
- 下一优先观察仍在 `COMPAT-004` 与 `COMPAT-001/002` 前置条件成熟度，不建议跳到批量收口。

## 26) 多点并行评估 + 单点串行试删（加速版）结果

本轮执行方式：

- 对剩余 B 级（`COMPAT-001/002/004/009`）并行再评估与证据补齐
- 删除策略保持串行，最多 1 个候选

并行评估结论：

- `COMPAT-001`: OBSERVE（fallback 主干依赖仍高）
- `COMPAT-002`: NOT_NOW（历史数据兼容主责仍在）
- `COMPAT-004`: OBSERVE（shadow evidence 仍 PARTIAL）
- `COMPAT-009`: NOT_NOW（灰度治理开关仍关键）

候选选择结果：

- 本轮剩余 B 级 `TRY_NOW`：无
- 因此本轮不进入试删

对后续顺序影响：

- 继续采用“并行评估提速 + 单点删除保守”节奏是合理的。
- 下一优先工作是保持阶段封版：`COMPAT-004` 的 dedicated shadow evidence 生成逻辑已移除（仅保留历史证据与冻结结论），不再进入该点证据补齐/试删节奏。
- `COMPAT-001/002` 应继续绑定数据治理与 fallback 主链路稳定性观察窗口。

本轮三选一结论：

- **暂不推进本轮 compat 收口**

## 27) COMPAT-004 Shadow 证据补齐结果

本轮范围（历史证据记录）：

- （历史证据）仅补 `COMPAT-004` dedicated shadow 证据，不做删除（当前代码不再生成该类证据）。
- 菜单链路未改动，tenant/scope 未新增。

结果摘要：

- focused tests（含 `PermissionVersionServiceTest`）PASS
- smoke PASS
- e2e overall PASS（suite1~suite6 全 PASS）
- runtime strict rows/hits 仍为 `0/0`
- shadow 结论：
  - 无漏刷新风险证据（四个场景 `version_changed_when_expected=true`）
  - 但 reason 等价性仍未达标（`reason_not_equivalent_found=true`）

对收口顺序影响：

- `COMPAT-004` 当前不宜直接进入试删，结论保持 `OBSERVE`。
- 下一步应先补 reason-equivalence 的规则化映射证据，再决定是否升级为 `TRY_NOW`。

## 28) COMPAT-004 Reason-Equivalence 规则化证据结果

本轮新增：

- 对 withReasons/withoutReasons 的 raw diff 做了枚举、分类、可接受性判定、normalized comparison。
- 新证据工件：
  - `test-results/compat-004-reason-equivalence.md`
  - `test-results/compat-004-reason-equivalence-summary.json`

结果：

- 4/4 场景落入 `CATEGORY_C_MISSING_EXPLANATION`（unacceptable）
- normalizedEquivalent: `0/4`
- final recommendation: `NOT_NOW`

链路稳定性：

- focused tests PASS
- smoke PASS
- e2e PASS（suite1~suite6 全 PASS）
- runtime strict rows/hits 仍为 `0/0`

对收口顺序影响：

- `COMPAT-004` 不应进入下一张单点试删卡。
- 应先修复 reason explainability 对齐策略，再重新进入 shadow 评估。

## 29) 当前阶段封版收口结论（已加固）

封版确认：

- 当前阶段从“持续试删”切换为“阶段性交付封版”。
- 本阶段停止新增 compat 收口动作。
- 剩余 compat 进入冻结观察，不再作为本阶段持续推进项。

冻结分桶（引用封版工件）：

- A（已完成删除并保留）：`COMPAT-003`, `COMPAT-005`
- B（当前冻结）：`COMPAT-001`, `COMPAT-002`, `COMPAT-004`, `COMPAT-009`
- C（下阶段处理）：菜单链路相关 compat

轻量一致性确认：

- runtime strict: `0/0`
- latest e2e overall: PASS

下阶段唯一主线（写死）：

- **菜单链路迁移前评估**

## 30) 主线推进状态（菜单链路迁移前评估）

已完成：

- 旧链路依赖盘点
- 菜单迁移风险矩阵
- 准入检查清单初始化
- 回滚演练计划文档化
- 新旧查询对照草案（query parity draft）
- 最小迁移切换策略草案（minimal cutover strategy draft）
- Stage 1（shadow-read only）执行卡草案
- Stage 1 检查清单草案

未进入：

- 菜单链路真实迁移执行（仍处于评估阶段）

## 31) Stage 1（Menu Shadow-Read, No Cutover）实施结果

本轮实施范围：

- 已在菜单读取链路接入 shadow-read comparator
- 正式响应仍保持 old-path（`role_resource -> resource`）
- new-path（`role_permission -> permission -> resource`）仅用于对比日志

实施与验证结果：

- focused tests PASS（含新增 `MenuServiceImplTest` shadow 行为用例）
- smoke PASS
- e2e overall PASS（suite1~suite6 全 PASS）
- runtime strict rows/hits 维持 `0/0`

Stage1 观测汇总工件：

- `test-results/permission-refactor-menu-shadow-read-stage1-implementation-summary.md`
- `test-results/permission-refactor-menu-shadow-read-stage1-signals.json`
- `test-results/permission-refactor-menu-shadow-read-stage1-signals.md`

当前窗口结论：

- `boundary_risk_count = 0`
- 未发现 Stage 1 阻断信号
- 可进入 Stage 2 canary-read 任务定义（仍需保持不跨租户/不跨 scope 的灰度控制）

## 32) Stage 2（Menu Canary-Read）任务定义就绪

新增工件：

- `test-results/permission-refactor-menu-canary-read-stage2-execution-card.md`
- `test-results/permission-refactor-menu-canary-read-stage2-checklist.md`

当前结论：

- Stage 2 尚未执行，仅完成任务定义与门禁清单。
- Go/No-Go 评审前置条件保持不变：
  - runtime strict `0/0`
  - latest focused/smoke/e2e PASS
  - canary fallback 热切换能力可验证

## 33) Stage 2（Menu Canary-Read）实现结果（默认关闭）

本轮代码结果：

- 已实现 canary-read 受控开关（默认关闭）：
  - `menuCanaryReadEnabled`
  - `menuCanaryTenantAllowList`
  - `menuCanarySampleRate`
- 菜单读链路新增决策：
  - canary 命中且新旧一致 -> 使用 new-path
  - 存在 mismatch 或 boundary risk -> 立即 fallback old-path

回归结果：

- focused tests PASS（含 canary use/fallback 两个分支用例）
- smoke PASS
- e2e overall PASS（suite1~suite6 全 PASS）
- runtime strict rows/hits = `0/0`

当前 gate 结论：

- Stage 2 已具备“可受控开启”能力，但尚未开启真实 canary 窗口。
- 下一步是按执行卡进入受控 canary 窗口并做 mismatch/boundary 风险连续观测。

## 34) Stage 2 受控窗口首轮验证结果

配置口径（dev）：

- `menu-canary-read-enabled: true`
- `menu-canary-tenant-allow-list: [1]`
- `menu-canary-sample-rate: 0.1`

验证结果：

- focused tests PASS（包含 canary use/fallback 分支）
- smoke PASS
- e2e overall PASS（suite1~suite6 全 PASS）
- runtime strict rows/hits = `0/0`

canary 信号导出：

- `test-results/permission-refactor-menu-canary-read-stage2-signals.json`
- `test-results/permission-refactor-menu-canary-read-stage2-signals.md`

当前窗口结论：

- `total_canary_reads = 0`（当前窗口无可统计 runtime canary 样本）
- `canary_boundary_risk_count = 0`
- Gate 判定：**PARTIAL_GO**（代码与门禁通过，待补非零 canary runtime 样本后再做 Stage2 pass/close）

补充（受控样本采集）：

- 已通过 focused 样本日志补齐非零 canary reads：
  - `total_canary_reads=2`
  - `canary_new_path_reads=1`
  - `canary_fallback_reads=1`
  - `canary_boundary_risk_count=0`
- 当前 mismatch 由受控构造样本触发（用于验证 fallback 热路径），未触发边界风险。

当前 gate 维持：

- **PARTIAL_GO**：允许继续 Stage2 受控窗口；
- Stage2 关闭条件仍需真实 runtime 连续窗口（非构造样本）证明 mismatch 收敛。

## 35) Stage 2 真实 runtime 连续窗口补样结果

本轮动作：

- 执行 real-link 菜单访问采样（平台登录 -> `/sys/menus/tree`）。
- 导出 runtime-only canary 信号工件（仅统计真实窗口日志）：
  - `test-results/permission-refactor-menu-canary-read-stage2-runtime-window-signals.json`
  - `test-results/permission-refactor-menu-canary-read-stage2-runtime-window-signals.md`

runtime-only 结果：

- `total_canary_reads = 2`
- `canary_new_path_reads = 2`
- `canary_fallback_reads = 0`
- `canary_mismatch_rate = 0.0`
- `canary_boundary_risk_count = 0`

Gate 结论更新：

- Stage 2 由 `PARTIAL_GO` 提升为 **CLOSE_GO**（已获得非零真实窗口样本，且 mismatch/boundary 风险为 0）。
- 可进入 Stage 3 准备（保持读路径受控，不扩大非必要变量）。

## 36) Stage 3 准备状态（Broader Switch, Read-Path Only）

本轮动作：

- 已新增 Stage 3 执行卡与检查清单：
  - `test-results/permission-refactor-menu-broader-switch-stage3-execution-card.md`
  - `test-results/permission-refactor-menu-broader-switch-stage3-checklist.md`

准备结论：

- Stage 3 当前状态：**READY_FOR_WINDOW**
- 当前策略：先执行“单变量扩展”（sample-rate 或 tenant allow-list 二选一），保持读路径与即时回退约束不变。
- Gate 要求保持：runtime strict `0/0`、boundary risk `0`、mismatch_rate 在阈值内。

## 37) Stage 3 首个 broader runtime 窗口结果

本轮执行：

- 仅做单变量扩展：`menu-canary-sample-rate` 从 `0.3` 提升到 `1.0`。
- tenant allow-list 保持不变（`[1, 3]`），未新增 scope。
- real-link 菜单访问窗口采样后导出 Stage3 runtime-only 信号：
  - `test-results/permission-refactor-menu-broader-switch-stage3-runtime-window-signals.json`
  - `test-results/permission-refactor-menu-broader-switch-stage3-runtime-window-signals.md`

窗口结果：

- `total_canary_reads = 6`
- `canary_new_path_reads = 6`
- `canary_fallback_reads = 0`
- `canary_mismatch_rate = 0.0`
- `canary_boundary_risk_count = 0`

Gate 结论：

- Stage 3 首个 broader window：**PASS**
- 下一步可进入 Stage 4 提案评审（最终 read-path switch proposal），并继续保留可回退约束。

## 38) Stage 4 提案评审准备状态（Final Read-Path Switch）

本轮动作：

- 已新增 Stage 4 执行卡与检查清单：
  - `test-results/permission-refactor-menu-final-read-switch-stage4-execution-card.md`
  - `test-results/permission-refactor-menu-final-read-switch-stage4-checklist.md`

准备结论：

- Stage 4 当前状态：**PROPOSAL_READY**
- 当前不直接执行 final switch，仅进入 Go/No-Go 评审窗口。
- 保持约束不变：runtime strict `0/0`、boundary risk hard-stop、old-path 可一键回退。

## 39) Stage 4 Go/No-Go 评审结论（提案窗口）

本轮新增评审工件：

- `test-results/permission-refactor-menu-final-read-switch-stage4-go-no-go.md`

结论摘要：

- Go/No-Go 判定：**GO_RECOMMENDED**
- 依据：
  - Stage 2 runtime-only close：`reads=2`, `mismatch_rate=0.0`, `boundary_risk=0`
  - Stage 3 broader window：`reads=6`, `mismatch_rate=0.0`, `boundary_risk=0`
  - runtime strict 维持 `0/0`
- 约束：
  - 该结论为“可执行建议”，最终执行审批仍保持人工确认。
  - 执行窗口必须启用硬判停与一键回退路径。

## 40) Stage 4 执行窗口结论

本轮新增执行摘要：

- `test-results/permission-refactor-menu-final-read-switch-stage4-execution-summary.md`

执行结论：

- 执行窗口结果：**PASS**
- 决策：**KEEP_SWITCH_ON_APPROVED_SCOPE**（在已批准范围保持 final read-path switch）
- 判停项复核：
  - `MISMATCH_BOUNDARY_RISK`: 未命中
  - mismatch 超阈值: 未命中
  - runtime strict 异常: 未命中（`0/0`）
  - menu 关键流程回归: 未命中
