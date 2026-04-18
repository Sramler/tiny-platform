# 平台用户管理 + 租户用户代管 + Impersonation 设计文稿（下一版本）

> 状态：设计草案（vNext）  
> 目标：在不破坏现有平台/租户隔离的前提下，补齐平台用户管理、平台对租户用户的代管能力，以及受控 impersonation 链路。  
> 约束：不改变现有运行态授权主链与 platform/tenant 边界；不引入隐式跨租户访问；不以 “/sys/users” 承载平台用户语义。

关联文档：
- `docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`
- `docs/TINY_PLATFORM_TENANT_GOVERNANCE.md`
- `docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`
- `docs/TINY_PLATFORM_AUTHORIZATION_NEXT_PHASE_AND_IMPROVEMENTS.md`
- `docs/TINY_PLATFORM_PLATFORM_RUNTIME_RBAC3_GOVERNANCE_DESIGN.md`

---

## 1. 现状基线（当前仓库行为）

### 1.1 平台身份与作用域
- 平台登录与平台角色解析已存在：`AuthUserResolutionService.resolveUserRecordInPlatform(...)` + `EffectiveRoleResolutionService.findEffectiveRoleIdsForUserInPlatform(...)`。
- 平台作用域由 `activeScopeType=PLATFORM` 表达，平台控制面以 `TenantContext.isPlatformScope()` 为入口门槛。
- 当前仓库已补最小 `/platform/users` Phase 1 skeleton：后端提供列表/详情/创建 profile/状态更新，前端提供最小控制面；但平台用户域仍是基于 `user + platform_user_profile` 的逻辑域，而非独立物理身份根。

### 1.2 租户用户控制面
- `/sys/users` 为租户侧控制面，`UserManagementAccessGuard` 明确拒绝 `PLATFORM` scope。
- 前端用户页也已做平台 scope 防护提示。
- 运行态用户 membership 由 `tenant_user` 表表达；`user` 为平台账号根。

### 1.3 Impersonation
- 当前仓库不存在 impersonation token/claims/filter/审计闭环。
- `TenantContextFilter` 只做 scope/tenant 裁决，不含 actor/act/impersonation 逻辑。

### 1.4 关键代码基线（当前实现映射）
- 平台登录解析：`tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/security/AuthUserResolutionService.java`
- 平台有效角色解析：`tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/role/service/EffectiveRoleResolutionService.java`
- 平台用户控制面：`tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/user/PlatformUserManagementController.java`
- 平台用户管理档案：`tiny-oauth-server/src/main/java/com/tiny/platform/infrastructure/auth/user/domain/PlatformUserProfile.java`
- 租户侧用户控制面守卫：`tiny-oauth-server/src/main/java/com/tiny/platform/application/controller/user/security/UserManagementAccessGuard.java`
- 租户侧用户页平台守卫：`tiny-oauth-server/src/main/webapp/src/views/user/user.vue`
- 平台用户页：`tiny-oauth-server/src/main/webapp/src/views/platform/users/PlatformUsers.vue`
- scope/tenant 解析主入口：`tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/tenant/TenantContextFilter.java`
- token claim 写入入口：`tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/JwtTokenCustomizer.java`

结论：现状已具备平台作用域与租户隔离基础，并已落地平台用户管理 Phase 1 skeleton；但仍缺少平台到租户用户的桥接层、平台角色绑定专门控制面、平台运行时 RBAC3 治理，以及受控 impersonation 子系统。

---

## 2. 设计目标与非目标

### 2.1 目标
1. **平台用户管理**：新增平台级用户管理视图与能力，不复用 `/sys/users`。
2. **租户用户代管**：平台侧以受控桥接方式读取/维护租户用户（仅在显式目标租户上下文下）。
3. **受控 impersonation**：显式启动/停止、可审计、短时、不可嵌套的 impersonation。
4. **不破坏隔离**：platform 与 tenant 的权限、数据访问路径保持强隔离。

### 2.2 非目标（本版本不做）
1. 不引入全新“平台用户表”并一次性替换当前 `user` 根模型（避免高风险重构）。
2. 不让平台控制面直接调用租户 repository/service（必须走 bridge）。
3. 不允许隐式 tenant 切换或默认 impersonation。

