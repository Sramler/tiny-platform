# 权限模型演进方案（完整最终版 / 经当前仓库实现校正 / 含文档覆盖声明、最终 deny 过滤闭环、缺记录 fail-closed、permission.enabled 初始化口径与 RBAC3 继承约束 / 可直接复制给 Cursor 审计）

## 一、方案定位

**阅读指南（先看这个）**：
- **当前运行态**：主链路为 `role_permission -> permission -> resource`（`role_resource` 已由 Liquibase 117 删除）。
- **历史回溯段落**：凡标注“历史背景/非当前态”或“历史回溯”的章节，仅用于迁移过程复盘，不代表当前默认生产路径。
- **兼容字段语义**：`resource.permission` 作为 `permission.permission_code` 的对齐/复用字段保留，供映射与兼容治理使用。

本方案用于指导 tiny-platform 当前权限模型从：

user
-> role_assignment
-> role
-> role_resource
-> resource

渐进演进到：

user
-> role_assignment
-> role
-> role_permission
-> permission
-> resource

注意：
本方案不是“直接替换现有模型”，而是“兼容现状代码、现状数据模型、现状菜单链路”的渐进演进方案。

**Liquibase 117（删除 `role_resource` 表）与前后验证**：见 [PERMISSION_REFACTOR_LIQUIBASE_117_RUNBOOK.md](./PERMISSION_REFACTOR_LIQUIBASE_117_RUNBOOK.md)；配套 SQL 见 `tiny-oauth-server/scripts/verify-pre-liquibase-117-role-resource-readiness.sql` 与 `verify-post-liquibase-117-canonical-health.sql`。

当前推进状态（封版后）：

1. 非菜单权限链路演进已阶段封版，`COMPAT-003`、`COMPAT-005` 删除结果已保留。
2. `tenant5/tenant6/ORG/DEPT` 扩灰结果已保留，runtime strict 维持 `0/0`。
3. `COMPAT-004` 在 reason-equivalence 规则化证据后结论为 `NOT_NOW`，与 `COMPAT-001/002/009` 一并进入冻结观察。
4. 本阶段停止新增 compat 收口动作；下一阶段唯一主线为“菜单链路迁移前评估”。
5. 菜单主线 Stage 1（shadow-read, no cutover）已实现并通过 focused/smoke/e2e，当前 `boundary_risk_count=0`。
6. 菜单主线 Stage 2（canary-read）执行卡与检查清单已就绪，待 Go/No-Go 评审后进入受控执行。
7. 菜单主线 Stage 2（canary-read）代码能力已实现且默认关闭，支持“命中 canary 才尝试新链路，失配/边界风险即刻回退旧链路（历史语境对照口径，非当前默认生产路径）”。
8. Stage 2 首轮受控窗口验证已完成（focused/smoke/e2e 全 PASS，runtime strict `0/0`），并已补齐真实 runtime 连续窗口样本。
9. Stage 2 runtime-only 窗口结果：`total_canary_reads=2`, `mismatch_rate=0.0`, `boundary_risk=0`，结论提升为 `CLOSE_GO`。
10. Stage 3（broader switch, read-path only）执行卡与检查清单已就绪，进入 `READY_FOR_WINDOW` 状态，按“单变量扩展 + 即时回退”策略推进。
11. Stage 3 首个 broader runtime 窗口已完成：sample-rate 单变量扩展后 `total_canary_reads=6`, `mismatch_rate=0.0`, `boundary_risk=0`，结论为 `PASS`，可进入 Stage 4 提案评审。
12. Stage 4（final read-path switch proposal）执行卡与检查清单已就绪，当前状态为 `PROPOSAL_READY`，待 Go/No-Go 评审后再执行最终切换。
13. Stage 4 Go/No-Go 评审工件已落盘，结论为 `GO_RECOMMENDED`（执行建议通过，最终执行审批保持人工确认）。
14. Stage 4 执行窗口已完成并判定 `PASS`，当前策略为 `KEEP_SWITCH_ON_APPROVED_SCOPE`（已批准范围保持 final read-path switch，继续保留一键回退能力）。
15. 菜单读链路演进：`MENU-EVOLVE-001`～`004` 已将租户与平台侧 `ResourceRepository` 中全部 `findGranted*` / `findGranted*AndParentId` **主读** SQL 对齐为 `role_permission → permission → resource`；历史阶段使用的 `*Shadow` 查询已下线（已删除），后续文档中如出现 `*Shadow` 均仅表示历史对照语境。`MenuServiceImpl` 在四条热路径上对已对齐查询 **单次拉取**（不再双查 old+shadow），canary 对比对同源列表恒为 `MATCH`。记录见 `test-results/menu-old-read-decommission-summary.md`。
16. 写路径演进（W-Off-001）：`RoleServiceImpl.updateRoleResources` 与 `MenuServiceImpl.deleteResourceWithRoleAssociations` 已移除 `permission-refactor.role-resource-write-enabled` 开关与全部 `role_resource` 业务写删分支，仅维护 `role_permission`；回退依赖 `git revert` 或数据侧 reconcile 脚本，不再提供运行时属性回切。`TenantBootstrapServiceImpl` 同步改为：模板侧关联从 `role_permission → permission → resource` 读取（`findGrantedRoleResourcePairs*`），克隆时仅 `addRolePermissionRelationByResourceId`，不再写 `role_resource`；平台/模板租户须已具备可对齐的 `role_permission`（可先跑 reconcile）。
17. 历史一致性治理：`reconcile-role-permission-from-role-resource.sql`、`verify-role-resource-role-permission-consistency.sql` 与 `verify-role-permission-gap-count.sql` 已从仓库删除（DEPRECATED 归档）；当前默认 `run-permission-dev-smoke-10m.sh` 与 `verify-role-resource-legacy-removal-proof-pack.sh` 已统一走 `verify-role-permission-canonical-health.sql`；不再按表是否存在分支调用。
18. 遗留表下线（W-Off-002～004，本批实现）：`Role` 实体与 `RoleRepository` 已移除 `role_resource` JPA/原生写读；门禁脚本 `tiny-oauth-server/scripts/verify-role-resource-legacy-removal-proof-pack.sh` + workflow `verify-role-resource-legacy-removal.yml`；Liquibase `117-drop-role-resource-legacy.yaml` 在 migrate 时 `DROP TABLE role_resource`（`preConditions` 表不存在则 `MARK_RAN`）。`tiny-web` 演示模块已去掉 `role_resource` 映射。

核心原则：
1. 先补齐模型
2. 再补齐实现点
3. 再做灰度验证
4. 最后迁移主链路
5. 最后再考虑下线旧模型

补充说明：
本方案中的阶段编号（如阶段 1 / 阶段 2 / 阶段 3）仅用于“permission 模型演进”这条子主线，不等同于
docs/TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md
中的“Phase1 / 第一阶段”。

为避免与既有技术设计基线冲突，后续在实现、审计、任务拆分和 Cursor 输出中，建议将本方案阶段统一理解为：

