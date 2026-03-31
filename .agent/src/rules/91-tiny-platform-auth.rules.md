# 91 tiny-platform 认证授权规范（平台特定）

## 适用范围

- 适用于：`**/oauth2/**`、`**/auth/**`、`**/security/**`、认证授权相关代码
- 配套文档：`docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md`（阅读入口与冲突裁决）、`docs/TINY_PLATFORM_AUTHORIZATION_MODEL.md`（授权模型主线）、`docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md`（Session/Bearer 来源矩阵与冲突处理）、`docs/TINY_PLATFORM_AUTHORIZATION_TASK_LIST.md`（当前完成度）、`docs/TINY_PLATFORM_TENANT_NAMING_GUIDELINES.md`（租户命名契约）

## 禁止（Must Not）

- ❌ Token Claims 中泄漏敏感信息（密码、私钥、完整权限列表）。
- ❌ 硬编码客户端配置（client_id, client_secret 必须从配置读取）。
- ❌ 混用不同的认证方式（JWT vs Session）在同一请求中。
- ❌ 跳过 MFA（多因素认证）验证（如配置了 TOTP 必须验证）。
- ❌ 自动化测试使用开发者个人账号、共享人工管理员账号或生产身份进行认证验证。

## 必须（Must）

- ✅ OAuth2 授权流程：使用 `authorization_code`（Web 应用）和 `refresh_token`（刷新令牌）。
- ✅ JWT Token Claims：标准字段（iss, sub, aud, exp, iat, jti）由框架自动添加；企业级字段应与当前授权主线一致，至少稳定包含 `userId`、`username`、`permissions`、`activeTenantId`、`activeScopeType`、`permissionsVersion`；`authorities` 可保留兼容语义，但不得替代 `permissions` 作为当前能力判断入口。
- ✅ 认证方式选择：按客户端来源切换 JWT/Session（Web 前端用 Session，API 客户端用 JWT）。
- ✅ 多认证方式：支持 PASSWORD（密码）和 TOTP（时间戳一次性密码），从 `user_authentication_method` 表动态查询。
- ✅ 安全策略：JWT 使用 RS256 算法，密钥使用 JWK Set；支持 MFA（TOTP）。
- ✅ Token 过期：Access Token 短期（如 1 小时），Refresh Token 长期（如 7 天）；过期后必须重新授权。
- ✅ 涉及角色、作用域、租户成员关系或数据权限的认证/会话改动，必须与 `docs/TINY_PLATFORM_AUTHORIZATION_DOC_MAP.md` 指向的当前真相源保持一致，不能只改 claims 或只改数据库结构。
- ✅ 经过真实认证链路的自动化测试必须使用专用测试身份，至少区分：普通用户、租户管理员、无权限或拒绝身份；启用 MFA 时还必须具备 MFA 测试用户。
- ✅ 测试身份必须与测试租户、测试 client、权限集合一起受控初始化，保证可重复执行与可回收。
- ✅ 自动化认证测试所需密码、client secret、TOTP secret 必须从受控配置注入，不能写入测试代码和仓库明文。
- ✅ 当 E2E 目标是验证登录、登出、MFA、OIDC 回调、Session/JWT 切换、租户 claim 注入、权限漂移时，必须覆盖真实认证步骤，不能用伪造 token、手写 cookie、手写 `storageState` 替代。
- ✅ 如需在非认证主题的 real-link E2E 中复用登录态，`storageState` 或 session 预置必须来自单独的真实登录 setup 步骤，并明确标注生成来源与适用场景。
- ✅ 认证相关 E2E 必须显式断言用户可观察结果和安全结果，例如登录成功后的身份状态、MFA 二次校验、拒绝页面、租户上下文、cookie/session 或 token 切换结果。
- ✅ 认证 E2E 所使用的测试 client 必须最小权限化，并区分 Web Session、API JWT、OIDC 浏览器回调等测试目标；不得用一个超大权限 client 混测所有场景。
- ✅ 认证 real-link 中涉及多身份或多租户 auth-state 生成时，环境变量覆盖顺序必须显式定义，并用自动化测试锁住：次身份不能继承主身份的 TOTP code、TOTP secret、client 或 auth-state 输出路径。
- ✅ 首绑 TOTP / post-login 安全中心 / MFA 继续跳转类 E2E 必须按真实浏览器会话契约工作：优先使用页面真实渲染结果或 `credentials: include` 的 first-party 请求，不能假设 OIDC callback 一定已经把 token 写入 `localStorage`。
- ✅ 涉及 Session / Bearer 同时存在、`activeTenantId` 来源裁决、`activeScopeType/id` 成对解析、`prompt=none` / silent renew 行为的改动，必须与 `docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md` 保持一致；不得用“浏览器请求一律只看 Session”或“只要带 Bearer 就完全忽略 Session”这种简化口径替代当前主线契约。
- ✅ **M4（Bearer + Session 一致）在 user 端点上分两种正式语义，不得混用**：`GET /sys/users/current` 为 **M4 读**（只读快照，不改 Session scope）；`POST /sys/users/current/active-scope` 为 **M4 写**（会话 active scope **持久化以 Session 为权威落点**，Bearer 写成功须返回 `tokenRefreshRequired` / `newActiveScope*`）。默认不得假设“矩阵 §4 的 M4 放行 = 所有 `/sys/users/**` 同一口径”；须按 `docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md` **§8**。
- ✅ **默认心智模型（Cursor/Codex/实现者）**：将 `POST /sys/users/current/active-scope` 视为 **会话状态变更写**（session-first 持久化）；在 **M4** 下 **额外**允许带 Bearer 完成同一写，且**必须**按响应 `tokenRefreshRequired` 规划 refresh，不得假装 JWT 已随该请求更新。
- ✅ `POST /sys/users/current/active-scope` 在 M4（Bearer + Session 成对一致）下允许写 Session 时，成功响应必须携带可机器解析的 `tokenRefreshRequired` 与 `newActiveScopeType`/`newActiveScopeId`（及说明写后 JWT 与 Session 可预测行为的文档），且与 `docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md` §8 一致；不得静默忽略“写后显式 scope claims 陈旧导致下一请求 M5 fail-closed”的风险。
- ✅ tiny-platform 本地认证 / 租户 / 平台模板验证应优先复用仓库脚本，且**默认入口**为 `tiny-oauth-server/scripts/verify-platform-local-dev-stack.sh`；只有在明确不需要前端联动时，才降级为 `tiny-oauth-server/scripts/verify-platform-dev-bootstrap.sh`；登录链专项快速门禁再使用 `tiny-oauth-server/scripts/verify-platform-login-auth-chain.sh`。不要先要求人工逐个启动数据库、后端、前端，再开始判断认证结论。
- ✅ 如需读取本机已导出的数据库密码、启动命令或路径，只允许通过 login shell 子进程读取**白名单环境变量**；`DB_*` 为 dev/bootstrap 主变量，`E2E_DB_*` 可作为兼容别名回填，不得打印或上传 `~/.zprofile`、`~/.zshrc`、`~/.bashrc` 全文。
- ✅ `verify-platform-dev-bootstrap.sh` / `verify-platform-local-dev-stack.sh` 返回 `exit 2` 时，只能记为“环境前置未满足”，不得写成认证链路失败或代码回归。

