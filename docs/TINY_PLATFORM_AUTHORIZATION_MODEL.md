# Tiny Platform 授权模型与重构方案

> 状态：设计与实施基线文档  
> 适用范围：`auth / oauth / security / tenant / menu / resource / role / user / scheduling / dict`  
> 配套规则：`.agent/src/rules/91-tiny-platform-auth.rules.md`、`.agent/src/rules/92-tiny-platform-permission.rules.md`、`.agent/src/rules/93-tiny-platform-authorization-model.rules.md`

---

## 1. 目的

本文件用于回答四件事：

1. 当前 tiny-platform 的授权模型到底做到哪一步；
2. `RBAC3 + Scope + Data Scope` 在 tiny-platform 中应该如何落地，而不是停留在抽象设计；
3. 当前项目哪些已经完成，哪些尚未开始，哪些必须继续改进；
4. 后续实现应按什么顺序拆解，避免一次性重构过重。

本文件是“授权模型总设计文档”，与 [TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md](./TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md) 的关系如下：

- `TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md`：只定义权限码命名与迁移规范；
- 本文件：定义角色、授权关系、作用域、数据权限、会话上下文、实施路线。

如需进入实现，请继续阅读：

- [TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md](./TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md)：第一阶段主交付的字段级技术设计、迁移顺序和运行时改造范围。

---

## 2. 为什么现在补文档和 `.agent` 规则是合理的

合理，而且应当先做。

原因：

1. 当前仓库已经开始做权限治理，但治理点分散在调度、菜单、租户、用户管理等模块，尚未形成统一授权模型。
2. 现有规范主要覆盖“权限码怎么命名”，没有回答“角色如何分配”“Scope 如何表达”“Data Scope 如何计算”。
3. 如果没有统一文档和 `.agent` 规则，后续人和 AI 很容易在不同模块里继续发明各自的授权结构，形成第二轮历史包袱。
4. 当前系统已经进入“控制面 RBAC 收口期”，此时补模型文档成本最低，收益最高。

结论：

- 先补文档和 `.agent` 规则是合理且必要的；
- 但不应直接跳到“大表重构 + 全量替换”，而应先把目标模型、边界和分阶段策略写清楚。

---

## 3. 当前实现基线

### 3.1 当前已经存在的核心能力

当前 tiny-platform 已具备以下授权基础：

1. **租户隔离基础**
   - 运行时已有 `TenantContext`，请求处理按当前租户执行。
   - 多数核心表已经补 `tenant_id`，并持续收口租户隔离。

2. **用户、角色、资源三层模型**
   - 当前核心关系是：`user -> user_role -> role -> role_resource -> resource.permission`
   - 角色和资源都已经是租户内数据。

3. **权限码规范化**
   - 运行态逐步统一到 `resource.permission` 和规范权限码；
   - 已开始清理历史 `menu:view`、`user:update`、`scheduling:read` 等旧值。

4. **控制面 RBAC 收口**
   - 调度、租户管理、菜单管理、用户管理等模块已开始通过 `@PreAuthorize + AccessGuard` 做后端硬约束；
   - 前端页面已逐步按 authority 隐藏/禁用操作。

5. **平台入口与租户入口分离**
   - 部分平台级控制面能力已从普通租户入口中隔离出来；
   - 新租户 bootstrap 也开始避开平台专用资源。

### 3.2 当前实现的真实结构

当前并不是 `RBAC3 + Scope + Data Scope`，而是：

- **RBAC0/RBAC1 增强版**
- **租户上下文 + 角色直挂资源**
- **权限点来源于 `resource.permission`**

当前数据库和运行时更接近：

```text
user
  -> user_role
  -> role
  -> role_resource
  -> resource.permission
```

当前 `SecurityUser` 的 authority 来源也仍然是：

- `role.code`
- `role.name`
- `resource.permission`

这意味着当前模型的重点是“控制功能访问”，还不是完整的“作用域授权 + 数据范围授权”。

### 3.3 当前已做 / 未做 / 待改进

#### 已做

