# Tiny Platform 权限/授权可执行任务清单

> 按「本周/本迭代」可拆分的具体任务，含涉及文件与修改要点。  
> 来源：`TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md`。  
> 本文件是“当前完成度、优先级、剩余执行项”的唯一真相源。

---

## 0. 文档职责与读取顺序

- `TINY_PLATFORM_AUTHORIZATION_MODEL.md`
  负责模型、术语、约束、边界和目标态，不作为逐项完成状态的唯一维护位置。
- `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`
  负责当前完成度、优先级、执行顺序和真实剩余项；做完/未做/进行中的口径以本文件为准。
- `TINY_PLATFORM_MODULE_GAP_ANALYSIS.md`
  负责模块视角盘点，已降级为附录型参考文档；不再独立维护并行完成状态。
- `TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md`
  负责路线图、后续阶段与改进方向，不直接承担当前执行状态维护。
- `TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md`
  负责兼容/迁移台账，用于说明遗留来源、收口结果和后续下线策略。
- `TINY_PLATFORM_TENANT_GOVERNANCE.md`
  负责租户生命周期、治理动作、平台模板与治理审计的专题约束；不独立维护授权主线完成度。
- `TINY_PLATFORM_TENANT_NAMING_GUIDELINES.md`
  负责 `activeTenantId/recordTenantId/executionTenantId/createdTenantId` 的命名契约与切片迁移说明。
- `TINY_PLATFORM_RBAC3_ENFORCE_ROLLOUT_SOP.md`
  负责 RBAC3 enforce 灰度发布、观测和回滚 SOP，不作为 RBAC3 建模真相源。
- `TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md`
  负责 `@DataScope` 扩面准入条件、接入步骤和验证清单，不独立维护扩面完成状态。

建议读取顺序：

1. 先看 `TINY_PLATFORM_AUTHORIZATION_MODEL.md` 理解模型与边界
2. 再看 `TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md` 判断当前真实状态与下一步
3. 需要模块视角时再看 `TINY_PLATFORM_MODULE_GAP_ANALYSIS.md`
4. 涉及租户生命周期或平台模板治理时再看 `TINY_PLATFORM_TENANT_GOVERNANCE.md`
5. 验证执行口径（dev-bootstrap exit 码、Maven 同模块勿并发）见 `TINY_PLATFORM_TESTING_PLAYBOOK.md` §1.2–§1.3 与 `TINY_PLATFORM_BUILD_TECH_DEBT_LEDGER.md` §0
6. 涉及 `tenantId` 语义拆分时再看 `TINY_PLATFORM_TENANT_NAMING_GUIDELINES.md`
7. 涉及 RBAC3 enforce 灰度操作时再看 `TINY_PLATFORM_RBAC3_ENFORCE_ROLLOUT_SOP.md`
8. 涉及 `@DataScope` 是否可扩面、如何扩面时再看 `TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md`
9. 需要路线图或遗留背景时再看 `NEXT_PHASE` / `LEGACY_COMPATIBILITY`

---

## 0.1 平台作用域认证拆分（CARD-01 ~ CARD-05）状态

> 来源：`docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md`  
> 口径：这里记录“当前已落地现状”，不把目标态写成已完成。

- **CARD-01（已完成）**：`UserAuthenticationMethodProfileService` 已移除 `PlatformTenantResolver` 依赖；平台态不再回退 legacy platform tenant 行。
- **CARD-02（已完成）**：新表 `user_auth_credential` / `user_auth_scope_policy` 已落库，且已补 `scope_type` + `scope_id` CHECK 约束。
- **CARD-03（已完成）**：认证关键写链已双写新模型；`unbindTotp` 删除链已补桥接删除并有回归测试锁定。
- **CARD-04（已完成）**：认证读链已切为新模型优先；平台态 `PLATFORM > GLOBAL` 已锁定；`scope_key` 读写契约已统一为 `GLOBAL` / `PLATFORM` / `TENANT:{id}`。
- **CARD-05（已完成）**：文档与门禁口径已同步到桥接态现状，不再把目标态冒充现状。

第二阶段收口状态（对应 `docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md` 的 `CARD-06 ~ CARD-09B3`）：

说明：这些剩余卡片是“PLATFORM 正确模型 7 件事”里的第二阶段收口动作，主轴是第 3 项（认证域去 `tenant_id` 语义），并同时落实第 6 项（迁移兼容壳降级/清零）与第 7 项（真实链路门禁同步）。

补充口径：这里的“认证域去 `tenant_id` 语义”指的是把语义迁到 `user_auth_credential + user_auth_scope_policy` 新模型，而不是继续在 `user_authentication_method` 旧表上追加 `scope_type / scope_id / scope_key` 演进。

1. **CARD-06（已完成）数据回填与对账**：backfill / dry-run / apply / reconcile 已跑通；当前证据为 `projected_credential_upserts=15`、`projected_scope_policy_upserts=19`、`conflict_groups=0`、`user_auth_credential=15`、`user_auth_scope_policy=19`、`legacy vs new-model diff = empty`。
2. **CARD-07A（已完成）命中观测固化**：旧表 fallback 的命中入口、scope/provider/type 维度和调用形态已完成观测固化。
3. **CARD-07B（已完成）fallback 收窄**：fallback 已收窄到显式异常场景，正常平台态/租户态认证链默认走新模型。
4. **CARD-07C（已完成）受控环境默认关闭 fallback**：受控环境下已具备 fail-fast 阻断与可审计异常信息。
5. **CARD-08A（已完成）平台真实认证链**：平台 real-link 已验证 `PLATFORM > GLOBAL` 与最终业务结果。
6. **CARD-08B（已完成）租户真实认证链**：租户 real-link 已验证平台/租户边界不串用与最终业务结果。
7. **CARD-09A（已完成）零 runtime 依赖旧表**：production runtime 主链已停止读取旧表；旧表仅保留迁移、审计、历史对账用途。
8. **CARD-09B1（已完成）下线 inventory 与 drop 前置确认**：已产出可执行 inventory 与 09B2/09B3 分工，见 `docs/TINY_PLATFORM_USER_AUTHENTICATION_METHOD_LEGACY_TABLE_INVENTORY.md`。
9. **CARD-09B2（已完成）代码/脚本/测试残留清理**：在不 drop 表的前提下，已清掉主路径对旧表 JPA/主链依赖；`tiny-web` 密码读取已切到新模型查询。
10. **CARD-09B3（已完成）旧表物理下线**：已补 Liquibase `134-drop-user-authentication-method.yaml`；`schema.sql` / `data.sql` 与 `tiny-web` 侧 schema/seed 已同步新模型；桥接迁移期正式结束。
11. **工具链一致性**：后续 SQL / 脚本 / 迁移代码必须复用统一 `buildScopeKey(...)` 规则，禁止再发明 `scope_key` 字符串形态；该约束适用于 `CARD-06 ~ CARD-09B3` 全阶段。

第三阶段收尾建议（对应 `docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md` 的 `CARD-09C1 ~ CARD-09C4`）：

1. **CARD-09C1（已完成，含 follow-up）角色码 authority 消费点 inventory**：已盘清 runtime / JWT / Session / downstream（含 `tiny-web`）对 `ROLE_*` authority 的 keep-list、migrate-list、test-only list，见 `docs/TINY_PLATFORM_ROLE_CODE_AUTHORITY_CONSUMER_INVENTORY.md`。
2. **CARD-09C2（已完成）合法消费者迁到显式 `roleCodes`**：`SecurityUser`/JWT 已补 `roleCodes` 显式契约，`CamundaIdentityBridgeFilter` 与 `tiny-web` 的角色码消费已优先读取显式 roleCodes（保留 authorities 兼容作为 09C3 前过渡）。
3. **CARD-09C3（已完成）authority 收缩**：新签发 JWT `authorities` 已收缩为非 `ROLE_*`（permission/factor 等）为主，`role.code` 不再作为主链 authority 输出；旧 token 读取兼容保留在 converter，合法角色码消费者继续走显式 `roleCodes`。
4. **CARD-09C4（已完成）文档与测试收尾**：核心授权文档已同步到 09C3 后契约（新签发 authorities 以 permission/factor/scope 为主、`roleCodes` 仅供少量合法消费者、旧 token 仅解码兼容）；并清理测试样例中的 `ROLE_ADMIN` 通用权限模拟口径。

第四阶段兼容壳收口建议（对应 `docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md` 的 `CARD-10A ~ CARD-10D`）：

说明：这组卡不再处理“主授权模型是否成立”，而是处理主线已经稳定后仍然保留的兼容壳。目标是把兼容边界写清楚、观测清楚、退出条件写清楚，禁止其继续扩散到新业务。

冻结边界：`tiny-web` 已视为冻结中的历史模块，不再作为权限/认证模型继续演进的承载体；这组卡默认只处理 `tiny-oauth-server` 主线兼容壳。`tiny-web` 若出现编译/启动/阻断性问题，仅接受最小生存修复，不纳入长期收敛主线。

1. **CARD-10A（已完成）JWT / Session 解码兼容窗口固化**：已固化 `SecurityUser` / JWT 旧快照解码兼容边界（仅解码兼容，不影响新签发契约），并补旧 payload / 旧 JWT 最小回归。
2. **CARD-10B（已完成）`roleCodes` 消费者最后一层 fallback 收窄**：`CamundaIdentityBridgeFilter` 继续以显式 `roleCodes` 为第一入口；`ROLE_* authorities` fallback 已收窄为“仅 legacy carrier 缺失 roleCodes 时兜底”，并补 MFA session / JWT / 普通 session 回归。
3. **CARD-10C（已完成）平台 `default/platformTenantCode` 兼容壳降级**：已将 `PlatformTenantResolver` 及相关调用点口径固化为“仅 bootstrap / 历史入口兼容”，并在主线代码注释中明确“新业务禁止依赖”。
4. **CARD-10D（已完成）carrier requirement fallback 收口**：保留兼容 fallback 的同时补充显式观测（compatibility fallback 日志）与审计 reason 断言，明确其仅为迁移期兼容，不得作为新业务默认接入方式。

