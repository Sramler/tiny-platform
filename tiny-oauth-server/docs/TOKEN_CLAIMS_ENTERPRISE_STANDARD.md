# Token Claims 企业规范（当前实现）

最后更新：2026-02-10  
适用模块：`tiny-oauth-server`

## 1. 文档目标

本文档描述当前代码中实际签发的 JWT Claims，用于排查以下问题：

- Token 是否包含 `tenantId`
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
- `tenantId`（仅在 `SecurityUser.tenantId` 非空时写入）
- `authorities`
- `client_id`
- `scope`
- `auth_time`
- `amr`

## 3. ID Token 当前 Claims

### 3.1 当前已实现字段

- `userId`
- `username`
- `tenantId`（同 Access Token）
- `auth_time`
- `amr`

### 3.2 基于 Scope 的扩展字段

当 `authorizedScopes` 包含对应 scope 且用户资料存在时，可能补充：

- `profile` -> `name`, `nickname`
- `email` -> `email`, `email_verified`
- `phone` -> `phone_number`, `phone_number_verified`

说明：相关字段由 `UserRepository.findUserByUsernameAndTenantId(...)` 查询补充。

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

## 7. `tenantId` 说明

- `tenantId` 来自 `SecurityUser.tenantId`
- 登录链路中租户上下文由 `TenantContextFilter` 建立
- 若登录/授权请求未携带可解析租户，后端应在前置阶段失败（如 `missing_tenant`），而不是签发无租户 token

## 8. 已知限制

- `auth_time` 当前优先读 `OAuth2Authorization.attributes["auth_time"]`，缺失时回退 `Instant.now()`
- `skipMfaRemind` 目前未持久化，仅用于会话内分支控制

## 9. 排查清单

1. 先看 `CustomLoginSuccessHandler` 日志：是否进入 `disableMfa` / `requireTotp` 分支。
2. 再看 `MfaAuthorizationEndpointFilter`：`/oauth2/authorize` 是否二次拦截。
3. 解码 access token，核对 `tenantId`、`amr`、`auth_time`。
4. 若 `mode=NONE` 仍出现 `totp`，优先检查是否走了旧会话或旧服务进程。