| 项 | 当前状态 |
| --- | --- |
| 租户上下文 | 已有 `TenantContext` |
| 用户、角色、资源 | 已有基础模型 |
| 用户-角色关系 | 已有 `user_role` |
| 角色-权限关系 | 已有 `role_resource`，运行态权限来自 `resource.permission` |
| 权限码规范 | 已有专门规范文档与迁移收口 |
| 控制面 RBAC | 调度、菜单、租户、用户管理已在推进或完成 |
| 平台入口隔离 | 已开始收口平台菜单与平台接口 |

#### 未做

| 项 | 当前状态 |
| --- | --- |
| `tenant_user` | 未实现 |
| `organization_unit` / `user_unit` | 未实现 |
| `role_assignment` | 未实现 |
| `role_hierarchy` | 未实现 |
| `role_mutex` | 未实现 |
| `role_prerequisite` | 未实现 |
| `role_cardinality` | 未实现 |
| `role_data_scope` / `role_data_scope_item` | 未实现 |
| 统一授权上下文（tenant + unit + assignment） | 未实现 |
| 按模块的数据权限过滤框架 | 未实现 |

#### 需要继续改进

| 项 | 改进方向 |
| --- | --- |
| `user_role` 直接分配 | 逐步过渡到 `role_assignment` |
| authority 组成 | 长期应减少对 `role.name` 这种展示值的依赖 |
| 控制面 RBAC | 继续覆盖 `role` / `resource` 等剩余控制面 |
| 仓储层隔离 | 从 Service 校验继续推进到 Repository 双保险 |
| 菜单/资源/权限初始化 | 继续消除历史命名漂移 |

---

## 4. 目标模型：基于 tiny-platform 的 `RBAC3 + Scope + Data Scope`

### 4.1 目标不是照抄通用模型，而是适配现状

本项目的目标态采用：

- **RBAC3**：角色继承、互斥、先决条件、基数限制；
- **Scope**：平台/租户/组织/部门作用域；
- **Data Scope**：模块级数据范围。

但 tiny-platform 不应直接照搬一套全新 ACL/ABAC 体系，而应保持以下约束：

1. **权限点仍以 `resource.permission` 为运行态真相源，直到显式迁移**
2. **先补授权关系模型，再补组织/部门，再补数据权限**
3. **约束应在分配时校验，不在运行时静默忽略**
4. **Data Scope 不应塞进角色码、权限码或业务 SQL 特判里**

### 4.2 目标模型组件

目标态建议包含：

1. **主体**
   - 初期：`USER`
   - 后续可扩展：`POST`、`GROUP`

2. **角色**
   - 平台角色、租户角色、组织角色、部门角色
   - 角色模板和角色授权关系要分离

3. **授权关系**
   - 统一使用 `role_assignment`
   - 至少包含：
     - `principal_type`
     - `principal_id`
     - `role_id`
     - `tenant_id`
     - `scope_type`
     - `scope_id`
     - `start_time`
     - `end_time`
     - `status`

4. **角色治理**
   - `role_hierarchy`
   - `role_mutex`
   - `role_prerequisite`
   - `role_cardinality`

5. **数据权限**
   - `role_data_scope`
   - `role_data_scope_item`
   - 按模块分别计算，不搞一条万能规则

6. **组织与部门**
   - `organization_unit`
   - `user_unit`

### 4.3 关于 `permission` 表的取舍

通用模型通常会引入独立 `permission` 表，但对 tiny-platform 当前阶段，不建议马上新增独立权限表。

理由：

1. 当前权限点已经挂在 `resource.permission` 上，并贯通菜单、前端、后端、初始化数据；
2. 现在再引入一个独立 `permission` 表，会让“权限定义真相源”变成双份；
3. 更合理的顺序是：先把授权关系与作用域建起来，再评估是否拆分“资源目录”和“权限目录”。

因此，当前阶段的推荐策略是：

- **短中期**：`resource.permission` 继续作为运行态权限真相源；
- **长期**：如果按钮/API/菜单生命周期显著分离，再考虑引入独立 `permission` 目录并做统一迁移。

---

## 5. 项目级决策（已定）

### 5.1 一人多租户：目标改为 membership 模型