第五阶段兼容代码移除建议（对应 `docs/TINY_PLATFORM_PLATFORM_SCOPE_CURSOR_TASK_CARDS.md` 的 `CARD-11A ~ CARD-11D`）：

说明：这一阶段不再以“固化兼容边界”为目标，而是以“在证据充分的前提下真正删除兼容代码”为目标。删除顺序按风险从低到高推进：先删 carrier fallback，再删 `ROLE_*` fallback，再退役 `default/platformTenantCode` 兼容壳，最后移除 JWT / Session 旧快照解码兼容。

冻结边界：`tiny-web` 继续视为冻结中的历史模块，不纳入这一阶段长期收敛主线；若因主线删除兼容产生阻断，只接受最小生存修复。

1. **CARD-11A（已完成）移除 carrier requirement fallback**：`CarrierPermissionRequirementEvaluator` 已删除 requirement 缺失时按 `fallbackPermission` 放行的主链兼容路径；requirement 缺失统一 fail-closed，并通过审计 reason `REQUIREMENT_ROWS_MISSING_FAIL_CLOSED` 显式诊断。
2. **CARD-11B（已完成）移除 `ROLE_*` 最后一层 fallback**：`CamundaIdentityBridgeFilter` 已删除 `ROLE_* authorities` 反推角色码路径，主线仅消费显式 `roleCodes`（principal/details/JWT claim）；legacy JWT 缺失 `roleCodes` 时不再回退角色 authority。
3. **CARD-11C（已完成）退役平台 `default/platformTenantCode` 兼容壳**：`TenantContextFilter` 已移除按 `PlatformTenantResolver` 推断平台 scope 的主链逻辑；`MenuServiceImpl` 写侧不再回退 `default/platformTenantCode`，改为要求显式 `activeTenantId`；运行主链平台语义仅认 `PLATFORM` scope。
4. **CARD-11D（已完成）移除 JWT / Session 旧快照解码兼容**：`SecurityUser @JsonCreator` 已移除“`roleCodes` 缺失时从 `authorities` 反推 `ROLE_*`”兼容；`TinyPlatformJwtGrantedAuthoritiesConverter` 已拒绝旧 `ROLE_*` authority claim 解码（仅保留 scope + 非 `ROLE_*` authorities + permissions），旧混合态快照改为显式不支持。

---

## 一、本周/本迭代可做（阶段 0 收口 + 小遗留）

### T1. 脚本与参考数据：规范码统一

| 任务 | 文件 | 修改要点 | 验证 |
|------|------|----------|------|
| T1.1 调度迁移脚本注释 | `tiny-oauth-server/scripts/verify-scheduling-migration-smoke.sh` | 脚本中 IN 列表为「039 迁移后应不存在的历史码」，保留不改；仅补充注释说明用途。**已做** | 运行该脚本或相关 smoke 不报错 |
| T1.2 参考 SQL 注释 | `tiny-oauth-server/src/main/resources/menu_resource_data.sql` | 在文件头增加注释：**「资源 name 列为历史 key，鉴权以 permission 列为准」**。**已做** | 无功能变更，仅文档化 |

### T2. 前端菜单权限常量（已完成）

| 任务 | 文件 | 修改要点 | 验证 |
|------|------|----------|------|
| T2.1 菜单页权限常量集中 | `tiny-oauth-server/src/main/webapp/src/views/menu/Menu.vue` | 将 `MENU_MANAGEMENT_*_AUTHORITIES` 抽到 `src/constants/permission.ts`（或 `auth.ts`），与后端 `MenuManagementAccessGuard` 语义一致；或至少加注释「与后端 LegacyAuthConstants + system:menu:* 一致，勿扩散硬编码」。**已加注释**；抽常量可选后续做 | 菜单管理页读/增/改/删按钮权限行为不变；若抽常量，全局搜索 `ROLE_ADMIN.*ADMIN.*system:menu` 可一并收敛 |

**T2.1 状态（已完成）**：已新建 `src/constants/permission.ts` 作为前端权限标识唯一真相源，包含全部模块权限码和聚合数组。12 个 Vue 文件（user/role/menu/resource/tenant/HomeView + 6 个 scheduling）已改为 import 该文件的常量，不再硬编码字符串。三段式别名在 `permission.ts` 中标记 `@deprecated`。

### T3. 控制面 RBAC 查漏（本迭代可做）

| 任务 | 文件 | 修改要点 | 验证 |
|------|------|----------|------|
| T3.1 确认无未保护控制面接口 | 全仓库 `*Controller.java` | 检查用户/角色/资源/菜单/租户/调度/字典/导出相关 Controller：所有写操作与敏感读是否均有 `@PreAuthorize` 或等效 Guard | 无新增未保护接口 |
| T3.2 禁止新增依赖 tenant_id 授权 | — | 在 93 或 90 规则中已存在「禁止新增依赖 user.tenant_id 做真实授权判断」；本项为自查，不做代码改动的可选项 | Code Review 检查清单 |

**T3.1 核查结果（已做 + 已修复）**：  
- UserController、RoleController、ResourceController、MenuController、TenantController、IdempotentMetricsController、IdempotentConsoleController、PlatformDictController：均已 `@PreAuthorize` + 对应 AccessGuard。  
- SchedulingController：已 `@PreAuthorize` + schedulingAccessGuard（canRead/canManageConfig/canOperateRun/canViewAudit/canViewClusterStatus 等）。  
- ExportController：无类级 `@PreAuthorize`，在方法内使用 `assertCanExport(currentAuthentication())` 做导出权限校验，符合设计。  
- **DictController（租户字典）**：**已修复**。新建 `DictManagementAccessGuard`（8 方法：type/item × read/create/edit/delete），13 个管理端点加 `@PreAuthorize`；6 个查找端点（by code/map/label/current）保持已认证即可访问（全应用渲染依赖）。  
- **ProcessController（工作流）**：**已修复**。新建 `WorkflowAccessGuard`（4 方法：view/config/instanceControl/tenantManage），全部 20 个端点加 `@PreAuthorize`，`/health` 保持公开。  
- **TenantController**：**已升级**。`TenantManagementAccessGuard` 新增 `canRead/canCreate/canUpdate/canDelete` 细粒度方法（`system:tenant:list|view|create|edit|delete`），Controller 从类级 `canManage` 改为方法级细粒度守卫。

---

## 二、第一阶段主交付（按迭代拆分）

### T4. tenant_user 与回填

| 任务 | 文件/范围 | 修改要点 | 验证 |
|------|-----------|----------|------|
| T4.1 设计 tenant_user 表 | `db/changelog/` 新 changelog | 字段：tenant_id, user_id, status, is_default, joined_at, left_at, created_at, updated_at；唯一 (tenant_id, user_id)；索引 (user_id, status)、(tenant_id, status) | 迁移可执行 |
| T4.2 回填脚本 | 新 changelog 或独立 SQL | 由现有 user 表 (tenant_id, id) 生成 tenant_user 行，status=ACTIVE，joined_at/created_at 合理 | 回填后 tenant_user 行数与「当前有 tenant_id 的用户」一致 |
| T4.3 双写：用户创建/更新 | `UserServiceImpl` 等 | 创建/更新用户时，同步写入或更新 tenant_user（保证至少一条 ACTIVE membership） | 单元/集成测试 |

**T4 状态**：迁移 **041-add-tenant-user-and-role-assignment.yaml** 已包含 tenant_user / role_assignment 建表及从 user、user_role 的回填。UserServiceImpl.create / createFromDto 已通过 `roleAssignmentSyncService.ensureTenantMembership()` 在保存用户后写入 tenant_user；updateFromDto 在更新角色时通过 replaceUserTenantRoleAssignments 间接 ensureTenantMembership。T4.1–T4.3 已落地。

### T5. role_assignment 为权限主来源

| 任务 | 文件/范围 | 修改要点 | 验证 |
|------|-----------|----------|------|
| T5.1 权限展开优先 assignment | `EffectiveRoleResolutionService`、`SecurityUser` 构建链路 | 有效角色统一从 `role_assignment` 查，并按 `role_hierarchy` 展开；不再回退 `user_role` | 运行态权限仅由 assignment 链路计算 |
| T5.2 登录加载走 assignment | `AuthUserResolutionService`、加载用户与角色的入口 | 加载「当前租户下有效角色」时仅使用 membership + assignment | 登录后 JWT / Session 中权限与 assignment 一致 |

**T5 状态**：EffectiveRoleResolutionService 与 AuthUserResolutionService 已完全切到 `tenant_user + role_assignment` 主链；`user_role` 回退与 `allowLegacyFallback` 均已下线。当前运行态角色会继续叠加 `role_hierarchy` 展开，JWT / Session 中的权限与 assignment 链路保持一致。

### T6. JWT / Session 契约收口

| 任务 | 文件/范围 | 修改要点 | 验证 |
|------|-----------|----------|------|
| T6.1 移除 role.name 进入 JWT/Session | `JwtTokenCustomizer`、Session 写入处、`SecurityUser` | 不再把 role.name 放入 authorities/claims；新签发 authorities 收缩为 permission/factor/scope；`roleCodes` 走显式字段 | JWT 中无 role.name；新签发 authorities 不含 `ROLE_*` 主链输出 |
| T6.2 permissions 与 activeScopeType 稳定 | `JwtTokenCustomizer`、`TenantContextFilter`、前端解析 | 确保 claims 中 permissions、activeScopeType、activeTenantId 稳定且文档化 | 文档与实现一致；前端仅用 permissions + activeTenantId |

**T6.1 状态**：✅ 已完成 09C3/09C4 收口。当前新签发 JWT/Session authorities 已收缩为 permission/factor/scope，不再输出 `ROLE_*` 混合态；`roleCodes` 通过显式 claim/字段提供给少量合法消费者；`role.name` 不进入 authority/JWT/Session。

