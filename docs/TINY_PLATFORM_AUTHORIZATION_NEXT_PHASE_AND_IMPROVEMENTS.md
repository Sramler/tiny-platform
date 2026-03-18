# Tiny Platform 下一阶段变更与权限/模型改进清单

> 基于当前权限标识符规范与授权模型文档，汇总：下一阶段应做变更、仍存在的遗留不符项、以及建议的改进与规范化处理。  
> 关联：`TINY_PLATFORM_AUTHORIZATION_MODEL.md`、`TINY_PLATFORM_PERMISSION_IDENTIFIER_SPEC.md`、`TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md`、`TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md`。

---

## 1. 下一阶段应该做哪些变更

按授权模型文档的阶段划分，建议顺序如下。

### 1.1 阶段 0 收口（现状收口，优先做）

| 项 | 说明 |
| --- | --- |
| 控制面剩余 RBAC 收口 | `RoleController` / `ResourceController` 已通过 AccessGuard 做方法级 RBAC；确认无遗漏接口、无直接依赖 `user.tenant_id` 做授权判断的新代码。 |
| `resource.permission` 规范码扫尾 | 确保所有运行态（后端 Guard、JWT、菜单、前端 v-permission）只接受规范码；历史旧码仅出现在迁移脚本的“被替换值”中。 |
| 平台/租户/普通用户入口分离 | 平台级入口（租户管理、幂等治理、字典平台、导出）已按规范码 + LegacyAuthConstants 收口；保持与文档一致，不新增“默认租户即平台”的语义。 |

### 1.2 第一阶段主交付（membership + role_assignment）

| 项 | 说明 |
| --- | --- |
| `tenant_user` 表与回填 | 新增表；把现有 `user.tenant_id` 数据回填到 membership；保留 `user.tenant_id` 双写/兼容读。 |
| `role_assignment` 已存在 | 迁移 041 已建表；写路径已走 assignment；需完成：运行态权限展开优先从 `role_assignment` 计算，`user_role` 仅作回退。 |
| UserDetailsService / 登录加载 | 按“平台账号 + tenant_user”加载，再按 `role_assignment` 展开权限；兼容期仍可读 `user_role`。 |
| SecurityUser / JWT / Session 契约 | authority 收敛为规范 `permissions` + 兼容 `roleCodes`；**移除 role.name 进入 JWT/Session**；`activeScopeType`、`activeTenantId` 已部分落地，继续收口。 |
| 平台作用域与 default 租户拆分 | 平台级能力用 `scope_type=PLATFORM` 表达，不再用“当前租户 = default”作为平台语义；配置化平台租户 code 已做，长期改为 PLATFORM 作用域。 |
| 控制面接口 RBAC | 用户/角色/资源/菜单/租户/调度/字典/导出已收口；查漏补缺，禁止新增接口绕过 Guard。 |
| 菜单与权限下发 | 菜单树按 `resource.permission` 与当前用户 permissions 过滤；permissions 来源逐步从 assignment 链路计算。 |

### 1.3 第二阶段及以后（按文档）

- **阶段 2**：`role_hierarchy`、`role_mutex`、`role_prerequisite`、`role_cardinality`；授权时校验，冲突明确报错。
- **阶段 3**：`organization_unit`、`user_unit`；扩展 `role_assignment.scope_type`（ORG/DEPT）；统一授权上下文。
- **阶段 4**：`role_data_scope`、`role_data_scope_item`；按模块计算数据范围，不散落业务 SQL。

---

## 2. 仍存在的、不符合新权限标识符与权限模型的遗留

### 2.1 权限标识符层面

| 位置 | 遗留表现 | 说明 |
| --- | --- | --- |
| `menu_resource_data.sql` | 资源行 `name` 列为历史风格（如 `user:update`、`menu:create`） | 该文件为参考脚本、不参与默认 Liquibase；`permission` 列已是规范码。建议：若继续保留此文件，将 `name` 与规范命名对齐或加注释“仅历史 key，以 permission 为准”。 |
| `verify-scheduling-migration-smoke.sh` | 脚本中仍出现 `'scheduling:read'` | 若脚本用于断言或展示，应改为规范码 `scheduling:console:view`，避免误导。 |
| 前端 `Menu.vue` | `MENU_MANAGEMENT_CREATE_AUTHORITIES = ['ROLE_ADMIN', 'ADMIN', 'system:menu:create']` | 与后端 Guard 一致（LegacyAuthConstants + 规范码）；建议抽成与后端一致的常量或从配置/接口下发，避免前端硬编码角色码扩散。 |
| 前端调度相关 Vue | 多处 `scheduling:*`、`scheduling:console:view` 等 | 已为规范码，无问题；保持与后端 SchedulingAccessGuard 一致即可。 |

### 2.2 授权模型层面

