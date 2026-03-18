# Tiny Platform 权限/授权可执行任务清单

> 按「本周/本迭代」可拆分的具体任务，含涉及文件与修改要点。  
> 来源：`TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md`。

---

## 一、本周/本迭代可做（阶段 0 收口 + 小遗留）

### T1. 脚本与参考数据：规范码统一

| 任务 | 文件 | 修改要点 | 验证 |
|------|------|----------|------|
| T1.1 调度迁移脚本注释 | `tiny-oauth-server/scripts/verify-scheduling-migration-smoke.sh` | 脚本中 IN 列表为「039 迁移后应不存在的历史码」，保留不改；仅补充注释说明用途。**已做** | 运行该脚本或相关 smoke 不报错 |
| T1.2 参考 SQL 注释 | `tiny-oauth-server/src/main/resources/menu_resource_data.sql` | 在文件头增加注释：**「资源 name 列为历史 key，鉴权以 permission 列为准」**。**已做** | 无功能变更，仅文档化 |

### T2. 前端菜单权限常量（可选本迭代）

| 任务 | 文件 | 修改要点 | 验证 |
|------|------|----------|------|
| T2.1 菜单页权限常量集中 | `tiny-oauth-server/src/main/webapp/src/views/menu/Menu.vue` | 将 `MENU_MANAGEMENT_*_AUTHORITIES` 抽到 `src/constants/permission.ts`（或 `auth.ts`），与后端 `MenuManagementAccessGuard` 语义一致；或至少加注释「与后端 LegacyAuthConstants + system:menu:* 一致，勿扩散硬编码」。**已加注释**；抽常量可选后续做 | 菜单管理页读/增/改/删按钮权限行为不变；若抽常量，全局搜索 `ROLE_ADMIN.*ADMIN.*system:menu` 可一并收敛 |

### T3. 控制面 RBAC 查漏（本迭代可做）

| 任务 | 文件 | 修改要点 | 验证 |
|------|------|----------|------|
| T3.1 确认无未保护控制面接口 | 全仓库 `*Controller.java` | 检查用户/角色/资源/菜单/租户/调度/字典/导出相关 Controller：所有写操作与敏感读是否均有 `@PreAuthorize` 或等效 Guard | 无新增未保护接口 |
| T3.2 禁止新增依赖 tenant_id 授权 | — | 在 93 或 90 规则中已存在「禁止新增依赖 user.tenant_id 做真实授权判断」；本项为自查，不做代码改动的可选项 | Code Review 检查清单 |

**T3.1 核查结果（已做）**：  
- UserController、RoleController、ResourceController、MenuController、TenantController、IdempotentMetricsController、IdempotentConsoleController、PlatformDictController：均已 `@PreAuthorize` + 对应 AccessGuard。  
- SchedulingController：已 `@PreAuthorize` + schedulingAccessGuard（canRead/canManageConfig/canOperateRun/canViewAudit/canViewClusterStatus 等）。  
- ExportController：无类级 `@PreAuthorize`，在方法内使用 `assertCanExport(currentAuthentication())` 做导出权限校验，符合设计。  
- DictController（租户字典）：无 `@PreAuthorize`，依赖 Service 层租户隔离与登录态；若需方法级 RBAC 可后续补。

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
| T5.1 权限展开优先 assignment | `EffectiveRoleResolutionService`、`SecurityUser` 构建链路 | 有效角色优先从 role_assignment 查；仅当无 assignment 时回退 user_role | 有 assignment 时不再读 user_role |
| T5.2 登录加载走 assignment | `UserDetailsServiceImpl`、加载用户与角色的入口 | 加载「当前租户下有效角色」时优先 assignment，兼容 user_role 回退 | 登录后 JWT/ Session 中权限与 assignment 一致（在已回填环境） |

**T5 状态**：EffectiveRoleResolutionService 已优先读 role_assignment，仅 allowLegacyFallback 时读 user_role。UserDetailsServiceImpl 通过 AuthUserResolutionService.resolveUserInActiveTenant 加载用户与角色，角色来自 EffectiveRoleResolutionService.findEffectiveRolesForUserInTenant；已改为 **allowLegacyFallback=true**，以便 041 未执行或历史环境中仍可登录（优先 assignment，无则回退 user_role）。

### T6. JWT / Session 契约收口

