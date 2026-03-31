# Tiny Platform 模块盘点附录

> 基于 6 大功能模块需求与当前实现的逐项盘点。  
> 初始日期：2026-03-16  
> 关联：`TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`、`TINY_PLATFORM_AUTHORIZATION_MODEL.md`
>
> 说明：
> - 本文件保留“按模块看问题”的阅读方式，适合作为附录和盘点参考。
> - 当前完成度、优先级、真实剩余项，以 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md` 为唯一真相源。
> - 本文件中的总览百分比仅作粗略盘点，不作为执行口径；若与 Task List 冲突，以 Task List 为准。

---

## 总览（粗略盘点，非执行口径）

| 模块 | 盘点结论 | 当前关注点 |
|------|----------|-----------|
| 1. 租户治理 | 基础能力已成型 | 更细粒度只读保留期策略、套餐/归档能力、独立详情页 |
| 2. 组织与部门 | 基础骨架已完成 | 负责人、搜索/分页、DTO 校验、批量操作 |
| 3. 用户管理 | 核心管理能力可用 | 租户成员管理、组织归属集成、OAuth/OIDC 绑定管理 |
| 4. 角色与权限管理 | 授权主链已基本成型 | DataScope 扩面、控制面体验收口、更多运行态消费 |
| 5. 资源与菜单管理 | 控制面已较完整 | resourceLevel 运行态接入、自动扫描、导入导出 |
| 6. 策略与审计 | 审计基座已可用 | 图表仪表盘、告警/风控联动、策略中心 |

---

## 模块 1：租户治理

### 需求对照

| 功能 | 状态 | 已实现 | 未实现 |
|------|------|--------|--------|
| **租户列表** | 大部分实现 | 分页、搜索（code/name/domain/enabled/lifecycleStatus）、CRUD、生命周期状态展示、`includeDeleted` 管理入口、只读详情抽屉 | 无更多治理维度筛选、无独立详情页 |
| **租户创建/导入** | 大部分实现 | 创建 + Bootstrap（克隆平台模板角色和资源）+ 平台模板显式初始化/回填入口 + 初始管理员用户创建 + `tenant_user` membership + `ROLE_ADMIN` 默认赋权 + 本地密码认证方法初始化 | 无导入功能、无试用/正式模式 |
| **配额与套餐管理** | 第一阶段已落地 | `maxUsers`/`maxStorageGb`/`planCode`/`expiresAt` 可编辑展示；已在初始管理员/用户创建、头像上传、导出文件落盘执行运行时校验 | 无套餐实体/定价表、无计费系统集成、更多文件写链路尚未接入配额 |
| **扩容与迁移** | 未实现 | — | 无纵横向扩容、无跨实例/跨地域迁移 |
| **合并与拆分** | 未实现 | — | 无租户合并/拆分功能 |
| **冻结与解冻** | 已实现 | 生命周期状态机、freeze/unfreeze/decommission 专用端点、前端按钮、FROZEN 登录阻断、DECOMMISSIONED 请求阻断、写操作保护 | 无更细粒度“只读保留期”策略 |
| **下线/归档** | 未实现 | 仅软删除（设 `deletedAt`） | 无数据导出/账单结算/保留期/彻底清除流程 |
| **操作审计** | 已实现 | 已接入 `TENANT_CREATE/UPDATE/DELETE/FREEZE/UNFREEZE/DECOMMISSION` 事件并进入统一授权审计；detail 已结构化，支持导出和概览聚合 | 无告警联动、无更强图表仪表盘 |

### 关键缺口清单

| ID | 缺口 | 优先级 | 预估工作量 | 说明 |
|----|------|--------|-----------|------|
| T-1 | 生命周期状态机 | 已完成 | 2026-03-19 | 已完成合法状态流转、FROZEN 登录阻断与 DECOMMISSIONED 请求阻断 |
| T-2 | 冻结/解冻专用 API + UI | 已完成 | 2026-03-19 | 已补 freeze/unfreeze/decommission 专用端点和前端操作按钮 |
| T-3 | 租户治理审计 | 已完成 | 2026-03-19 | 已接入统一授权审计 |
| T-4 | lifecycleStatus 前端展示+筛选 | 已完成 | 2026-03-19 | 列表已支持状态展示与筛选 |
| T-5 | Bootstrap 增强：创建管理员用户 + tenant_user | 已完成 | 2026-03-19 | 创建租户时已自动创建初始管理员用户、建立 `tenant_user` 记录、赋 `ROLE_ADMIN` 并初始化本地密码认证方法 |
| T-6 | 配额运行时校验 | 已完成（第一阶段） | 2026-03-19 | 已在初始管理员/用户创建、头像上传、导出文件落盘执行配额校验；剩余文件/附件写链路后续继续扩面 |
| T-7 | 套餐实体与管理 | P2 | L | Plan 表/实体/CRUD，`planCode` 关联实际套餐定义 |
| T-8 | 下线/归档流程 | P2 | L | 数据导出 + 保留期 + 彻底清除 + 账单结算 |
| T-9 | expiresAt 日期选择器 | P2 | XS | 前端 TenantForm 的 `expiresAt` 改为 `a-date-picker` |
| T-10 | 租户详情页 | P2 | S | 已有列表内只读详情抽屉；如需更强治理体验，可再补独立详情页/时间轴 |
| T-11 | 按钮级权限门控 | 已完成 | 2026-03-19 | 租户管理页已按细粒度权限常量控制创建/编辑/删除/冻结/解冻/下线等动作 |
| T-12 | 导入/导出/合并/拆分/扩容 | P3 | XL | 高级运营能力，涉及基础设施层 |

---

## 模块 2：组织与部门

### 需求对照

| 功能 | 状态 | 已实现 | 未实现 |
|------|------|--------|--------|
| **组织/部门管理** | 基础实现 | ORG/DEPT 树 CRUD；环检测；删除前检查（子节点/成员）；审计；前端左右分栏布局 | 无分页/搜索/过滤；无拖拽排序；无批量操作；无子树移动；无物化路径；Controller 使用 `Map<String,Object>` 无校验 DTO |
| **部门负责人管理** | 未实现 | — | `OrganizationUnit` 无 `leader_id`/`head_id`；`UserUnit` 无 `is_head`/`is_leader`；无 API/UI |
| **岗位/职级管理** | 未实现 | — | 无 position/rank/job_title 实体、表、API、UI，零代码 |
| **用户归属管理** | 部分实现 | 查看成员列表、添加/移除成员、设置/查询主部门；审计 | 添加成员只能输入 ID（无用户搜索）；成员列表不含用户名/邮箱；无批量调岗；`toDto` N+1 查询 |

### 关键缺口清单

| ID | 缺口 | 优先级 | 预估工作量 | 说明 |
|----|------|--------|-----------|------|
| O-1 | 部门负责人 | P1 | M | `organization_unit` 加 `leader_user_id`（或 `user_unit` 加 `is_head`），API + UI + 审计 |
| O-2 | 用户搜索选择器 | P1 | S | 添加成员时替换 ID 输入为可搜索用户选择组件 |
| O-3 | 成员列表联查用户详情 | P1 | S | JOIN user 表返回 username/nickname/email，解决 N+1 |
| O-4 | 请求校验 DTO | P1 | S | Controller 改为 typed DTO + `@Valid`，替换 `Map<String,Object>` |
| O-5 | 分页与搜索 | P2 | S | 列表端点增加分页、按 name/code/status/unitType 过滤 |
| O-6 | 批量调岗 | P2 | M | 批量移除/添加/转移成员的 API + UI |
| O-7 | 岗位/职级管理 | P3 | L | 新建 position/rank 实体表，关联 role_assignment 和 data_scope |
| O-8 | 子树操作 | P3 | M | 移动子树、级联启用/禁用、拖拽排序 |

---

## 模块 3：用户管理

### 需求对照

| 功能 | 状态 | 已实现 | 未实现 |
|------|------|--------|--------|
| **用户列表** | 已实现 | 分页搜索（username/nickname）；批量启/禁/删除；头像管理；登录历史；管理端与用户自服务端点已成套 | email/phone 不在列表 DTO 和搜索条件中 |
| **账号详情与安全** | 大部分实现 | 密码设置（bcrypt）；TOTP 自助绑定/解绑；OAuth 方法实体支持 GITHUB/GOOGLE/LDAP；临时锁定策略；账号安全端点已较完整 | 无管理员侧 TOTP 管理；无 OAuth/OIDC 绑定管理 UI/API；无 "强制下次登录改密码" 标记；无邮件重置密码 |
| **租户成员关系** | 隐式实现 | `tenant_user` 表；角色赋权时自动创建成员记录；列表按 membership 过滤 | 无显式加入/退出/邀请 API；无成员管理 UI；无租户切换 UI |
| **组织/岗位分配** | 基础闭环已实现 | `UserUnitService` 支持归属 CRUD；用户创建/编辑表单已集成组织/部门多选与主部门设置；后端 `UserCreateUpdateDto` / `UserServiceImpl` 已同步 `user_unit` | 组织控制面仍无用户搜索选择器；无批量调岗；组织 Controller 仍使用 `Map<String,Object>` 而非校验 DTO |
| **账户启停/冻结** | 基础实现 | 批量启用/禁用；`accountNonLocked` 管理员可设；`TenantLifecycleGuard` 租户级写保护 | 无 "冻结原因" 记录字段；无用户级冻结专用端点 |

### 关键缺口清单

| ID | 缺口 | 优先级 | 预估工作量 | 说明 |
|----|------|--------|-----------|------|
| U-1 | 租户成员管理 API | P1 | M | `POST/DELETE /sys/tenants/{id}/members/{userId}`，加入/退出/邀请流程 |
| U-2 | 用户表单集成组织归属 | 已完成 | 2026-03-19 | `UserForm` 已支持组织/部门多选与主部门设置；`UserServiceImpl.createFromDto/updateFromDto` 已写入 `user_unit`，并补齐邮箱/手机号创建链路 |
| U-3 | OAuth/OIDC 绑定管理 | P1 | M | `GET /sys/users/{id}/auth-methods`，管理员查看/解绑用户的外部认证方式 |
| U-4 | email/phone 搜索与列表展示 | P2 | S | `UserRequestDto` 增加 email/phone 过滤；`UserResponseDto` 增加 email/phone 字段 |
| U-5 | 管理员 TOTP 管理 | P2 | S | 管理员强制解绑用户 TOTP（安全场景：用户丢失设备） |
| U-6 | 强制改密码标记 | P2 | S | User 实体增加 `mustChangePassword` 字段 + 登录流程拦截 |
| U-7 | 禁用原因记录 | P2 | S | User 增加 `disabledReason`/`disabledAt` 字段 |
| U-8 | 邮件密码重置 | P3 | L | 邮件发送 + 重置 token + 安全验证流程 |
| U-9 | 批量角色分配原子化 | P3 | S | 新增 `POST /sys/users/batch/roles` 端点替代前端循环调用 |

---

## 模块 4：角色与权限管理

### 需求对照

| 功能 | 状态 | 已实现 | 未实现 |
|------|------|--------|--------|
| **角色模板** | 部分实现 | Role 实体有 `roleLevel`（PLATFORM/TENANT）、`builtin` 标记；平台模板 `tenant_id IS NULL`；完整 CRUD + 前端页面 | `roleLevel` 前端未展示/过滤；无 ORG/DEPT 级角色；无 "从平台模板派生租户角色" 机制 |
| **权限码与资源** | 已实现 | `resource.permission` 为运行时权限真相源；Resource 实体支持 4 种类型（目录/菜单/按钮/API）；完整 CRUD + 树 + 前端页面 | 无独立 permission 注册表；无权限码 action 字典 |
| **角色治理 (RBAC3)** | 已实现 | 四类约束全覆盖（hierarchy/mutex/prerequisite/cardinality）；dry-run + enforce 模式；违例日志；管理 API + 前端页面；层级展开已接入权限解析 | enforce 默认关闭；前端 cardinality 表单缺少 scopeType 字段；无层级可视化图；无 "what-if" 模拟 |
| **角色分配** | 大部分实现 | `role_assignment` 实体支持 PLATFORM/TENANT/ORG/DEPT scope；`RoleAssignmentSyncService` 完整读写；前端 Transfer 组件；ORG/DEPT scope 分配 API 已补齐；赋权写入已补齐 `grantedBy/grantedAt` | 更多运行态消费仍主要围绕租户级链路；无批量跨用户分配端点 |
| **数据范围配置** | 大部分实现 | 完整框架：`@DataScope` 注解 + AOP 切面 + JPA Specification 构建器；8 种 scope 类型；管理 CRUD + 前端页面；`user/resource/menu/org/scheduling/export/dict` 已消费运行态数据范围；默认租户 `ROLE_ADMIN` 已补 `READ=ALL` seed，避免无规则时退回 SELF | 更多业务模块尚未接入；前端无 CUSTOM 明细管理 UI；无缓存层 |

### 关键缺口清单

| ID | 缺口 | 优先级 | 预估工作量 | 说明 |
|----|------|--------|-----------|------|
| R-1 | EffectiveRoleResolution 集成层级展开 | 已完成 | 2026-03-19 | 运行时 effective role / effective principal 已与 `role_hierarchy` 一致 |
| R-2 | ORG/DEPT scope 分配 API | 已完成 | 2026-03-19 | 端点 + Service + 前端选择器已补齐；当前剩余重点是更广泛运行态消费 |
| R-3 | @DataScope 业务接入扩面 | P1 | M | `user/resource/menu/org/scheduling/export/dict` 已接入，继续向更多核心查询扩展 |
| R-4 | enforce 模式灰度推进 | 已完成 | 2026-03-19 | 已完成租户灰度、阻断消息与控制面说明收口 |
| R-5 | roleLevel 前端展示 | P2 | S | 角色列表/表单中展示 PLATFORM/TENANT 标记，支持过滤 |
| R-6 | 资源搜索修复 | 已完成 | 2026-03-19 | 资源搜索链路已收口，当前不再作为角色模块剩余缺口维护 |
| R-7 | cardinality 表单 scopeType | P2 | XS | 前端 RoleConstraint.vue cardinality 模态框增加 scopeType 下拉 |
| R-8 | grantedBy 填充 | 已完成 | 2026-03-19 | `RoleAssignmentSyncService` 已在批量赋权写入时从 SecurityContext 解析当前操作者并回填 `grantedBy`，`grantedAt` 同批次统一落库 |
| R-9 | CUSTOM 数据范围明细 UI | P2 | M | DataScope.vue 增加 CUSTOM 明细管理界面（target_type + target_id 编辑） |
| R-10 | 角色层级可视化 | P3 | L | 前端使用 D3/mermaid 渲染 DAG 图 |

---

## 模块 5：资源与菜单管理

### 需求对照

| 功能 | 状态 | 已实现 | 未实现 |
|------|------|--------|--------|
| **资源目录** | 大部分实现 | 完整 CRUD + 树 + 批量删除 + 唯一性检查 + 租户隔离；前端树形表格 + 搜索 + 抽屉表单；搜索过滤已修复 | 无自动扫描（注解发现 API 并注册）；无导入/导出；无版本历史 |
| **菜单配置** | 已实现 | 菜单 CRUD；基于角色的菜单树过滤；平台菜单隐藏策略；循环引用检测；级联删除；前端懒加载 + 图标选择器 | `resourceLevel` 过滤未接入；无拖拽排序；无菜单克隆；无 i18n |
| **资源分配** | 已实现 | 角色管理页树形资源选择器（ResourceTransfer）；通过角色 CRUD 管理绑定；运行态关系已收口到 `role_permission -> permission -> resource` | 无独立资源分配端点；无批量导入/导出 |

### 关键缺口清单

| ID | 缺口 | 优先级 | 预估工作量 | 说明 |
|----|------|--------|-----------|------|
| M-1 | 资源搜索过滤修复 | 已完成 | 2026-03-19 | 资源搜索链路已切回真实检索条件 |
| M-2 | 新页面菜单注册 | 已完成 | 2026-03-19 | 已为组织/数据范围/审计/RBAC3 约束页面补齐 resource 与 `role_permission -> permission` 绑定 |
| M-3 | API 自动扫描（可选） | P3 | L | 基于注解扫描 Controller 端点并自动注册为 resource 记录 |
| M-4 | 资源导入/导出 | P3 | M | CSV/JSON 批量导入/导出资源定义 |
| M-5 | 菜单拖拽排序 | P3 | M | 前端拖拽 + 批量排序更新端点 |
| M-6 | resourceLevel 过滤接入 | P2 | S | 菜单 Service 按 resourceLevel 区分平台模板与租户菜单 |

---

## 模块 6：策略与审计

### 需求对照

| 功能 | 状态 | 已实现 | 未实现 |
|------|------|--------|--------|
| **授权审计** | 大部分实现 | 多类事件类型；同步/异步写入（REQUIRES_NEW）；查询端点 + 时间范围过滤 + 清理 + CSV 导出；前端分页表格 + 展开详情 + 概览卡片；共享事件类型常量已对齐 | 尚未做完整图表化仪表盘 |
| **认证审计** | 大部分实现 | 多类事件类型；独立事务写入；记录 IP/UA/租户解析；已补查询 Controller、前端页面、概览卡片与 CSV 导出 | 无告警/锁定联动 |
| **租户治理审计** | 已实现 | 租户治理事件已接入统一授权审计，前端可按租户查询并纳入概览统计；导出能力随授权审计一并可用 | — |
| **策略中心 (PBAC/OPA)** | 未实现 | — | 无策略引擎、无策略定义模型、无动态策略加载 |

### 关键缺口清单

| ID | 缺口 | 优先级 | 预估工作量 | 说明 |
|----|------|--------|-----------|------|
| A-1 | 认证审计查询 API + 前端页面 | 已完成 | 2026-03-19 | 已补 Controller、权限守卫、前端页面与路由入口 |
| A-2 | 租户治理审计接入 | 已完成 | 2026-03-19 | 已接入 AuthorizationAuditService |
| A-3 | 前端事件类型常量修正 | 已完成 | 2026-03-19 | 已抽取共享常量到 `webapp/src/constants/audit.ts`，授权/认证审计页面统一消费 |
| A-4 | 日期范围过滤 | 已完成 | 2026-03-19 | 授权/认证审计列表与 summary 接口均已支持时间范围参数 |
| A-5 | 审计日志导出 | 已完成 | 2026-03-19 | 已补 authorization/authentication CSV 导出端点、前端下载按钮与独立 export 权限码 |
| A-6 | 审计统计概览 | 已完成 | 2026-03-19 | 已补 authorization/authentication summary 聚合接口与页面概览卡片；完整图表仪表盘可作为后续增强 |
| A-7 | 会话管理 | 已完成 | 2026-03-19 | 已补当前登录用户服务端活跃会话查询、下线其他会话、指定会话强制下线；并通过服务端会话登记 + 过滤器阻断已撤销 session 的后续请求 |
| A-8 | 策略中心 (PBAC/OPA) | P3 | XL | 引入外部策略引擎，集中管理动态授权规则（长期规划） |

---

## 优先级分层建议

> 当前真实优先级与执行顺序以 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md` 为准。  
> 本节只保留模块视角下的优先主题，不再维护并行的逐项批次表。