- PermissionRefactor Phase A
- PermissionRefactor Phase B
- PermissionRefactor Phase C
- PermissionRefactor Phase D
- PermissionRefactor Phase E

对应关系如下：

- 当前文档“阶段 1：新增 permission / role_permission”
  = PermissionRefactor Phase A（permission 模型补齐阶段）

- 当前文档“阶段 2：建立 permission 主数据”
  = PermissionRefactor Phase B（permission 主数据抽取阶段）

- 当前文档“阶段 3：建立 role_permission 关系”
  = PermissionRefactor Phase C（role_permission 回填阶段）

- 当前文档“阶段 4：改造 authority / permissionsVersion 参与新链路”
  = PermissionRefactor Phase D（非菜单鉴权链路接入阶段）

- 当前文档“阶段 5 及以后”
  = PermissionRefactor Phase E+（灰度校验、逐模块迁移、评估下线阶段）

结论：
本方案阶段编号与
TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN
不是同一尺度，禁止在审计、实现或任务拆分时直接将两者视为同一“第一阶段”。

文档覆盖声明（新增，必须明确）

本方案属于 tiny-platform 权限模型的后续演进基线，用于定义 permission / role_permission 引入后的目标路径、灰度策略与运行时约束。

为避免与仓库内既有“阶段性保守方案”产生理解冲突，现作如下覆盖声明：

1. 本方案对以下旧文档中的“暂不引入独立 permission 表 / deny 能力 / 相关新链路灰度规则”条目进行更新替代：
   - docs/TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md

2. 本方案不否认以下文档对“当前运行态真相源”的描述；
   相关描述仍然成立，但仅表示“现状事实”，不表示“后续禁止演进”：
   - docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md
   - docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md
   - 以及其他明确写有“当前运行态仍以 resource.permission 为真相源”的文档

3. 因此必须区分两类语义：
   - 现状语义：当前运行态真相源为 role_permission -> permission -> resource（并与 `resource.permission` 的 permission_code 对齐）；鉴权按 role_permission/permission 口径计算
   - 演进语义：本方案定义 permission / role_permission 引入后的 PermissionRefactor Phase A-E 目标路径与约束

4. 若旧文档中存在以下表述：
   - “暂不引入独立 permission 表”
   - “暂不引入 deny 或 fail-closed 约束”
   - “不提前建设 role_permission”
   则在 PermissionRefactor 主线下，均以本方案为最新约束口径。

5. 本覆盖声明的作用范围仅限于：
   - permission / role_permission 模型建设
   - 非菜单鉴权链路灰度
   - deny / fail-closed 约束
   - permissionsVersion 新输入扩展
   - RBAC3 继承闭包与 scope 分桶比较
   不自动覆盖与本方案无关的其他模块性设计结论。

结论：
旧文档中“当前仍以 resource.permission 为真相源”的描述，不应再作为当前态结论保留；如需保留，只能降级为历史背景，并明确 `resource.permission` 现仅承担与 `permission.permission_code` 对齐的兼容字段语义；
旧文档中“暂不引入独立 permission / deny”的阶段性限制，由本方案更新替代。

==================================================
二、当前现状（必须承认的事实）
==================================================

当前仓库里，权限能力已经显式落到 `permission / role_permission`；`resource.permission` 仍存在，但当前语义是兼容字段、运营可读字段和历史回填输入，而不是功能权限真相源。

当前事实如下：

1. Spring Security authority 生成依赖 role_permission -> permission（permission_code）
   - SecurityUserAuthorityService 通过 `role_permission -> permission` 聚合 permission_code，并与 role.code 组件组合为最终 authority
   - permission.permission_code 的命名口径直接复用 resource.permission

2. PermissionVersionService 计算 permissionsVersion 依赖 role_permission -> permission，并纳入 role_hierarchy 变更快照输入

3. 菜单可见性依赖 role_permission -> permission -> resource
   - /sys/menus/tree 的菜单可见性由 MenuServiceImpl / ResourceRepository 的已对齐主读 SQL 计算

4. 当前 Resource 实体同时承载两类信息
   一类是资源载体信息：
   - uri
   - url
   - method
   - type
   - parent_id
   - title
   - component
   - redirect
   - hidden
   - keep_alive
   等

   另一类是能力标识信息：
   - permission

因此：
当前系统已具备独立 permission / role_permission；resource.permission 仍作为兼容字段保留，但权限计算主链路为 role_permission -> permission -> resource。

==================================================
三、演进目标
==================================================

本次演进目标不是推翻 resource.permission，而是：

1. 把当前散落在 resource.permission 中的能力标识，抽取到独立 permission 主表
2. 新增 role_permission，替代 role_resource 作为未来主授权关系
3. 在灰度期内保持新旧模型可并行
4. 保证 API / 按钮 / 接口权限的新旧链路可比对
5. 菜单链路在灰度期保持稳定，不扩大改动面
6. 最终为后续权限审计、模板治理、ABAC、数据权限扩展打基础

==================================================
四、核心落地规则（最重要）
==================================================

规则 1：
permission_code 直接复用现有 resource.permission 的命名规范

规则 2：
新增 permission 表时，permission.permission_code 的值直接来源于 resource.permission

规则 3：
灰度期内 resource.permission 不删除，继续作为兼容字段存在

规则 4：
短期内不强制引入 permission_resource 表，先用
resource.permission = permission.permission_code
作为映射口径

规则 5：
菜单链路已对齐为主读模型：role_permission -> permission -> resource
即：
/sys/menus/tree 的菜单可见性基于 role_permission 映射得到 permission_code（与 resource.permission 对齐）并计算

规则 6：
灰度期新链路验证聚焦于：
- Security authority
- API / 按钮 / 接口权限
- 权限指纹 / permissionsVersion
不强制菜单同时切新链路

规则 7：
若要做“新链路优先 + 旧链路兜底 + diff 校验”（历史语境对照口径），则必须修改实现代码；
只建表不改代码，新链路不会生效

规则 8：
阶段 4 之前的任何 permission / role_permission 回填都只用于数据就绪，不产生现网鉴权效果；
只有进入阶段 4 之后，新链路才开始真实参与鉴权计算，并进入“新链路优先 + 旧链路兜底 + diff 校验”（历史语境对照口径）的观察期。

==================================================
五、为什么选择“直接复用 resource.permission”
==================================================

必须直接复用 resource.permission，而不是现在另起一套新 permission_code 规范。

原因如下：

1. 现有 authority 已依赖 resource.permission
   如果 permission_code 另起新命名，必须同步改：
   - SecurityUser
   - authority 生成逻辑
   - 前端权限判断
   - API / 按钮 guard
   - 菜单/按钮/API 鉴权

2. PermissionVersionService 当前也依赖 resource.permission
   如果 permission_code 与 resource.permission 脱钩，会导致权限版本和实际鉴权口径不一致

