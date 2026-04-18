# 平台角色治理 + RBAC3 + 审批设计文稿（vNext）

> 状态：设计草案（2026-04-17）  
> 目标：在不破坏现有 platform / tenant 隔离的前提下，为平台侧补齐“平台角色、角色绑定、RBAC3 约束、审批链、审计链”的完整治理能力。  
> 范围：平台角色治理，不重做租户 bootstrap 主链，不展开 impersonation 实现细节本身。  
> 关联：`docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`、`docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`、`docs/TINY_PLATFORM_PLATFORM_USER_MANAGEMENT_AND_IMPERSONATION_DESIGN.md`、`docs/TINY_PLATFORM_PLATFORM_ROLE_GOVERNANCE_CURSOR_TASK_CARDS.md`

---

## 1. 现状基线（当前仓库真实行为）

### 1.1 已存在的平台角色运行时基座

- 平台登录并非“完全没有角色”，当前已要求用户同时满足：
  - `platform_user_profile.status=ACTIVE`
  - 至少存在一条 `role_assignment(principal_type=USER, scope_type=PLATFORM, tenant_id IS NULL, scope_id IS NULL)`
- 对应实现：
  - `core/oauth/security/AuthUserResolutionService.resolveUserRecordInPlatform(...)`
  - `infrastructure/auth/user/repository/PlatformUserProfileRepository`
  - `infrastructure/auth/role/service/EffectiveRoleResolutionService.findEffectiveRoleIdsForUserInPlatform(...)`

### 1.2 当前缺口不是“有没有角色”，而是“有没有治理闭环”

- 平台用户控制面当前只展示 `hasPlatformRoleAssignment` 布尔值，不提供角色绑定明细与编辑能力。
- 现有 `/platform/template-roles` 页已经落地，但页面文案把 `tenant_id IS NULL + role_level=PLATFORM` 这批角色主要描述成“模板角色”，并声明“不能直接分配用户”。
- 现有 `RoleConstraintRuleController` 仍强依赖 `activeTenantId`，因此 `/sys/role-constraints/*` 目前是 tenant-only 能力，不能覆盖平台侧角色互斥、先决条件、基数和继承治理。
- `findEffectiveRoleIdsForUserInPlatform(...)` 当前未做 `role_hierarchy` 展开，平台 effective role 与租户侧已落地语义不一致。

### 1.3 当前真正冲突的不是“字段不够”，而是“语义不统一”

- 同一批 `tenant_id IS NULL + role_level=PLATFORM` 角色，一方面被用于租户 bootstrap 来源，另一方面平台登录又已经依赖其运行时赋权结果。
- 这说明当前更大的问题不是“缺少 `role_usage` 字段”，而是系统口径还没有明确承认：
  - 这就是一套统一的“平台角色”；
  - “模板”只是平台角色在租户初始化场景中的一种用途；
  - “运行时”只是平台角色在平台用户赋权场景中的另一种用途。

结论：

- 当前仓库已经有平台角色运行时赋权基座；
- 但还没有“平台角色治理”能力；
- 这不是简单的“菜单没挂出来”，而是缺少一整条治理闭环。

---

## 2. 问题定义

如果产品侧已经存在以下业务事实：

1. 平台账号会被授予真实平台角色；
2. 平台角色之间存在互斥、继承、先决条件、基数或审批要求；
3. 平台角色变更会影响平台控制面权限与审计责任；

那么当前缺失就是不合理的，必须补齐。

本设计采用以下统一语义：

- `tenant_id IS NULL + role_level=PLATFORM` 统一表示“平台角色”；
- 平台角色既可用于平台用户真实赋权，也可作为租户 bootstrap 的来源；
- “模板”是用途，不是独立角色类型；
- 因此不新增 `role_usage=TEMPLATE/RUNTIME` 字段。

---

## 3. 设计目标与非目标

### 3.1 目标

