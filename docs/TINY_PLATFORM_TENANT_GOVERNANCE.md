# Tiny Platform Tenant Governance

> 状态：租户治理专题文档  
> 适用范围：`tenant / auth / security / audit / bootstrap / control-plane`  
> 关联主线：`TINY_PLATFORM_AUTHORIZATION_MODEL.md`、`TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`、`TINY_PLATFORM_TENANT_NAMING_GUIDELINES.md`

> 说明：
>
> - 本文件负责租户生命周期、治理动作、平台模板与治理审计的专题约束。
> - 当前真实完成度与优先级，以 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md` 为准。
> - 关联背景：`saas-multi-tenant-issues-analysis.md`。

---

## 1. 租户生命周期阶段（目标态）

| 阶段                      | 描述                                                                     | 设计要点（借鉴多租户最佳实践）                                                                                                           |
| ------------------------- | ------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------- |
| PENDING_CREATE / TRIAL    | 请求已接收但尚未完成 bootstrap；或试用期租户                             | 需考虑自助/人工开通流程、试用配额与有效期、数据驻留与合规要求；在该阶段应准备好模板数据库、预设 plan/配额、试用限制和到期策略。          |
| ACTIVE                    | 正常运行状态，允许登录、业务写入、调度/导出等能力                        | 需要持续的容量规划与“噪声邻居”治理；应在文档/运维手册中说明监控指标、告警策略、版本升级与变更窗口管理。                                  |
| FROZEN / DEACTIVATED      | 冻结/停用状态，不再允许新登录和写操作，保留只读访问                      | 适用于客户暂停订阅、风险排查或欠费缓冲期；应定义最长停留时间和自动转 DECOMMISSIONED 的条件，冻结期间计费策略可按产品决定（降费或暂停）。 |
| DECOMMISSIONED / OFFBOARD | 已下线状态，仅保留合规期内所需的审计/账单/归档数据，不再对外提供业务访问 | 应在进入该状态前完成账单结算和必要的数据导出/迁移；需定义数据保留期、再入驻策略以及归档位置，避免无界期的历史租户残留。                  |

> 说明：当前实现已具备 `lifecycleStatus`、`freeze/unfreeze/decommission` 控制面端点、登录与写入 fail-closed 保护、统一错误码、结构化租户治理审计、以及第一阶段配额执行（初始管理员/用户创建、头像上传、导出文件落盘）；`PENDING_CREATE/TRIAL/OFFBOARD`、计费联动、归档保留等仍属于后续治理目标。

---

## 2. 治理操作矩阵（草案）

| 操作                            | 允许发起者                                                           | 影响范围                                                                                                                                                                                               | 基本约束                                                                                                                                  | 审计要点                                                                    |
| ------------------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| 创建租户                        | 平台租户内具备租户管理权限的管理员（`@tenantManagementAccessGuard`） | 新建 `tenant` 记录、触发 `TenantBootstrapServiceImpl` 从平台模板派生角色/资源/菜单，并在同一事务中创建初始管理员用户、建立 `tenant_user` membership、赋 `ROLE_ADMIN`、初始化 `LOCAL/PASSWORD` 认证方法 | 禁止在普通业务租户内创建其他租户；必须通过平台租户控制面执行；需幂等保护（已有 `@Idempotent`）                                            | 记录 `createdTenantId`、初始管理员用户名/ID、发起人、plan、配额、来源 IP 等 |
| 更新租户（名称/域名/配额/plan） | 同上                                                                 | 更新 `tenant` 基本信息及配额字段                                                                                                                                                                       | 不得在业务 API 中直接修改租户配额，仅允许通过控制面；当前已对用户创建、头像上传、导出文件落盘执行第一阶段配额校验，剩余写链路后续继续补齐 | 记录变更前后 diff、发起人、时间点                                           |
| 冻结租户（ACTIVE → FROZEN）     | 平台管理员                                                           | 限制该租户的登录、业务写入、调度/导出执行                                                                                                                                                              | - 登录入口应拒绝该租户的新会话创建；<br>- 业务写 API 必须 fail-closed；<br>- 调度/导出 job 可选暂停或仅允许查看历史                       | 记录冻结原因、发起人、影响模块、预计恢复时间（如有）                        |
| 解冻租户（FROZEN → ACTIVE）     | 平台管理员                                                           | 恢复登录与业务写入能力                                                                                                                                                                                 | 仅在风险排查完成后执行；如涉及账单/合规，需要额外确认                                                                                     | 记录解冻原因、发起人、时间点                                                |
| 标记下线（DECOMMISSIONED）      | 平台管理员 + 合规流程                                                | 停止对外服务，仅保留审计/账单/归档访问                                                                                                                                                                 | 应先冻结，再完成数据导出/迁移，最后标记下线；不建议直接物理删除业务数据                                                                   | 记录决策依据、保留期限、归档位置                                            |

---

## 3. 与平台作用域语义的关系

当前运行态的平台控制面已经以 `activeScopeType=PLATFORM` 作为访问前提，并通过：

- `TenantManagementAccessGuard`：仅允许平台作用域用户访问 `/sys/tenants` 控制面；在此前提下继续校验 `system:tenant:*` 细粒度权限码。
- `TenantBootstrapServiceImpl`：优先使用 `tenant_id IS NULL` 的平台模板资源/角色；仅在缺失模板的历史环境下，才回退到配置的平台租户 code 做一次性回填。
- `TenantBootstrapServiceImpl`：已拒绝非法平台模板快照（`tenant_id IS NULL` 但 `role_level/resource_level` 非 `PLATFORM`）以及“目标租户已存在角色/资源副本”时的重复 bootstrap，避免把平台模板治理继续停留在约定层。
- **平台模板副本差异检测（治理可观测）**：已提供最小可用 diff 能力用于审计与排障：
  - API：`GET /sys/tenants/{id}/platform-template/diff`（平台作用域 + tenant read 权限）
  - 审计：`AuthorizationAuditEventType.PLATFORM_TEMPLATE_DIFF`（包含 summary + bounded diff sample）
  - 差异口径：仅比较 `menu/ui_action/api_endpoint` 载体快照的稳定逻辑 key（优先 `permissionCode`），并对关键字段（如 enabled/title/path/uri/method/requiredPermissionId/sort 等）输出字段级差异；该结果**不作为运行时授权真相源**。

### 3.1 平台模板副本治理策略（当前明确口径）

当前策略明确为：**不自动同步**（no auto-follow），仅允许显式治理入口：

- **初始化/回填**：当 `tenant_id IS NULL` 平台模板缺失时，通过 `/sys/tenants/platform-template/initialize` 触发一次性回填（按配置的平台租户租户 code 复制一次），之后新租户只从平台模板派生。
- **派生（bootstrap）**：新租户创建链路会调用 `TenantBootstrapServiceImpl.bootstrapFromPlatformTemplate(...)` 派生副本；目标租户若已存在任何角色或 carrier 副本，直接 fail-closed 拒绝重复派生（避免把“副本”当“模板”或引入隐式覆盖）。
- **差异审计/观测**：通过 `/sys/tenants/{id}/platform-template/diff` 获取差异结果并写入审计；用于回答“当前租户副本是否偏离模板、偏离在哪里”。

当前仍不提供（明确边界，而非缺失能力）：

- **自动跟随同步**：平台模板变更不会自动推送到租户副本；避免引入“无审计的跨租户批量变更”风险。
- **一键回退/重建**：当前不提供“删除租户副本并重新派生”的通用治理 API（高破坏性且需要更严格的前置条件与回滚策略）；若确需执行，必须走运维脚本/人工流程，并以审计留痕与最小影响为约束。

### 3.2 正式契约（选项 B）：不提供租户副本「一键重建 / 回退」API

**决策（2026-03-29）**：在缺少以下**可书面定义且可审计**的产品语义前，**不**开放受控「重建 / 回退」HTTP 入口（即不选阶段 A）：

- **回退目标态**：租户副本需与平台模板 `tenant_id IS NULL` 快照 **完全一致**，还是保留部分租户自定义（角色名、菜单标题、额外绑定等）？合并规则与冲突解决策略。
- **审批与幂等**：谁可发起、是否双人复核、是否与租户生命周期（FROZEN/DECOMMISSIONED）强绑定。
- **数据范围**：`role_assignment`、`tenant_user`、业务数据与 carrier 副本是否一并重置；若否，如何保证授权链一致。

**当前代码已实现的替代手段（正式支持）**：

| 能力 | 作用 |
| --- | --- |
| `POST /sys/tenants/platform-template/initialize` | 仅在 **`tenant_id IS NULL` 平台模板缺失** 时，按配置的平台租户 **一次性回填** 模板；已存在模板则 no-op（**不是**租户副本重建）。 |
| `GET /sys/tenants/{id}/platform-template/diff` | 对比平台模板与租户 carrier 副本，输出 **治理前证据**（含审计 `PLATFORM_TEMPLATE_DIFF`）；**不作为**运行时授权真相源。 |
| 新租户创建 | 同一事务内 **仅一次** `TenantBootstrapServiceImpl.bootstrapFromPlatformTemplate`；若目标租户已存在角色或 carrier 行，**fail-closed**（`IllegalStateException`，消息含「不允许重复从平台模板派生」/「目标租户已存在角色或资源副本」）。 |
| 人工 / 运维脚本 | 在明确变更单与备份前提下，对单租户做数据修复；**禁止**静默覆盖、**禁止**自动跟随平台模板。 |

**结论**：「不提供一键重建/回退」是**正式产品边界**，不是遗漏；若未来满足 A 的前置定义，可另立专项（新 API + 审计类型 + 双阶段 dry-run）。

迁移兼容层面仍保留：

- `PlatformTenantProperties.platformTenantCode`
- `PlatformTenantResolver`

它们当前主要用于迁移期的平台租户识别和模板回填，不再应被视为平台语义本身。

### 3.1 认证作用域语义（运行时已切新模型，2026-04）

当前认证域运行时主链已完成从旧表桥接态向新模型的收口：

- **新模型**（长期目标载体）：
  - `user_auth_credential`：只存认证材料（password hash / totp secret / verify metadata）。
  - `user_auth_scope_policy`：只存作用域策略（`GLOBAL/PLATFORM/TENANT` + enabled/primary/priority）。
  - `scope_key` 正式契约：`GLOBAL` / `PLATFORM` / `TENANT:{id}`。
- **运行时读写现状**：
  - production runtime 主链已只读新模型，不再通过 `user_authentication_method` 做 fallback。
  - 密码创建/更新、TOTP 预绑/激活/解绑、MFA remind skip、认证验证记录、TOTP 锁定状态均已写入新模型。
  - 平台态 bulk/read 已固定为 `PLATFORM > GLOBAL`；租户态保持 `TENANT > GLOBAL`。
- **旧表角色**：
  - `user_authentication_method` 仍保留，但当前只允许用于迁移、审计、历史对账；不再参与 production runtime 主链鉴权。

实现约束（防漂移）：

- 认证读写、回填脚本与后续迁移代码必须复用同一 `scope_key` 构造规则（`buildScopeKey(...)`），禁止再手写第二套格式。
- 平台态语义以 `scope_type=PLATFORM` + `scope_key=PLATFORM` 表达，不允许回落到“platform tenant id/code”兼容壳。

长期目标（见 `TINY_PLATFORM_AUTHORIZATION_MODEL.md` §5.2）：

- 平台语义由 `scope_type=PLATFORM` 表达，而不是特定租户 code；
- 平台租户仅作为控制面的一个逻辑视图，不再承载全部平台模板数据；
- 平台模板通过显式初始化/只读治理维护，而不是依赖 `default` 租户隐式承载。

---

## 4. 当前实现快照（2026-03-19）

### 4.1 生命周期运行策略

- `ACTIVE`：允许登录、发 token、建立服务端会话、执行业务读写。
- `FROZEN`：拒绝新登录、拒绝新 token、拒绝新服务端会话；已持有旧 token/session 的请求在入口被 `TenantContextFilter` fail-closed，登录提交路径仍保留到认证链路以返回“租户已冻结”的业务错误；写路径同时由 `TenantLifecycleGuard` 阻断。
- `DECOMMISSIONED`：租户作用域请求统一拒绝；平台作用域仅允许显式白名单内的治理只读动作。

### 4.1.1 平台只读例外白名单（安全审计基线）

当前代码状态：

- 已通过 `TenantLifecycleReadPolicy` + `TenantContextFilter` 将生命周期只读白名单收口为统一策略。
- 当前已落代码的白名单子集包括：租户基础信息查看、授权审计查看/导出/摘要、认证审计查看/导出/摘要。
- 账单、套餐/配额专用视图、历史导出/历史调度/历史会话、归档查看/下载采用相同裁决模型，但若当前仓库还不存在稳定端点，则继续保留为专题约束，不伪造运行时开放。

裁决原则：

- 默认拒绝，例外显式允许。
- 仅 `activeScopeType=PLATFORM` 可进入白名单；`tenant scope` 在 `FROZEN` / `DECOMMISSIONED` 下不保留治理例外。
- 仅允许无副作用的治理只读动作；创建任务、触发调度、生成新业务数据一律不在白名单内。
- 审计导出、归档下载、账单导出等高敏感只读动作必须使用独立权限码，不得与普通 `view` 复用。
- 所有白名单访问都必须写统一审计，至少记录：`tenantId`、`tenantLifecycleStatus`、`actor`、`resourcePermission`、`reason`、`result`。

| 类别                                   | FROZEN | DECOMMISSIONED | 说明                                                                        |
| -------------------------------------- | ------ | -------------- | --------------------------------------------------------------------------- |
| 平台查看租户基础信息                   | 允许   | 允许           | 只读查看租户详情、套餐、配额、到期时间、联系人、生命周期                    |
| 平台查看授权审计 / 认证审计            | 允许   | 允许           | 合规、调查、风控分析必须保留                                                |
| 平台导出审计日志                       | 允许   | 允许           | 需独立 `export` 权限，建议要求 reason / ticketId                            |
| 平台查看账单 / 套餐 / 配额使用         | 允许   | 允许           | 账单结算、续费、争议处理所需                                                |
| 平台查看历史导出 / 历史调度 / 历史会话 | 允许   | 可选允许       | 建议 `FROZEN` 允许；`DECOMMISSIONED` 仅在合规需要时允许查看历史，不允许新建 |
| 平台查看归档产物 / 下载归档包          | 不建议 | 允许           | 仅限已归档的只读产物                                                        |
| 新建导出任务                           | 禁止   | 禁止           | 属于新副作用，不属于治理只读                                                |
| 调度执行 / 重试 / 暂停 / 恢复          | 禁止   | 禁止           | 都是运行态控制动作                                                          |
| 普通业务数据查询 API                   | 禁止   | 禁止           | 不进入治理白名单                                                            |
| 任意写操作                             | 禁止   | 禁止           | 包括用户、角色、资源、菜单、业务单据                                        |

推荐的最小权限集合：

- `system:tenant:view`
- `system:audit:auth:view`
- `system:audit:auth:export`
- `system:audit:authentication:view`
- `system:audit:authentication:export`
- `system:tenant:billing:view`
- `system:tenant:quota:view`
- `system:tenant:archive:view`
- `system:tenant:archive:download`

安全补充：

- 对 `DECOMMISSIONED` 下的高敏感白名单访问，建议默认要求 MFA 已完成。
- 对审计导出、归档下载、账单导出，建议强制要求 `reason` 或 `ticketId`。
- 若当前代码还未补齐上述校验链路，必须在实现文档和规则中标记为“待补”，不能默认为开放。

### 4.2 错误模型

- 租户治理链路不再使用裸 `RuntimeException("...")` 表达业务错误。
- 当前统一使用 `BusinessException` / `NotFoundException` + `ErrorCode`：
  - 参数缺失：`MISSING_PARAMETER`
  - 参数非法：`INVALID_PARAMETER`
  - 编码/域名冲突：`RESOURCE_ALREADY_EXISTS`
  - 生命周期或配额状态不允许：`RESOURCE_STATE_INVALID`
  - 资源不存在：`NOT_FOUND`
  - 模板/治理不变量破坏：`BUSINESS_ERROR`

### 4.3 审计 detail 结构

- 租户治理事件 detail 已改为结构化 JSON 序列化，不再手拼字符串。
- 当前最小结构为：
  - `action`
  - `tenant`
  - `operator`
  - `reason`
  - `before`
  - `after`
  - `diff`
- 授权审计查询/导出/summary 目前已支持 `actorUserId`、`result`、`resourcePermission`、`detailReason` 等筛选维度。
- 对生命周期白名单访问，建议继续沿用相同 detail 结构，并额外记录 `tenantLifecycleStatus` 与 `ticketId`（如有）。

### 4.4 配额执行边界

- `maxUsers` 已执行于：
  - 创建租户时的初始管理员创建
  - `UserServiceImpl.create(...)`
  - `UserServiceImpl.createFromDto(...)`
- `maxStorageGb` 已执行于：
  - `AvatarServiceImpl.uploadAvatar(...)`
  - `ExportTaskService` 导出文件落盘成功
- 这属于第一阶段运行时校验；如果后续接入统一文件中心或更多附件写链路，需要继续扩面。

### 4.5 控制面入口

- 租户列表已支持 `includeDeleted` 管理开关。
- 租户控制面已支持只读详情抽屉，复用 `GET /sys/tenants/{id}` 展示生命周期、套餐、到期时间、配额、联系人和审计辅助字段。
- 若后续需要更强治理体验，可再升级为独立详情页或带时间轴的治理详情页。

---

## 5. 后续实施建议（专题剩余项）

1. 扩展租户生命周期状态体系：在现有 `ACTIVE/FROZEN/DECOMMISSIONED` 基础上，再评估 `PENDING_CREATE/TRIAL/OFFBOARD`、最大停留时长、自动流转和保留期计时。
2. 扩展白名单覆盖面：当前统一策略已经落到租户详情和审计治理子集；后续若新增稳定的账单、套餐/配额专用视图、历史会话/历史导出/历史调度或归档端点，应继续接入同一 `TenantLifecycleReadPolicy`，不得各自发明例外。
3. 对 `DECOMMISSIONED` 的高敏感白名单访问补齐真正的 MFA / `reason` / `ticketId` 强制校验，并让审计查询支持据此检索。
4. 为冻结/下线补齐 real-link E2E：覆盖冻结后登录拒绝、冻结后写入拒绝、下线后控制面非只读操作拒绝，以及平台白名单只读仍可用。
5. 打通计费与生命周期事件：冻结降费/停费、解冻恢复计费、下线前账单结清与导出路径需要和治理审计一起设计。
6. 针对扩容、迁移、地域搬迁、合并与拆分等高级运维操作补专题章节，定义停机窗口、一致性保障、权限迁移与命名冲突处理。
7. 继续与规则文档（90/91/93/94）对齐，把冻结期限、再激活流程、高风险治理操作审批链等待定策略逐步收成明确约束。
