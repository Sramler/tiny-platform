# 93 tiny-platform 授权模型规范（平台特定）

## 适用范围

- 适用于：角色模型、授权关系、Scope、Data Scope、菜单/资源授权、租户内与平台级控制面授权
- 典型文件：`**/auth/**`、`**/oauth/**`、`**/security/**`、`**/tenant/**`、`**/role/**`、`**/resource/**`、`**/menu/**`、`**/*AccessGuard.java`、`**/*.sql`、`**/*.yaml`
- 配套文档：`docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md`（阅读入口与冲突裁决）、`docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`（模型与边界）、`docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`（当前完成度唯一真相源）、`docs/TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md`（功能权限/载体层/数据权限分层）、`docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md`（**§8**：user 端点 **M4 读** vs **M4 写** 分口径）、`docs/TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md`（`api_endpoint` 统一守卫证据等级）、`docs/TINY_PLATFORM_RBAC3_ORG_DATASCOPE_ALLOCATION_ER_MODEL.md`（组织/数据权限/划拨一体化目标态）、`docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md`（遗留演进路径）、`docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md`（权限码与控制面规范码）、`docs/TINY_PLATFORM_TENANT_GOVERNANCE_CURSOR_FIX_PROMPT.md`（租户治理剩余修复基线）

## 禁止（Must Not）

- ❌ 在没有统一模型和迁移计划的前提下，在业务模块中自行发明一套新的授权关系表或“伪 Scope”字段。
- ❌ 用角色名称、权限码前缀、组织编码字符串等约定俗成方式，替代正式的 Scope / Data Scope 模型。
- ❌ 在运行态同时维护两套权限真相源，例如一套来自 `resource.permission`，另一套来自临时 `permission` 表。
- ❌ 把 Data Scope 直接编码进角色码、菜单名、SQL 片段或控制器分支中。
- ❌ 将角色继承、互斥、先决条件、基数限制留给运行时“碰到再说”，而不在授权时显式校验。
- ❌ 在未定义当前租户成员模型之前，直接假设系统已经支持“一人多租户”。
- ❌ 新增逻辑不得依赖 `user.tenant_id` 做真实授权判断；应以 membership（tenant_user）、role_assignment、当前活动租户（activeTenantId/Token/Session）为准；仅数据归属、展示、审计可继续使用 user.tenant_id。

## 必须（Must）

- ✅ 当前 tiny-platform 的运行态功能权限真相源以 `role_permission -> permission` 为准；`resource` 仅作为目录/菜单/按钮/API 载体层，兼容期可保留 `resource.permission`，但新逻辑应优先维护 `resource.required_permission_id -> permission.id`。
- ✅ 新增授权模型能力时，必须同时更新 `docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`，说明目标、边界、迁移与兼容策略。
- ✅ 目标态多租户授权采用 membership 模型；如从 `user.tenant_id` 迁移到 `tenant_user`，必须经过回填、兼容读取和双写阶段，不能一步删除旧字段。
- ✅ 若引入 Scope，必须通过正式授权关系表达，例如 `role_assignment` 的 `scope_type/scope_id`，而不是在 `user_role`、业务表或代码常量中临时拼接。
- ✅ 若引入 Data Scope，必须使用独立模型表达，并在查询层或统一过滤层计算，不得散落在各业务模块私有实现中。
- ✅ 角色继承、互斥、先决条件、基数限制必须在“授权/分配”阶段做校验，并返回明确错误。
- ✅ 涉及授权模型的数据库迁移必须给出历史数据回填、唯一约束、索引和回滚/兼容说明。
- ✅ 平台级授权与租户级授权必须显式区分，不得让普通租户入口默认继承平台管理能力。
- ✅ `scope_type=PLATFORM` 必须是一等作用域；默认租户最多只能作为迁移期的初始化模板或兼容承载，不得继续作为平台语义本身。
- ✅ 若平台模板与租户模板共表存储，必须使用独立的模板层级字段区分平台/租户模板，不能复用 `role_assignment.scope_type` 表达模板层级。
- ✅ 平台模板只允许平台控制面创建、修改和删除；租户侧最多只能读取平台模板或派生租户副本，不得直接更新或删除平台模板记录。
- ✅ 判断“当前是否已落地 / 是否已闭合 / 证据等级到哪一档”时，不得引用历史设计稿直接下结论；应按 `docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md` 的裁决顺序，分别回到 `AUTHORIZATION_TASK_LIST`、`AUTHORIZATION_LAYERED_MODEL`、`API_ENDPOINT_GUARD_COVERAGE` 与 `RBAC3_ORG_DATASCOPE_ALLOCATION_ER_MODEL`。
- ✅ 涉及 **`GET /sys/users/current`** 与 **`POST /sys/users/current/active-scope`** 的产品语义、文档或测试时，必须区分 **M4 读** 与 **M4 写**（Session 权威落点、写后 `tokenRefreshRequired`），以 `docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md` §8 与 `91-tiny-platform-auth.rules.md` 为准；不得将矩阵 §4 的 M4 行泛化为“所有 user 端点同一行为”。