3. 菜单与接口权限当前均以 permission_code（来自 permission.permission_code，并与 resource.permission 对齐）作为能力标识
   如果 permission_code 语义发生偏离，会导致：
   - 菜单能看但接口不能调
   - 接口能调但菜单不显示
   - 新旧链路结果不一致

4. 复用 resource.permission 是最小风险迁移
   只需要把旧字段中的能力标识抽取出来，不需要先做命名翻译和历史迁移

因此结论：
permission_code = resource.permission

==================================================
六、permission 的设计定位
==================================================

permission 是“动作能力 / 访问能力 / 业务能力点”。

它回答的问题是：

“能做什么？”

示例统一使用当前仓库规范码（三段式 / 四段式），不再使用两段式示例：

1. system:tenant:create
2. system:tenant:freeze
3. system:tenant:unfreeze
4. system:user:view
5. system:user:assign-role
6. system:role:create
7. system:role:permission:assign
8. system:audit:view
9. system:audit:export
10. system:module:enable
11. system:module:disable
12. system:dict:view
13. system:dict:edit
14. workflow:task:approve
15. idempotent:ops:view

permission 不等于菜单，不等于 API 路径，也不等于页面对象。
permission 是能力点。

==================================================
七、resource 的设计定位
==================================================

resource 是“被控对象 / 资源载体”。

它回答的问题是：

“权限作用到哪里？”

resource 可以包括：

1. MENU 菜单
2. PAGE 页面
3. BUTTON 按钮
4. API 接口
5. MODULE 模块
6. CONFIG 配置项
7. DICT 字典
8. REPORT 报表
9. WORKFLOW 流程资源
10. FILE 文件资源

一句话区分：

- permission = 能做什么
- resource = 作用到哪里

==================================================
八、当前现状与目标态的关系（状态对照）
==================================================

当前现状：

user
-> role_assignment
-> role
-> role_permission
-> permission(permission_code)
-> resource

目标态：

user
-> role_assignment
-> role
-> role_permission
-> permission(permission_code)
-> resource

灰度期关系：

resource.permission = permission.permission_code

也就是说：

1. resource.permission 仍然存在
2. permission.permission_code 从 resource.permission 抽取
3. role_permission 作为主授权关系
4. role_resource 已由 Liquibase 117 删除（新环境不再存在）
5. 菜单以 role_permission -> permission -> resource 作为主读链路计算
6. API / 按钮 / authority / permissionsVersion 均已基于 role_permission/permission 作为主读判定链路

==================================================
九、推荐表结构方向
==================================================

一）permission 表建议字段（最小化版本）

1. id
2. permission_code
3. permission_name
4. module_code
5. action_code
6. permission_type
7. description
8. enabled
9. built_in_flag
10. tenant_id
11. created_by
12. created_at
13. updated_by
14. updated_at

当前阶段不建议先引入：
- deleted_flag
- delete_version

原因：
当前 resource 语义主要是 enabled，没有统一软删除治理口径。
permission 如果先引入 deleted_flag，会导致灰度期：
- authority 判定规则不一致
- 迁移脚本与回滚脚本复杂度增加
- diff 结果难解释

因此当前阶段最小口径是：
permission 先与 resource 对齐，只使用 enabled。

字段说明：

permission_code
- 直接复用 resource.permission 的值
- 是权限编码
- 灰度期内必须与 resource.permission 同值同义

module_code
- 取 permission_code 的第一段 domain
- 例如 system、workflow、scheduling、idempotent
- 用于权限归类、治理、统计与字典展示
- 不等同于具体资源名，也不等同于 role_data_scope.module
- 与 role_data_scope.module（数据范围模块）不可混用

action_code
- 表示动作编码
- 例如 list、view、create、edit、delete、grant、export、approve

permission_type
- 表示权限类型
- 当前阶段作为聚合属性存在，不承担运行时主判定职责
- 用于分类、审计、治理、字典展示，不直接替代 resource.type

二）role_permission 表建议字段

1. id（或复合主键）
2. role_id
3. permission_id
4. tenant_id
5. created_by
6. created_at

建议唯一约束：

(role_id, permission_id)

若租户维度参与唯一语义，则建议按当前项目统一规范处理 null 值问题，例如引入 normalized_tenant_id。

九点五、role_permission.tenant_id 一致性规则（新增，必须明确）

当前仓库的数据库真实语义中，role_resource 的查询路径大量依赖 tenant_id 做平台模板 / 租户实例过滤；
但在部分 JPA 映射视角下，tenant_id 并未作为完整关系实体显式暴露，而是遵循“由角色侧 / 资源侧 / 查询侧共同约定”的惯例。

因此，role_permission 在建模时必须补齐 tenant_id 一致性规则，避免后续回填或鉴权时出现语义漂移。

统一规则如下：

1. 平台模板关系
   - 当 role.tenant_id IS NULL 且 permission.tenant_id IS NULL 时，
     role_permission.tenant_id 必须为 NULL
   - 表示平台模板角色与平台模板权限的关联

2. 租户实例关系
   - 当 role.tenant_id = 某租户 且 permission.tenant_id = 同一租户时，
     role_permission.tenant_id 必须等于该租户ID
   - 表示租户内角色与租户内权限的关联

3. 禁止出现以下不一致：
   - role.tenant_id IS NULL，但 role_permission.tenant_id = 某租户
   - permission.tenant_id IS NULL，但 role_permission.tenant_id = 某租户，且该 permission 实际表示平台模板权限
   - role.tenant_id = A，permission.tenant_id = B，但 role_permission.tenant_id = C
   - role.tenant_id、permission.tenant_id、role_permission.tenant_id 三者语义不一致

4. 若数据库 schema 将 role_permission.tenant_id 设计为冗余列，则必须遵循以下派生优先级：
   - 优先由 role.tenant_id 与 permission.tenant_id 的一致结果派生
   - 不允许把 role_permission.tenant_id 当作独立业务真相源
   - 若 role.tenant_id 与 permission.tenant_id 无法推出唯一合法值，则该关系不得落表

5. 灰度期回填要求
   - 阶段 3 回填 role_permission 时，必须按现有 role_resource.tenant_id 的惯例生成
   - 生成结果必须同时满足：
     role.tenant_id 一致
     permission.tenant_id 一致
     role_permission.tenant_id 一致

结论：
role_permission.tenant_id 可以作为查询与过滤友好的冗余列存在，但其语义必须严格服从 role 与 permission 的模板/租户归属规则，禁止出现三者不一致。

==================================================
十、permission 字段解析规则（新增，必须落地）
==================================================

由于当前仓库中并不存在现成的 permission 主表与解析逻辑，
因此必须明确 permission_code -> module_code / action_code / permission_type 的派生规则。

当前统一规则如下：

1. permission_code 的来源
   - 直接取 resource.permission
   - 命名规范遵循现有权限命名文档：
     docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md

