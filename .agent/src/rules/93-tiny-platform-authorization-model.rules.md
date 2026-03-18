# 93 tiny-platform 授权模型规范（平台特定）

## 适用范围

- 适用于：角色模型、授权关系、Scope、Data Scope、菜单/资源授权、租户内与平台级控制面授权
- 典型文件：`**/auth/**`、`**/oauth/**`、`**/security/**`、`**/tenant/**`、`**/role/**`、`**/resource/**`、`**/menu/**`、`**/*AccessGuard.java`、`**/*.sql`、`**/*.yaml`
- 配套文档：`docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`、`docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md`（遗留演进路径）、`docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md`（权限码与控制面规范码）

## 禁止（Must Not）

- ❌ 在没有统一模型和迁移计划的前提下，在业务模块中自行发明一套新的授权关系表或“伪 Scope”字段。
- ❌ 用角色名称、权限码前缀、组织编码字符串等约定俗成方式，替代正式的 Scope / Data Scope 模型。
- ❌ 在运行态同时维护两套权限真相源，例如一套来自 `resource.permission`，另一套来自临时 `permission` 表。
- ❌ 把 Data Scope 直接编码进角色码、菜单名、SQL 片段或控制器分支中。
- ❌ 将角色继承、互斥、先决条件、基数限制留给运行时“碰到再说”，而不在授权时显式校验。
- ❌ 在未定义当前租户成员模型之前，直接假设系统已经支持“一人多租户”。
- ❌ 新增逻辑不得依赖 `user.tenant_id` 做真实授权判断；应以 membership（tenant_user）、role_assignment、当前活动租户（activeTenantId/Token/Session）为准；仅数据归属、展示、审计可继续使用 user.tenant_id。

## 必须（Must）

- ✅ 当前 tiny-platform 的运行态权限真相源仍以 `resource.permission` 为准，除非存在明确的全链路迁移方案。
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

## 应该（Should）

- ⚠️ 实施顺序优先为：权限码规范与控制面 RBAC收口 -> `role_assignment` -> RBAC3 约束 -> Scope -> Data Scope。
- ⚠️ 在未明确支持 `tenant_user` 前，默认按当前 `user.tenant_id` 模型推进授权重构，避免一次性改动过重。
- ⚠️ 若未来明确支持“一人多租户”，应将 `tenant_user`、当前激活租户、claims/session、菜单下发、审计日志一起设计，不单点修改。
- ⚠️ 授权上下文应逐步从“只看 tenantId”升级为“tenant + 有效角色分配 + 组织单元 + 数据范围”的统一视图。
- ⚠️ 控制面接口、菜单管理、前端权限守卫和初始化数据应使用同一套授权模型术语，避免文档与代码语义漂移。
- ⚠️ 如采用平台模板派生租户副本的模型，应提前定义副本优先级、同步策略和回退策略，避免运行态模板选择不一致。

## 可以（May）

- 💡 可以在后续阶段引入 `role_assignment`、`role_hierarchy`、`role_mutex`、`role_prerequisite`、`role_cardinality`、`role_data_scope`、`role_data_scope_item` 等正式模型。
- 💡 可以为高风险授权操作增加审计日志，例如角色分配、范围变更、数据权限变更。
- 💡 可以维护“已做 / 未做 / 待改进”的授权治理清单，用于分阶段推进与评审。

## 例外与裁决

- 权限码命名与规范码列表由 `92-tiny-platform-permission.rules.md` 与 `docs/TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md` 约束，本规范不重新定义权限码格式。
- 遗留角色码（如 ROLE_ADMIN/ADMIN）与规范权限码的演进关系、控制面 Guard 与规范码对应见 `docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md` 与权限规范文档“控制面与平台级”小节。
- JWT / Session / claims / MFA 等认证链路要求由 `91-tiny-platform-auth.rules.md` 约束。
- 冲突时：`90-tiny-platform.rules.md` 优先级高于本规范；授权模型实现细节以 `docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md` 为设计基线。

## 示例

### ✅ 正例

```text
阶段 1：保留 resource.permission 作为权限真相源，新增 role_assignment 替换 user_role
阶段 2：新增 role_hierarchy / role_mutex / role_prerequisite / role_cardinality
阶段 3：新增 organization_unit / user_unit，再扩展 scope_type=ORG/DEPT
阶段 4：新增 role_data_scope / role_data_scope_item，并在查询层统一下推数据过滤
```

### ❌ 反例

```text
在 user_role 上直接加 dept_id，当作部门 Scope 使用
在角色编码里写死 DEPT_SELF、ORG_ALL，代替正式数据权限模型
新建 permission 表，但运行态仍继续从 resource.permission 鉴权，长期双轨并存
```
