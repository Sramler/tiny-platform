# Permission Refactor｜进入更大范围联调 / 灰度的准入标准

## 文档目标

用于判断当前权限模型演进成果，是否已经具备从“小范围开发/测试验证”进入“更大范围联调 / 灰度”的条件。

本准入标准只针对：

- 非菜单链路
- `authority` / `permissionsVersion` / `fallback` / `deny` / `scope bucket`

不代表：

- 菜单链路已可迁移
- 旧模型已可下线

## 一、适用范围

本准入标准适用于以下阶段之后的放量决策：

- Phase A：DDL 完成
- Phase B：`permission` 主数据抽取完成
- Phase C：`role_permission` 回填完成
- Phase D-1：`authority` 双轨 + deny/fail-closed 完成
- Phase D-2：`permissionsVersion` 输入扩展完成
- Phase E：观测增强完成
- Phase F：灰度 runbook / report 工件完成
- 开发阶段 10 分钟灰度冒烟：PASS
- 联调 / 测试阶段 E2E 收口：PASS

## 二、决策目标

本准入标准回答一个问题：

“当前是否可以从小范围验证，进入更大范围联调 / 灰度？”

允许的决策结果只有三种：

1. 允许扩大范围
2. 保持当前范围继续观察
3. 暂不扩大，先修复问题

## 三、准入原则

原则 1：先保证非菜单链路稳定，再谈菜单迁移评估。  
原则 2：先保证“可解释”，再扩大范围。  
原则 3：允许存在少量 `fallback` / `deny` / `unknown`，但必须可解释、可治理、可收敛。  
原则 4：不因测试通过就直接推导“可全量”。

## 四、准入检查项总览

准入检查项分 6 组：

- A. 数据与结构基础
- B. 运行时主链路
- C. 观测与可解释性
- D. E2E 与联调验证
- E. 灰度风险控制
- F. 阻断项检查

只有在 A~E 均满足、且 F 无阻断项时，才允许扩大范围。

---

## A. 数据与结构基础

### A-1. permission 主数据已落地

通过标准：

- `permission` 表已创建
- 主数据抽取已完成
- 平台 / 租户投影成立

### A-2. role_permission 已回填

通过标准：

- `role_permission` 已实际存在并有数据
- 回填差异有解释
- 重复关系检查为 0

### A-3. 异常样本已识别

通过标准：

- `INVALID_FORMAT` / `EMPTY_PERMISSION` 等异常样本已清单化
- `PERMISSION_NOT_FOUND` 不处于失控状态
- 当前异常样本不会直接放权

### A-4. fail-closed 基础成立

通过标准：

- `permission.enabled = 0` 会被 deny
- `permission` 主表缺记录会被 deny
- 旧链路 fallback 不会绕过 deny

---

## B. 运行时主链路

### B-1. authority 双轨已生效

通过标准：

- 新链路可参与 authority 计算
- 旧链路可作为 fallback
- `role.code` 不混入 permission 覆盖比较

### B-2. permissionsVersion 已扩展

通过标准：

- `role_permission` 变化可触发版本变化
- `permission.enabled` 变化可触发版本变化
- `role_hierarchy` 变化可触发版本变化

### B-3. 作用域透传成立

通过标准：

- `scopeType + scopeId` 已显式进入 authority 计算
- `scopeType + scopeId` 已显式进入 permissionsVersion 计算

### B-4. 菜单链路未受损

通过标准：

- `/sys/menus/tree` 结果稳定
- 没有明显菜单漂移
- 非菜单改动未误伤菜单

---

## C. 观测与可解释性

### C-1. fallback 可观测

通过标准：

- `OLD_PERMISSION_ONLY` (旧口径: `OLD_FALLBACK`) 有日志
- 能定位 `tenantId / scopeType / scopeId / userId`
- 能解释 fallback 原因

### C-2. deny 可观测

通过标准：

- `DENY_DISABLED` 有日志
- `DENY_UNKNOWN` 有日志
- 能定位具体 `permission_code` 或摘要

### C-3. permissionsVersion 变化原因可观测

通过标准：至少能区分

- `ROLE_ASSIGNMENT_CHANGED`
- `OLD_PERMISSION_INPUT_CHANGED`
- `ROLE_PERMISSION_CHANGED`
- `PERMISSION_MASTER_CHANGED`
- `ROLE_HIERARCHY_CHANGED`

### C-4. ORG / DEPT 分桶可验证

通过标准：

- 若已纳入测试/灰度，必须证明不串桶
- 若尚未纳入，则必须明确“当前不扩到 ORG / DEPT”

---

## D. E2E 与联调验证

### D-1. 开发阶段冒烟通过

通过标准：

- 10 分钟自动化 smoke 为 PASS

### D-2. 联调主干 E2E 通过

通过标准：

- Suite1：PASS
- Suite2：PASS
- Suite3：PASS
- Suite5：PASS

### D-3. pending / skip 已收口