**T6.2 状态**：已在 `tiny-oauth-server/docs/TOKEN_CLAIMS_ENTERPRISE_STANDARD.md` 中补充 §2.2 的 `activeScopeType` 及 §2.3「permissions / activeScopeType / activeTenantId 稳定契约」；实现与 `TenantContextContract`、`JwtTokenCustomizer.applyTenantClaims` 一致，前端可稳定使用 `permissions` + `activeTenantId`（可选 `activeScopeType`）。

### T7. 平台语义（可拆分）

| 任务 | 文件/范围 | 修改要点 | 验证 |
|------|-----------|----------|------|
| ~~T7.1 字典平台 tenant_id 统一~~ | ~~`DictTenantScope`、字典相关查询、迁移脚本~~ | ✅ 已完成：方案 B — `tenant_id IS NULL` 表示平台字典，与 role/resource 模式统一 | ~~行为不变或更一致~~ |
| T7.2 平台模板层级字段（后续） | role/resource 表、迁移 | 增加 role_level/resource_level 或等价字段区分平台模板与租户模板；第一阶段可只做设计/预留 | 见 Phase1 技术设计 |

**T3.2 状态**：已在 93 规则「禁止」中增加：新增逻辑不得依赖 `user.tenant_id` 做真实授权判断，应以 membership/role_assignment/当前活动租户为准。

### T3.F 冻结租户行为 + real-link E2E（本迭代已完成）

| 任务 | 文件/范围 | 修改要点 | 验证 |
|------|-----------|----------|------|
| T3.F1 冻结租户禁止登录 | `MultiAuthenticationProvider`、`TenantRepository` | 在用户名密码认证链路中，解析出 `activeTenantId` 后，若对应租户 `lifecycle_status=FROZEN`，直接抛出「租户已冻结」而非「用户不存在」，避免冻结场景被误判为账号问题 | 集成测试 `AuthenticationFlowE2eProfileIntegrationTest`：`tenantCode=bench-1m + e2e_admin` 在 ACTIVE 时登录成功，在 FROZEN 时返回「租户已冻结」 |
| T3.F2 冻结租户禁止写（统一守卫） | `TenantLifecycleGuard`、`SchedulingService`、`UserServiceImpl` 等写入口 | 在调度写、用户写等模块统一通过 `TenantLifecycleGuard.assertNotFrozenForWrite` 收口 FROZEN 租户的写保护，抛出 `RESOURCE_STATE_INVALID(409)` 等业务错误码 | real-link E2E：冻结后对 `/scheduling/task-type`、`/sys/users` 的写请求返回 403/409，且不会误写入 |
| T3.F3 real-link 冻结场景回归 | `tiny-oauth-server/src/main/webapp/e2e/real/tenant-lifecycle-freeze.spec.ts`、`playwright.real.config.ts`、`.env.e2e.local`、`scripts/e2e/ensure-scheduling-e2e-auth.sh`、`application-e2e.yaml` | 补充一条从「租户 ACTIVE → 正常登录 + 正常写 → 租户 FROZEN → 写操作被拒绝 + 登录被拒绝 → 恢复 ACTIVE」的 real-link 用例，使用非默认且非冻结租户（如 `bench-1m`）作为自动化租户与平台租户；修复 `ensure-scheduling-e2e-auth.sh` 在 CI/Playwright 环境下的 JShell 兼容性；通过 `tiny.platform.tenant.platform-tenant-code` 与 `E2E_PLATFORM_TENANT_CODE` 对齐平台租户语义；避免 `.env.e2e.local` 默认回退到 `default` | 本地与 CI 环境下 `npm run test:e2e:real -- e2e/real/tenant-lifecycle-freeze.spec.ts` 绿色，通过日志与 trace 确认：冻结前登录+写成功，冻结后登录被拒绝且写请求返回拒绝状态码，测试结束后租户状态恢复为 ACTIVE |

**T7.1 状态（已完成）**：采用方案 B（`tenant_id IS NULL` = 平台字典）：数据库迁移 `101-dict-platform-tenant-null.yaml` 将 dict_type/dict_item 的 `tenant_id` 从 NOT NULL DEFAULT 0 改为 NULLABLE，平台数据 `UPDATE SET tenant_id = NULL WHERE tenant_id = 0`，唯一约束替换为 `COALESCE(tenant_id, 0)` 函数索引。Java 层 `DictTenantScope.PLATFORM_TENANT_ID` 常量移除，`isPlatformTenant()` 改为检查 `null`；Repository 新增 `*TenantIdIsNull` 系列方法；JPQL 查询 `tenantId = 0` 替换为 `tenantId IS NULL`。前端 `dictType.vue`/`dictItem.vue` 的平台判定从 `=== 0` 改为 `== null`。init SQL (`init_default_dicts.sql`) 同步更新。71 个回归测试全绿。

**T7.2 状态**：设计/预留已在 `docs/TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md` 中完成：§2.2 平台作用域与模板层级、role/resource 表增加 `role_level`/`resource_level` 的迁移示例与约束（PLATFORM 时 tenant_id IS NULL、平台模板只允许平台控制面维护等）。实现留待第二阶段与 TenantBootstrap「平台模板 + 租户派生」一并落地。

---

## 三、后续阶段

### 阶段 2：RBAC3 约束（已完成）

- **模型与 dry-run**：已接入 RBAC3 Phase2 表（051–055）到 `db.changelog-master.yaml`；`RoleConstraintServiceImpl` 实现 dry-run 的 `role_hierarchy` 展开、`role_mutex`/`role_prerequisite`/`role_cardinality` 检测并写入 `role_constraint_violation_log`；`RoleAssignmentSyncService` 在写入前调用校验 hook；已补 MySQL 集成测试覆盖互斥/先决条件/基数三类违例入库。
- **enforce 模式**：`tiny.platform.auth.rbac3.enforce` 开关 + `tiny.platform.auth.rbac3.enforce-tenant-ids` 租户灰度允许名单。enforce 阻断 + allowlist 旁路均有集成回归用例，违例日志使用 `REQUIRES_NEW` 事务确保阻断时仍持久化。
- **控制面 API**：`/sys/role-constraints/*` 提供规则 CRUD（hierarchy/mutex/prerequisite/cardinality）+ `/violations` 分页查询。写侧防呆（环检测、self-mutex、非法 cardinality 等）已集成到 `RoleConstraintRuleAdminService`。
- **权限与观测**：新增 `system:role:constraint:view|edit`、`system:role:constraint:violation:view` 权限码并 seed 到 `data.sql`；`RoleManagementAccessGuard` 提供 `canViewRoleConstraints/canManageRoleConstraints/canViewRoleConstraintViolations` 守卫方法。
- **运维治理**：`role_constraint_violation_log` 已追加索引（056 changelog）；提供 30 天 retention 清理脚本和 SOP（`docs/TINY_PLATFORM_RBAC3_ENFORCE_ROLLOUT_SOP.md`）。

### 阶段 2.5：权限标识符与控制面加固（已完成）

- **DictController RBAC 加固**：新建 `DictManagementAccessGuard`，13 个管理端点加 `@PreAuthorize`；权限码 `dict:type:list|create|edit|delete`、`dict:item:list|create|edit|delete`。
- **ProcessController RBAC 加固**：新建 `WorkflowAccessGuard`，20 个端点加 `@PreAuthorize`；权限码 `workflow:console:view|config`、`workflow:instance:control`、`workflow:tenant:manage`。
- **TenantManagementAccessGuard 细粒度化**：从 `canManage`（仅 ADMIN）升级为 `canRead/canCreate/canUpdate/canDelete`（`system:tenant:list|view|create|edit|delete`）。
- **写操作权限码全量 seed**：`data.sql` 新增 45 条 type=2 资源记录，覆盖 user/role/menu/resource/tenant/dict/scheduling/workflow 全部写操作权限码，全部授予 `ROLE_ADMIN`。非 admin 用户可通过角色分配获得细粒度写权限。
- **前端权限常量抽取**：新建 `src/constants/permission.ts` 作为唯一真相源，12 个 Vue 文件改为 import 常量。
- **双码收敛**：三段式别名（`system:user:assign-role`、`system:role:assign-permission`）在后端 Guard 和前端 `permission.ts` 中标记 `@deprecated`，新 seed 只使用四段式规范码。
- **回归验证**：88 个测试（60 controller/guard + 28 RBAC constraint）全绿，后端编译 + 前端 TypeScript 类型检查零错误。

### 阶段 3：组织/部门（已完成）

- **数据库迁移**：`061-organization-unit.yaml`（组织/部门树表，单表自引用，`(tenant_id, code)` 唯一）、`062-user-unit.yaml`（用户归属表，主部门标记，`(tenant_id, user_id, unit_id)` 唯一）、`063-role-assignment-extend-org-dept-scope.yaml`（扩展 `role_assignment` CHECK 约束支持 `ORG/DEPT` scope_type）。
- **Domain 层**：`OrganizationUnit` + `UserUnit` 实体，`OrganizationUnitRepository` + `UserUnitRepository`。
- **Service 层**：`OrganizationUnitService`（树 CRUD + 环检测 + 删除前检查）、`UserUnitService`（归属 CRUD + 主部门唯一性）。
- **scope_type 扩展**：`RoleConstraintRuleAdminService` 接受 `ORG/DEPT`；`RoleConstraintServiceImpl` 基数检查支持 ORG/DEPT scope（通过新增 `countActiveUsersForRoleInScope` + `findActiveRoleIdsForUserInScope` 查询方法）。
- **Controller + 权限守卫**：`OrganizationController`（`/sys/org/*`，12 个端点：树/列表/CRUD + 用户归属管理），`OrganizationAccessGuard`（canRead/canCreate/canUpdate/canDelete/canAssignUser/canRemoveUser）。
- **权限 seed**：`data.sql` 新增 7 条 `system:org:*` 权限码（list/view/create/edit/delete/user:assign/user:remove），授予 `ROLE_ADMIN`。
- **前端常量**：`permission.ts` 新增 `ORG_*` 常量和 `ORG_MANAGEMENT_*_AUTHORITIES` 聚合数组。
- **回归验证**：63 个测试全绿，后端编译 + 前端 TypeScript 类型检查零错误。