---

## 3. 设计原则（硬约束）

1. **平台与租户权限严格隔离**：platform 角色与 tenant 角色不混用。
2. **bridge-only**：平台跨租户操作必须通过桥接层，禁止 controller 直调 tenant domain service/repo。
3. **impersonation 必须显式、可审计、可撤销**。
4. **不扩散 /sys/users**：平台用户管理必须使用 `/platform/users` 等独立入口。
5. **fail-closed**：未注册、未授权、非法 scope 均应拒绝。

---

## 4. 目标态架构（高层）

```
Platform Scope
  - platform_user_profile
  - platform_role / platform_permission (tenant_id IS NULL)
  - platform_user_management API (/platform/users)
  - platform_tenant_bridge API (/platform/tenants/{id}/users ...)
  - impersonation_service + audit

Tenant Scope
  - tenant_user
  - role_assignment (scope=TENANT)
  - /sys/users

Bridge Layer
  - PlatformTenantBridgeService
  - PlatformTenantUserBridgeService
  - TenantScopedExecutionTemplate
```

说明：
- **平台用户**仍以 `user` 作为身份根，但增加 `platform_user_profile` 作为管理域载体。
- **租户用户**继续以 `tenant_user + /sys/users` 为核心入口。
- 跨域访问必须通过 **bridge layer**，禁止 controller 直连 tenant service。

---

## 5. 数据模型建议（vNext）

### 5.1 平台用户域（新增）

```sql
-- 平台用户管理档案（逻辑域）
CREATE TABLE platform_user_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL COMMENT 'ACTIVE/DISABLED',
  display_name VARCHAR(64),
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_platform_user_profile_user (user_id)
);
```

用途：
- 不改变 `user` 作为身份根，但明确“平台管理语义”的边界。
- 平台用户管理页以 profile 为中心展示。

### 5.2 Impersonation（新增）

```sql
CREATE TABLE impersonation_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  actor_user_id BIGINT NOT NULL,
  target_user_id BIGINT NOT NULL,
  target_tenant_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL COMMENT 'ACTIVE/STOPPED/EXPIRED/DENIED',
  reason VARCHAR(256),
  started_at DATETIME NOT NULL,
  stopped_at DATETIME NULL,
  expires_at DATETIME NOT NULL
);
```

---

## 6. API 设计（草案）

### 6.1 平台用户管理（新）

```
GET    /platform/users
POST   /platform/users
GET    /platform/users/{id}
PATCH  /platform/users/{id}/status
```

说明：
- 平台用户是“平台身份 + profile 管理域”，不允许直接管理租户 membership。

### 6.2 平台代管租户用户（bridge）

```
GET    /platform/tenants/{tenantId}/users
POST   /platform/tenants/{tenantId}/users
GET    /platform/tenants/{tenantId}/users/{userId}
PATCH  /platform/tenants/{tenantId}/users/{userId}/status
```

约束：
- 必须显式 `tenantId`，不得依赖当前 scope 隐式切换。
- 所有 API 走桥接服务（PlatformTenantUserBridgeService）。

### 6.3 Impersonation（新）

```
POST   /platform/impersonations
POST   /platform/impersonations/{id}/stop
GET    /platform/impersonations
```

---

## 7. 权限模型（建议新增规范码）

平台用户管理：
- `platform:user:list`
- `platform:user:view`
- `platform:user:create`
- `platform:user:edit`
- `platform:user:disable`

租户代管：
- `platform:tenant:user:list`
- `platform:tenant:user:view`
- `platform:tenant:user:manage`

Impersonation：
- `platform:impersonation:start`
- `platform:impersonation:stop`
- `platform:impersonation:list`

注意：这些权限与租户侧 `system:user:*` 分离，避免跨域混用。

---

## 8. Impersonation 设计细节

### 8.1 Token Claims（建议）

```
{
  "sub": "tenant_user_subject",
  "userId": 123,
  "activeTenantId": 456,
  "activeScopeType": "TENANT",
  "impersonation": true,
  "impersonationId": 789,
  "actor": {
    "userId": 999,
    "username": "platform_admin"
  }
}
```