### 近期待办（模块视角）

- 租户治理：`T-10` 详情页产品化、只读保留期策略、套餐/归档能力。
- 组织与部门：`O-1/O-2/O-3/O-4`，即负责人、用户搜索、成员联查、请求校验 DTO。
- 用户管理：`U-1/U-2/U-3`，即租户成员管理、组织归属集成、OAuth/OIDC 绑定管理。
- 角色与权限：`R-3` 数据范围继续扩面、`R-5/R-7/R-8/R-9` 这类控制面与体验收口。
- 资源与菜单：`M-6` resourceLevel 进一步接入运行时消费。
- 审计：优先考虑图表化仪表盘和告警联动；会话管理已完成。

### 中长期主题

- 租户治理：套餐、归档、导入/导出、合并/拆分/扩容。
- 组织与部门：岗位/职级、子树操作。
- 用户管理：邮件重置密码、批量角色分配原子化。
- 角色与权限：角色层级可视化。
- 资源与菜单：自动扫描、资源导入导出、菜单拖拽排序。
- 审计与策略：图表化仪表盘、告警联动、PBAC/OPA 策略中心。

---

## 工作量标注说明

| 标记 | 含义 | 参考天数 |
|------|------|---------|
| XS | 极小 | < 0.5 天 |
| S | 小 | 0.5-1 天 |
| M | 中等 | 2-3 天 |
| L | 大 | 1-2 周 |
| XL | 极大 | > 2 周 |

---

## 附录：代表性控制器覆盖概览

> 下表只保留“模块由哪些控制器与守卫承接”的阅读入口，不再维护易过期的精确端点数。  
> 若需精确统计，以对应 Controller 源码中的 `@*Mapping` 为准。

| 模块 | Controller | 权限守卫 / 访问方式 |
|------|------------|-------------------|
| 租户 | `TenantController` | `TenantManagementAccessGuard` |
| 组织 | `OrganizationController` | `OrganizationAccessGuard` |
| 用户 | `UserController` | `UserManagementAccessGuard` |
| 角色 | `RoleController`、`RoleConstraintRuleController` | `RoleManagementAccessGuard` |
| 资源 | `ResourceController` | `ResourceManagementAccessGuard`、`MenuManagementAccessGuard` |
| 菜单 | `MenuController` | `MenuManagementAccessGuard` |
| 数据范围 | `DataScopeController` | `DataScopeAccessGuard` |
| 授权审计 | `AuthorizationAuditController` | `AuthorizationAuditAccessGuard` |
| 认证审计 | `AuthenticationAuditController` | `AuthenticationAuditAccessGuard` |
| 认证安全 | `SecurityController` | 用户自服务（已认证即可） |