通过标准：

- Suite4：PASS
- Suite6：PASS
- 关键 skip 已清零
- mutate / restore 可自动恢复

### D-4. 短稳回归通过

通过标准：

- 至少一轮短稳回归完成
- 无明显串 tenant / 串 scope
- 无脏状态残留

---

## E. 灰度风险控制

### E-1. 灰度范围可控

通过标准：

- `gray-tenant-allow-list` 已明确
- `gray-scope-type-allow-list` 已明确
- 不存在“全量放开但无观察”的情况

### E-2. 当前范围结果可解释

通过标准：

- `OLD_PERMISSION_ONLY` 未异常放大
- `DENY_UNKNOWN` 未异常放大
- `DENY_DISABLED` 与当前数据状态一致

### E-3. 报告工件齐全

通过标准：

- checklist 已存在
- gray report 已存在
- 开发 smoke report 已存在
- E2E summary 已存在

### E-4. 扩大范围有顺序

通过标准：

- 已明确下一批租户名单
- 已明确是否纳入 ORG / DEPT
- 已明确回滚方式

---

## F. 阻断项检查

出现以下任一项，禁止扩大范围：

- F-1. `DENY_UNKNOWN` 明显上升且原因不明
- F-2. `OLD_PERMISSION_ONLY` 大量出现且无法解释
- F-3. `role_hierarchy` 变化后 `permissionsVersion` 无反应
- F-4. `permission.enabled = 0` 后仍继续放权
- F-5. tenant / scope 串用
- F-6. 菜单出现明显漂移
- F-7. E2E 自动恢复失败，存在脏状态残留
- F-8. ORG / DEPT 已纳入灰度但分桶验证不成立

## 五、建议量化阈值（当前阶段）

以下不是永久标准，而是当前阶段建议阈值：

1. `DENY_UNKNOWN`
   - 开发 / 测试阶段允许出现
   - 但必须稳定、可解释、不可持续扩大
   - 若连续多轮增加，禁止扩围

2. `OLD_PERMISSION_ONLY`
   - 可以存在
   - 但应主要集中在已知旧链路缺口或历史数据问题
   - 若在“新链路完整映射用户”中仍频繁出现，禁止扩围

3. 短稳回归
   - 10 轮通过 / 10 轮无脏状态，可视为当前阶段通过
   - 若出现偶发串租户 / 串 scope，则禁止扩围

4. E2E 套件
   - 核心 suites 全 PASS
   - Suite4 / 6 不再 PENDING
   - 关键 skip 清零

## 六、推荐放量顺序

在满足准入标准后，放量顺序建议如下：

第一档：

- 扩大 TENANT 租户数量
- 仍只覆盖 PLATFORM / TENANT

第二档：

- 引入 ORG
- 单独观察 ORG 分桶与 version 变化

第三档：

- 引入 DEPT
- 单独观察 DEPT 分桶与 version 变化

第四档：

- 在非菜单链路稳定后，才讨论菜单迁移评估
- 不是直接迁菜单，而是评估是否具备条件

## 七、当前建议的准入判定模板

每次决定是否扩围，必须写出如下结论：

1. 当前范围
   - tenant:
   - scope:

2. 关键结果
   - `OLD_PERMISSION_ONLY`:
   - `DENY_DISABLED`:
   - `DENY_UNKNOWN`:
   - `permissionsVersion` reason 分布:
   - 是否存在 scope 串桶:
   - 菜单是否稳定:

3. 判定（3 选 1）
   - 允许扩大范围
   - 保持当前范围继续观察
   - 暂不扩大，先修复问题

4. 理由
   - 说明是因为 fallback 收敛、unknown 收敛，还是因为仍存在阻断项

## 八、当前项目建议结论口径

如沿用当前已落地结果，推荐写法：

**当前建议结论：保持当前范围继续观察，再决定是否扩大灰度。**

原因：

1. 非菜单链路结构验证已通过
2. E2E 主干与 pending/skip 收口已完成
3. fallback / deny 当前可解释
4. 但 ORG / DEPT 的真实放量样本仍不足
5. 长一点窗口下的运行信号仍需再观察一轮
6. 因此当前不建议直接扩大到更多 scope

## 九、完成定义（DoD）

当且仅当以下全部成立时，才可以判定“允许扩大范围”：

1. A~E 全部满足
2. F 无任一阻断项
3. 已输出正式准入结论文档或报告
4. 已明确下一批灰度租户 / scope
5. 已明确回滚方案

## 十、使用建议

这份准入标准建议放到以下任一位置：

1. `docs/PERMISSION_REFACTOR_PHASE_F_GRAY_REPORT.md` 附录
2. `docs/PERMISSION_REFACTOR_GRAY_GATE.md`
3. PR 描述中的“放量准入标准”段落

一句话结论：

当前不是缺设计，而是缺“基于证据做放量决定”的门槛；这份文档就是那个门槛。
