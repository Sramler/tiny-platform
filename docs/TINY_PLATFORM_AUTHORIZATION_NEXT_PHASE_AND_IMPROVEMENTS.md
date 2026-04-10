# Tiny Platform 下一阶段变更与权限/模型改进清单

> 基于当前权限标识符规范与授权模型文档，汇总：下一阶段应做变更、仍存在的遗留不符项、以及建议的改进与规范化处理。  
> 关联：`TINY_PLATFORM_AUTHORIZATION_MODEL.md`、`TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md`、`TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md`、`TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md`。
> 说明：本文件是路线图与改进池，不承担“当前完成度”的唯一维护职责；当前真实状态以 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md` 为准。

---

## 1. 下一阶段应该做哪些变更

按授权模型文档的阶段划分，建议顺序如下。

### 1.1 阶段 0 收口（现状收口，优先做）

| 项 | 说明 |
| --- | --- |
| 控制面剩余 RBAC 收口 | `RoleController` / `ResourceController` 已通过 AccessGuard 做方法级 RBAC；确认无遗漏接口、无直接依赖 `user.tenant_id` 做授权判断的新代码。 |
| `resource.permission` 规范码扫尾 | 确保所有运行态（后端 Guard、JWT、菜单、前端 v-permission）只接受规范码；历史旧码仅出现在迁移脚本的“被替换值”中。 |
| 平台/租户/普通用户入口分离 | 平台级入口（租户管理、幂等治理、字典平台、导出）已按规范码 + TenantContext.isPlatformScope() 判定；LegacyAuthConstants 已删除；保持与文档一致，不新增“默认租户即平台”的语义。 |

### 1.2 第一阶段主交付（membership + role_assignment）

| 项 | 说明 |
| --- | --- |
| `tenant_user` 表与回填 | 已完成建表与回填；运行态 membership 已切到 `tenant_user`，`user.tenant_id` 仅剩展示/审计兼容。 |
| `role_assignment` 已存在 | 已建表且成为运行态授权主来源；`user_role` 读回退已下线。 |
| UserDetailsService / 登录加载 | 已按“平台账号 + tenant_user”加载，再按 `role_assignment` 展开权限，不再回退 `user_role`。 |
| SecurityUser / JWT / Session 契约 | ✅ 已完成 09C3/09C4 收口：已移除 `role.name`；新签发 authorities 以 permission/factor/scope 为主；`roleCodes` 为显式契约供少量合法消费者；claims 已含 `permissions`、`roleCodes`、`activeScopeType`、`activeTenantId`、`permissionsVersion`。 |
| 平台作用域与 default 租户拆分 | 平台级能力用 `scope_type=PLATFORM` 表达，不再用“当前租户 = default”作为平台语义；配置化平台租户 code 已做，长期改为 PLATFORM 作用域。 |
| 控制面接口 RBAC | 用户/角色/资源/菜单/租户/调度/字典/导出已收口；查漏补缺，禁止新增接口绕过 Guard。 |
| 菜单与权限下发 | 菜单树按 `resource.permission` 与当前用户 permissions 过滤；permissions 来源逐步从 assignment 链路计算。 |

### 1.3 第二阶段及以后（按文档）

- **阶段 2**：`role_hierarchy`、`role_mutex`、`role_prerequisite`、`role_cardinality`；授权时校验，冲突明确报错。
- **阶段 3**：`organization_unit`、`user_unit`；扩展 `role_assignment.scope_type`（ORG/DEPT）；统一授权上下文。
- **阶段 4**：`role_data_scope`、`role_data_scope_item`；按模块计算数据范围，不散落业务 SQL。
- **阶段 4.5**：carrier requirement 继续收口；`menu_permission_requirement / ui_action_permission_requirement / api_endpoint_permission_requirement` 已落库，下一步统一 `ui_action` 展示门控、`api_endpoint` 路由守卫与 requirement-aware 审计。

### 1.4 平台模板治理与副本语义（正式契约 B，2026-03-29）

- **选项 B（当前唯一口径）**：**不**提供租户副本「一键重建 / 回退」HTTP 入口；原因与替代手段见 `TINY_PLATFORM_TENANT_GOVERNANCE.md` **§3.2**。这不是“将来默认会做 A”，而是在缺少稳定回退目标态与合并语义前的**正式产品边界**。
- **副本差异审计（最小可用）**：已提供 `GET /sys/tenants/{id}/platform-template/diff`，并写入 `AuthorizationAuditEventType.PLATFORM_TEMPLATE_DIFF`（包含 summary + bounded diff sample）。差异口径只覆盖 carrier 快照关键字段，不作为运行时授权真相源。
- **跟随同步策略**：**不自动同步**（no auto-follow），仅显式 `initialize`（仅回填 **`tenant_id IS NULL` 缺失** 的平台模板）与 `diff`；避免无审计跨租户批量变更。
- **重复派生（fail-closed）**：`TenantBootstrapServiceImpl.bootstrapFromPlatformTemplate` 在目标租户已存在角色或 carrier 副本时拒绝；仅**新租户创建**路径调用一次 bootstrap。

---

## 2. 仍存在的、不符合新权限标识符与权限模型的遗留

### 2.1 权限标识符层面

| 位置 | 遗留表现 | 说明 |
| --- | --- | --- |
| `menu_resource_data.sql` | 资源行 `name` 列为历史风格（如 `user:update`、`menu:create`） | 该文件为参考脚本、不参与默认 Liquibase；`permission` 列已是规范码。建议：若继续保留此文件，将 `name` 与规范命名对齐或加注释“仅历史 key，以 permission 为准”。 |
| `verify-scheduling-migration-smoke.sh` | 脚本中仍出现 `'scheduling:read'` | 若脚本用于断言或展示，应改为规范码 `scheduling:console:view`，避免误导。 |
| ~~前端 `Menu.vue`~~ | ~~`MENU_MANAGEMENT_CREATE_AUTHORITIES = ['ROLE_ADMIN', 'ADMIN', 'system:menu:create']`~~ | ✅ 已收口：菜单管理页权限常量已集中到 `permission.ts`，当前为纯规范码集合。 |
| 前端调度相关 Vue | 多处 `scheduling:*`、`scheduling:console:view` 等 | 已为规范码，无问题；保持与后端 SchedulingAccessGuard 一致即可。 |

### 2.2 授权模型层面

| 位置 | 遗留表现 | 说明 |
| --- | --- | --- |
| ~~`user.tenant_id` 必填 + 双写~~ | ~~创建/更新用户仍写 tenant_id~~ | ✅ **已退场**：createFromDto 不再写 tenant_id，字段已 nullable（044 迁移），setter 标记 @Deprecated。归属以 tenant_user 为准。 |
| ~~`uk_user_tenant_username`~~ | ~~唯一约束为 `(tenant_id, username)`~~ | ✅ **已收口**：已删除旧唯一约束并改为全局唯一 `uk_user_username`。 |
| ~~平台 = code "default" 的租户~~ | ~~TenantManagementAccessGuard、MenuServiceImpl、IdempotentMetricsAccessGuard~~ | ✅ **已收口**：Guard 和 MenuServiceImpl 已改为 `TenantContext.isPlatformScope()`，`PlatformTenantResolver` 缓存平台租户 ID，`TenantContextFilter` 在请求入口设置 `activeScopeType`。 |
| 平台 = tenant_id 0（字典） | ~~DictTenantScope.PLATFORM_TENANT_ID = 0~~ | ✅ **已收口（T7.1）** | 与“default 租户”可能不一致；需统一平台语义（可配置平台租户 ID 或显式约定 0 仅字典用）。 |
| ~~ROLE_ADMIN / ADMIN 双码~~ | ~~各 Guard、MenuServiceImpl、ExportController~~ | ✅ **已收口**：`LegacyAuthConstants` 已删除，所有 Guard 改为纯细粒度权限码。`data.sql` 将权限码授予 ROLE_ADMIN 角色。 |
| role.name 进入展示/下游 | RoleController、CamundaIdentityProvider | 鉴权已不依赖 role.name；展示与工作流可保留，建议注释“仅展示/下游，鉴权不依赖”。 |
| role.code 进入 authority | SecurityUser、JWT | ✅ 已完成 09C3 收缩：新签发 authorities 不再承载 `ROLE_*` 主链输出；角色码通过显式 `roleCodes` 给少量合法消费者；旧 token 读兼容仅保留在解码侧。 |
| ~~user_role 只读回退~~ | ~~RoleAssignmentRepository、EffectiveRoleResolutionService~~ | ✅ **已收口**：运行态不再读取或回退 `user_role`。 |
| TenantBootstrap 模板来源 | TenantBootstrapServiceImpl | ✅ 已收口到“平台模板 + 租户派生”：运行时统一从 `tenant_id IS NULL` 模板派生；仅当历史环境缺模板时，按配置的平台租户 code 回填一次模板。 |

### 2.3 运行态契约

| 项 | 当前 | 目标 |
| --- | --- | --- |
| JWT/Session 中的 authority | ✅ 已完成 09C3：新签发 authorities 收缩为 permission / factor / scope；claims 含 `permissions`、`roleCodes`、`activeScopeType`、`activeTenantId`、`permissionsVersion` | 保持“新签发不输出 `ROLE_*`，旧 token 仅解码兼容”边界，不回退到混合态。 |
| ORG/DEPT **active scope** 非法态 | ✅ **已收口（2026-03-28）**：`TenantContextFilter` 对非法 ORG/DEPT scope **fail-closed**（Session：**403** `invalid_active_scope` + 重置 session scope / 清理 `SecurityContext`；Bearer：**401** + `invalid_token`）；与 `UserController.switchActiveScope` 的 **400/403/503** 语义一致；不因校验失败 **500**。 | 不改变上述拒绝语义。 |
| 前端租户控制面与 `active-scope-changed` | ✅ **边界固化（2026-03-29）**：`shouldReloadTenantControlPlaneOnActiveScopeChange()`（`activeScopeEvents.ts`）为唯一推荐入口；无活动租户时不响应 scope 切换重拉（**非缺能力**）。构建卫生（Javac / JaCoCo / Vite）台账见 `TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md` §11。 | 新增页面复用 helper；禁止第二套「有无租户」推断。 |
| ORG/DEPT active scope **业务读路径** | ✅ **已扩面（2026-03-28）**：`DataScopeResolverService` + `@DataScope` 驱动 user/org；调度 **DAG 列表**、导出任务控制面在切换 scope 后重拉；`listDags` 与 **`ExportTaskService.findReadableTasks`（第三批）** 均在库侧 `Specification` 消费 `DataScopeContext`（export：`tenant_id` + owner 双字段 OR）。**菜单/资源控制面（第四批）**：`Menu.vue` / `resource.vue` scope 切换重拉；`ResourceServiceImpl` 树/top/child 读与分页读共享 `created_by` 谓词。**字典（第五批）**：租户字典 `dictType.vue` / `dictItem.vue` 在已选活动租户时 scope 切换重拉；后端 `DictTypeServiceImpl`/`DictItemServiceImpl` 已 `@DataScope`。`getRoleIdsByUserId` 按 scope 取有效角色。**运行历史（正式不接入）**：`getDagRuns` / `DagHistory` 为租户内运维视图，无 `@DataScope`、不读 `DataScopeContext`、前端不监听 scope 切换；契约与测试见扩面指南「运行历史契约」。 | **Contract B（正式固化，2026-03-28）**：ORG active scope 下 ORG 形规则锚活动组织，DEPT 形规则锚**主部门**；非「未实现」，变更须先产品约定 + 测试（见 `TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md` Contract B）。 |
| 新增代码禁止 | — | 禁止新增依赖 `user.tenant_id` 做真实授权判断；禁止新增以 `user_role` 为唯一权限真相源。 |

---

## 3. 按当前权限标识符与权限模型应做的改进与规范化

### 3.1 权限标识符

| 方面 | 改进建议 |
| --- | --- |
| **唯一真相源** | 运行态、前端、菜单、初始化数据、文档仅使用规范码；旧码仅出现在迁移脚本的 CASE/WHERE 中。 |
| **控制面与平台级** | 已文档化（权限规范 5.5）；Guard 已统一为规范码 + 平台 scope 判定，不再依赖 `LegacyAuthConstants`。 |
| **新增权限** | 一律三段式/四段式；domain 稳定；action 用 list/view/create/edit/delete 等规范动作；不新增两段式或 manage 等模糊动作。 |
| **调度域** | 已规范：scheduling:console:view、scheduling:console:config、scheduling:run:control、scheduling:audit:view、scheduling:cluster:view、scheduling:*；保持与 SchedulingAccessGuard 一致。 |
| **导出** | 已引入 system:export:view；与管理员兼容；无需再改，保持即可。 |
| **字典平台** | dict:platform:manage；保持即可。 |
| **幂等治理** | idempotent:ops:view；保持即可。 |
| **扫描与治理** | 定期扫描代码与数据中的旧权限码、未使用权限、重复权限；新能力必须同步更新后端、前端、初始化与文档。 |

### 3.2 授权模型

| 方面 | 改进建议 |
| --- | --- |
| **权限来源** | 运行态权限主链已统一为 `role_assignment → role_hierarchy → role → role_permission → permission`；carrier 由 `menu / ui_action / api_endpoint + *_permission_requirement` 承接。历史文档中的 `resource.permission` 仅作迁移命名口径保留。 |
| **避免第二套真相源** | `permission` / `role_permission` 已落地；后续不再回退到 `role_resource` 或仅以 `resource.permission` 充当唯一关系真相源。 |
| **平台作用域** | 将“平台”从“default 租户”语义中拆出；用 scope_type=PLATFORM 或可配置平台租户 ID；平台模板与租户模板共表时用 role_level/resource_level 等区分。 |
| **角色与授权分离** | role 为模板；role_assignment 为“谁在何作用域下拥有何角色”；不在运行时用 role.name 做鉴权。 |
| **会话上下文** | 从“仅 tenantId”升级为“活动租户 + 有效角色分配 + 可选组织/部门”；切换上下文时刷新 Session/JWT。 |
| **export 模块查询形态** | ✅ **第三批（2026-03-28）**：`findReadableTasks` 已改为 `JpaSpecificationExecutor` + `Specification`：固定 `tenant_id`，受限时追加 `user_id`/`username` 与可见 owner 键集合的 OR 谓词，排序下推；与 `scheduling` 列表同为库侧收敛。 |
| **审计** | 关键授权操作记录 active_tenant_id、scope_type、permission_code 等；不记录敏感信息。 |

### 3.3 代码与数据

| 方面 | 改进建议 |
| --- | --- |
| **Guard 与 @PreAuthorize** | 控制面接口统一经 AccessGuard；Guard 内只使用规范码常量与平台 scope 判定，不散落字符串。 |
| **前端权限** | 与后端规范码一致；避免再次引入 ROLE_ADMIN/ADMIN 角色码硬编码；按钮/路由继续以统一权限常量为准。 |
| **初始化与迁移** | 新 changelog 只写规范码；旧码仅在迁移脚本中作为被替换值；menu_resource_data.sql 等参考脚本注明“以 permission 列为准”或更新 name 列。 |
| **脚本与 E2E** | 断言或展示用规范码（如 scheduling:console:view）；ensure-scheduling-e2e-auth.sh 等校验脚本与 037/039 规范码一致。 |

### 3.4 文档与规则

| 方面 | 改进建议 |
| --- | --- |
| **92 / 93 规则** | 已引用权限规范与遗留清单；新增权限或授权逻辑时先查 92/93 与 docs，避免发明新风格。 |
| **遗留清单** | 每完成一项收口或下线（如 user_role 停用、ADMIN 收口），更新 TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md。 |
| **权限规范文档** | 新增规范码时同步更新 TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md 与 5.5 控制面表；迁移目标表保持与 039 等一致。 |

---

## 4. 汇总：优先级建议

- **立即/短期**  
  - 阶段 0 收口：控制面 RBAC 与 permission 规范码扫尾；前端 Menu.vue 等与后端常量一致或从接口下发。  
  - 脚本与参考数据：verify-scheduling-migration-smoke.sh、menu_resource_data.sql 使用或注明规范码。  

- **第一阶段**  
  - 完成 tenant_user 回填与双写、role_assignment 为权限主来源、JWT/Session 契约收口（去掉 role.name、以 permissions 为主）。  
  - 平台语义从“default 租户”向 scope_type=PLATFORM 或可配置平台租户 ID 演进。  

- **后续阶段**  
  - 按授权模型文档阶段 2/3/4 推进 RBAC3、Scope、Data Scope；不提前引入独立 permission 表或 DENY 等未设计能力。  

以上内容与 `TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md`、`TINY_PLATFORM_AUTHORIZATION_MODEL.md` 保持一致，可作为下一阶段需求拆解与评审依据。

---

## 5. 验证门禁与执行口径（2026-03-30）

- **dev 串联**：`verify-platform-dev-bootstrap.sh` 前置条件与 **exit 0 / 1 / 2** 语义见 `TINY_PLATFORM_TESTING_PLAYBOOK.md` §1.2；**exit 2 = 环境未满足**，不得记为代码回归失败。
- **Maven**：同一 `tiny-oauth-server` 模块 **禁止**并发 `compile`/`test`；顺序门禁见 `tiny-oauth-server/scripts/mvn-tiny-oauth-server-gate-sequential.sh` 与 `TINY_PLATFORM_BUILD_TECH_DEBT_LEDGER.md` §2.1。
- **技术债全文**：`TINY_PLATFORM_BUILD_TECH_DEBT_LEDGER.md`（Spring/OAuth2/JaCoCo 等保留项；**Vite mixed import** §1.4；**主入口拆包 + Ant Design 策略 B（按需解析）** §1 / §2 与 `TINY_PLATFORM_TESTING_PLAYBOOK.md` §1.4.1）。
