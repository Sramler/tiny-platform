# Token Claims 企业规范（当前实现）

最后更新：2026-02-10  
适用模块：`tiny-oauth-server`

## 1. 文档目标

本文档描述当前代码中实际签发的 JWT Claims，用于排查以下问题：

- Token 是否包含 `activeTenantId`
- `amr` 是否符合本次认证因子
- 不同 `security.mfa.mode` 下的 Claims 预期

对应实现：`src/main/java/com/tiny/platform/core/oauth/config/JwtTokenCustomizer.java`

## 2. Access Token 当前 Claims

### 2.1 框架标准字段（Spring Authorization Server）

- `iss`
- `sub`
- `aud`
- `exp`
- `iat`
- `jti`

### 2.2 当前已实现的业务字段

- `userId`
- `username`
- `activeTenantId`（主字段，当前活动租户 ID）
- `activeScopeType`（`PLATFORM` 或 `TENANT`，与 activeTenantId 一致：有租户则为 TENANT，否则 PLATFORM）
- `permissions`（仅包含 `:` 的权限标识符列表，供前端/资源端鉴权）
- `permissionsVersion`（用于 token 失效检测，与 TenantContextFilter 校验一致）
- `authorities`（兼容：角色码 + 权限标识符；新逻辑以 permissions 为准）
- `client_id`
- `scope`
- `auth_time`
- `amr`

### 2.3 permissions / activeScopeType / activeTenantId 稳定契约（T6.2）

以下三个 claim 为运行态最小授权上下文，实现与 `TenantContextContract`、`JwtTokenCustomizer.applyTenantClaims` 一致，前端与资源端可稳定依赖：

| Claim | 类型 | 说明 |
|-------|------|------|
| `activeTenantId` | number | 当前活动租户 ID；无租户或平台级时为 null/不写 |
| `activeScopeType` | string | `PLATFORM` 或 `TENANT`；有 activeTenantId 时为 TENANT，否则 PLATFORM |
| `permissions` | string[] | 当前租户下有效权限标识符（仅含 `system:xxx:yyy` 形式），鉴权以本列表为准 |

约定：前端鉴权与展示仅使用 `permissions` 与 `activeTenantId`；如需区分平台级/租户级入口可选用 `activeScopeType`。`authorities` 保留兼容，新逻辑不依赖其内的角色名（role.name）。

## 3. ID Token 当前 Claims

### 3.1 当前已实现字段

- `userId`
- `username`
- `activeTenantId`（主字段）
- `auth_time`
- `amr`

### 3.2 基于 Scope 的扩展字段

当 `authorizedScopes` 包含对应 scope 且用户资料存在时，可能补充：

- `profile` -> `name`, `nickname`
- `email` -> `email`, `email_verified`
- `phone` -> `phone_number`, `phone_number_verified`

说明：相关字段由 membership-aware 的用户解析链补充，不再保留旧 `tenantId` claim 回退。

## 4. Refresh Token Claims

`JwtTokenCustomizer` 中实现了 Refresh Token claims 填充逻辑，但仅在 Refresh Token 实际为 JWT 时生效。  
若系统使用默认 opaque refresh token，则不会直接看到这些 claims。

代码中可写入字段：

- `userId`
- `username`
- `client_id`
- `grant_type`
- `scope`
- `auth_time`

## 5. `amr` 生成规则

`amr` 来源：`MultiFactorAuthenticationToken.completedFactors`

映射关系：

- `PASSWORD` -> `password`
- `TOTP` -> `totp`
- `OAUTH2` -> `oauth2`
- `EMAIL` -> `email`
- `MFA` -> `mfa`

兜底规则：

- 若 `principal.isAuthenticated() == true` 且未提取到任何因子，则写入 `password`

## 6. MFA 模式与 `amr` 预期

### 6.1 `mode=NONE`

- 预期：`amr` 仅包含 `password`
- 代码保证：`CustomLoginSuccessHandler` 在 `disableMfa=true` 分支不会调用 `promoteToFullyAuthenticated`，避免无条件补 `totp`

### 6.2 `mode=OPTIONAL`

- 已绑定且激活 TOTP：预期 `amr=["password","totp"]`
- 未绑定或未激活（且允许跳过）：预期 `amr=["password"]`

### 6.3 `mode=REQUIRED`

- 已绑定并完成 TOTP：预期 `amr=["password","totp"]`
- 未绑定/未激活：应被绑定流程拦截，不应签发最终授权 token

## 7. 活动租户 Claims 说明

- `activeTenantId` 是当前运行时的唯一租户 claim
- 登录链路中的租户上下文由 `TenantContextFilter` 建立，认证后优先信任 token/session 中冻结的 `activeTenantId`
- 登录页提交 `tenantCode`，后端在认证前统一解析为租户 ID
- 若登录/授权请求未携带可解析租户，后端应在前置阶段失败（如 `missing_tenant`），而不是签发无租户 token
- 前端高频当前上下文接口 `/sys/users/current` 与 `/self/security/status` 直接返回 `activeTenantId`，前端据此同步本地上下文
- 前端页面内部的“当前活动租户”路由状态统一使用 `activeTenantId`；新接口不得再用 `tenantId` 表达当前上下文
- 示例脚本、历史接口文档、业务 DTO 中若仍出现 `tenantId`，默认将其视为遗留字段或存储层语义，而不是当前活动租户上下文主字段
- 首页/列表/详情等内部页面回退可保留 `activeTenantId`；登录页与 `/login` 跳转默认不携带该 query，而是继续依赖 `tenantCode` 输入和认证上下文冻结
- 当前活动租户请求头统一为 `X-Active-Tenant-Id`，其运行时值直接对应 `activeTenantId`
- 请求日志与 MDC 的当前上下文字段统一为 `activeTenantId`，不再使用 `tenantId` 作为日志上下文键名

补充说明：

- 当前 token claim 以 `activeTenantId` 为准，不写入 `tenantCode`
- 前端本地可保留 `tenantCode` 仅用于登录输入体验，运行时鉴权链路只依赖活动租户 ID

## 8. 已知限制

- `auth_time` 当前优先读 `OAuth2Authorization.attributes["auth_time"]`，缺失时回退 `Instant.now()`
- `skipMfaRemind` 目前未持久化，仅用于会话内分支控制

## 9. 排查清单

1. 先看 `CustomLoginSuccessHandler` 日志：是否进入 `disableMfa` / `requireTotp` 分支。
2. 再看 `MfaAuthorizationEndpointFilter`：`/oauth2/authorize` 是否二次拦截。
3. 解码 access token，核对 `activeTenantId`、`amr`、`auth_time`。
4. 若 `mode=NONE` 仍出现 `totp`，优先检查是否走了旧会话或旧服务进程。
5. 若页面当前上下文异常，优先检查 `/sys/users/current` 与 `/self/security/status` 返回值是否已归一化为 `activeTenantId`。