1. 明确平台侧只有“一套平台角色”，不再人为拆成两套数据语义。
2. 为平台用户补齐可审计、可审批的角色绑定能力。
3. 为平台角色补齐 RBAC3 的继承、互斥、先决条件、基数与违例日志。
4. 保持 platform / tenant 的接口、权限码、菜单和审计边界分离。
5. 让平台 effective role、JWT/Session 权限展开、`permissionsVersion` 与 RBAC3 运行时保持一致。

### 3.2 非目标

1. 不在本版本重做整套身份根模型，`user` 仍是账号根。
2. 不让 `/sys/role-constraints/*` 继续承载平台语义。
3. 不把平台角色 RBAC3 与 impersonation 强行耦合在一个版本里一次性完成。
4. 不把“审批流引擎深度集成”设为 P0 阻断项；第一版可先落表驱动审批闭环。

---

## 4. 目标态原则（硬约束）

1. 平台侧只有一套平台角色，不能再把“模板”误写成一种独立角色类型。
2. 租户 bootstrap 对平台角色的使用默认是“创建时快照复制”，不是“持续同步”。
3. 平台角色治理必须独立于租户侧 `/sys/roles` 与 `/sys/role-constraints/*`。
4. RBAC3 约束必须在“审批通过并真正写入 `role_assignment` 前”做 fail-closed 校验。
5. 平台角色审批不得隐式代替赋权；审批只是授权写入前的一道显式控制。
6. 平台角色约束与租户角色约束不得串用同一套权限码与菜单入口。

---

## 5. 推荐方案（核心决策）

## 5.1 统一平台角色语义，继续沿用现有 `role` 表

目标态语义：

| tenant_id | role_level | 含义 |
| --- | --- | --- |
| `NULL` | `PLATFORM` | 平台角色 |
| `NOT NULL` | `TENANT` | 租户角色 |

说明：

- 不新增 `role_usage` 字段。
- 不新增第二套“平台运行时角色表”。
- 不推荐长期保留“平台模板角色不能直接分配用户”的文案，因为这会和平台登录已依赖平台角色赋权的事实冲突。
- 平台角色可以同时承担两种用途：
  - 平台用户真实赋权；
  - 租户初始化时的角色模板来源。

关键约束：

- 平台角色用于 bootstrap 时，默认采用“创建租户时复制一份快照”的语义；
- 后续平台角色变更不会自动重写所有已创建租户，除非显式执行 diff / sync / repair 主链；
- 因此“同一条平台角色承担两种用途”本身不是问题，前提是 bootstrap 语义要定义清楚。

## 5.2 平台角色赋权继续使用现有 `role_assignment(scope=PLATFORM)` 主链

保持：

- `role_assignment.tenant_id IS NULL`
- `role_assignment.scope_type = PLATFORM`
- `role_assignment.scope_id IS NULL`

新增能力：

- 平台侧补正式写入口，而不只保留 `findActiveRoleIdsForUserInPlatform(...)` 读入口。
- 平台用户页从“只展示 hasPlatformRoleAssignment 布尔值”升级为“角色明细 + 绑定历史 + 审批状态”。

## 5.3 平台 RBAC3 复用现有四张约束表，但补 platform-null 语义

当前四张表：

- `role_hierarchy`
- `role_mutex`
- `role_prerequisite`
- `role_cardinality`

推荐目标态：

- 平台角色约束仍落在这四张表上；
- 平台侧约束记录使用 `tenant_id IS NULL`；
- repository / service / controller 明确区分：
  - tenant path：`tenant_id = ?`
  - platform path：`tenant_id IS NULL`
- 平台用户角色绑定主链在写入 `role_assignment(scope_type=PLATFORM, tenant_id IS NULL, scope_id IS NULL)` 前，必须走同一套 RBAC3 校验与 violation log 逻辑；不能出现“平台控制面可配，但平台赋权链不消费 mutex / prerequisite / cardinality”的假闭环。
- 平台约束记录引用的 roleId 必须 fail-closed 校验为 `tenant_id IS NULL + role_level=PLATFORM` 的平台角色，不能把 tenant roleId 混进 `tenant_id IS NULL` 的平台约束表。
- 由于 MySQL 对复合唯一键中的 `NULL` 不做同值约束，若四张约束表使用 `tenant_id IS NULL` 承载平台记录，则必须同步补 `normalized_tenant_id` 或等价唯一键方案；不能只把 `tenant_id` 改成可空。