### 阶段 4：数据范围（已完成）

- **数据库迁移**：`071-role-data-scope.yaml`（角色数据范围规则表，`(tenant_id, role_id, module, access_type)` 唯一，scope_type 支持 ALL/TENANT/ORG/ORG_AND_CHILD/DEPT/DEPT_AND_CHILD/SELF/CUSTOM，access_type 区分 READ/WRITE）、`072-role-data-scope-item.yaml`（CUSTOM 明细表，`(role_data_scope_id, target_type, target_id)` 唯一，target_type 支持 ORG/DEPT/USER）。
- **Domain 层**：`RoleDataScope` + `RoleDataScopeItem` 实体，`RoleDataScopeRepository` + `RoleDataScopeItemRepository`。
- **数据范围解析器（阶段 4 原始落地）**：`DataScopeResolverService` — 根据用户有效角色 + 模块 + accessType，合并多角色取最宽覆盖，解析为 `ResolvedDataScope`（不可变值对象）。支持 ALL/TENANT（无过滤）、SELF（仅本人）、CUSTOM（从 `role_data_scope_item` 加载明细），以及 ORG/ORG_AND_CHILD/DEPT/DEPT_AND_CHILD（基于 `organization_unit` 树与 `user_unit` 归属做几何展开）。无规则时默认 SELF（最小权限）。
- **扩面补充（2026-03-28 active scope）**：请求级 `TenantContext.activeScopeType` / `activeScopeId` 与 `EffectiveRoleResolutionService` 联动，用于**有效角色集合**与（对已挂 `@DataScope` 的读路径）**数据范围几何**。**阶段 4 原始语义（TENANT）**：未显式 ORG/DEPT 时，ORG/DEPT 形 `role_data_scope` 的几何锚点为 **user_unit 主部门**。**ORG active scope**：ORG / ORG_AND_CHILD 形规则从 **活动组织** `activeScopeId` 起算。**DEPT active scope**：DEPT / DEPT_AND_CHILD 形规则从 **活动部门** `activeScopeId` 起算。**Contract B（正式契约）**：在 **ORG active scope** 下，纯 **DEPT / DEPT_AND_CHILD** 形规则仍锚 **主部门**（与 TENANT 一致），不是「活动组织内默认部门」；理由与变更门槛见 `TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md`。**勿误读**：「主部门」不是「所有子级规则一律只从主部门展开」的笼统口径，而是上列 TENANT / ORG / DEPT / Contract B 的分支语义。**调度运行历史（正式不接入 `@DataScope`）**：`SchedulingService.getDagRuns` / `DagHistory` 为租户内运维/排障视图，不按 active scope 收缩，见同指南「运行历史契约」。
- **菜单/资源控制面读链（2026-03-28 证据闭环）**：`MenuServiceImpl` 分页与 `list` 已 `@DataScope`（`DataScopeContext` + `created_by` 谓词）；`ResourceServiceImpl` 的 `findTopLevelDtos` / `findChildDtos` / `findResourceTreeDtos` 已与分页 `resources` 对齐，在 JPA 层追加相同 `created_by` 数据范围谓词并挂 `@DataScope`；前端 `Menu.vue`、`resource.vue` 监听 `active-scope-changed` 重拉。**解析层证据**：`DataScopeResolverServiceTest.resolve_menu_module_uses_scoped_effective_roles_when_dept_active_scope` 锁定 `menu` 模块在 DEPT active scope 下走成对有效角色解析。
- **字典（dict，2026-03-28 租户控制面闭环）**：`DictTypeServiceImpl.query` / `DictItemServiceImpl.query` 已 `@DataScope`（`created_by` 用户名 ∩ 可见用户集；平台字典行只读展示不收缩）。前端 `dictType.vue` / `dictItem.vue` 通过 **`shouldReloadTenantControlPlaneOnActiveScopeChange()`**（`activeScopeEvents.ts`）统一判定：在**已选活动租户**时监听 `active-scope-changed` 重拉；**无活动租户**时不跟 scope（平台/未选租户边界，**正式契约**）。证据：`DictTypeServiceImplTest` / `DictItemServiceImplTest`（无限制走分页仓库路径 + 受限过滤）、`DataScopeResolverServiceTest.resolve_dict_module_uses_scoped_effective_roles_when_org_active_scope` / `resolve_dict_module_uses_scoped_effective_roles_when_dept_active_scope`、`activeScopeEvents.test.ts`、`dictType.test.ts` / `dictItem.test.ts`。
- **运行时框架**：`@DataScope` 注解（标注 Service 方法，声明 module + accessType）、`DataScopeAspect`（AOP 切面，进入方法前解析并写入 ThreadLocal）、`DataScopeContext`（ThreadLocal 持有者）、`DataScopeSpecification`（JPA Specification 构建器，将 ResolvedDataScope 翻译为查询谓词，支持 unitField/ownerField 映射）。
- **管理 CRUD**：`DataScopeAdminService`（upsert + 明细替换 + 删除）、`DataScopeController`（`/sys/data-scope/*`，7 个端点）、`DataScopeAccessGuard`（canView/canEdit）。
- **权限 seed**：`data.sql` 新增 2 条权限码 `system:datascope:view|edit`，授予 `ROLE_ADMIN`。
- **前端常量**：`permission.ts` 新增 `DATASCOPE_*` 常量和聚合数组。
- **回归验证**：63 个测试全绿，后端编译 + 前端 TypeScript 类型检查零错误。

---

## 四、任务与文档的对应关系

| 本文任务 | 对应「下一阶段与改进」文档 |
|----------|----------------------------|
| T1 | §2.1 权限标识符层面（脚本、参考 SQL） |
| T2 | §2.1 前端 Menu.vue、§3.3 前端权限 |
| T3 | §1.1 阶段 0 收口 |
| T4–T7 | §1.2 第一阶段主交付 |
| 阶段 2 | RBAC3 约束（dry-run/enforce/控制面/灰度/SOP） |
| 阶段 2.5 | 权限标识符与控制面加固（Guard/seed/前端常量/双码收敛） |

完成 T1、T2、T3 后，可在 `TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md` 中更新对应项的收口状态。

---

## 五、第一阶段收口小结

- **阶段 0 / 本迭代**：T1.1、T1.2、T2.1（注释 + 常量抽取）、T3.1（核查 + 修复 DictController/ProcessController/TenantController）、T3.2（93 规则）已收口。
- **第一阶段主交付**：T4（tenant_user/role_assignment + 041 + membership 主链）、T5（assignment 主来源，不再 legacy 回退）、T6.1/T6.2（JWT 契约）、T7.1（字典 0 约定）、T7.2（设计预留）均已落地或文档化。
- **阶段 2 已完成**：RBAC3 全四类约束（hierarchy/mutex/prerequisite/cardinality）的 dry-run + enforce + 灰度 + 控制面 API + 权限守卫 + 运维 SOP 均已落地。
- **阶段 2.5 已完成**：权限标识符全面加固 — 无保护控制面归零、写操作权限码全量 seed、前端常量唯一真相源、双码收敛（deprecated 三段式）。88 个回归测试全绿。
- **阶段 3 已完成**：组织/部门树（`organization_unit` + `user_unit`）建表、Domain/Repository/Service、Controller + AccessGuard、scope_type 扩展 ORG/DEPT、role_cardinality 部门 scope 基数检查、7 条权限码 seed。63 个回归测试全绿。
- **阶段 4 已完成**：数据范围过滤框架（`role_data_scope` + `role_data_scope_item`），`@DataScope` 注解 + AOP 切面 + JPA Specification / 服务层 owner 谓词，管理 CRUD + AccessGuard。摘要口径：**原始落地**（TENANT 下 ORG/DEPT 形规则锚主部门 + 无规则 SELF）；**2026-03-28 active scope 扩面**（ORG 活动组织 / DEPT 活动部门为几何锚 + 有效角色成对解析；**Contract B**：ORG scope 下 DEPT 形仍锚主部门）；**调度运行历史**正式不接入 DataScope；**menu/resource/dict（租户态）** 控制面列表 query-time + 前端 scope 重拉已闭环。详见上节「扩面补充」与 `TINY_PLATFORM_DATASCOPE_EXPANSION_GUIDE.md`。63+ 回归测试全绿（随扩面递增）。
- **阶段 5 已完成**：授权审计框架（`authorization_audit_log`），16 种事件类型全覆盖，接入角色赋权 / 数据范围 / 组织 / 用户归属四大变更路径，独立事务写入 + retention 清理。66 个回归测试全绿。
- **阶段 6 已完成**：carrier requirement 层已落库并完成运行时闭环；`menu/ui_action/api_endpoint` requirement 表、compatibility group 回填、`CarrierPermissionRequirementEvaluator`、`ui_action` 统一运行时门控、`api_endpoint` 统一守卫与 requirement-aware 审计均已就绪并通过回归验证。后续只保留持续回归、覆盖证据维护与文档同步，不再作为主线阻断。
- **遗留彻底下线已完成**：见§六。
- **后续清理已完成**：`allowLegacyFallback` 已确认零残留、`user_role` 基线 SQL 已替换为 `tenant_user` + `role_assignment`、`user.tenant_id` 双写已移除（setter 标记 @Deprecated）、灰度验证脚本 `verify-authorization-model-rollout.sh` 已就绪、前端管理页面（组织/数据范围/授权审计/RBAC3 约束）已创建、`SecurityServiceImplTotpThrottleTest` 修复。660 个回归测试全绿。
- **T4/T5 回归测试（已补）**：`UserServiceImplTest.create_should_call_ensureTenantMembership_after_save` 校验 create() 后必调 ensureTenantMembership；`EffectiveRoleResolutionServiceTest` 已覆盖 assignment 主链与 `role_hierarchy` 展开，不再保留 legacy fallback 测试。