约束：
- impersonation token 必须短时、不可 refresh。
- 不允许嵌套 impersonation。
- impersonation token 必须在审计中包含 actor 与 target。

### 8.2 Filter / Context
- 新增 `ImpersonationSecurityFilter`，在 `TenantContextFilter` 之后执行。
- 若 `impersonation=true`，则：
  - 强制 scope 为 `TENANT`。
  - 禁止切换 scope 回到 `PLATFORM`。

---

## 9. 审计与合规

新增审计事件：
- `IMPERSONATION_START`
- `IMPERSONATION_STOP`
- `IMPERSONATION_DENIED`
- `IMPERSONATED_ACTION`（可选，按成本评估）

审计字段建议：
- actorUserId
- targetUserId
- targetTenantId
- reason
- result
- impersonationId

---

## 10. Bridge Layer 设计

新增服务：
- `PlatformTenantBridgeService`
- `PlatformTenantUserBridgeService`

职责：
- 接收 platform controller 请求
- 明确传入目标 tenantId
- 调用 tenant domain service（禁止 controller 直调）
- 审计记录与权限校验集中

---

## 11. 分阶段落地建议

### Phase 1：平台用户管理
- 新增 `platform_user_profile` + `/platform/users`
- 不涉及 impersonation 与代管操作

### Phase 2：租户代管 bridge
- 新增 `/platform/tenants/{id}/users`
- 引入 PlatformTenantUserBridgeService

### Phase 3：Impersonation
- 新增 impersonation token/claims/filter
- 新增 impersonation 审计闭环

### 11.1 Phase 1 可直接开工任务拆分

当前状态（2026-04-14）：
- `PUM-1` 已落地。
- `PUM-2` 已落地。
- `PUM-3` 已落最小闭环（`/platform/users` 路由 + 页面 + API + 单测），创建仍以手输 `userId` 方式补建 profile，候选用户 lookup 尚未单开。
- `PUM-4` 与 `PUM-5` 仍可继续增强，但不阻塞当前最小平台用户控制面使用。

#### PUM-1 Schema / Seed
- 新增 `platform_user_profile` 表，作为平台用户管理档案。
- 增加平台用户管理权限码：
  - `platform:user:list`
  - `platform:user:view`
  - `platform:user:create`
  - `platform:user:edit`
  - `platform:user:disable`
- 回填策略：
  - 已具备 `PLATFORM` scope 有效角色分配的 `user`，自动补一条 `platform_user_profile`。
  - 不具备平台赋权的普通租户用户，不自动进入平台用户域。
- 补 unified guard 所需 `api_endpoint` / `api_endpoint_permission_requirement` 回填。

#### PUM-2 Backend
- 新增 `PlatformUserManagementController`：
  - `GET /platform/users`
  - `POST /platform/users`
  - `GET /platform/users/{id}`
  - `PATCH /platform/users/{id}/status`
- 新增 `PlatformUserManagementAccessGuard`：
  - 仅允许 `PLATFORM` scope。
  - 权限码只认 `platform:user:*`。
- 新增 `PlatformUserManagementService` / `PlatformUserManagementServiceImpl`：
  - 查询平台用户列表与详情。
  - 创建平台用户基础档案。
  - 启停平台用户。
- 新增 `PlatformUserProfileRepository` 与最小 projection / dto。

#### PUM-3 Frontend
- 新增路由 `/platform/users`。
- 新增页面 `PlatformUsers.vue`：
  - 列表、搜索、状态切换、创建弹窗。
  - 平台 scope 守卫，tenant scope 下不发请求。
- 新增平台用户 API：
  - `listPlatformUsers`
  - `getPlatformUserDetail`
  - `createPlatformUser`
  - `updatePlatformUserStatus`
- 页面文案明确“平台用户”与“租户用户”不是同一控制面，不跳转 `/sys/users`。

#### PUM-4 平台角色绑定边界
- Phase 1 不新建第二套平台角色模型。
- 平台用户是否能登录平台，仍以 `role_assignment(scope_type=PLATFORM)` 为准。
- 若需要在同一阶段补平台角色绑定 UI，必须显式标注“仅绑定平台角色，不涉及租户角色”。