| 位置 | 遗留表现 | 说明 |
| --- | --- | --- |
| `user.tenant_id` 必填 + 双写 | 创建/更新用户仍写 `tenant_id`，与 `tenant_user` 双写 | 按阶段一设计保留；新逻辑应优先用 membership + `findById`，不再新增仅依赖 `tenant_id` 的授权判断。 |
| `uk_user_tenant_username` | 唯一约束为 `(tenant_id, username)` | 与目标“平台账号 username 全局唯一”冲突；下一阶段需配合 `tenant_user` 与数据清理，规划 `uk_user_username` 与废弃旧约束。 |
| 平台 = code "default" 的租户 | TenantManagementAccessGuard、MenuServiceImpl、TenantBootstrapServiceImpl、IdempotentMetricsAccessGuard | 已配置化 `platformTenantCode`；长期改为 `scope_type=PLATFORM`，不再用租户 code 表示平台。 |
| 平台 = tenant_id 0（字典） | DictTenantScope.PLATFORM_TENANT_ID = 0 | 与“default 租户”可能不一致；需统一平台语义（可配置平台租户 ID 或显式约定 0 仅字典用）。 |
| ROLE_ADMIN / ADMIN 双码 | 各 Guard、MenuServiceImpl、ExportController | 已统一到 LegacyAuthConstants；目标为数据与前端收口后仅 ROLE_ADMIN，或完全由控制面权限集替代。 |
| role.name 进入展示/下游 | RoleController、CamundaIdentityProvider | 鉴权已不依赖 role.name；展示与工作流可保留，建议注释“仅展示/下游，鉴权不依赖”。 |
| role.code 进入 authority | SecurityUser、JWT | 符合“不把 role.name 放入 authority”；若未来仅用 permissions，可逐步不再把 role.code 放入。 |
| user_role 只读回退 | RoleAssignmentRepository、EffectiveRoleResolutionService | 迁移 041 回填后多数环境可不依赖；计划第二阶段停用并下线 user_role 表。 |
| TenantBootstrap 从 default 复制 | TenantBootstrapServiceImpl | 目标态“平台模板 + 租户派生”；需引入模板层级或平台模板表后再改。 |

### 2.3 运行态契约

| 项 | 当前 | 目标 |
| --- | --- | --- |
| JWT/Session 中的 authority | 含 role.code、resource.permission；部分已含 permissions 与 activeScopeType | 仅保留 permissions + 必要 roleCodes（兼容）；移除 role.name；activeScopeType/activeTenantId 稳定。 |
| 新增代码禁止 | — | 禁止新增依赖 `user.tenant_id` 做真实授权判断；禁止新增以 `user_role` 为唯一权限真相源。 |

---

## 3. 按当前权限标识符与权限模型应做的改进与规范化

### 3.1 权限标识符

| 方面 | 改进建议 |
| --- | --- |
| **唯一真相源** | 运行态、前端、菜单、初始化数据、文档仅使用规范码；旧码仅出现在迁移脚本的 CASE/WHERE 中。 |
| **控制面与平台级** | 已文档化（权限规范 5.5）；Guard 统一“规范码 或 LegacyAuthConstants”；目标态仅规范码。 |
| **新增权限** | 一律三段式/四段式；domain 稳定；action 用 list/view/create/edit/delete 等规范动作；不新增两段式或 manage 等模糊动作。 |
| **调度域** | 已规范：scheduling:console:view、scheduling:console:config、scheduling:run:control、scheduling:audit:view、scheduling:cluster:view、scheduling:*；保持与 SchedulingAccessGuard 一致。 |
| **导出** | 已引入 system:export:view；与管理员兼容；无需再改，保持即可。 |
| **字典平台** | dict:platform:manage；保持即可。 |
| **幂等治理** | idempotent:ops:view；保持即可。 |
| **扫描与治理** | 定期扫描代码与数据中的旧权限码、未使用权限、重复权限；新能力必须同步更新后端、前端、初始化与文档。 |

### 3.2 授权模型

| 方面 | 改进建议 |
| --- | --- |
| **权限来源** | 运行态权限以 `resource.permission` 为准；展开逻辑优先 role_assignment → role → role_resource → resource.permission；user_role 仅回退。 |
| **不引入第二套 permission 表** | 在未定迁移方案前，不新增独立 permission 表；避免双真相源。 |
| **平台作用域** | 将“平台”从“default 租户”语义中拆出；用 scope_type=PLATFORM 或可配置平台租户 ID；平台模板与租户模板共表时用 role_level/resource_level 等区分。 |
| **角色与授权分离** | role 为模板；role_assignment 为“谁在何作用域下拥有何角色”；不在运行时用 role.name 做鉴权。 |
| **会话上下文** | 从“仅 tenantId”升级为“活动租户 + 有效角色分配 + 可选组织/部门”；切换上下文时刷新 Session/JWT。 |
| **审计** | 关键授权操作记录 active_tenant_id、scope_type、permission_code 等；不记录敏感信息。 |

### 3.3 代码与数据

| 方面 | 改进建议 |
| --- | --- |
| **Guard 与 @PreAuthorize** | 控制面接口统一经 AccessGuard；Guard 内只使用规范码常量与 LegacyAuthConstants，不散落字符串。 |
| **前端权限** | 与后端规范码一致；避免硬编码 ROLE_ADMIN/ADMIN 列表扩散；可从接口或配置下发“控制面权限集”用于按钮/路由。 |
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