### 阶段 5：授权审计框架（已完成）

- **数据库迁移**：`081-authorization-audit-log.yaml`（授权审计日志表，字段覆盖 tenant_id, event_type, actor_user_id, target_user_id, scope_type, scope_id, role_id, module, resource_permission, event_detail(JSON), result, result_reason, ip_address, created_at；索引覆盖 tenant+event+time、actor+time、target+time、time）。
- **Domain 层**：`AuthorizationAuditLog` 实体、`AuthorizationAuditEventType` 常量类（16 种事件类型：ROLE_ASSIGNMENT_GRANT/REVOKE/REPLACE, DATA_SCOPE_UPSERT/DELETE/ITEM_REPLACE, CONSTRAINT_RULE_UPSERT/DELETE, CONSTRAINT_VIOLATION, ORG_UNIT_CREATE/UPDATE/DELETE, USER_UNIT_ASSIGN/REMOVE/SET_PRIMARY, ROLE_RESOURCE_ASSIGN）、`AuthorizationAuditLogRepository`（分页查询 + retention 清理）。
- **审计服务**：`AuthorizationAuditService` — 同步写入（`REQUIRES_NEW` 独立事务，确保业务回滚时审计仍持久化）、异步写入（`@Async`）、便捷方法 logSuccess/logDenied、IP 地址自动提取（`X-Forwarded-For` 优先）、actor_user_id 自动从 SecurityContext 提取。
- **接入点**：`RoleAssignmentSyncService`（角色赋权替换审计）、`DataScopeAdminService`（数据范围 upsert/delete 审计）、`OrganizationUnitService`（组织节点 create/update/delete 审计）、`UserUnitService`（用户归属 assign/remove/set_primary 审计）。
- **权限失效**：`PermissionVersionService` 已基于 `role_assignment.updated_at` + `tenant_user.updated_at` + 权限集 SHA-256 指纹自动感知授权变更，无需额外 bump 机制；JWT/Session 刷新时自动检测版本漂移。
- **管理接口**：`AuthorizationAuditController`（`/sys/audit/authorization/*`，4 个端点：分页列表、按事件类型过滤、按目标用户查询、retention 清理）、`AuthorizationAuditAccessGuard`（canView/canPurge）。
- **权限 seed**：`data.sql` 新增 2 条权限码 `system:audit:auth:view|purge`，授予 `ROLE_ADMIN`。
- **前端常量**：`permission.ts` 新增 `AUDIT_AUTH_*` 常量和聚合数组。
- **回归验证**：66 个测试全绿（含 `RoleAssignmentSyncServiceTest` 构造器适配），后端编译 + 前端 TypeScript 类型检查零错误。

---

### 阶段 6：载体 requirement 层（已完成）

- **数据库迁移**：`123/124/125` 已完成显式绑定与 carrier split；`126-carrier-permission-requirement-tables.yaml` 已新增 `menu_permission_requirement`、`ui_action_permission_requirement`、`api_endpoint_permission_requirement` 三张组合需求表，并完成 compatibility group (`requirement_group=0`) 回填。
- **运行时求值**：`CarrierPermissionRequirementEvaluator` 已支持组内 `AND`、组间 `OR`、`negated=true` 排除条件；无 requirement 行时回退到 carrier 的单权限字段（兼容旧模型）。
- **当前消费范围**：
  - 菜单运行时树：已改为按 requirement 求值；
  - `api_endpoint` 统一守卫：`ApiEndpointRequirementFilter` 已接入安全链路，已实现模板 URI 严格匹配与命中后 fail-closed；覆盖证据按“服务层 / 同路径 filter-chain / 真实模块 controller”分级，当前状态为：`tenant / user / role / menu / authorization audit / authentication audit / scheduling` 均已升级为真实模块 controller 证明，`resource` 已补静态 + 模板 URI 的真实 filter-chain 证明，`dict` 当前有意豁免且不纳入统一守卫（详见 `docs/TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md`）；
  - `ui_action`：已闭合为统一运行时按钮/操作门控入口：后端 `findAllowedUiActionDtos(...)` 按 requirement 求值并记录 requirement-aware 审计；前端页面统一通过 `getRuntimeUiActions()` 返回的 allow 集合决定按钮可见性（允许额外更严格隐藏，但不允许放宽后端结论）。回归：`ResourceServiceImplTest.findAllowedUiActionDtos_should_support_and_or_and_negated_requirements_with_audit_details`。
- **门禁与治理**：rollout 脚本已要求 carrier 的 `required_permission_id` 与 requirement compatibility group 对齐；菜单删除最后一层载体时已按 carrier + requirement 全量引用检查是否撤销 `role_permission`。
- **阶段收口结论**：
  - `menu / ui_action / api_endpoint` 的 requirement runtime 已闭合：`menu` 运行时树、`ui_action` 统一运行时门控、`api_endpoint` 统一守卫均已进入同一 requirement 语义；
  - requirement-aware 审计已覆盖三类 carrier 的运行时决策，后端查询/导出与前端筛选参数保持一致；
  - `dict` 当前明确属于“有意豁免，不纳入统一守卫”的专题边界，原因与重新纳入触发条件见 `docs/TINY_PLATFORM_API_ENDPOINT_GUARD_COVERAGE.md`，不再作为阶段 6 阻断；
  - 后续仅保留持续回归、覆盖证据维护与文档同步，不再将 `api_endpoint` 守卫或 requirement-aware 审计记为未闭合主线事项。
- **兼容层清退（2026-03-26）**：
  - 已立即删除：`MenuServiceImpl.mergeTreeMenus(...)`、`MenuServiceImpl.resolveCurrentUsername()`、`MenuEntryRepository.findGranted*ByUsername*`、`ResourceRepository.findGrantedResourcesByUsername*`。
  - 已完成 runtime 迁移并收口：资源管理控制面的按钮门控改为运行时 `ui_action` requirement 求值，运行时 API 判断改为 `api_endpoint` evaluator，资源树/按类型列表/父级选择已改读 `menu / ui_action / api_endpoint`。
  - 2026-03-27 继续收缩：`MenuServiceImpl` 的父子校验、循环引用检查与递归删除子菜单已优先改读 `MenuEntryRepository`（carrier 读链）；删除链 direct-delete carrier，并继续用 `existsPermissionReference` 判定“最后载体撤权”。详见 `docs/TINY_PLATFORM_RESOURCE_RETIREMENT_PLAN.md`。
  - 2026-03-27（追加）：`MenuServiceImpl` 删除链已去掉对 `resourceRepository` 的任何前置读取与伴随删除；运行时删除链不再依赖 `resource` 表。
  - 2026-03-27（追加）：`RoleServiceImpl.updateRoleResources` 已从 `addRolePermissionRelationByResourceId` 收缩为按 `required_permission_id` 直接写 `role_permission`；并将 `resource` 读取从实体加载收缩为最小字段投影（`id/permission/required_permission_id`）用于兼容输入与租户范围校验。
  - 2026-03-27（追加）：`TenantBootstrapServiceImpl` 在复制平台模板角色授权时，已从按 `resourceId` 回放授权收缩为按目标资源的 `required_permission_id` 写入 `role_permission`；缺失绑定时 fail-closed 抛错。
  - 2026-03-27（追加）：`ResourceServiceImpl.delete` 的子节点枚举已从 `resource` 子表读取收缩为 carrier 读链（`menu_entry` + `ui_action_entry`）；运行时删除链不再依赖 `resource` 表。
  - 2026-03-27（追加）：`ResourceServiceImpl.findByRoleId/findByUserId` 已从 `resourceId -> ResourceRepository.findAllById` 收缩为 `role_permission(permission_id)` -> `menu/ui_action/api_endpoint(required_permission_id)` 组装返回，保持原对外资源 DTO/列表语义。
  - 2026-03-27（追加）：`RoleServiceImpl.updateRoleResources` 已从 `resource` 兼容投影读取收缩为按输入 `resourceIds` 直接读取 carrier（`menu/ui_action/api_endpoint`）并提取 `required_permission_id` 去重写入 `role_permission`；缺失/越界 id 与缺失绑定仍 fail-closed。
  - 2026-03-27（追加）：`RoleServiceImpl.updateRoleResources` 已补事务边界并调整为“先校验后删写”，避免 fail-closed 异常导致“旧授权已删、新授权未写”中间态。
  - 2026-03-27（追加）：`TenantBootstrapServiceImpl` 模板资源实体复制主读已从 `resource` 快照切到 carrier template snapshot（`menu/ui_action/api_endpoint` union -> `Resource` 风格快照），平台模板与 configured tenant 回填两条链路统一改走 carrier 主读。
  - 2026-03-27（追加）：`TenantBootstrapServiceImpl` 模板复制已不再写 `resource` 壳；复制直接落 carrier 表，授权回放仅依赖 carrier `required_permission_id`，缺失即 fail-closed，不再保留 resource fallback。
  - 2026-03-27（追加）：bootstrap 切片的 `toPermissionIdMap` 已补冲突防护：同一 resourceId 对应多个不同 required_permission_id 时直接 fail-closed，避免静默覆盖。
  - 2026-03-27（追加）：共享 ID 解耦冲刺包已落地 carrier 主键自增与显式 locator 字段（历史数据已回填）；但**运行时主线不再执行基于 locator 的 `resource` 兼容删除**，相关能力仅允许作为历史数据治理/运维脚本的输入维度（如需）。
  - 2026-03-27（追加）：compatibility group 回填已改成显式 carrier 输入（`carrierType/carrierSourceId/tenantId/requiredPermissionId`）；回填写入 requirement 表时不再读取 `resource.id` 作为外键锚点。
  - 2026-03-27（追加）：新增 `128-carrier-id-autoincrement-safe-migration.yaml`，在迁移期临时摘除 requirement 外键后启用 `menu/ui_action/api_endpoint.id` 自增并重建外键；新建 carrier 默认可与 compatibility resource 使用不同 id。
  - 2026-03-27（追加）：角色授权写入主契约已切到 `permissionIds`；`/sys/roles/{id}/resources` 仍兼容 `resourceIds` alias，但运行时主逻辑不再以 `resourceIds` 为唯一入口。
  - 2026-03-27（追加）：已补 bootstrap 主读迁移等价性回归：`BUTTON / API` 字段复制、树结构 parentId 回填、平台模板回填链路均通过；`resource` 在 bootstrap 中不再承担任何运行时读写职责。
  - 2026-03-27（追加）：资源管理控制面剩余主读已收口：`ResourceServiceImpl.resources/findDetailById/findByType(find menu)/findByPermission/findByName/findByUrl/findByUri/existsBy*` 已迁到 `menu/ui_action/api_endpoint` 读模型组装；`resource` 不再承担控制面主读来源，阶段 1 正式关闭。
  - 2026-03-27（追加）：`ResourceServiceImpl` / `MenuServiceImpl` 的正常 create/update/updateSort/delete 已下线 bridge `sync/delete` 双写，改为 service 内 direct-write / direct-delete carrier；legacy `ResourceCarrierProjectionSyncService` 已退出运行时主线，剩余 `replaceCompatibilityRequirement` 与 `existsPermissionReference` 已迁入 `CarrierCompatibilitySafetyService`。
  - 2026-03-27（追加）：共享 ID 前置条件已继续清零一层：`RoleRepository.findResourceIdsByRoleId/findGrantedRoleCarrierPairs*` 已改为直接从 carrier union 反查，不再借 `resource.required_permission_id` 做 role_permission -> resourceId 映射；`TenantBootstrapServiceImpl.assertPermissionBindingsReady` 也已改为 carrier template snapshot 校验。
  - 2026-04-10（口径同步）：`resource` 兼容表已由 Liquibase 131 物理删除，不再属于活动 schema；`resource.permission` 仅作为历史迁移名词与旧文档口径保留。当前仍需保留的是 `CarrierCompatibilitySafetyService` 这类显式安全语义承接点，而不是 `resource` 表本身。**shared-id 运行时依赖已清零，legacy projection bridge 已退出主线，compatibility resource 主动保存与运行时删除链路已全部退出主线**。