注意：

- 当前 `RoleConstraintRuleAdminService` 与 `RoleConstraintRuleController` 都把 `tenantId > 0` 当成硬前提，这一逻辑必须拆成 tenant / platform 双路径。
- 平台路径不能简单把 `NULL` 传进现有 `= :tenantId` 查询里，而是要补 `...IsNull...` 专用仓储方法或统一的 nullable-spec 路径。

## 5.4 平台审批与平台 RBAC3 不是二选一，而是串联关系

推荐顺序：

1. 提交平台角色赋权申请；
2. 审批流进入 `PENDING`；
3. 审批通过时做最终 RBAC3 hard validation；
4. 校验通过后才真正写入 `role_assignment`；
5. 写入成功后记录审计与申请完成态；
6. 若 RBAC3 校验失败，审批动作返回明确冲突原因，不得静默跳过。

这样可以避免两种坏结果：

- 审批通过但写入失败，页面却显示“已完成”；
- 为了让审批成功而绕过 RBAC3 hard validation。

## 5.5 页面与命名建议：平台角色优先，模板入口可过渡兼容

推荐目标态：

- 主入口命名统一为“平台角色”；
- 新治理页建议使用 `/platform/roles`；
- 现有 `/platform/template-roles` 如需保留，可作为过渡 alias，但页面文案必须改成“平台角色（可作为租户初始化来源）”。

不推荐继续维持以下口径：

- 页面标题叫“模板角色”，但平台登录又依赖这批角色的真实赋权；
- 服务层禁止给平台用户绑定这些角色，同时系统又要求平台用户必须具备平台角色才能登录。

---

## 6. 数据模型建议

## 6.1 `role` 表不新增 `role_usage`

本方案下，`role` 表只建议在有真实治理需要时增补以下治理属性：

```sql
ALTER TABLE `role`
  ADD COLUMN `risk_level` VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'LOW/NORMAL/HIGH/CRITICAL',
  ADD COLUMN `approval_mode` VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/ONE_STEP';
```

用途：

- `risk_level`：支撑审批和审计分级。
- `approval_mode`：声明该角色绑定是否必须走审批。

## 6.2 平台角色赋权申请表（新增）

```sql
CREATE TABLE `platform_role_assignment_request` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `target_user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  `action_type` VARCHAR(16) NOT NULL COMMENT 'GRANT/REVOKE',
  `status` VARCHAR(16) NOT NULL COMMENT 'PENDING/APPROVED/REJECTED/APPLIED/CANCELED/FAILED',
  `requested_by` BIGINT NOT NULL,
  `requested_at` DATETIME NOT NULL,
  `reviewed_by` BIGINT NULL,
  `reviewed_at` DATETIME NULL,
  `reason` VARCHAR(256) NULL,
  `review_comment` VARCHAR(256) NULL,
  `applied_at` DATETIME NULL
);
```

第一版建议“一请求一角色一动作”，不要一开始就做复杂批量 item 子表。

## 6.3 RBAC3 违例日志沿用现有表

要求：

- `role_constraint_violation_log` 必须允许记录平台侧违例；
- 平台侧写入时 `tenant_id IS NULL`，`scope_type = PLATFORM`，`scope_id = NULL`；
- 查询接口和前端页面要能按平台路径筛选，不得复用 tenant-only 入口。

## 6.4 历史迁移策略

本方案不做 role clone，不做 assignment remap。

建议按以下顺序迁移：

1. 保持现有 `tenant_id IS NULL + role_level=PLATFORM` 数据不拆分；
2. 把文档、接口注释、页面标题统一收敛到“平台角色”；
3. 移除“平台模板角色不支持直接分配用户”的运行时限制，或新增平台专用赋权写链绕开该限制；
4. 为平台角色补齐 hierarchy 展开、RBAC3 控制面与审批闭环；
5. 明确租户 bootstrap 语义为“快照复制”，并补充 diff / sync 说明，避免被误解为平台角色持续同步。

---

## 7. API 设计（建议）

## 7.1 平台角色治理

```text
GET    /platform/roles
POST   /platform/roles
GET    /platform/roles/{id}
PUT    /platform/roles/{id}
DELETE /platform/roles/{id}

