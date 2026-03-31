# Tiny Platform RBAC3 Enforce Rollout SOP

> 状态：RBAC3 灰度发布运行手册  
> 适用范围：`role-constraint` 控制面、enforce 灰度、违例观测、回滚操作  
> 关联主线：`TINY_PLATFORM_AUTHORIZATION_MODEL.md`、`TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`

> 说明：
> - 本文件只负责 RBAC3 从 dry-run 到 enforce 的操作步骤、观测口径与回滚策略。
> - RBAC3 的建模边界、完成度与后续优先级，以授权模型文档和任务清单为准。

## 目标

把 RBAC3 Phase2 的 **dry-run 违例观测**，安全地推进到 **enforce 阻断**（带灰度 allowlist + 可回滚）。

## 前置条件

- 已启用 RBAC3 Phase2 校验链路（写入前校验）。
- 控制面已可查询违例日志：`GET /sys/role-constraints/violations`
- enforce 配置项已上线：
  - `tiny.platform.auth.rbac3.enforce`（默认 false）
  - `tiny.platform.auth.rbac3.enforce-tenant-ids`（空=全量；非空=仅这些租户）

## 观测口径（先 dry-run）

- **违例类型**（`violationType`）：
  - `MUTEX`：互斥冲突（同一主体/作用域下同时拥有互斥角色）
  - `PREREQUISITE`：先决条件缺失（授予目标角色时缺 required 角色）
  - `CARDINALITY`：基数超限（新增后超过 max_assignments）
- **关键字段**：
  - `tenantId`：租户维度隔离与灰度目标
  - `principalType/principalId`：谁被赋权导致违例（通常 USER）
  - `directRoleIds/effectiveRoleIds`：直接授予 vs 层级展开后的有效角色
  - `details`：冲突对/缺失项/计数等补充信息（JSON 字符串）

## 决策流程（从 dry-run 到 enforce）

### Step 1：以租户为单位统计违例

建议先按租户筛选，再按类型筛选，关注近 7/14/30 天趋势：

- **仅看某租户近 14 天 MUTEX**：
  - `GET /sys/role-constraints/violations?violationType=MUTEX&createdAtFrom=<ISO>&createdAtTo=<ISO>&sort=createdAt,desc`
- **定位到具体用户（principalId）**：
  - `GET /sys/role-constraints/violations?principalId=<userId>&sort=createdAt,desc`

输出：
- 是否存在“高频固定违例”（说明现有赋权流程或规则配置需要先修）
- 是否存在“偶发违例”（可能是历史脏数据/偶发误操作，适合先治理数据）

### Step 2：选择 allowlist 租户

选择策略（从安全到激进）：

- **最安全**：先选“违例为 0 或接近 0”的租户
- **折中**：选“违例已可解释/可治理”的租户，并准备回滚
- **不建议**：直接全量 enforce（除非已经完成数据治理与流程改造）

### Step 3：灰度开启 enforce

配置示例：

- **只对租户 1 开启 enforce**：
  - `tiny.platform.auth.rbac3.enforce=true`
  - `tiny.platform.auth.rbac3.enforce-tenant-ids=1`

预期行为：
- allowlist 内租户发生违例：赋权写操作被阻断（409 业务冲突）
- allowlist 外租户发生违例：仍 dry-run（记录日志但不阻断）

### Step 4：上线后监控与回滚

上线后关注：
- 赋权相关接口的 409/拒绝比例是否异常上升
- 是否出现“必须允许但被误拦”的真实业务反馈
- `violations` 中是否出现集中爆发（说明流程/数据没准备好）

回滚策略（从轻到重）：
- **最快**：清空 allowlist（仅保留 dry-run）
  - `tiny.platform.auth.rbac3.enforce-tenant-ids=`
- **完全回滚**：关闭 enforce
  - `tiny.platform.auth.rbac3.enforce=false`
- 如需“止血后修复”：
  - 临时删除/修正冲突的 RBAC3 规则（通过 `/sys/role-constraints/*` 控制面）

## 数据治理与保留

- 表：`role_constraint_violation_log`
- 建议 retention：默认保留 30 天（可按合规调整）
- 清理脚本：`tiny-oauth-server/scripts/cleanup-role-constraint-violation-log.sql`