已确定：

- tiny-platform 的目标态采用“平台账号 + 租户成员关系”模型；
- 用户可加入多个租户，并在不同租户下拥有不同授权关系；
- 后续授权模型以 `tenant_user`（或 `user_tenant`）为租户成员载体。

同时明确迁移策略：

1. **不能一步删除 `user.tenant_id`**
   - 当前代码和数据仍广泛依赖 `user.tenant_id`；
   - 需要先新增 `tenant_user`、回填历史数据，并进入一段双写/兼容期。
2. **逐步迁移读路径**
   - 登录、用户查询、角色加载、菜单下发、审计逐步改读 `tenant_user`；
3. **最后再废弃 `user.tenant_id`**
   - 只有当运行态和迁移脚本都不再依赖 `user.tenant_id` 时，才允许下线。

结论：

- `tenant_user` 是已定方向；
- `user.tenant_id` 是过渡兼容字段，而不是长期真相源。

### 5.2 PLATFORM 作用域是一等公民，不再由默认租户逻辑承载

已确定：

- `scope_type=PLATFORM` 必须作为正式作用域进入授权模型；
- 平台级角色、平台级授权、平台级控制面不再以“默认租户就是平台”作为逻辑语义；
- 当前 `default` 租户只允许在迁移期继续作为初始化模板或兼容承载，不得继续作为平台语义本身。
- 平台级角色模板和资源模板可继续与租户级模板共表存储，但必须通过独立字段区分模板层级，例如：
  - `role_level`
  - `resource_level`
  - `owner_scope_type`

实现约束：

- 平台级 `role_assignment` 应使用 `scope_type=PLATFORM`；
- 平台级授权不应要求业务层先切入某个租户；
- 平台控制面与租户业务数据面必须审计分离。
- 平台模板只允许平台控制面创建、修改和删除；租户侧只能读取平台模板，或基于平台模板派生租户级副本，不能直接修改平台模板行。
- 若采用共表存储且平台模板使用 `tenant_id IS NULL`，则必须同步补齐：
  - 数据库唯一约束策略，不能直接沿用当前 `(tenant_id, code)` 的唯一键假设；
  - 租户请求对平台模板行的更新/删除防护；
  - Service 层对“平台模板只读、租户模板可写”的统一校验。

推荐派生策略：

1. 平台模板由平台控制面维护；
2. 租户如需自定义平台模板能力，应创建一条 `tenant_id` 非空的租户副本；
3. 运行态引用时优先使用租户副本，其次回退到平台模板。

### 5.3 运行态 Authority / JWT / Session 最终契约

已确定：

- 运行态授权契约应尽量精简，只保留鉴权和数据过滤所需信息；
- `role.name` 这类展示值不得继续进入 JWT / Session / authority 契约。

建议契约：

- **必须保留**
  - `sub` / `userId`
  - `active_tenant_id`
  - 规范权限码集合 `permissions`
- **按需要保留**
  - 少量平台/管理员角色码（如 `ROLE_ADMIN`），用于兼容粗粒度守卫
  - `active_org_id`
  - `active_dept_id`
  - `active_role_assignment_ids`
  - `permissions_version`
- **必须移除**
  - `role.name`
  - 其他纯展示用途字段

说明：

- Session 与 JWT 的载体形式可以不同，但语义必须一致；
- 前后端都只应消费规范权限码，不消费角色展示名。

### 5.4 Role 的定位：角色模板与授权单元分离

已确定：

- `role` 是角色模板，不直接代表某次授权；
- `role_resource` 表达“角色模板拥有哪些功能权限”；
- 新增的 `role_assignment` 表达“谁在什么作用域下拥有哪些角色模板”。

因此：

- `role` 负责权限集合与治理属性；
- `role_assignment` 负责主体、租户、作用域、生效时间等运行态授权信息；
- 用户、岗位、用户组等主体的扩展都应优先通过 `role_assignment.principal_type` 完成。

### 5.5 会话上下文与切换机制

已确定：

- 后续运行态上下文不能只保留 legacy `tenantId` 视图；
- 必须升级为“授权上下文”，至少包含：
  - 当前活动租户
  - 当前主组织 / 主部门
  - 当前有效角色分配