2. module_code 的解析
   - 由 permission_code 的第一段 domain 得到
   - 例如：
     system:user:list -> module_code = system
     workflow:task:approve -> module_code = workflow
     scheduling:console:view -> module_code = scheduling
     idempotent:ops:view -> module_code = idempotent

3. action_code 的解析
   - 默认取 permission_code 最后一段
   - 例如：
     system:user:list -> action_code = list
     system:user:reset-password -> action_code = reset-password
     system:role:permission:assign -> action_code = assign

4. permission_name 的默认生成
   - 当前阶段可直接复用 permission_code
   - 后续若需要更友好的显示名，再通过字典或文案表补齐

5. description 的默认策略
   - 当前阶段可为空
   - 或由迁移脚本写入“从 resource.permission 抽取生成”

6. 冲突处理原则
   - 若同一 permission_code 不能按规范解析出稳定的 module_code / action_code，
     则记为异常样本，进入迁移校验清单人工处理
   - 当前阶段不允许为单个异常样本单独发明第二套解析规则

十点五、异常样本落地策略（新增，必须明确）

当前方案已要求：
- 无法解析的 permission_code 进入异常清单
- 不允许为单个异常样本发明第二套解析规则

为了让方案具备可执行性，还必须明确“异常样本如何落地、是否参与阶段 4 鉴权”。

统一规则如下：

1. 异常样本的定义
   异常样本包括但不限于：
   - permission_code 为空或格式非法
   - 无法稳定解析出 domain / action
   - 同一 permission_code 在抽取时出现不可接受的语义冲突
   - 历史值不符合当前权限命名规范，且无法自动归一

2. 异常样本的落表策略
   当前阶段采取“允许落表，但标记为治理异常，不直接进入运行时新链路授权”的策略。

   建议最小落地方式如下：
   - permission_code：保留原始值
   - permission_name：默认等于 permission_code
   - module_code：可为空
   - action_code：可为空
   - permission_type：统一写 OTHER
   - description：写入“异常样本：待人工治理”
   - enabled：建议置为 0
   - built_in_flag：按原来源决定
   - tenant_id：仍严格继承 resource.tenant_id

3. 非异常样本的初始化规则（必须写死）
   - 非异常样本（可解析且无语义冲突）的 permission.enabled 初始化默认置为 1（governance_enabled）
   - 初期不强制把 resource.enabled 派生为 permission.enabled，以避免改变当前仓库既有的 authority 授权语义
   - 当前 authority / permissionsVersion 生成未见基于 resource.enabled 的统一过滤，因此不得把 permission.enabled 默认等同解释为 resource.enabled
   - 若未来需要把 resource.enabled 的语义纳入 permission.enabled，必须作为后续单独阶段给出明确的派生、回滚与回归验证口径

4. 异常样本与阶段 4 的关系
   - 异常样本默认不得作为新链路授权依据
   - 即使已经落入 permission 表，也不得因为“表里有记录”而参与新链路放权
   - 新链路遇到异常样本时，必须继续依赖旧链路兜底，且记录差异日志

5. 异常样本的安全策略
   - 默认 fail closed
   - 不允许因解析失败而自动放宽权限
   - 不允许因异常样本落表而产生“新链路多放权”的结果

6. 异常样本治理要求
   - 异常样本必须进入迁移异常清单
   - 必须在后续治理中被：
     规范化替换
     人工确认归类
     或彻底清理
   - 在异常样本未治理完成前，不得宣称该模块已完成 permission 模型迁移

结论：
异常样本可以为了“主数据完整性”和“迁移可追踪性”而落入 permission 表，但默认不进入阶段 4 的新链路授权计算，必须按 fail closed 策略处理，避免越权或灰度误判。

异常样本闭环约束：

1. 异常样本落表时，默认必须以 enabled = 0 进入 permission 主表。
2. enabled = 0 在当前方案中的语义不是“普通禁用建议”，而是：
   governance_only / 不得进入阶段 D 新链路授权计算。
3. 因此异常样本 enabled = 0 与阶段 D 的新链路过滤规则是同一闭环的两个环节：
   - 数据落表侧：异常样本默认 enabled = 0
   - 运行时计算侧：新链路仅允许 enabled = true 的 permission 参与 authority / permissionsVersion / API / 按钮 / 接口权限计算
4. 不允许出现以下偏差实现：
   - 异常样本虽然 enabled = 0，但仍被新链路参与 authority 计算
   - 异常样本虽然 enabled = 0，但仍被纳入 permissionsVersion 计算
   - 异常样本虽然 enabled = 0，但因“表里有记录”而参与 API / 按钮放权
5. 阶段 D / E 的最终授予集合必须执行 deny 过滤：
   不论采用新链路还是旧链路兜底，凡 permission.enabled = 0 对应的 permission_code 都必须从最终 authority / permissionsVersion / API / 按钮 / 接口权限集合中剔除；
   因此旧链路兜底也不得授予异常样本。
6. 结论：
   异常样本 enabled = 0 不是可选治理建议，而是阶段 D fail-closed 的强约束前提。

==================================================
十一、permission_type 冲突策略（新增，必须明确）
==================================================

短期内 permission_code 直接复用 resource.permission，
而 resource.permission 可能被不同 resource.type 复用。

因此必须明确：
当同一个 permission_code 对应多个 ResourceType 时，permission.permission_type 如何取值。

当前阶段统一策略如下：

1. permission_type 采用“聚合优先级取值”
2. 不允许因为 permission_type 冲突而阻断 permission 主数据落表
3. permission_type 当前不作为运行时授权主判断条件，只作为治理属性

优先级建议如下：

API > BUTTON > PAGE > MENU > MODULE > CONFIG > DICT > REPORT > WORKFLOW > FILE > OTHER

解释：
- 如果同一个 permission_code 同时出现在 API 和 MENU 资源上，则 permission_type 取 API
- 如果同时出现在 BUTTON 和 MENU 上，则取 BUTTON
- 若无法识别，则取 OTHER

原因：
1. 当前阶段 permission_type 只是聚合属性
2. 真正的资源载体类型仍然以 resource.type 为准
3. 不引入 permission_resource 的前提下，必须先有一个稳定的单值策略

未来若出现以下需求，再评估升级：
- 一个 permission 需要明确挂多个 resource.type
- permission_type 需要参与运行时判定
- 需要引入 permission_resource 或 permission_type 关系表

==================================================
十二、平台 / 租户模板规则
==================================================

permission 表必须和当前 role/resource 的模板规则一致。

当前 role/resource 已采用：

- 平台模板：tenant_id IS NULL
- 租户数据：tenant_id = 某租户ID

permission 也必须保持同样规则。

建议规则如下：

1. 平台模板权限
   - tenant_id IS NULL
   - 作用域为 PLATFORM

2. 租户权限
   - tenant_id = 某租户ID
   - 作用域为 TENANT

唯一性建议：

permission 表建议支持 tenant_id IS NULL 的模板模型，
并通过 normalized_tenant_id 解决唯一性约束问题。

推荐唯一键口径：

(normalized_tenant_id, permission_code)

