# 91 tiny-platform 认证授权规范（平台特定）

## 适用范围

- 适用于：`**/oauth2/**`、`**/auth/**`、`**/security/**`、认证授权相关代码

## 禁止（Must Not）

- ❌ Token Claims 中泄漏敏感信息（密码、私钥、完整权限列表）。
- ❌ 硬编码客户端配置（client_id, client_secret 必须从配置读取）。
- ❌ 混用不同的认证方式（JWT vs Session）在同一请求中。
- ❌ 跳过 MFA（多因素认证）验证（如配置了 TOTP 必须验证）。

## 必须（Must）

- ✅ OAuth2 授权流程：使用 `authorization_code`（Web 应用）和 `refresh_token`（刷新令牌）。
- ✅ JWT Token Claims：标准字段（iss, sub, aud, exp, iat, jti）由框架自动添加；企业级字段（userId, username, authorities, client_id, scope）必须包含。
- ✅ 认证方式选择：按客户端来源切换 JWT/Session（Web 前端用 Session，API 客户端用 JWT）。
- ✅ 多认证方式：支持 PASSWORD（密码）和 TOTP（时间戳一次性密码），从 `user_authentication_method` 表动态查询。
- ✅ 安全策略：JWT 使用 RS256 算法，密钥使用 JWK Set；支持 MFA（TOTP）。
- ✅ Token 过期：Access Token 短期（如 1 小时），Refresh Token 长期（如 7 天）；过期后必须重新授权。

## 应该（Should）

- ⚠️ Token Claims 扩展：考虑添加 `auth_time`（认证时间）、`amr`（认证方法引用，如 password, totp）、`tenant_id`（租户 ID）。
- ⚠️ 客户端配置：使用配置文件（`application.yaml`）管理客户端信息（client_id, redirect_uris, scopes, grant_types）。
- ⚠️ 权限传递：Token Claims 中的 `authorities` 包含角色（ROLE_ADMIN）和资源权限（RESOURCE:user:read）。
- ⚠️ 刷新策略：Refresh Token 使用后轮换（旧 Token 失效，返回新 Token）。

## 可以（May）

- 💡 认证上下文：记录认证方法（`amr`）、认证时间（`auth_time`）、设备信息（`device_id`）。
- 💡 Token 黑名单：支持 Token 撤销（Redis 黑名单或数据库标记）。

## 例外与裁决

- OAuth2 标准端点（`/oauth2/authorize`、`/oauth2/token`）遵循 OAuth2 2.1 和 OIDC 1.0 规范。
- 第三方认证（如 LDAP、SAML）可扩展 `MultiAuthenticationProvider`。
- 冲突时：安全规范（40-security）优先于本规范。

## 示例

### ✅ 正例

```java
// Token Claims 包含标准字段 + 企业级字段
{
  "iss": "https://auth.tiny-platform.com",
  "sub": "user123",
  "aud": "web-frontend",
  "exp": 1234567890,
  "iat": 1234567890,
  "jti": "token-id-123",
  "userId": 123,
  "username": "admin",
  "authorities": ["ROLE_ADMIN", "RESOURCE:user:read", "RESOURCE:user:write"],
  "client_id": "web-frontend",
  "scope": "openid profile email",
  "tenant_id": 1
}
```

### ❌ 反例

```java
// 错误：泄漏敏感信息、缺少必要字段
{
  "userId": 123,
  "password": "encrypted-password", // ❌ 不应包含密码
  "allPermissions": ["...100+权限..."] // ❌ 不应包含完整权限列表
}
```