## 应该（Should）

- ⚠️ Token Claims 扩展：可继续补 `auth_time`（认证时间）、`amr`（认证方法引用，如 password, totp）等安全字段；如需新增租户/作用域相关字段，应优先沿用当前主线中的 `activeTenantId` / `activeScopeType` 口径，而不是回退到旧 `tenant_id` 命名。
- ⚠️ 客户端配置：使用配置文件（`application.yaml`）管理客户端信息（client_id, redirect_uris, scopes, grant_types）。
- ⚠️ 权限传递：兼容期内 `authorities` 可继续包含 `role.code` 与能力标识，但运行态能力判断应优先基于 `permissions`；权限码命名遵循 `92-tiny-platform-permission.rules.md`。
- ⚠️ 刷新策略：Refresh Token 使用后轮换（旧 Token 失效，返回新 Token）。
- ⚠️ 建议维护认证自动化身份矩阵，明确每个测试身份的租户归属、权限级别、是否启用 MFA、适用的 Session / JWT / OIDC 场景。
- ⚠️ 测试环境如需固定 TOTP secret 或固定 client secret，必须限定在隔离测试环境，并记录用途与轮换方式。
- ⚠️ 认证相关 real-link E2E 建议将登录 setup、身份初始化、用例执行拆分，避免每条用例重复走全量初始化。
- ⚠️ OIDC/MFA 场景建议保留至少一条浏览器级 E2E，而不是只用 controller/integration test 证明服务端逻辑正确。

## 可以（May）

- 💡 认证上下文：记录认证方法（`amr`）、认证时间（`auth_time`）、设备信息（`device_id`）。
- 💡 Token 黑名单：支持 Token 撤销（Redis 黑名单或数据库标记）。
- 💡 为自动化测试提供一键初始化脚本，批量创建测试用户、测试租户、测试 client 与测试认证方式。

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
  "authorities": ["ROLE_ADMIN", "system:user:view", "system:user:edit"],
  "permissions": ["system:user:view", "system:user:edit"],
  "client_id": "web-frontend",
  "scope": "openid profile email",
  "activeTenantId": 1,
  "activeScopeType": "TENANT",
  "permissionsVersion": 7
}
```

### ✅ 正例：认证自动化身份矩阵

```text
- auth-e2e-user: 当前租户普通用户，验证基础登录和受限访问
- auth-e2e-tenant-admin: 当前租户管理员，验证管理能力
- auth-e2e-deny: 已认证但缺少目标权限，验证 403 / 拒绝路径
- auth-e2e-cross-tenant: 登录成功但访问其他租户资源应失败
- auth-e2e-mfa: 启用 TOTP，验证二次认证流程
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