其中：
normalized_tenant_id = IFNULL(tenant_id, 0)

这样可以支持：
- 平台模板权限
- 租户实例权限
- 同名 permission_code 在平台模板与租户实例下共存

如果不采用这个策略，就必须改成全局唯一 permission_code。
结合当前 tiny-platform 模板设计，推荐采用“模板 + 租户实例共存”策略。

==================================================
十三、平台模板抽取映射规则（新增，必须明确）
==================================================

当从现有 resource.permission 抽取 permission 主数据时，
permission.tenant_id 的取值必须与当前模板规则严格一致。

当前统一规则如下：

1. 当 resource.tenant_id IS NULL 时
   - permission.tenant_id 也必须为 NULL
   - 表示平台模板权限
   - normalized_tenant_id = 0

2. 当 resource.tenant_id = 某租户ID 时
   - permission.tenant_id 也必须等于该租户ID
   - 表示租户实例权限

3. 不允许出现以下漂移：
   - resource.tenant_id IS NULL，但 permission.tenant_id = 某租户
   - resource.tenant_id = 某租户，但 permission.tenant_id 被错误写成 NULL

4. 抽取脚本必须以 resource.tenant_id 作为 permission 主数据 tenant_id 的直接来源

原因：
若不明确这条规则，会在灰度期出现：
- permission 主数据插入唯一键冲突
- 平台模板权限被误识别为租户权限
- 租户权限被误识别为平台模板
- 新旧链路映射漂移

==================================================
十四、permission 与 resource 的映射规则
==================================================

短期映射规则：

permission.permission_code = resource.permission

即：
resource.permission 既是旧模型里的权限标识字段，
也是新模型里 permission 主数据的抽取来源。

因此在灰度期：

1. 不需要立刻新增 permission_resource 表
2. resource 与 permission 的映射可按 permission_code 同值推导
3. resource.permission 暂不删除

只有在未来出现以下情况时，才考虑新增 permission_resource：

1. 一个 permission 需要对应多个 resource
2. 一个 resource 需要绑定多个 permission
3. resource.permission 准备彻底下线
4. 菜单/API/按钮不再适合同码映射

==================================================
十五、与 role_data_scope.module 的区分（必须写清）
==================================================

当前系统中已经存在数据范围模型：

role_data_scope (module, scope_type, access_type)

因此必须明确：

1. permission.module_code
   = permission_code 第一段 domain，对应功能权限域
   例如 system、workflow、scheduling、idempotent

2. role_data_scope.module
   = 数据范围控制模块
   例如某个业务域的数据可见范围配置

两者不是同一个概念，不可混用，不可直接等价。

一句话：

- permission.module_code 是“功能权限域（domain）”
- role_data_scope.module 是“数据权限模块”

==================================================
十六、灰度期菜单策略（本方案已明确选择）
==================================================

本方案明确选择：

菜单灰度期按方案 A 执行。

即：

1. /sys/menus/tree 基于 role_permission -> permission -> resource 主读链路计算（与 resource.permission 对齐的 permission_code 语义）

2. 灰度期菜单目标不是“新旧双跑”，而是“确保菜单结果保持稳定不漂移”

3. 新链路验证聚焦于：
   - authority 计算
   - API / 按钮 / 接口权限
   - permissionsVersion
   - 审计日志差异

4. 在 canary 窗口内，主读与历史对照口径用同口径列表完成对比（默认恒为 MATCH），避免旧链路差异引入漂移

==================================================
十七、灰度期到底改不改鉴权链路（历史背景/非当前态，已完成项回溯）
==================================================

说明：本节中“旧链路/兜底/fallback”仅用于历史灰度与对照策略描述，不代表当前生产默认执行路径。

当前方案明确分两个阶段：

阶段 1（已完成）：
- permission / role_permission 表结构已落地
- permission 主数据抽取与 role_permission 回填已落地

阶段 2（已完成）：
- 新链路已参与运行态鉴权计算（authority / permissionsVersion / 菜单主读）
- 关键实现点已接入 role_permission -> permission 口径

结论：
- “只建表不改代码”的历史风险已被关闭；当前代码已是新链路主读

==================================================
十八、必须修改的实现点清单
==================================================

如果要让“新链路优先 + 旧链路兜底 + diff 校验”真正生效，
至少需要补齐以下实现点：

1. SecurityUser
   作用：
   - authority 构建

   当前现状：
   - 基于 role_permission -> permission 生成 permission_code authority（并与 role.code 兼容 authority 合并）

   已落地约束：
   - authority 从 permission_code 聚合并执行 enabled/unknown fail-closed 过滤
   - 差异日志与兜底策略保留用于观测

2. PermissionVersionService
   作用：
   - permissionsVersion 指纹计算

   当前现状：
   - 基于 role_permission -> permission 快照计算 permissionsVersion，并纳入 role_hierarchy 变更输入

   已落地约束：
   - role_permission / permission 变更已纳入计算输入
   - 计算结果遵循 enabled/unknown fail-closed 口径

3. API / 按钮 / 接口权限判定逻辑
   当前口径为 permission_code 主链路判定，并执行 enabled/unknown 过滤。

4. 菜单链路
   当前阶段不改。
   但方案中必须明确记录：
   菜单已基于 role_permission -> permission -> resource 作为主读链路计算；本轮只需验证稳定性与差异闭环。

==================================================
十九、permissionsVersion / token / session 刷新策略
==================================================

当前 PermissionVersionService 的输入里包含 role_assignment.updatedAt，
但不天然包含：
- role_permission 更新
- permission 主数据更新

因此灰度期存在一个重要风险：

即使你已经回填了 permission / role_permission，
只要用户的 role_assignment 行没有变化，
旧 session / JWT 的权限版本也可能不刷新。

这会导致：
你观察到的新旧差异，可能来自缓存，而不是权限链路本身。

因此本方案必须补充以下验证规则：

1. 灰度迁移 / 主数据回填 / role_permission 回填完成后，
   验证用例必须重新登录或刷新 token / session

2. 在未改造 PermissionVersionService 之前，
   不允许直接以旧 token 的结果判断新链路是否正确

3. 当阶段 4 让新链路开始参与鉴权计算时，
   必须同时改造 PermissionVersionService 的指纹输入，
   使其能够感知以下变更：
   - role_permission 变更
   - permission 主数据变更

4. 当前实现口径：
   已将 role_permission / permission（以及 role_hierarchy）变更纳入 permissionsVersion 指纹输入

当前阶段至少要落实：
“迁移验证必须重新登录 / 刷新 token”
以及
“阶段 4 起 PermissionVersionService 必须纳入新链路变更输入”

==================================================
二十、推荐演进步骤（历史背景/非当前态，供回溯）
==================================================

说明：本节阶段步骤用于回溯迁移过程；凡涉及“旧链路/兜底/fallback”，均为历史对照语境而非当前默认路径。

--------------------------------------------------
阶段 1（历史回溯）：新增 permission / role_permission
--------------------------------------------------