#### PUM-5 Tests
- 后端：
  - `PlatformUserManagementControllerTest`
  - `PlatformUserManagementRbacIntegrationTest`
  - `PlatformUserManagementApiEndpointGuardRealControllerIntegrationTest`
  - `PlatformUserManagementServiceImplTest`
- 前端：
  - `src/api/platform-user.test.ts`
  - `src/views/platform/users/PlatformUsers.test.ts`
- 验收锁定：
  - `PLATFORM` scope 可访问 `/platform/users`
  - tenant scope 被前后端同时拒绝
  - `/sys/users` 在 `PLATFORM` scope 仍被拒绝

### 11.2 Phase 2 可直接开工任务拆分

#### PTUB-1 Bridge Contract
- 新增 `PlatformTenantUserBridgeController`：
  - `GET /platform/tenants/{tenantId}/users`
  - `POST /platform/tenants/{tenantId}/users`
  - `GET /platform/tenants/{tenantId}/users/{userId}`
  - `PATCH /platform/tenants/{tenantId}/users/{userId}/status`
- controller 只依赖 `PlatformTenantUserBridgeService`，不直调 `UserServiceImpl` / `TenantUserRepository`。

#### PTUB-2 Bridge Service
- 新增 `PlatformTenantUserBridgeService` / `PlatformTenantBridgeService`。
- 显式入参必须包含 `tenantId`。
- 桥接层负责：
  - 平台权限校验
  - 目标租户可见性校验
  - 审计落点
  - 调用租户域服务

#### PTUB-3 Frontend
- 在 `/platform/tenants/:id` 下增加“租户用户代管”页签，或独立 `/platform/tenants/:id/users` 页面。
- 所有请求都显式携带路径 tenantId，不借用活动租户上下文。

#### PTUB-4 Tests
- 锁住“未显式 tenantId 不可访问”
- 锁住“platform controller 不直调 tenant repo/service 主链”
- 锁住“bridge 审计包含 actor + targetTenant + action”

### 11.3 Phase 3 可直接开工任务拆分

#### IMP-1 Token / Claims
- 新增 impersonation session 表与服务。
- 新增 impersonation claims：
  - `impersonation=true`
  - `impersonationId`
  - `actor`
- impersonation token 禁止 refresh / offline_access。

#### IMP-2 API / Filter
- 新增 `PlatformImpersonationController`：
  - `POST /platform/impersonations`
  - `POST /platform/impersonations/{id}/stop`
  - `GET /platform/impersonations`
- 新增 `ImpersonationSecurityFilter`：
  - 仅接受显式 impersonation token
  - 禁止嵌套 impersonation
  - 禁止 impersonated token 切回 `PLATFORM`

#### IMP-3 Audit
- 新增审计事件：
  - `IMPERSONATION_START`
  - `IMPERSONATION_STOP`
  - `IMPERSONATION_DENIED`
- 审计必须包含：
  - actorUserId
  - targetUserId
  - targetTenantId
  - impersonationId
  - reason
  - result

#### IMP-4 Tests
- controller / service / filter 定向测试
- `userinfo` / platform page / tenant page 组合回归
- 锁住“不可嵌套、不可 refresh、可停止、停止后立即失效”

---

## 12. 风险与兼容性

1. **跨域直连风险**：必须阻止 controller 直调 tenant service。
2. **隐式 scope 切换风险**：必须通过显式 tenantId + bridge。
3. **权限混用风险**：平台权限不得与租户权限混用。
4. **审计缺失风险**：impersonation 必须完整审计。

---

## 13. 验收标准（建议）

平台用户管理：
- `/platform/users` 可用，`/sys/users` 仍拒绝 PLATFORM scope。

租户代管：
- 平台可按指定 tenantId 管理用户，且审计完整。

Impersonation：
- token 带 actor/impersonation claims
- 操作可审计、可终止、不可嵌套

---

## 14. 结论

该设计以现有仓库“平台作用域 + 租户隔离”运行态为基础，避免一次性重构身份根模型，采用“平台用户 profile + bridge + impersonation 子系统”的组合方式，既满足平台控制面诉求，也保持租户隔离与权限模型的清晰边界。