## 六、遗留彻底下线（已完成）

> 目标：运行态不再兼容旧权限模型（不再读 `user_role`、不再依赖 `user.tenant_id` 授权），只做向前演进。

### 已完成项

- **`code='ADMIN'` 数据迁移**：`091-cleanup-legacy-admin-code.yaml` — 将历史 `code='ADMIN'` 角色记录迁移到 `ROLE_ADMIN`，删除旧记录。Seed 数据 (`data.sql`) 只使用 `ROLE_ADMIN`。
- **三段式权限别名移除**：后端 Guard 仅保留四段式规范码。前端 `permission.ts` 清理 deprecated 常量。
- **`TenantManagementAccessGuard.canManage()` 移除**：仅保留细粒度方法（canRead/canCreate/canUpdate/canDelete）。
- **~~`ROLE_ADMIN` 超级管理员兜底~~**：✅ 11 个 AccessGuard 全部移除 ROLE_ADMIN 兜底，改为纯细粒度权限码。`data.sql` 将全部权限码授予 ROLE_ADMIN 角色。
- **~~平台语义双轨统一~~**：✅ 字典模块从 `PLATFORM_TENANT_ID=0` 迁移到 `tenant_id IS NULL`（`101-dict-platform-tenant-null.yaml`），与 role/resource 统一。
- **~~`UserRepository.findByIdAndTenantId` 退场~~**：✅ 接口方法已移除，`UserServiceImpl.findById` 统一走 `tenant_user` membership。
- **~~`TenantBootstrapServiceImpl.bootstrapFromDefaultTenant` 重命名~~**：✅ 重命名为 `bootstrapFromPlatformTemplate`。
- **~~`LegacyAuthConstants` 删除~~**：✅ 运行时零消费者，类已物理删除。`SCHEDULING_ADMIN_AUTHORITIES` 重命名为 `SCHEDULING_PRIVILEGED_PERMISSIONS`。
- **~~平台判定从 DB 查询收口到 `TenantContext.isPlatformScope()`~~**：✅ 新增 `PlatformTenantResolver`（缓存平台租户 ID）和 `TenantContext.activeScopeType`（`PLATFORM`/`TENANT`）。`TenantContextFilter` 在请求入口设置 scopeType，`TenantManagementAccessGuard`、`IdempotentMetricsAccessGuard`、`MenuServiceImpl` 不再每次请求查库，改为 `TenantContext.isPlatformScope()`。
- **~~`User.java` 死代码清除~~**：✅ 注释掉的 `role.getName()` getAuthorities 代码已删除。
- **遗留注释清理**：Java 代码中对 `LEGACY_REMOVAL_PLAN.md` 的引用已替换为具体迁移编号（如 043/044/045/047/101），不再指向计划文档。
- **回归验证**：658 个非集成测试全绿（0 failures / 0 errors）。

---

## 七、代码基线校准后的优先待办（2026-03）

> 本节以当前仓库代码为准，用于补齐“文档已规划、代码已部分落地、但运行态或业务闭环尚未完成”的事项。  
> 口径说明：优先级按 P0/P1/P2，工作量按 XS/S/M/L 估算。