目标：

1. 新增 permission 表
2. 新增 role_permission 表
3. 从 resource.permission 抽取 permission 主数据
4. 已完成菜单与鉴权主链路对齐（role_permission -> permission -> resource）

阶段结论：模型补齐与运行态切换均已完成，当前进入稳定性与治理收口阶段。

--------------------------------------------------
阶段 2（历史回溯）：建立 permission 主数据
--------------------------------------------------

需要做的事：

1. 扫描现有 resource 表
2. 收集所有非空 resource.permission
3. 去重后生成 permission 记录
4. permission_code 直接等于 resource.permission
5. 按 resource.permission 解析出：
   - module_code
   - action_code
   - permission_type
6. 无法解析或存在冲突的样本进入异常清单

可理解为：
把原来散落在 resource.permission 中的能力点，集中沉淀成 permission 主表。

--------------------------------------------------
阶段 3（历史回溯）：建立 role_permission 关系
--------------------------------------------------

做法：

1. 读取历史映射关系（仅用于遗留环境回填）
2. 按 resource.permission 对齐 permission.permission_code
3. 找到对应 permission.permission_code
4. 建立 role -> permission 的关联
5. 严格按 resource.tenant_id 投影到 permission.tenant_id

即：
历史关系已投影为 role_permission；新环境不再依赖 role_resource。

注意：
到阶段 3 为止，permission / role_permission 仍然只是数据就绪，不产生现网鉴权效果。

--------------------------------------------------
阶段 4（历史回溯）：改造 authority / permissionsVersion 参与新链路
--------------------------------------------------

此阶段必须改代码，至少包括：

1. SecurityUser
2. PermissionVersionService
3. 任何直接依赖 resource.permission 的接口权限计算逻辑

目标：
让新链路真实参与计算，而不是停留在数据库表层面。

阶段 D 强制约束：
新链路必须对 permission.enabled = true（或等价状态）进行过滤；
异常样本（enabled = 0）不得参与 authority / permissionsVersion / API / 按钮 / 接口权限计算，
保证 fail closed。

阶段 D 闭环补充：

1. 阶段 D 新链路只允许计算 enabled = true 的 permission。
2. 所有异常样本若已按方案落表，必须默认为 enabled = 0（governance_only）。
3. 因此：
   - enabled = 0 的异常样本不得参与 authority 计算
   - enabled = 0 的异常样本不得参与 permissionsVersion 计算
   - enabled = 0 的异常样本不得参与 API / 按钮 / 接口权限授予
4. 这不是实现建议，而是阶段 D 必须满足的 fail-closed 约束。
5. 若实现中无法证明已对 enabled 状态完成运行时过滤，则不得宣称阶段 D 已完成接入。

最终 deny 过滤约束（阶段 D / E 强约束）：

1. 阶段 D / E 的最终授予集合必须执行 deny 过滤。
2. deny 过滤的判断基准是 permission 主数据中的 enabled 状态：
   - permission.enabled = true 的 permission_code 才允许保留在最终授予集合中
   - permission.enabled = 0 的 permission_code 必须从最终授予集合中剔除
3. 该规则同时作用于以下最终结果：
   - authority 集合
   - permissionsVersion 计算输入集合
   - API 权限集合
   - 按钮权限集合
   - 接口权限集合
4. 不论最终采用的是：
   - 新链路结果
   - 旧链路兜底结果
   - 新旧链路合并后的结果
   都必须在“最终授予前”执行一次基于 permission.enabled 的 deny 过滤。
5. 若某个在最终授予集合中出现的 permission_code 在 permission 主表中找不到对应记录（enabled 为空、缺失或无法判定），
   该 permission_code 必须视为 disabled / unknown，并从最终授予集合中剔除（fail-closed）。
6. 因此：
   - 旧链路兜底不得绕过异常样本禁用约束
   - 凡 permission.enabled = 0 对应的 permission_code，即使旧链路可从 resource.permission 推导出来，也不得进入最终授予集合
   - 凡 permission 主表中不存在对应记录的 permission_code，也不得进入最终授予集合
7. 该规则的目的：
   - 保证 enabled = 0 的异常样本真正 fail-closed
   - 防止旧链路兜底重新授予本应禁止的 permission_code
   - 防止“permission 主表缺记录”被宽松解释为默认放行
   - 保证“异常样本不授予 / 缺记录不授予”是最终运行态约束，而不是仅限于新链路的局部约束
8. 若实现中无法证明“最终授予集合”已执行该 deny 过滤，则不得宣称阶段 D / E 的 fail-closed 已完成。

--------------------------------------------------
阶段 5（历史回溯）：新链路优先 + 旧链路兜底（仅限非菜单链路）
--------------------------------------------------

灰度期推荐逻辑：

1. authority / API / 按钮 / 接口权限
   先查新链路：
   role -> role_permission -> permission

2. 若新链路未命中，再兼容旧链路：
   role -> role_permission -> permission

3. 记录差异日志：
   - authority 差异
   - API 权限差异
   - permissionsVersion 差异

4. 兜底策略（强一致防断裂）：
   在阶段 D / E 观察期，针对同一请求上下文计算 authority / 按钮 / API 权限集合时，
   如果新链路无法覆盖旧链路为同一 role 生成的全部 permission_code，
   或无法覆盖预期高风险资源集合，
   则该上下文使用旧链路结果作为最终授予集合；
   同时记录 diff 日志用于后续修复新链路投影缺口。

5. 新链路覆盖判断口径（强约束）：
   - 覆盖判断必须以 permission_code 去重集合为准
   - 若运行时比较的是 authority，则必须先归一为等价 permission_code 集合后再比较
   - 覆盖判断集合仅针对由 permission_code 诱导出的授权能力；
     role.code 固有 authority 不参与覆盖比较
   - oldPermissionCodeSet 与 newPermissionCodeSet 必须可明确计算
   - 只要 newPermissionCodeSet 不能完整覆盖 oldPermissionCodeSet 中应授予集合，即视为未覆盖，必须触发兜底
   - 禁止以数量接近、部分命中、主角色命中等弱判断代替集合覆盖判断

6. 补充约束：
   新旧链路覆盖比较、diff 记录与兜底触发判断，统一以归一后的 permission_code 去重集合作为唯一口径；
   不得直接以 role.code 或未经归一的 authority 原始字符串集合作为最终比较集合。

7. diff 日志最小字段集合（强约束）：
   diff 日志至少必须记录：
   - tenantId
   - scopeType
   - scopeId
   - userId
   - roleIds
   - oldAuthorityCodes
   - newAuthorityCodes
   - diffReason
   - requestContextId 或 traceId（若已有）

8. 该策略的目的：
   - 避免“部分命中”造成授权断裂
   - 避免平台菜单策略读取 authority 时发生异常漂移
   - 在可观测差异的前提下保持运行态稳定