建议实现：

1. 登录后，用户选择或恢复上次使用的当前租户/组织上下文；
2. 切换上下文时，由后端刷新 Session 或签发新 JWT；
3. 所有数据过滤与控制面鉴权均以当前活动上下文为准，而不是以“用户曾拥有的所有上下文”直接并行生效。

### 5.6 数据权限合并策略

已确定：

- **功能权限**：取并集
- **数据权限**：默认按模块取最大覆盖范围

当前项目约定：

1. 同模块多个角色同时生效时，Data Scope 默认取更宽的允许范围；
2. 暂不把 `DENY` 作为第一阶段前提能力；
3. `CUSTOM` 的对象粒度先支持：
   - `ORG`
   - `DEPT`
   - `USER`

补充约束：

- 读权限和写权限的数据范围不能简单共用；
- 如未来确需 `DENY`，必须以独立设计和冲突优先级进入，不得临时混入。

### 5.7 “阶段 0”重命名与第一阶段主交付

已确定：

- 原文档中的“阶段 0”继续表示**基线收口**，不是大规模模型重构；
- 你提出的那组核心改造，不再命名为“阶段 0”，统一命名为**第一阶段主交付**。

第一阶段主交付至少包括：

1. 引入 `tenant_user` membership 模型并完成历史数据迁移与兼容；
2. 引入 `role_assignment`；
3. 平台作用域与默认租户语义拆分；
4. JWT / Session 契约收口；
5. 所有控制面管理接口补齐方法级 RBAC；
6. 菜单与权限下发策略统一；
7. 授权审计与必要缓存策略。

### 5.8 organization_unit 的规划

已确定：

- 组织与部门使用单表树模型 `organization_unit`；
- `unit_type` 至少区分 `ORG` 与 `DEPT`；
- 组织树严格限定在租户内；
- 用户必须有且仅有一个主部门，同时可以拥有多个兼职部门；
- 组织管理员与部门管理员使用统一的角色模板体系，通过 `role_assignment.scope_type/scope_id` 区分具体管理范围，而不是复制角色模板。

### 5.9 RBAC3 角色治理契约（role_hierarchy / role_mutex / role_prerequisite / role_cardinality）

> 本小节用于在实现前“写死” RBAC3 相关概念的语义与约束，避免后续出现语义不完整或各模块各自发明的实现。

#### 5.9.1 约束对象与载体

- 所有 RBAC3 约束（继承、互斥、先决条件、基数）默认挂在 **角色模板层（`role`）**，而不是挂在单条 assignment 上：  
  - `role_hierarchy`：描述角色模板之间的“权限包含”关系；  
  - `role_mutex`：描述两类角色模板在同一主体+作用域下**不得共存**；  
  - `role_prerequisite`：描述在分配某角色前，主体必须已拥有的其他角色集合；  
  - `role_cardinality`：描述某角色或某类角色的基数限制。  
- 运行态检查统一通过 **当前主体在某作用域下的 `role_assignment` 集合 + 角色模板上的约束** 完成。

#### 5.9.2 语义边界

- `role_hierarchy`（角色继承）：
  - 表达 **权限包含关系**，而不是 UI 分组；  
  - 若 `A > B`，则 A 至少拥有 B 的全部权限点，展开顺序为：  
    - `role_assignment` 中显式分配的角色模板；  
    - 加上从这些模板按层级向下展开得到的所有子角色模板；  
  - 不允许形成环：`role_hierarchy` 必须是有向无环图（DAG），不允许“自指”或环路。
- `role_mutex`（角色互斥）：
  - 为 **强互斥约束（MUST NOT 共存）**，而不是建议性告警；  
  - 若 `A` 与 `B` 互斥，则同一主体在同一 `(scope_type, scope_id)` 下不得同时拥有 `A` 与 `B`；  
  - 互斥关系默认对所有 `scope_type` 生效（PLATFORM/TENANT/ORG/DEPT），如需例外必须在约束模型中显式表达。