| 任务 | 文件/范围 | 修改要点 | 验证 |
|------|-----------|----------|------|
| T6.1 移除 role.name 进入 JWT/Session | `JwtTokenCustomizer`、Session 写入处、`SecurityUser` | 不再把 role.name 放入 authorities/claims；保留 role.code（兼容）与 permissions | JWT 中无 role.name；前端不依赖 role.name 鉴权 |
| T6.2 permissions 与 activeScopeType 稳定 | `JwtTokenCustomizer`、`TenantContextFilter`、前端解析 | 确保 claims 中 permissions、activeScopeType、activeTenantId 稳定且文档化 | 文档与实现一致；前端仅用 permissions + activeTenantId |

**T6.1 状态**：`SecurityUser.buildAuthorities` 仅使用 `role.getCode()` 与 `resource.getPermission()`，未使用 `role.name`；JWT 的 authorities 来自该列表，故当前已满足「JWT 不含 role.name」。已在 `SecurityUser` 上补充 Javadoc 说明。若后续有其它入口构造 authorities，需保持同样约定。

**T6.2 状态**：已在 `tiny-oauth-server/docs/TOKEN_CLAIMS_ENTERPRISE_STANDARD.md` 中补充 §2.2 的 `activeScopeType` 及 §2.3「permissions / activeScopeType / activeTenantId 稳定契约」；实现与 `TenantContextContract`、`JwtTokenCustomizer.applyTenantClaims` 一致，前端可稳定使用 `permissions` + `activeTenantId`（可选 `activeScopeType`）。

### T7. 平台语义（可拆分）

| 任务 | 文件/范围 | 修改要点 | 验证 |
|------|-----------|----------|------|
| T7.1 字典平台 tenant_id 可配置（可选） | `DictTenantScope`、字典相关查询 | 将 PLATFORM_TENANT_ID 改为可配置（与 tiny.platform.tenant 对齐），或显式文档约定「0 仅字典用」 | 行为不变或更一致 |
| T7.2 平台模板层级字段（后续） | role/resource 表、迁移 | 增加 role_level/resource_level 或等价字段区分平台模板与租户模板；第一阶段可只做设计/预留 | 见 Phase1 技术设计 |

**T3.2 状态**：已在 93 规则「禁止」中增加：新增逻辑不得依赖 `user.tenant_id` 做真实授权判断，应以 membership/role_assignment/当前活动租户为准。

### T3.F 冻结租户行为 + real-link E2E（本迭代已完成）

| 任务 | 文件/范围 | 修改要点 | 验证 |
|------|-----------|----------|------|
| T3.F1 冻结租户禁止登录 | `MultiAuthenticationProvider`、`TenantRepository` | 在用户名密码认证链路中，解析出 `activeTenantId` 后，若对应租户 `lifecycle_status=FROZEN`，直接抛出「租户已冻结」而非「用户不存在」，避免冻结场景被误判为账号问题 | 集成测试 `AuthenticationFlowE2eProfileIntegrationTest`：`tenantCode=bench-1m + e2e_admin` 在 ACTIVE 时登录成功，在 FROZEN 时返回「租户已冻结」 |
| T3.F2 冻结租户禁止写（统一守卫） | `TenantLifecycleGuard`、`SchedulingService`、`UserServiceImpl` 等写入口 | 在调度写、用户写等模块统一通过 `TenantLifecycleGuard.assertNotFrozenForWrite` 收口 FROZEN 租户的写保护，抛出 `RESOURCE_STATE_INVALID(409)` 等业务错误码 | real-link E2E：冻结后对 `/scheduling/task-type`、`/sys/users` 的写请求返回 403/409，且不会误写入 |
| T3.F3 real-link 冻结场景回归 | `tiny-oauth-server/src/main/webapp/e2e/real/tenant-lifecycle-freeze.spec.ts`、`playwright.real.config.ts`、`.env.e2e.local`、`scripts/e2e/ensure-scheduling-e2e-auth.sh`、`application-e2e.yaml` | 补充一条从「租户 ACTIVE → 正常登录 + 正常写 → 租户 FROZEN → 写操作被拒绝 + 登录被拒绝 → 恢复 ACTIVE」的 real-link 用例，使用非默认且非冻结租户（如 `bench-1m`）作为自动化租户与平台租户；修复 `ensure-scheduling-e2e-auth.sh` 在 CI/Playwright 环境下的 JShell 兼容性；通过 `tiny.platform.tenant.platform-tenant-code` 与 `E2E_PLATFORM_TENANT_CODE` 对齐平台租户语义；避免 `.env.e2e.local` 默认回退到 `default` | 本地与 CI 环境下 `npm run test:e2e:real -- e2e/real/tenant-lifecycle-freeze.spec.ts` 绿色，通过日志与 trace 确认：冻结前登录+写成功，冻结后登录被拒绝且写请求返回拒绝状态码，测试结束后租户状态恢复为 ACTIVE |