注意：
此阶段不包括菜单集合查询切换。
菜单集合查询仍保持稳定计算路径（基于 role_permission -> permission -> resource），不再依赖 role_resource 旧链路。
平台菜单相关 authority 判定基于同链路计算。

附加约束：

1. 若 oldAuthorityCodes / newAuthorityCodes 存储的是 authority 字符串，则必须能回溯到等价 permission_code 集合。
2. diffReason 不允许只写自然语言模糊描述，至少要有稳定枚举值。
3. 若触发旧链路兜底，必须明确写出 fallback 触发原因，不允许只记录“有差异”而不记录原因。
4. diff 日志字段必须足以支持后续回答以下问题：
   - 哪个租户 / 哪个作用域 / 哪个用户触发了差异
   - 哪些角色参与了计算
   - 旧链路与新链路分别生成了什么权限集合
   - 为什么触发兜底
5. 结论：
   diff 日志不是可选调试信息，而是阶段 D / E 新旧链路灰度验证的必备审计输出。

--------------------------------------------------
阶段 6（历史回溯）：菜单新链路保持稳定
--------------------------------------------------

菜单灰度期策略明确如下：

1. /sys/menus/tree 已切到 role_permission -> permission -> resource 新链路
2. MenuServiceImpl / ResourceRepository 按新链路计算菜单可见性
3. 菜单验证目标为：
   新链路结果保持稳定不漂移
4. 平台菜单相关 authority 判定基于同链路计算（role_resource 旧链路不再作为回退依赖）

--------------------------------------------------
阶段 7（历史回溯）：迁移验证
--------------------------------------------------

验证必须覆盖：

1. permission 主数据抽取正确
2. permission_code -> module_code / action_code / permission_type 解析正确
3. role_permission 回填正确
4. SecurityUser authority 新旧链路可对齐
5. API / 按钮 / 接口权限新旧链路可对齐
6. permissionsVersion 行为可解释
7. 验证前必须重新登录 / 刷新 token
8. 菜单结果保持主链路稳定，不发生漂移
9. 平台模板 tenant_id IS NULL 与租户 tenant_id 投影正确，不发生映射漂移
10. 平台菜单相关 authority 判定不因新链路缺失而异常漂移
11. enabled = 0 的异常样本不进入新链路授权计算
12. 新链路部分缺失时，旧链路兜底不会造成授权断裂
13. 最终授予集合 deny 过滤已生效，旧链路兜底不会重新授予 enabled = 0 的 permission_code
14. permission 主表缺记录的 permission_code 不会被最终授予集合保留，缺记录按 disabled / unknown 处理
15. role_hierarchy 变更后，permissionsVersion 能触发刷新且新旧链路结果可解释
16. 若涉及 ORG / DEPT 作用域，覆盖比较、deny 过滤、兜底触发与 diff 日志必须按 scopeType + scopeId 分桶验证，不得仅按 tenant 维度比较

--------------------------------------------------
阶段 8（历史回溯）：逐模块迁移
--------------------------------------------------

建议不要一次性迁全系统。

推荐优先迁移模块：

1. 平台租户治理模块
2. 租户用户治理模块
3. 角色与授权管理模块
4. 审计模块
5. 其余业务模块

原则：
先迁最清晰、边界最明确的模块。

--------------------------------------------------
阶段 9（历史回溯）：菜单链路已迁新（无需再评估）
--------------------------------------------------

当前实现中，菜单可见性已基于 `role_permission -> permission -> resource` 计算，
因此此阶段主要保留为“稳定性回归与差异观测”的说明，不再执行“是否迁新”的决策步骤。

--------------------------------------------------
阶段 10（历史回溯）：role_resource 已下线（resource.permission 兼容字段保留）
--------------------------------------------------

resource.permission 是否进一步下线需单独评审；role_resource 下线已完成。

1. permission 主数据完整
2. role_permission 全量建立完成
3. authority / API / 按钮 / 接口已稳定跑在新链路
4. permissionsVersion 已正确纳入新模型输入
5. 菜单若已决定切新，则也已稳定
6. 差异日志长期为零或可接受
7. 初始化脚本已切到 permission 模型
8. 测试与审计都验证通过

在此之前：
- role_resource 已由 Liquibase 117 删除（新环境不再存在）
- resource.permission 仍作为兼容字段保留

==================================================
二十一、当前阶段不要做的事
==================================================

1. 不要再依赖 role_resource（Liquibase 117 已删除；遗留环境仅用于对账/回填前置）
2. 不要立刻删除 resource.permission
3. 不要现在引入一套全新的 permission_code 命名规范
4. 不要在 permission / resource 边界未定清前就做全量历史迁移
5. 不要把 permission.module_code 和 role_data_scope.module 混用
6. 不要让平台/租户 permission 模型脱离现有 tenant_id IS NULL 模板规则
7. 不要把菜单链路反向切回 role_resource 口径（菜单已基于 role_permission 主链路计算）
8. 不要在不刷新 token / session 的情况下判断灰度差异

==================================================
二十二、Cursor 审计提示
==================================================

当 Cursor 基于本方案进行审计、DDL 设计或代码生成时，应遵循以下规则：

1. 当前现状模型：
   user -> role_assignment -> role -> role_permission -> permission -> resource
   role_resource 已由 Liquibase 117 删除（新环境不再存在）

2. 当前事实上的 permission 来源：
   permission.permission_code（与 resource.permission 对齐/复用）

3. 新 permission 表设计时：
   permission_code 必须直接复用 resource.permission

4. 灰度期内：
   resource.permission 保留为兼容字段，不得立即删除

5. 菜单灰度期策略：
   菜单以 role_permission -> permission -> resource 为主读链路计算（与 resource.permission 对齐）

6. 新链路优先验证对象：
   - SecurityUser authority
   - API / 按钮 / 接口权限
   - permissionsVersion

7. diff 校验的历史风险提示：
   - “只建表不改代码”会导致新链路不生效（该风险已在当前实现中关闭）
   - 当前应重点验证 SecurityUser / PermissionVersionService / guard 侧口径一致性与回归稳定性

8. permission 表必须支持平台模板 / 租户实例规则：
   - tenant_id IS NULL 表示平台模板
   - tenant_id = 某租户表示租户实例

9. permission 最小字段集当前应与 resource 语义对齐：
   先使用 enabled，不先引入 deleted_flag

10. 灰度验证必须重新登录 / 刷新 token / session，
    否则 permissionsVersion 缓存可能导致结果失真

11. permission_code -> module_code / action_code / permission_type 的解析规则
    必须遵循现有权限命名规范文档：
    docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md

12. 当前阶段 permission_type 采用聚合优先级单值策略，
    不作为运行时主判定依据

13. 抽取 permission 主数据时，permission.tenant_id 必须严格继承 resource.tenant_id，
    平台模板保持 tenant_id IS NULL

14. 菜单集合查询以 role_permission -> permission -> resource 为主读链路，
    平台菜单相关 authority 判定基于同链路计算（role_resource 不再作为回退依赖）