## 应该（Should）

- ⚠️ 实施顺序优先为：权限码规范与控制面 RBAC收口 -> `role_assignment` -> RBAC3 约束 -> Scope -> Data Scope。
- ⚠️ 当前 membership 已以 `tenant_user` 为运行态真相源；后续授权重构、数据权限扩面和会话改动不应再回退到 `user.tenant_id` 作为默认授权模型。
- ⚠️ 若未来明确支持“一人多租户”，应将 `tenant_user`、当前激活租户、claims/session、菜单下发、审计日志一起设计，不单点修改。
- ⚠️ 授权上下文应逐步从“只看 tenantId”升级为“tenant + 有效角色分配 + 组织单元 + 数据范围”的统一视图。
- ⚠️ 控制面接口、菜单管理、前端权限守卫和初始化数据应使用同一套授权模型术语，避免文档与代码语义漂移。
- ⚠️ 文档状态词建议固定理解为：`已落地` = 结构/基础能力已在库或已在运行态出现；`已闭合` = 当前主消费链与验证证据已收口；`待闭合` = 表结构/能力存在但运行时统一消费、证据等级或兼容清退仍未完成。
- ⚠️ 功能权限与数据权限应分层推进：`permission` 负责能力，`role_data_scope*` 负责数据范围，避免在 `resource/menu/api` 载体层硬编码数据过滤语义。
- ⚠️ 如采用平台模板派生租户副本的模型，应提前定义副本优先级、同步策略和回退策略，避免运行态模板选择不一致。

## 可以（May）

- 💡 可以在后续阶段引入 `role_assignment`、`role_hierarchy`、`role_mutex`、`role_prerequisite`、`role_cardinality`、`role_data_scope`、`role_data_scope_item` 等正式模型。
- 💡 可以为高风险授权操作增加审计日志，例如角色分配、范围变更、数据权限变更。
- 💡 可以维护“已做 / 未做 / 待改进”的授权治理清单，用于分阶段推进与评审。

## 例外与裁决

- 权限码命名与规范码列表由 `92-tiny-platform-permission.rules.md` 与 `docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md` 约束，本规范不重新定义权限码格式。
- 遗留角色码（如 ROLE_ADMIN/ADMIN）与规范权限码的演进关系、控制面 Guard 与规范码对应见 `docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md` 与权限规范文档“控制面与平台级”小节。
- JWT / Session / claims / MFA 等认证链路要求由 `91-tiny-platform-auth.rules.md` 约束。
- 租户生命周期、治理审计、租户配额、租户控制面剩余修复基线由 `94-tiny-platform-tenant-governance.rules.md` 约束。
- 冲突时：`90-tiny-platform.rules.md` 优先级高于本规范；阅读入口与冲突裁决顺序以 `docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md` 为准；授权模型实现细节以 `docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md` 为设计基线，当前完成度以 `docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md` 为准。

## 示例

### ✅ 正例

```text
阶段 1：保持 role_permission -> permission 作为功能权限真相源，新增 role_assignment 替换 user_role
阶段 1.5：为 resource 维护 required_permission_id，逐步退场 resource.permission 的字符串 join
阶段 2：新增 role_hierarchy / role_mutex / role_prerequisite / role_cardinality
阶段 3：新增 organization_unit / user_unit，再扩展 scope_type=ORG/DEPT
阶段 4：新增 role_data_scope / role_data_scope_item，并在查询层统一下推数据过滤
```

### ❌ 反例

```text
在 user_role 上直接加 dept_id，当作部门 Scope 使用
在角色编码里写死 DEPT_SELF、ORG_ALL，代替正式数据权限模型
新建 permission 表，但运行态仍继续新增 `resource.permission = permission_code` 的新主链路，长期双轨并存
```