- `role_prerequisite`（先决条件）：
  - 表达“分配某角色前必须已拥有的角色集合”；  
  - 缺失先决条件时，**不得自动补齐授权**，而是直接拒绝该次分配；  
  - 用于描述例如“仅允许已是租户成员管理员的用户被赋予某高级平台角色”等场景。
- `role_cardinality`（基数限制）：
  - 默认语义为“**同一主体在同一作用域下**，最多拥有 N 个给定类型（或集合）内的角色”；  
  - 全局“最多 N 个持有者”的场景需要单独设计字段与告警/审批流程，本阶段默认不实现全局基数限制。

#### 5.9.3 检查时机与裁决顺序

- **保存约束配置时**（创建/修改 `role_hierarchy` / `role_mutex` / `role_prerequisite` / `role_cardinality`）：
  - 必须校验配置本身的一致性：  
    - 无环（hierarchy）；  
    - 无自互斥、无明显自相矛盾的组合；  
    - 先决条件不会形成不可满足的闭环依赖。  
  - 不允许保存明显自相矛盾的约束，防止运行态进入“永远不可达”的状态。
- **分配/撤销角色时**（写 `role_assignment`）：
  - 这是 RBAC3 的“强制执行点”，必须按 **统一裁决顺序** 检查：  
    1. `role_mutex`：若新 assignment 导致互斥冲突，直接拒绝本次操作；  
    2. `role_cardinality`：若违反基数限制，拒绝本次操作；  
    3. `role_prerequisite`：若先决条件不满足，拒绝本次操作；  
    4. `role_hierarchy`：用于展开权限与辅助诊断（如必要，可在此处提示潜在冗余分配）。  
  - 检查失败时必须 **fail-closed**：拒绝写入新的 assignment，而不是尝试自动改写/撤销旧授权。
- **运行时授权决策**（读取 `role_assignment` 计算 `permissions`）：
  - 默认假设存量数据已通过上述写时检查保持一致；  
  - 可以做轻量的“防御性复查”（例如：检测到互斥状态时拒绝访问并打审计日志），但不得在此阶段“悄悄修正” assignment 集合。

#### 5.9.4 错误反馈与审计

- 发生 RBAC3 约束冲突时，API 层必须：
  - 返回明确的错误码和 HTTP 状态，例如：  
    - `409 CONFLICT` + `ROLE_MUTEX_CONFLICT`；  
    - `409 CONFLICT` + `ROLE_PREREQUISITE_MISSING`；  
    - `409 CONFLICT` + `ROLE_CARDINALITY_EXCEEDED`；  
  - 同时写入审计日志，至少包含：  
    - `actorUserId`（谁在操作）  
    - `targetUserId`（目标主体）  
    - `scope_type` / `scope_id`  
    - `roleId` 或角色集合  
    - 违反的约束类型与关键字段（如互斥对端 roleId、缺失的先决角色列表）。

#### 5.9.5 默认启用策略与兼容期

- 默认策略：
  - RBAC3 约束检查在后端实现后，应立即在 **新建/修改角色约束** 以及 **新建 assignment** 时启用；  
  - 对历史 assignment 数据，允许通过 **只读审计脚本** 先发现潜在冲突，再逐步清理。
- 兼容期建议：
  - 可通过配置开关或按租户/租户组分批启用严格检查：  
    - 第一阶段：仅对新租户、新创建角色启用 RBAC3 检查；  
    - 第二阶段：对已通过审计清理的租户启用强约束；  
    - 最终阶段：在全平台强制执行 RBAC3，历史异常数据需在切换前清理完毕。  
  - 无论是否启用严格模式，都不得引入“静默忽略约束”的代码路径。

---

## 6. 待定决策（必须在实现前明确）

以下事项方向已明确，但实现契约尚未完全定死：

1. **平台级角色/资源的物理存储方案**
   - 统一放在现有 `role/resource` 表中并加层级字段；
   - 还是引入独立的平台模板层。

2. **JWT 中是否显式存 `active_role_assignment_ids`**
   - 若存：运行时更快，token 失效策略更复杂；
   - 若不存：每次请求要回库展开授权关系。