15. 阶段 D 起新链路必须对 permission.enabled = true 做过滤，
    enabled = 0 的异常样本不得参与 authority / permissionsVersion / API / 按钮 / 接口权限计算

16. 阶段 D / E 的新旧合并逻辑必须采用“强一致防断裂兜底”：
    如果新链路不能覆盖旧链路为同一上下文生成的完整 permission_code 集合，
    则使用旧链路结果作为最终授予集合，并记录 diff 日志

17. 覆盖判断仅针对 permission_code 归一集合；
    role.code 固有 authority 不参与覆盖比较

18. 阶段 D / E 的最终授予集合必须执行 deny 过滤：
    不论最终采用新链路、旧链路兜底还是两者合并结果，凡 permission.enabled = 0 对应的 permission_code 都必须从最终 authority / permissionsVersion / API / 按钮 / 接口权限集合中剔除

19. 若最终授予集合中出现的 permission_code 在 permission 主表中找不到对应记录（enabled 为空、缺失或无法判定），
    该 permission_code 必须按 disabled / unknown 处理，并从最终授予集合中剔除（fail-closed）

20. 非异常样本（可解析且无语义冲突）的 permission.enabled 初始化默认置为 1（governance_enabled）；
    初期不强制把 resource.enabled 派生为 permission.enabled，避免改变当前仓库既有 authority 语义

21. 若未来 resource.enabled 需要纳入 permission.enabled，必须作为后续单独阶段给出明确的派生、回滚与回归验证口径

22. 若未来 resource.permission 不再适合作为唯一映射键，
    再考虑引入 permission_resource，而不是现在提前复杂化

23. 若涉及 ORG / DEPT 作用域，覆盖比较与兜底判断需按 scopeType + scopeId 分桶执行，不得跨作用域混合比较

24. 若仓库内旧文档与本方案发生冲突，且冲突内容涉及以下主题：
    - 是否引入独立 permission 表
    - 是否引入 role_permission
    - 是否启用 deny / fail-closed 闭环
    - 是否要求最终授予集合过滤 enabled = 0 / unknown permission_code
    - 是否要求 permissionsVersion 纳入 role_permission / permission / role_hierarchy 变更
    则一律以本方案（PermissionRefactor Phase A-E）为准。

25. 旧文档中“当前运行态真相源仍为 resource.permission”的描述，
    在 Cursor 审计时应理解为“现状代码事实”，不得被误解释为“禁止进入 permission 新模型”。

==================================================
二十三、角色继承与 RBAC3 约束补充（新增，必须明确）
==================================================

1. 覆盖比较口径必须基于有效角色闭包
   - oldPermissionCodeSet 与 newPermissionCodeSet 的计算，必须以“直授角色 + 继承角色（角色层级闭包）”为统一输入
   - 禁止仅以直授角色集合进行覆盖判断

2. 角色继承边界必须遵守租户/作用域隔离
   - 角色继承解析不得跨 tenant_id
   - 平台模板与租户实例角色继承关系不得串用
   - 若后续引入 ORG/DEPT 作用域继承，必须按 scopeType + scopeId 限定闭包范围
   - ORG / DEPT 作用域下的有效角色闭包、oldPermissionCodeSet / newPermissionCodeSet、deny 过滤结果与兜底触发判断，均必须在相同 scopeType + scopeId 桶内完成，禁止跨桶合并

3. RBAC3 约束在新链路中仍必须生效
   - mutex / prerequisite / cardinality 约束在 Phase D / E 不得弱化
   - 不得因 role_permission 新增而绕过既有约束校验链路

4. deny 优先级高于继承放权
   - 无论 permission_code 来自直授角色还是继承角色，
     只要 permission.enabled = 0 或缺记录（unknown），
     均必须在最终授予集合中剔除（fail-closed）

5. 兜底触发判断顺序必须固定
   - 先完成角色继承闭包展开
   - 再完成 enabled / 缺记录 deny 过滤
   - 再进行新旧覆盖比较与兜底触发判断

6. permissionsVersion 变更输入必须补齐 role_hierarchy
   - Phase D 起，除 role_permission / permission 变更外，
     role_hierarchy 变更也必须纳入 permissionsVersion 指纹输入或等价刷新机制
   - 否则不得宣称权限版本刷新覆盖完整

==================================================
二十四、最终结论
==================================================

本次权限模型演进的正确方向是：

从：
user
-> role_assignment
-> role
-> role_resource
-> resource

演进到：
user
-> role_assignment
-> role
-> role_permission
-> permission
-> resource

但结合 tiny-platform 当前代码与数据模型，
必须采用“兼容现状”的落地方式：

1. permission_code 直接复用 resource.permission
2. permission 表先作为主数据抽取层建立
3. role_permission 已成为主授权关系；role_resource 已由 Liquibase 117 删除（遗留环境仅用于对账/回填前置）
4. authority / API / 按钮 / 接口权限灰度期双轨并行
5. 菜单以 role_permission -> permission -> resource 为主读链路计算（与 resource.permission 对齐）
6. resource.permission 暂不删除
7. role_resource 已下线（遗留环境仅用于对账/兼容脚本）
8. permissionsVersion 灰度验证必须结合重新登录 / 刷新 token
9. 阶段 4 起 PermissionVersionService 必须纳入 role_permission / permission 变更输入
10. 平台模板 tenant_id IS NULL 规则必须在 permission 抽取阶段严格保持
11. 平台菜单相关 authority 判定基于同链路计算
12. 阶段 D 起异常样本（enabled = 0）必须被新链路过滤，保证 fail closed
13. 阶段 D / E 必须采用“强一致防断裂”兜底策略，避免部分命中导致授权缺口
14. 覆盖判断仅针对 permission_code 归一集合，不将 role.code 固有 authority 混入覆盖比较
15. 阶段 D / E 的最终授予集合必须执行 deny 过滤，确保旧链路兜底也不得重新授予 enabled = 0 的 permission_code
16. permission 主表缺记录的 permission_code 必须按 disabled / unknown 处理并从最终授予集合中剔除，禁止宽松默认放行
17. 非异常样本 permission.enabled 默认初始化为 1，且初期不直接派生自 resource.enabled，避免改变当前现网 authority 语义
18. 角色继承闭包、RBAC3 约束与 role_hierarchy 刷新必须纳入新链路灰度与 permissionsVersion 刷新口径
19. 最终在新链路稳定后，再考虑是否迁菜单、是否下线旧模型

一句话定稿：

方向正确，必须推进；
但要以 resource.permission 为当前 permission_code 来源口径，
以“菜单主读链路保持稳定、非菜单权限双轨灰度验证”为实施策略，
并补齐 permission 解析规则、permission_type 冲突策略、权限指纹刷新输入、平台模板投影规则、enabled 过滤、强一致兜底策略、覆盖判断口径、最终 deny 过滤闭环、permission.enabled 初始化口径以及 RBAC3 继承约束，
按兼容演进方式逐步落地，不能硬切。