GET    /platform/roles/options
GET    /platform/roles/{id}/permissions
PUT    /platform/roles/{id}/permissions
```

说明：

- 不建议新建 `/platform/runtime-roles`。
- 后端可复用 `RoleService` 主链，但平台路径必须承认“这就是平台角色”，而不是模板-only 角色。
- 供平台 RBAC3 / 审批前端选择角色时，必须优先提供 `/platform/roles/options` 这类平台域最小 lookup；不要让 `/platform/**` 页面继续把 `/sys/roles` 或 `system:role:list` 当核心依赖。

## 7.2 平台用户角色绑定

```text
GET    /platform/users/{userId}/roles
PUT    /platform/users/{userId}/roles
GET    /platform/users/{userId}/role-requests
```

约束：

- `PUT` 只对 `approval_mode=NONE` 的角色做直接写入；
- 对需要审批的角色，`PUT` 直接拒绝并引导走申请接口，避免一个端点同时承担“直写”和“发起审批”两套语义。

## 7.3 平台 RBAC3 约束

```text
GET    /platform/role-constraints/hierarchy
POST   /platform/role-constraints/hierarchy
DELETE /platform/role-constraints/hierarchy

GET    /platform/role-constraints/mutex
POST   /platform/role-constraints/mutex
DELETE /platform/role-constraints/mutex

GET    /platform/role-constraints/prerequisite
POST   /platform/role-constraints/prerequisite
DELETE /platform/role-constraints/prerequisite

GET    /platform/role-constraints/cardinality
POST   /platform/role-constraints/cardinality
DELETE /platform/role-constraints/cardinality

GET    /platform/role-constraints/violations
```

说明：

- 路由级别直接表明 platform-only。
- 不建议让前端在 `PLATFORM` scope 下继续调用 `/sys/role-constraints/*`。
- 这样可以把租户侧和平台侧的 requirement / guard / 审计分开建模。
- 所有真实暴露的 method（GET / POST / DELETE）都必须同步登记 `api_endpoint` 与 requirement，并补 real-controller 守卫测试；少任何一个 method 都会被统一守卫 fail-closed。

## 7.4 平台角色审批

```text
GET    /platform/role-assignment-requests
POST   /platform/role-assignment-requests
POST   /platform/role-assignment-requests/{id}/approve
POST   /platform/role-assignment-requests/{id}/reject
POST   /platform/role-assignment-requests/{id}/cancel
```

约束：

- 审批申请、审批动作与最终 apply 链路中引用的 `roleId` 必须 fail-closed 校验为 `tenant_id IS NULL + role_level=PLATFORM` 的平台角色；
- 审批通过不是绕过 RBAC3 的捷径，apply 前必须再走一次当前运行时 RBAC3 hard validation；
- 所有真实暴露的 approval method 都必须同步登记 `api_endpoint` / requirement，并补 real-controller 守卫测试；否则真实部署会被统一守卫 fail-closed；
- 平台用户页与独立审批页若展示审批摘要，应由平台域接口返回，不要回退到 tenant/system 控制面拼装。

---

## 8. 权限码与菜单建议

## 8.1 权限码

平台角色：

- `platform:role:list`
- `platform:role:view`
- `platform:role:create`
- `platform:role:edit`
- `platform:role:delete`
- `platform:role:permission:assign`

平台用户角色绑定：

- `platform:user:role:view`
- `platform:user:role:assign`

平台 RBAC3：

- `platform:role:constraint:view`
- `platform:role:constraint:edit`
- `platform:role:constraint:violation:view`

平台审批：

- `platform:role:approval:list`
- `platform:role:approval:submit`
- `platform:role:approval:approve`
- `platform:role:approval:reject`
- `platform:role:approval:cancel`

原则：

- 不复用 tenant-side `system:role:*` 或 `system:role:constraint:*`；
- 平台角色治理权限与租户角色治理权限保持分域。

## 8.2 菜单与页面

建议新增：

- `/platform/roles`
- `/platform/role-constraints`
- `/platform/role-approvals`

平台用户页同步补：

- 角色明细 tab
- 待审批申请 tab
- 审计时间线 tab

---

## 9. 运行时接入点（必须改）

## 9.1 `EffectiveRoleResolutionService`

必须补齐：

- `findEffectiveRoleIdsForUserInPlatform(...)` 对平台角色的 `role_hierarchy` 展开；
- 展开条件应基于 `tenant_id IS NULL + role_level=PLATFORM`，而不是新增角色用途字段。

否则会出现：

- 平台用户直接绑定子角色却拿不到父角色权限；
- 平台 RBAC3 hierarchy 页面存在，但 JWT / Session 权限却不生效。

## 9.2 `PermissionVersionService`

必须同步：

- 平台路径的 `permissionsVersion` 输入要包含平台 direct assignments 摘要；
- 也要包含 hierarchy 展开后的 effective role 输入；
- 否则平台角色约束或层级改动后，token 指纹不会及时变化。

## 9.3 `JwtTokenCustomizer`

要求：

- 平台 token 的 `roleCodes`、`permissions` 必须基于平台 effective roles；
- 平台角色的 bootstrap 来源属性不能被误当成另一套运行时角色来源。

## 9.4 `PlatformUserManagementService`

要求：

- 列表和详情接口从布尔 `hasPlatformRoleAssignment` 升级为“角色明细 + 审批状态摘要”；
- UI 上不能再只有“已绑定/未绑定”这种过粗粒度展示。

---

## 10. 审批模型（最小可用）

## 10.1 审批状态机

```text
PENDING -> APPROVED -> APPLIED
PENDING -> REJECTED
PENDING -> CANCELED
APPROVED -> FAILED
```

## 10.2 审批与 RBAC3 的关系

- 提交申请时可做一次“预检查”，用于尽早提示互斥/缺先决条件；
- 但真正的强校验必须在 `APPROVED -> APPLIED` 写入前再执行一次；
- 若审批期间角色或约束已变化，以最终 apply 时的硬校验结果为准。

## 10.3 审批与审计

新增审计事件建议：

- `PLATFORM_ROLE_ASSIGNMENT_REQUEST_SUBMIT`
- `PLATFORM_ROLE_ASSIGNMENT_REQUEST_APPROVE`
- `PLATFORM_ROLE_ASSIGNMENT_REQUEST_REJECT`
- `PLATFORM_ROLE_ASSIGNMENT_APPLY`
- `PLATFORM_ROLE_ASSIGNMENT_APPLY_FAILED`
- `PLATFORM_ROLE_CONSTRAINT_UPDATE`

---

## 11. 实施分期与落地清单

> 口径：P0/P1/P2 为优先级；XS/S/M/L 为工作量估算。
>
> 说明：自 2026-04-18 起，执行落地统一以 `CARD-PR-06/07/08` 为准；本表中的 `PR-6/7/8` 已按最新拆卡口径同步。此前“bootstrap 语义与 diff/sync 口径收口”“审计、菜单和 requirement 收口”不再单独拆成执行卡，而作为 `PR-7/8` 的同卡验收项一起完成。

| 编号 | 优先级 | 工作量 | 任务 | 范围 | 验证 |
| --- | --- | --- | --- | --- | --- |
| PR-1 | P0 | S | 统一平台角色语义与页面文案 | 新设计文档、任务清单、页面文案、接口注释 | 文档统一口径；`TemplateRoles.vue` / `PlatformUsers.vue` 不再暗示“两套平台角色” |
| PR-2 | P0 | M | 平台角色 CRUD + 权限绑定控制面收口 | `/platform/roles` 前后端、权限码、菜单 requirement | 平台 admin 可治理平台角色；平台角色既可赋权平台用户，也可作为 bootstrap 来源 |
| PR-3 | P0 | M | 平台用户角色绑定能力 | `/platform/users/{id}/roles`、PlatformUsers 新 tab、服务层写入 `role_assignment(scope=PLATFORM)` | 平台用户详情可查看与变更平台角色绑定 |
| PR-4 | P0 | S | 平台 effective role 补 `role_hierarchy` 展开 | `EffectiveRoleResolutionService`、`PermissionVersionService`、JWT/登录测试 | 只绑子角色时平台 token 仍拿到父权限；`permissionsVersion` 正确刷新 |
| PR-5 | P0 | M | 平台 RBAC3 约束控制面 | `/platform/role-constraints/*`、repo null-tenant path、违例查询、菜单与 requirement | 平台侧可维护 hierarchy/mutex/prerequisite/cardinality，并查询违例日志 |
| PR-6 | P1 | M | 平台 RBAC3 前端与平台域 lookup 收口 | `/platform/role-constraints/*` 前端、`/platform/roles/options`、平台菜单与 scope guard | 平台 RBAC3 页面不依赖 `/sys/role-constraints/*` 或 `system:role:list` |
| PR-7 | P1 | M | 平台角色审批后端 | `platform_role_assignment_request`、审批 API、状态机、平台绑定写链集成、bootstrap 语义注释收口 | 需要审批的高风险角色不能直写，审批通过后才应用，且平台角色 bootstrap 语义明确为快照复制 |
| PR-8 | P1 | S | 平台角色审批前端与门禁同步 | 审批页、PlatformUsers 审批摘要、`api_endpoint` / `menu` requirement 回填、审计事件、前端 scope guard | 平台审批页与平台用户页都能感知审批态；统一守卫不过早 403 |
| PR-9 | P2 | M | 与平台代管租户用户、impersonation 串联 | 后续 bridge/impersonation 文档与实现 | 平台角色审批与 impersonation actor 语义不冲突 |

推荐执行顺序：

1. `PR-1` -> `PR-2` -> `PR-3`
2. `PR-4` -> `PR-5`
3. `PR-6` -> `PR-7` -> `PR-8`
4. 最后再接 `PR-9`

---

## 12. 验证门禁建议

后端：

- 平台角色解析：
  - `EffectiveRoleResolutionServiceTest`
  - `PermissionVersionServiceTest`
  - `AuthUserResolutionServiceTest`
- 平台 RBAC3：
  - 新增 `PlatformRoleConstraintController...IntegrationTest`
  - 新增 `PlatformRoleConstraintService...Test`
- 平台角色绑定/审批：
  - `PlatformUserManagementServiceImplTest`
  - 新增 `PlatformRoleAssignmentRequest...Test`

前端：

- `PlatformUsers.test.ts`
- 新增 `PlatformRoles.test.ts`
- 新增 `PlatformRoleConstraints.test.ts`
- 新增 `PlatformRoleApprovals.test.ts`

最小命令建议：

```bash
mvn -pl tiny-oauth-server -Dtest=RoleConstraintRuleControllerRbacIntegrationTest,PlatformUserManagementControllerTest test
```

后续实现期应补平台专用定向测试类，不建议长期依赖 tenant-only 的 `RoleConstraintRuleControllerRbacIntegrationTest` 作为平台能力证明。

---

## 13. 最终建议（一句话）

如果平台侧已经承载真实角色治理业务，就不应该继续把问题理解成“模板角色缺个菜单”，而应该直接承认“平台只有一套平台角色”，然后按“平台角色绑定 -> 平台 RBAC3 -> 平台审批”的顺序，把平台角色治理主线补齐。