3. **权限变更后的失效策略**
   - JWT、Session、菜单缓存、前端本地状态如何同步失效。

4. **Data Scope 是仅挂角色模板，还是允许 assignment override**
   - 当前文档默认挂模板；
   - 如需 assignment 覆盖，必须单独设计优先级。

5. **最小审计字段**
   - 至少需要明确是否固定记录：
     - `active_tenant_id`
     - `scope_type`
     - `scope_id`
     - `role_assignment_id`
     - `permission_code`

6. **控制面剩余 RBAC 收口时间点**
   - 当前 `RoleController` / `ResourceController` 仍未完全纳入新收口标准；
   - 必须在第一阶段主交付内补齐，而不是拖到后续 Scope/Data Scope 阶段。

7. **平台模板派生的生命周期**
   - 平台模板更新后，已有租户副本是否跟随同步；
   - 租户副本与平台模板的差异如何审计；
   - 是否允许租户回退到平台模板默认配置。

---

## 7. 当前阶段不建议做的事

1. **不建议立即引入独立 `permission` 表**
   - 当前运行态真相源仍是 `resource.permission`；
   - 现在拆第二套权限目录会形成双真相源。

2. **不建议把 `DENY` 作为第一阶段必做能力**
   - 先把 ALLOW-only 的功能权限、Data Scope 和授权关系模型做稳。

3. **不建议在第一阶段同时引入 `POST/GROUP` 主体授权**
   - 主体先收敛到 `USER`，避免范围失控。

4. **不建议为每个组织/部门复制一套角色模板**
   - 应通过 `scope_type/scope_id` 复用模板，而不是复制角色。

5. **不建议一步删除 `user.tenant_id`**
   - 必须经过 `tenant_user` 回填、双写、读路径切换后再下线。

---

## 8. 重构方案（完整路线）

### 阶段 0：现状收口

目标：

- 先把当前 RBAC0/RBAC1 模型收口，不再继续漂移。

应完成：

1. 控制面剩余模块的 RBAC 补齐；
2. `resource.permission` 规范码继续统一；
3. 平台级入口、租户级入口、普通用户入口分离；
4. 统一“权限来源于角色+资源”的口径。

### 阶段 1：第一阶段主交付

目标：

- 完成从“单租户用户 + user_role”向“membership + role_assignment + 平台/租户分离”的第一轮跃迁。

实施细节见：

- [TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md](./TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md)

应完成：

1. 新增 `tenant_user`，并把现有 `user.tenant_id` 数据回填到 membership；
2. 保留 `user.tenant_id` 作为兼容字段，进入双写/兼容期；
3. 新增 `role_assignment`，初期至少支持：
   - `principal_type=USER`
   - `scope_type=PLATFORM`
   - `scope_type=TENANT`
4. 将现有 `user_role` 平滑迁移到 `role_assignment`；
5. JWT / Session 契约去掉展示值，切到 `active_tenant_id + permissions`；
6. 所有控制面管理接口补齐方法级 RBAC；
7. 菜单下发与后端鉴权统一按规范权限码执行；
8. 授权审计至少记录当前活动上下文和关键权限决策。

### 阶段 2：补 RBAC3 角色治理

目标：

- 把角色治理从“平面角色列表”升级为“可治理角色体系”。

新增结构：

- `role_hierarchy`
- `role_mutex`
- `role_prerequisite`
- `role_cardinality`

关键原则：

1. 角色约束在“授权时”校验；
2. 冲突必须明确报错；
3. 不在运行时做模糊容错。

### 阶段 3：补 Scope（组织/部门）

目标：

- 让角色不只是“属于某租户”，而是“在某个范围生效”。

新增结构：

- `organization_unit`
- `user_unit`

扩展 `role_assignment.scope_type`：

- `PLATFORM`
- `TENANT`
- `ORG`
- `DEPT`

运行时需要新增统一授权上下文：

- 当前租户
- 当前主组织/主部门
- 当前有效角色分配

### 阶段 4：补 Data Scope

目标：

- 把“能不能操作功能”与“能看哪些数据”分离。

新增结构：

- `role_data_scope`
- `role_data_scope_item`