**T7.1 状态**：采用显式文档约定「0 仅字典用」：已在遗留清单 §2、`DictTenantScope` Javadoc 中写明；可配置化留待与平台租户统一时做。

**T7.2 状态**：设计/预留已在 `docs/TINY_PLATFORM_AUTHORIZATION_PHASE1_TECHNICAL_DESIGN.md` 中完成：§2.2 平台作用域与模板层级、role/resource 表增加 `role_level`/`resource_level` 的迁移示例与约束（PLATFORM 时 tenant_id IS NULL、平台模板只允许平台控制面维护等）。实现留待第二阶段与 TenantBootstrap「平台模板 + 租户派生」一并落地。

---

## 三、后续阶段（仅列项，不拆具体任务）

- **阶段 2**：role_hierarchy、role_mutex、role_prerequisite、role_cardinality；分配时校验。  
  - 当前状态：已接入 RBAC3 Phase2 表（051–055）到 `db.changelog-master.yaml`；`RoleConstraintServiceImpl` 已实现 **dry-run** 的 `role_hierarchy` 展开、`role_mutex`/`role_prerequisite`/`role_cardinality` 检测并写入 `role_constraint_violation_log`（不阻断赋权）；`RoleAssignmentSyncService` 已在写入前调用校验 hook；已补 MySQL 集成测试覆盖互斥/先决条件/基数三类违例入库。
  - 观测与控制面：已提供 `GET /sys/role-constraints/violations`（分页 + 条件过滤）用于 dry-run 观测；并补充“触发违例 -> 控制面可查询”的集成回归用例。
  - 运维治理（建议）：为 `role_constraint_violation_log` 追加索引（tenant + type + created_at）以支撑查询性能；制定 retention 策略（默认保留 30 天），可使用 `tiny-oauth-server/scripts/cleanup-role-constraint-violation-log.sql` 作为定时清理脚本。
- **阶段 3**：organization_unit、user_unit；scope_type 扩展 ORG/DEPT。
- **阶段 4**：role_data_scope、role_data_scope_item；按模块数据范围。

---

## 四、任务与文档的对应关系

| 本文任务 | 对应「下一阶段与改进」文档 |
|----------|----------------------------|
| T1 | §2.1 权限标识符层面（脚本、参考 SQL） |
| T2 | §2.1 前端 Menu.vue、§3.3 前端权限 |
| T3 | §1.1 阶段 0 收口 |
| T4–T7 | §1.2 第一阶段主交付 |

完成 T1、T2、T3 后，可在 `TINY_PLATFORM_LEGACY_COMPATIBILITY_INVENTORY.md` 中更新对应项的收口状态。

---

## 五、第一阶段收口小结

- **阶段 0 / 本迭代**：T1.1、T1.2、T2.1（注释）、T3.1（核查）、T3.2（93 规则）已收口。
- **第一阶段主交付**：T4（tenant_user/role_assignment + 041 + 双写）、T5（assignment 优先 + legacy 回退）、T6.1/T6.2（JWT 契约）、T7.1（字典 0 约定）、T7.2（设计预留）均已落地或文档化。
- **下一步可选**：推进阶段 2（role_hierarchy、role_mutex 等）设计或实现；或按业务迭代收口遗留清单中的角色码、Bootstrap 语义。
- **T4/T5 回归测试（已补）**：`UserServiceImplTest.create_should_call_ensureTenantMembership_after_save` 校验 create() 后必调 ensureTenantMembership；`EffectiveRoleResolutionServiceTest.findEffectiveRolesForUserInTenant_should_fallback_to_legacy_when_assignments_empty_and_allowLegacyFallback_true` 校验 assignment 为空且 allowLegacyFallback=true 时从 user_role 回退。

---

## 六、遗留彻底下线（不再兼容旧模型）

> 目标：运行态不再兼容旧权限模型（不再读 `user_role`、不再依赖 `user.tenant_id` 授权），只做向前演进。
>
> 执行基线见：`docs/TINY_PLATFORM_AUTHORIZATION_LEGACY_REMOVAL_PLAN.md`。