| 编号 | 优先级 | 任务 | 文件/范围 | 主要改动点 | 验证 |
|------|--------|------|-----------|-----------|------|
| C1 | P0 | 运行时接入 `role_hierarchy` 展开（已完成，2026-03-19） | `infrastructure/auth/role/service/EffectiveRoleResolutionService.java`、`RoleHierarchyRepository.java`、相关测试 | 已在运行态按 `child -> parent` 展开用户有效角色，并补齐 `parent -> child` 的反向主体解析；`role -> role_permission -> permission -> resource` 权限展开链路与角色继承一致 | 仅分配子角色时，父角色资源权限会进入 JWT `permissions`；按父角色查询用户时也能看到子角色赋权主体；层级环不会导致死循环 |
| C2 | P0 | 核心业务查询接入 `@DataScope`（已完成，2026-03-19） | `UserServiceImpl.java`、`ResourceServiceImpl.java`、`OrganizationUnitService.java`、`UserUnitService.java`、`SchedulingService.java`、`ExportTaskService.java`、`MenuServiceImpl.java`、`DictTypeServiceImpl.java`、`DictItemServiceImpl.java`、`datascope/framework/*`、相关测试 | 用户列表已补齐 `SELF/ORG(DEPT)/CUSTOM` 运行态覆盖测试；资源列表已补 `resource.created_by` 并按创建者可见性接入 `@DataScope`；菜单管理列表已按 `resource.created_by` 接入 `menu` 模块数据范围；组织列表/树/成员查询已接入 `org` 模块数据范围；调度列表已接入 `scheduling` 模块 owner 维度过滤；导出任务管理列表已接入 `export` 模块 owner 维度过滤；字典管理查询已接入 `dict` 模块数据范围并补齐 overlay 可见性回退 | 用户、资源、菜单、组织、调度、导出任务、字典这 7 类查询都会随数据范围变化返回不同结果；管理员默认环境不会因缺少规则而把控制面意外收窄 |
| C3 | P0 | 补齐 `ORG/DEPT` scope 赋权入口（已完成，2026-03-19） | `UserController.java`、`RoleController.java`、`RoleAssignmentSyncService.java`、`webapp/src/api/{user,role}.ts`、`views/{user,role}/*.vue` | 已支持在用户/角色分配接口与前端交互中提交 `scopeType/scopeId`，可创建、查询、覆盖 ORG/DEPT scope 的角色分配；部门 scope 下仍保留 RBAC3 基数约束校验 | `User/Role` 控制器单测、Service 单测、RBAC 集成测试和前端 API 测试均已通过。 |
| C3.1 | P1 | ORG/DEPT active scope 主线（成对解析 + 读路径扩面 + query-time + 控制面证据，2026-03-28） | `TenantContextFilter.java`、`DataScopeResolverService.java`、`UserServiceImpl.java`、`MenuServiceImpl.java`、`ResourceServiceImpl.java`、`DictTypeServiceImpl.java`、`DictItemServiceImpl.java`、`SchedulingService` / `ExportTaskService`、`ExportTaskRepository`、`webapp` HeaderBar + `activeScopeEvents.ts` + `user.vue` + `Menu.vue` + `resource.vue` + `dictType.vue` + `dictItem.vue` + `ExportTask.vue` + `Dag.vue` + `DagHistory.vue` | **成对解析**：JWT/Session 不混拼 type+id；JWT 显式 type 时 session 仅残留 id（无 type）须与 JWT 可比 id 一致，否则冲突 fail-closed；冲突/孤儿 **fail-closed**；Bearer 路径 `invalid_active_scope` 同步清理 `SecurityContext`。**业务消费**：`DataScopeResolverService` 按 `TenantContext` 取有效角色 + ORG/DEPT 几何；**Contract B（正式）**：ORG active scope 下 ORG 形规则锚活动组织，DEPT 形规则锚主部门（见类 Javadoc + 扩面指南）。**第一批**：user/org + 用户页重拉；**第二批**：DAG 列表、导出任务列表 scope 切换后重拉；**第三批**：`findReadableTasks` 已下推 **`tenant_id` + owner OR** 的 JPA `Specification`（无 `activeTenantId` 时空列表）；`listDags` 仍为库侧 `Specification`。**第四批**：菜单/资源控制面 — `findTopLevelDtos`/`findChildDtos`/`findResourceTreeDtos` 与分页 `resources` 共享 `created_by` 谓词 + `@DataScope`；`Menu.vue`/`resource.vue` scope 切换重拉。**第五批（dict）**：`dictType.vue`/`dictItem.vue` 在已选活动租户时 scope 切换重拉；无活动租户时不跟 scope。**运行历史（正式不接入 `@DataScope`）**：`getDagRuns` 租户+DAG 校验、不读 `DataScopeContext`；`DagHistory` 页内说明 + 不监听 scope；见扩面指南「运行历史契约」。 | 回归：`ExportTaskServiceTest`、`ExportTaskServiceReadableQueryIntegrationTest`、`SchedulingServiceTenantScopeTest`（含 `listDags_contract_b_*`、`getDagRuns_contract_*`）、`DataScopeResolverServiceTest`（含 `resolve_contract_b_*`、`resolve_menu_module_*`、`resolve_dict_module_*`（ORG+DEPT））、`UserServiceImplTest`、`MenuServiceImplTest`、`ResourceServiceImplTest`、`DictTypeServiceImplTest`、`DictItemServiceImplTest`、前端 `Menu.test.ts`、`resource.test.ts`、`dictType.test.ts`、`dictItem.test.ts`、`ExportTask.test.ts`、`DagHistory.test.ts`、`Dag.test.ts` 等。 |
| C4 | P0 | 租户生命周期状态机 + 冻结/解冻专用 API/UI（已完成，2026-03-19） | `application/controller/tenant/TenantController.java`、`TenantServiceImpl.java`、`TenantLifecycleGuard.java`、`core/oauth/tenant/TenantContextFilter.java`、`core/oauth/tenant/TenantLifecycleReadPolicy.java`、`webapp/src/views/tenant/*`、`webapp/src/api/tenant.ts` | 已将 `ACTIVE/FROZEN/DECOMMISSIONED` 从普通字段升级为受控状态流转；新增 freeze/unfreeze/decommission 专用端点、细粒度权限码与前端按钮；`TenantLifecycleReadPolicy` 已统一收口 `FROZEN/DECOMMISSIONED` 下的平台治理只读白名单子集 | 只允许合法状态流转；`FROZEN` 阻断登录、写操作和租户作用域非登录入口；`DECOMMISSIONED` 阻断租户作用域入口，平台作用域仅允许显式白名单内的租户/审计治理只读 |
| C5 | P1 | 租户治理事件接入统一审计（已完成，2026-03-19） | `TenantServiceImpl.java`、`AuthorizationAuditEventType.java`、`AuthorizationAuditService.java` | 已为租户创建、更新、删除、冻结、解冻补充审计事件类型与埋点，事件写入统一授权审计表 | 授权审计页可按租户查询到租户治理事件；actor、tenant、result、resourcePermission 等字段已保留 |
| C6 | P1 | 认证审计查询 API + 管理页面（已完成，2026-03-19） | `core/oauth/service/impl/AuthenticationAuditServiceImpl.java`、`UserAuthenticationAuditRepository.java`、`AuthenticationAuditController.java`、`webapp/src/api/audit.ts`、`views/audit/AuthenticationAudit.vue` | 已补齐认证审计分页查询 API、权限守卫、前端页面与静态路由入口；支持按租户、用户、用户名、事件类型、结果、时间范围检索；并已补 summary 聚合接口、CSV 导出、页面概览卡片与共享事件类型常量；普通用户仍保留 `/sys/users/current/login-history` 仅查本人登录历史 | 后端 Controller/Service/RBAC/AccessGuard 测试、前端 API/页面测试、`build-only` 均已通过；页面权限码为 `system:audit:authentication:view` / `system:audit:authentication:export` |
| C7 | P1 | 推进 RBAC3 `enforce` 灰度（已完成，2026-03-19） | `RoleConstraintServiceImpl.java`、`RoleConstraintRuleController.java`、相关配置与文档 | 基于现有 `tiny.platform.auth.rbac3.enforce` 与 `enforce-tenant-ids`，已补充可读的阻断消息、前端分配操作错误提示和运维说明，不再停留在“只会写日志、不会解释”的状态 | 指定租户开启后违例赋权被阻断且消息可直接定位到互斥/前置/基数问题；未开启租户保持 dry-run |
| C8 | P1 | 收口“default 租户即平台模板”的遗留语义（已完成，2026-03-19） | `TenantBootstrapServiceImpl.java`、`PlatformTenantResolver.java`、`role/resource` 模板相关代码、`data.sql` | Bootstrap 已统一以 `tenant_id IS NULL` 的平台模板为来源；若历史环境缺模板，仅在首次 bootstrap 时按配置的平台租户回填一次模板，之后统一从模板派生 | 新租户 bootstrap 不再直接克隆 `default` 租户，模板选择规则稳定一致 |
| C9 | P1 | 为新增控制面页面补资源/菜单/授权绑定（已完成，2026-03-19） | `data.sql`、`menu_data_insert.sql`、`menu_resource_data.sql`、`webapp/src/constants/permission.ts` | 已为组织、数据范围、授权审计、RBAC3 约束 4 个页面补齐菜单型资源、参考脚本与 `ROLE_ADMIN` 默认绑定；相关权限常量已在前端统一维护 | 新环境初始化后，这 4 个页面可通过菜单下发访问，不需人工补库 |
| C10 | P1 | 修资源检索与同步文档漂移（已完成，2026-03-19） | `ResourceServiceImpl.java`、`webapp/src/views/resource/resource.vue`、`webapp/src/api/resource.ts`、`docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`、`docs/TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md` | 资源查询过滤已修复；并已同步校准授权模型、遗留清单、后续改进文档中的过期“未实现/兼容回退”描述 | 资源管理页搜索条件恢复可用；文档不再把 `tenant_user`、`role_assignment`、`DataScope` 框架等已落地能力写成未实现 |

补充推进记录：`TenantServiceImpl.create()` 已于 2026-03-19 完成“租户创建闭环”，新租户创建后会在同一事务中完成平台模板 bootstrap、初始管理员用户创建、`tenant_user` membership 建立、`ROLE_ADMIN` 赋权以及 `LOCAL/PASSWORD` 认证方法写入；前端租户创建表单已补齐管理员字段并完成定向测试。

补充推进记录：用户管理已于 2026-03-19 完成“用户与组织/部门归属闭环”，`UserForm` 已支持邮箱、手机号、组织/部门多选和主部门设置；`UserServiceImpl.createFromDto/updateFromDto` 已通过 `UserUnitService.replaceUserUnits(...)` 同步 `user_unit`，并补齐创建链路的邮箱/手机号持久化。

补充推进记录：租户治理专题已于 2026-03-19 完成一轮专项收口，当前已统一租户治理错误模型（`BusinessException` / `NotFoundException` + `ErrorCode`）、结构化治理审计 detail 及其筛选维度、第一阶段配额执行（初始管理员/用户创建、头像上传、导出文件落盘），并补齐 `includeDeleted` 管理入口和列表内只读详情抽屉。

### 建议执行顺序

1. 第一批：`C2`、`C4`
2. 第二批：`C7`、`C9`、`C10`
3. 结构治理项：`C8`

### 与现有文档的关系

- `C1/C2/C3` 对应授权模型中的运行态收口缺口，优先级高于新增概念设计。
- `C1` 已于 2026-03-19 完成，当前运行态的 effective role / effective principal 解析已与 `role_hierarchy` 一致。
- `C2` 已于 2026-03-19 完成，当前用户、资源、菜单、组织、调度、导出任务、字典都已接入运行态数据范围过滤；资源/菜单列表通过 `resource.created_by` 建立 owner 维度，调度列表通过 `createdBy(userId/username)` 建立 owner key 过滤，导出任务列表通过 `export_task.user_id/username` 建立 owner key 过滤，字典管理查询通过 `dict_type/dict_item.created_by` 建立 owner 维度，并修复了隐藏 overlay 遮挡平台基线的问题。
- `C3` 已于 2026-03-19 完成“赋权入口 + 管理面查询/覆盖闭环”；运行时权限展开仍需继续依赖 `C1/C2` 收口。
- `C4/C5/C6` 对应 `docs/TINY_PLATFORM_MODULE_GAP_ANALYSIS.md` 中的租户治理与审计短板。
- `C6` 已于 2026-03-19 完成“认证审计查询 API + 管理页面”，并追加补齐审计 summary 聚合接口、授权/认证审计页面概览卡片、CSV 导出、共享事件类型常量，以及当前登录用户的服务端会话管理（活跃会话查询、下线其他会话、指定会话强制下线）；后续如需进一步演进，可聚焦图表仪表盘和告警/风控联动，而不是重复补查询基座。
- `C7` 已于 2026-03-19 完成 enforce 灰度收口；当前剩余重点转向更广泛的业务接入与模板层级治理。
- `C8` 已于 2026-03-19 完成 bootstrap 模板收口；当前新租户统一从 `tenant_id IS NULL` 平台模板派生，历史环境只在缺模板时做一次回填。
- `C9` 已于 2026-03-19 完成初始化资源树补齐；组织、数据范围、授权审计、RBAC3 约束页面已具备菜单下发入口，不再需要手工补库。
- `C10` 已于 2026-03-19 完成资源检索与高频文档校准；后续重点转向平台模板层级和更多业务接入。
- `C10` 用于校准当前文档漂移，避免后续人或 AI 继续依据过时状态做判断。

### 当前真实剩余 5 项（2026-03-19）