推荐数据范围枚举：

- `ALL`
- `TENANT`
- `ORG`
- `ORG_AND_CHILD`
- `DEPT`
- `DEPT_AND_CHILD`
- `SELF`
- `CUSTOM`

落地原则：

1. 按模块分别计算；
2. 查询条件通过统一构造器生成；
3. 不允许各业务模块自己拼一套数据范围解释逻辑。

---

## 9. 实施拆解（建议顺序）

### 第一批：文档与规则先行

1. 完成本文件；
2. 补 `.agent` 授权模型规则；
3. 明确权限码规范与授权模型规范的边界；
4. 明确当前阶段“不立即引入独立 permission 表”；
5. 明确已定决策、待定决策和当前阶段不建议项。

### 第二批：控制面与权限模型收口

1. 补齐 `role` / `resource` 控制面 RBAC；
2. 收口 authority 组成与资源初始化；
3. 建立权限/角色/资源治理的统一口径。

### 第三批：membership + 授权关系升级

1. 新增 `tenant_user`
2. 回填 `user.tenant_id`
3. 新增 `role_assignment`
4. 从 `user_role` 迁移
5. 保持运行态兼容
6. 增加分配审计

### 第四批：RBAC3 约束

1. `role_hierarchy`
2. `role_mutex`
3. `role_prerequisite`
4. `role_cardinality`
5. 分配接口与管理页面校验

### 第五批：组织/部门 Scope

1. `organization_unit`
2. `user_unit`
3. 当前授权上下文扩展
4. 组织/部门角色授权

### 第六批：Data Scope

1. `role_data_scope`
2. `role_data_scope_item`
3. 查询层过滤器
4. 模块级接入

---

## 10. 当前项目的结论

### 10.1 已经完成的部分

- 权限码规范开始统一；
- `resource.permission` 作为当前运行态权限真相源；
- 多租户隔离基础存在；
- 控制面 RBAC 已在多个模块推进；
- 平台控制面与普通租户入口开始分离。

### 10.2 尚未完成的部分

- 还没有 `tenant_user` membership 模型
- 还没有真正的 `role_assignment`
- 还没有 RBAC3 治理表
- 还没有组织/部门作用域
- 还没有统一数据权限模型
- 平台作用域仍未真正从默认租户语义中独立出来
- JWT / Session 契约仍未完全收口到最终形态
- **角色/资源模板层级**：`role` 表、`resource` 表的 `role_level`、`resource_level` 字段及平台模板只读/租户派生读写策略，留待第二阶段（平台模板与租户模板共表）时实现。

### 10.3 最需要改进的部分

1. 不要继续让各模块各自发明授权结构；
2. 不要在业务模块里手写“伪数据权限”；
3. 不要在没有统一模型前引入第二套权限目录表；
4. 先完成 membership + role_assignment + 平台作用域分离，再做数据范围。

---

## 11. 对后续实现的硬约束

1. 新增授权模型能力必须先更新本文件和对应 `.agent` 规则。
2. 未完成统一迁移前，运行态权限真相源仍以 `resource.permission` 为准。
3. 角色继承、互斥、先决条件、基数限制必须在授权时校验。
4. Data Scope 必须按模块建模，不得以“角色名约定”代替。
5. `tenant_user` 的引入必须同步改登录态、claims、菜单、审计，不允许只改表不改运行时。
6. 平台控制面不得继续长期依赖“默认租户即平台”的语义假设。
7. 平台模板只允许平台控制面维护；租户侧最多只能读取或派生租户副本，不得直接更新或删除平台模板行。

---

## 12. 关联文档

- [TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md](./TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md)
- [saas-multi-tenant-issues-analysis.md](./saas-multi-tenant-issues-analysis.md)
- [SCHEDULING_SAAS_GOVERNANCE_FINAL_REPORT.md](../tiny-oauth-server/docs/SCHEDULING_SAAS_GOVERNANCE_FINAL_REPORT.md)

本文件负责“授权模型与实施路线”，权限码命名仍由权限标识规范负责，自动化约束则以 `.agent/src/rules/**` 为准。