1. 平台模板治理：**正式契约 B**（见 `TINY_PLATFORM_TENANT_GOVERNANCE.md` §3.2）— **不开放**租户副本「一键重建 / 回退」API；替代手段为 `initialize`（仅平台模板缺失时回填）、`diff`（治理前证据 + 审计）、新租户单次 bootstrap、人工/脚本修复。**不自动跟随**平台模板；重复派生仍 **fail-closed**（`TenantBootstrapServiceImpl` + `TenantBootstrapServiceImplTest`）。构建与技术债登记见 `TINY_PLATFORM_BUILD_TECH_DEBT_LEDGER.md`。
2. `ORG/DEPT` scope 扩到更多运行态：赋权入口已完成，但更多业务查询和权限解析仍主要围绕租户级链路。
3. `@DataScope` 继续扩面：`user` / `resource` / `menu` / `org` / `scheduling` / `export` 已接入，更多核心业务模块仍未消费运行态数据范围。
4. 模块差距文档继续校准：已完成第三轮收口，`docs/TINY_PLATFORM_MODULE_GAP_ANALYSIS.md` 已降级为模块盘点附录，并移除了并行优先级批次表、精确完成度百分比、精确端点数和精确事件类型数；后续仍需继续回收低频陈旧结论。
5. 长期能力仍未启动：策略中心（PBAC/OPA）、岗位/职级管理、租户合并/拆分/归档等属于下一阶段，不在本轮收口范围。

---

## 八、第一批任务拆解（C1 / C2 / C4）

> 用于直接拆 issue / 子任务。若资源有限，建议按 `C1 -> C4 -> C2` 顺序推进。

### C1. 运行时接入 `role_hierarchy` 展开

**目标**

- 让运行态有效角色解析不再只依赖直接分配角色；
- 层级继承结果应进入 `role -> role_permission -> permission -> resource` 权限展开链路；
- 避免“约束校验知道层级，鉴权运行时不知道层级”的语义分裂。

**代码落点**

- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/service/EffectiveRoleResolutionService.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/repository/RoleHierarchyRepository.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/model/SecurityUser.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/auth/role/service/EffectiveRoleResolutionServiceTest.java`

**子任务**

1. 在 `EffectiveRoleResolutionService` 中新增“直接角色 ID -> 递归/迭代展开父角色 ID”的私有逻辑或独立 helper。
2. 保证展开后的角色 ID 去重、稳定排序，并避免环导致死循环。
3. `findEffectiveRolesForUserInTenant` 改为按“展开后的角色集合”加载带 resources 的角色。
4. 补单元测试：
   - 直接角色为空时仍返回空；
   - 存在单层继承时返回父子角色；
   - 存在多层继承时返回完整闭包；
   - 存在环时不会死循环且结果稳定。
5. 评估 `findEffectiveUserIdsForRoleInTenant` 是否也需要“按层级反查主体”语义；若暂不做，文档注明边界。

**验收标准**

- 只给用户分配子角色时，父角色绑定的 `resource.permission` 会出现在 JWT `permissions` 中；
- 现有 AccessGuard 无需改动即可识别继承来的权限；
- 不引入新的权限真相源，仍以 `role_permission -> permission` 为准；`resource.permission` 仅作为兼容字段保留。

**建议验证**

- 先跑：`EffectiveRoleResolutionServiceTest`
- 再补：登录发 token / SecurityUser authorities 相关测试

### C4. 租户生命周期状态机 + 冻结/解冻专用 API/UI

**目标**

- 将 `lifecycleStatus` 从“普通可写字段”升级为“受控状态流转”；
- 补齐冻结/解冻的专用后端入口和前端操作；
- 明确 `ACTIVE/FROZEN/DECOMMISSIONED` 的系统行为边界。

**代码落点**

- `tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/tenant/TenantController.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/service/TenantServiceImpl.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/tenant/guard/TenantLifecycleGuard.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/infrastructure/tenant/service/TenantServiceImplTest.java`
- `tiny-oauth-server/src/test/java/com/tiny/platform/application/controller/tenant/TenantControllerTest.java`
- `tiny-oauth-server/src/main/webapp/src/views/tenant/Tenant.vue`
- `tiny-oauth-server/src/main/webapp/src/views/tenant/TenantForm.vue`
- `tiny-oauth-server/src/main/webapp/src/api/tenant.ts`

**子任务**

1. 在 `TenantServiceImpl` 中抽出状态流转校验方法，定义允许的转换：
   - `ACTIVE -> FROZEN`
   - `FROZEN -> ACTIVE`
   - `ACTIVE/FROZEN -> DECOMMISSIONED`（如业务允许）
2. 禁止通过通用 `update` 直接随意写任意状态值。
3. 在 `TenantController` 新增专用端点：
   - `POST /sys/tenants/{id}/freeze`
   - `POST /sys/tenants/{id}/unfreeze`
   - 如需要，可补 `POST /sys/tenants/{id}/decommission`
4. 前端 `Tenant.vue` 增加状态列、冻结/解冻按钮、二次确认交互。
5. `TenantForm.vue` 不再承担冻结/解冻职责，只保留基础资料编辑。
6. 明确 `DECOMMISSIONED` 的运行时策略：
   - 至少在登录与写操作层面阻断；
   - 若当前轮不做全链路阻断，必须在文档里注明“暂时边界”。
7. 将 `FROZEN/DECOMMISSIONED` 的平台治理只读例外收口为统一策略：
   - 优先通过 `TenantLifecycleReadPolicy` / `TenantContextFilter` 表达；
   - 当前最小子集至少覆盖租户详情与审计治理端点；
   - 若账单、归档、历史会话等稳定端点未来落地，必须接入同一策略。

**验收标准**

- 非法状态流转会被明确拒绝；
- 冻结后的租户登录失败，写请求失败；
- 租户作用域在 `FROZEN/DECOMMISSIONED` 下不会因为平台治理需求被意外放开；
- 平台作用域仅能访问显式白名单内的租户/审计只读端点，且会写统一审计；
- 前端列表能直观看到状态并执行冻结/解冻；
- 通用更新接口不再承担状态机职责。

**建议验证**

- 先跑：`TenantServiceImplTest`、`TenantControllerTest`、`TenantControllerRbacIntegrationTest`
- 如已有 real-link/E2E，回归冻结场景

### C2. 核心业务查询接入 `@DataScope`

**目标**

- 让已存在的数据范围框架从“管理面能力”变成“业务运行态能力”；
- 优先选 2 个高价值列表查询接入，证明模型闭环成立；
- 先做 READ 场景，不在第一轮扩散到全部写逻辑。

**代码落点**

- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/user/service/UserServiceImpl.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/resource/service/ResourceServiceImpl.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/datascope/framework/DataScopeAspect.java`
- `tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/datascope/framework/DataScopeSpecification.java`
- 相关 repository / specification 组装处
- 新增测试：建议放在 `infrastructure/auth/datascope/` 或对应 service test 中

**子任务**

1. 选定首批接入对象：
   - 用户列表查询
   - 资源列表查询
2. 为对应 Service 查询方法标注 `@DataScope(module = "...", accessType = "READ")`。
3. 将 `DataScopeContext` 解析结果真正下推到 JPA 查询条件中：
   - unit 维度字段映射
   - owner/user 维度字段映射
4. 对没有组织归属或没有规则时的默认行为做显式约定，保持最小权限原则。
5. 补测试覆盖：
   - `SELF`
   - `ORG`
   - `DEPT`
   - `CUSTOM`
   - 无规则默认 `SELF`
6. 若某查询当前缺少必要字段映射，先在文档记录“不适用原因”，不要硬塞错误过滤。

**验收标准**

- 同一租户下不同数据范围角色看到的列表结果不同；
- 无规则用户不会获得全量可见；
- `@DataScope` 不是只触发切面，而是真正影响最终 SQL / 查询谓词结果。

**建议验证**

- 新增针对 `UserServiceImpl` / `ResourceServiceImpl` 的数据范围测试；
- 必要时补集成测试，验证 ThreadLocal 清理与多请求隔离。

**当前进展（2026-03-19）**

- `UserServiceImpl.users(...)` 已在运行态消费 `ResolvedDataScope`，并补齐 `SELF`、单位范围（覆盖 ORG/DEPT 类场景）、`CUSTOM` 的服务层测试。
- `ResourceServiceImpl.resources(...)` 已从失真的自定义 JPQL 切到真实 `Specification` 条件检索，并通过新增 `resource.created_by` 建立 owner 维度，按创建者可见性接入 `@DataScope(module = "resource")`。
- `MenuServiceImpl.menus(...) / list(...)` 已接入 `@DataScope(module = "menu")`：菜单管理列表会按 `resource.created_by` 收敛到可见创建者集合；运行时菜单树仍只按 `resource.permission` 授权，不受 owner 过滤影响。
- `OrganizationUnitService` / `UserUnitService` 已接入 `@DataScope(module = "org")`：组织列表按可见 unit 过滤，组织树会保留可见节点的祖先路径，成员查询会按可见 unit / user 收敛。
- `SchedulingService.listTaskTypes/listTasks/listDags` 已接入 `@DataScope(module = "scheduling")`：运行态会把可见用户集合归一化为 `userId + username` 两类 owner key，再过滤 `createdBy`。
- `ExportTaskService.findReadableTasks()` 已接入 `@DataScope(module = "export")`：在库侧以 `Specification` 约束 `tenant_id` 与可见 owner（`user_id` / `username` OR），按 `createdAt` 降序；不再租户内全表加载后内存收敛（第三批，2026-03-28）。
- `DictTypeServiceImpl/DictItemServiceImpl` 已接入 `@DataScope(module = "dict")`：字典管理查询会保留平台字典可见性，并按 `created_by` 过滤租户自定义/overlay；隐藏 overlay 不再遮掉平台基线值。
- `data.sql` 与 `083-seed-admin-data-scope.yaml` 已为默认租户 `ROLE_ADMIN` 回填 `user/resource/menu/org/scheduling/export/dict` 的 `READ=ALL` 规则，避免无规则时默认退回 `SELF`。
- 已新增迁移 `102-resource-created-by.yaml`，会为存量资源回填 `created_by`，避免升级后运行态数据范围失效。
- 已新增迁移 `103-dict-created-by-backfill.yaml`，会为存量租户字典回填 `created_by/updated_by`，避免升级后字典管理数据范围失效。
