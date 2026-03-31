# Tiny Platform Session / Bearer 认证来源与冲突处理矩阵

> 状态：运行态认证来源专题文档  
> 目的：统一回答“当前请求到底信 Session 还是 Bearer”“`activeTenantId / activeScopeType / activeScopeId` 从哪里取”“二者同时存在时如何裁决”。  
> 适用范围：`/login`、`/csrf`、业务控制面接口、`/oauth2/**`、OIDC 浏览器回调、静默续期、`TenantContextFilter` 所覆盖的请求主链。  
> 配套规则：`.agent/src/rules/91-tiny-platform-auth.rules.md`

---

## 1. 先看结论

1. tiny-platform 不是“浏览器永远只看 Session、API 永远只看 Bearer”的粗暴二选一模型。
2. 更准确的口径是：**先分请求类型，再分认证主体来源，最后分 active scope 成对来源**。
3. **认证主体来源** 与 **active scope 来源** 不是同一个问题，不能混成一句“当前请求走 JWT 还是 Session”。
4. 当请求上 **同时存在 Bearer 与 Session** 时，不做静默降级；只要二者对 active scope 的断言冲突，就必须 **fail-closed**。
5. 当前主线默认：
   - Web 前端控制面：以 **Session** 为主
   - API 客户端：以 **Bearer/JWT** 为主
   - active scope：遵循 `TenantContextFilter` 的**成对解析**，不是“有 token 就全看 token”
6. **M4（Bearer + Session 一致）不是“全端点同一口径”**：矩阵行 M4 只说明 **TenantContextFilter 是否放行**与 **active scope 成对来源**。**读接口**与**写接口**在 user 控制面上仍有独立契约，见 **§8**；不得推断为“只要 M4 一致，所有 `/sys/users/**` 行为都相同”。

---

## 2. 术语约定

### 2.1 认证主体来源

指当前请求的“已登录用户身份”从哪里来：

- **Session 主体**：来自 `HttpSession` + `SecurityContext`
- **Bearer 主体**：来自 `Authorization: Bearer ...` 对应的 JWT / `JwtAuthenticationToken`
- **匿名主体**：未建立可用登录态

### 2.2 active tenant / active scope

指当前请求用于授权、数据范围与控制面行为判定的运行态上下文：

- `activeTenantId`
- `activeScopeType`
- `activeScopeId`

其中 **`activeScopeType` 与 `activeScopeId` 必须成对解析**，禁止跨来源混拼。

### 2.3 成对解析

“成对解析”是 tiny-platform 当前正式契约：

- 要么整对来自 **JWT**
- 要么整对来自 **Session**
- 不允许出现“JWT 提供 `activeScopeType`，Session 提供 `activeScopeId`”这类混拼

---

## 3. 端点分类

在判断 Session / Bearer 之前，先区分请求落在哪条安全链上。

### 3.1 默认业务链

见 [DefaultSecurityConfig.java](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/DefaultSecurityConfig.java)：

- 典型端点：`/login`、`/csrf`、业务控制面接口、前端同源请求
- 主要特征：
  - 支持表单登录
  - 支持 Session
  - 也支持 `oauth2ResourceServer().jwt(...)`，因此业务接口并非“绝不接受 Bearer”

### 3.2 授权服务器链

见 [AuthorizationServerConfig.java](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/config/AuthorizationServerConfig.java)：

- 典型端点：`/oauth2/**`、`/.well-known/**`、`/connect/logout`、`/userinfo`
- 主要特征：
  - HTML 交互请求可跳登录页
  - 非 HTML 请求返回 `401` JSON，不强制走表单页
  - OIDC `prompt=none` / silent renew 场景属于该链路，不能按普通控制面 Session 页面理解

---

## 4. 认证来源矩阵

| 场景 | Bearer | Session 登录态 | JWT 显式 `activeScopeType` | 当前请求主体来源 | `activeTenantId` 来源 | active scope 成对来源 | 结果 / 拒绝语义 | 典型请求 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M0 匿名请求 | 无 | 无 | 无 | 匿名 | header / issuer / 未认证推断（若适用） | 依据 `activeTenantId` 推断 `PLATFORM/TENANT`；无 ORG/DEPT 成对 | 公开端点可放行；受保护端点走 challenge / redirect / OAuth 错误 | `GET /login`、`GET /csrf`、未登录访问保护资源、`prompt=none` |
| M1 仅 Session | 无 | 有 | 无 | Session | 已认证主体 / Session | Session 成对 | 允许；若 active scope 非法则 **403** 或重定向到 `/login?...invalid_active_scope`，并清理 Session 安全上下文 | Web 控制面页面与同源 XHR |
| M2 仅 Bearer，JWT 显式带 scope type | 有 | 无或忽略 | 有 | Bearer/JWT | JWT / Bearer claims | JWT 成对；TENANT/PLATFORM 的 id 不回落 Session，ORG/DEPT 的 id 必须来自 JWT claim | 允许；若 type 非法、缺 id、跨租户、非成员，则 **401** `invalid_active_scope` + `WWW-Authenticate: Bearer error="invalid_token"` | API 客户端、携带显式 scope claims 的 Bearer 调用 |
| M3 仅 Bearer，JWT 未显式带 scope type | 有 | 无或可有残留 | 无 | Bearer/JWT | JWT / Bearer claims | Session 成对；若 Session 也没有显式 scope，则按 `activeTenantId` 推断 `PLATFORM/TENANT` | 允许；若 JWT 只带 `activeScopeId` 不带 type，则按孤儿声明拒绝 **401** | Bearer API，claims 仅带 `activeTenantId` |
| M4 Bearer + Session 并存，断言一致 | 有 | 有 | 分情况 | 当前请求按 Bearer 驱动处理；Session 视为并存态 | 先对齐 tenant，再冻结到 Session | 若 JWT 显式带 type，则用 JWT 成对；若 JWT 未带 type，则用 Session 成对 | 允许继续；不做冲突告警 | 浏览器带 cookie，同时前端请求又显式携带 Bearer |
| M5 Bearer + Session 并存，断言冲突 | 有 | 有 | 分情况 | 不可信，必须拒绝 | 视为冲突请求 | 不允许混拼 / 不允许不一致 | **fail-closed**：返回 **401** `invalid_active_scope`，重置 Session scope 为 TENANT，清理 `SecurityContextHolder` 与 Session 中的 `SPRING_SECURITY_CONTEXT` | Bearer 明示 ORG/DEPT，但 Session 残留不一致 id/type |

---

## 5. 裁决细则

### 5.1 `activeTenantId` 的优先级

当前实现见 [TenantContextFilter.java](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/tenant/TenantContextFilter.java#L167)：

1. 已认证主体解析出的 `activeTenantId`
2. Bearer claims 中的 `activeTenantId`
3. issuer path 中解析出的租户
4. Session 中冻结的 `activeTenantId`
5. 未认证请求的受控推断

补充：

- 一旦上游多个来源对 `activeTenantId` 断言不一致，请求会被拒绝，不做静默覆盖。
- `POST /login` 且未提交 `tenantCode` 的“平台登录尝试”会显式忽略会话残留租户，避免上次租户登录串到平台登录。

### 5.2 active scope 的成对解析

当前实现见 [TenantContextFilter.java](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/tenant/TenantContextFilter.java#L339)：

1. **有 Bearer，且 JWT 显式带 `activeScopeType`**
   - 整对来自 JWT
   - `TENANT` / `PLATFORM` 不读 Session id
   - `ORG` / `DEPT` 必须从 JWT 读取 `activeScopeId`
2. **有 Bearer，但 JWT 没显式带 `activeScopeType`**
   - 整对来自 Session
   - 若 Session 也未显式写 scope，则按 `activeTenantId` 推断为 `PLATFORM/TENANT`
   - JWT 单独携带 `activeScopeId` 但没有 type，视为孤儿声明，必须拒绝
3. **无 Bearer**
   - 整对来自 Session
   - 若 Session 未显式写 scope，则按 `activeTenantId` 推断 `PLATFORM/TENANT`

### 5.3 Bearer + Session 冲突时的处理

当前实现见 [TenantContextFilter.java](/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/java/com/tiny/platform/core/oauth/tenant/TenantContextFilter.java#L602)：

- Bearer 驱动请求遇到 active scope 非法或冲突：
  - 返回 **401**
  - 错误码：`invalid_active_scope`
  - `WWW-Authenticate: Bearer error="invalid_token"`
  - 若已有 `activeTenantId`，将 Session scope 重置到 `TENANT`
  - 清理 `SecurityContextHolder` 与 Session 中的 `SPRING_SECURITY_CONTEXT`

- Session 驱动请求遇到 active scope 非法：
  - HTML 页面：重定向到 `/login?...error=invalid_active_scope`
  - 非 HTML：返回 **403**
  - 同样清理 Session 安全上下文，并将 scope 回退到 `TENANT`

---

## 6. 特殊场景

### 6.1 `prompt=none` / silent renew

这类请求应视为 **OIDC 授权服务器链路**，不是普通控制面页面请求。

约束：

- 不应把 `prompt=none` 失败解释成“表单登录失败”
- 未建立可用登录态时，预期结果是 OAuth/OIDC 语义下的 `login_required`，而不是把请求强行改写成普通 `/login` 页面流程
- 排查时应优先看 `/oauth2/authorize` 链路，而不是先怀疑控制面 Session 页面

### 6.2 `/csrf`

- `/csrf` 是默认业务链里的公开端点
- 它常被前端用于建立 CSRF cookie / trace 上下文
- `/csrf` 成功不代表用户已登录，只代表默认链可达

### 6.3 页面请求与业务 API 请求不能只按 URL 想当然分类

不要使用下面这些错误简化：

- “浏览器请求一定只看 Session”
- “只要带 Bearer 就完全忽略 Session”
- “业务接口一定不会走 Session”
- “授权服务器端点一定只返回跳登录页”

应按本文件的三步判断：

1. 落在哪条安全链
2. 当前请求主体从哪里来
3. active scope 成对从哪里来

---

## 7. 对实现与测试的要求

1. 涉及 Session / Bearer / active scope 改动时，不得只改 claims 或只改 filter 文案，必须与本矩阵同步。
2. 新增认证相关测试时，至少应覆盖：
   - Session-only
   - Bearer-only（JWT 显式 type）
   - Bearer + Session 冲突 fail-closed
   - `prompt=none` / silent renew 的未登录行为
3. 文档、规则、测试矩阵若出现冲突，按以下顺序裁决：
   - 本文的认证来源矩阵
   - [TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md](/Users/bliu/code/tiny-platform/docs/TINY_PLATFORM_AUTHORIZATION_LAYERED_MODEL.md)
   - [TINY_PLATFORM_LOGIN_AUTHORIZATION_TEST_MATRIX.md](/Users/bliu/code/tiny-platform/docs/TINY_PLATFORM_LOGIN_AUTHORIZATION_TEST_MATRIX.md)

### 7.1 CI 门禁映射（把矩阵钉进可执行入口）

> 目标：避免“文档有矩阵、门禁无入口”。本节只给出 **最小高价值映射**：能阻断回归、且失败可定位。

| 矩阵场景 | 最小证据（测试/用例） | CI / 门禁入口 |
| --- | --- | --- |
| M0 匿名请求（含 `prompt=none` 未登录） | `AuthenticationFlowE2eProfileIntegrationTest.unauthenticatedPromptNoneShouldReturnLoginRequiredToRedirectUri`（integration） | 后端集成测试 job（PR/main） |
| M1 仅 Session | `DefaultSecurityConfigUserEndpointIntegrationTest.currentUserShouldAllowSessionOnlyRequestWithActiveScope`、`currentActiveScopeSwitchShouldSucceedWithSessionOnlyAuthentication`（integration） | 后端集成测试 job（PR/main） |
| M2 仅 Bearer（JWT 显式 type） | `TenantContextFilterTest.*Bearer*explicit*`（unit） | 后端单测 job（PR/main） |
| M3 仅 Bearer（JWT 未显式 type） | `TenantContextFilterTest.shouldUseSessionPairedScopeWhenBearerOmitsActiveScopeType`、`shouldRejectOrphanBearerActiveScopeIdWithoutType_with401`（unit） | 后端单测 job（PR/main） |
| M4 Bearer + Session 并存且一致 | `DefaultSecurityConfigUserEndpointIntegrationTest.currentUserShouldAllowWhenBearerAndSessionScopeMatch`、`currentActiveScopeSwitchShouldSucceedWithM4BearerAndRequireTokenRefresh`（integration）；real-link：`e2e/real/active-scope-token-refresh.spec.ts` | Nightly real-link：`.github/workflows/verify-scheduling-real-e2e.yml` |
| M5 Bearer + Session 并存但冲突（fail-closed） | `TenantContextFilterTest.shouldReject401WhenBearerAndSessionActiveScopeConflict`、`shouldClearSessionSecurityContextOnBearerInvalidActiveScope`（unit/integration） | 后端单测 + 集成测试 job（PR/main） |

补充：
- real-link 用例 `e2e/real/active-scope-token-refresh.spec.ts` 必须从 **租户态身份**进入（`e2e/.auth/scheduling-tenant-user.json`），否则会因平台态缺失 `activeTenantId` 导致“用例前提不成立”的伪失败。

---

## 8. `UserController`：M4 **读** 与 M4 **写** 的正式契约（分口径）

> 前提：以下均在 **默认业务链**、且请求已通过 `TenantContextFilter`（含 M5 fail-closed）之后讨论。  
> **核心区分**：表 **§4** 的 M4 描述的是 **过滤器层** 的“Bearer + Session 成对一致”；**本节**描述的是 **user 控制面**上 **只读** 与 **会话状态写** 的差异——**二者不得混为一谈**。

### 8.1 正式语义：M4 在读接口上是什么

**端点：`GET /sys/users/current`（只读）**

| 维度 | 正式语义 |
| --- | --- |
| **契约类型** | 读当前用户快照；**不修改** Session 中的 active scope 键。 |
| **M4 含义** | 当过滤器判定 M4（Bearer 与 Session 对 `activeTenantId` + active scope **成对**一致）时，本接口**允许**返回 200；响应中 `activeTenantId` / `activeScopeType` / `activeScopeId` 与 `TenantContext` 一致。 |
| **主体** | Session 链：`SecurityUser`；Bearer 链：`JwtAuthenticationToken` 时仍受同一 `TenantContext` 约束。 |
| **permissionsVersion** | Session：`SecurityUser` 指纹；JWT：claim 优先，缺失时可由 `PermissionVersionService` 按当前 tenant+scope 权威解析（实现见 `CurrentActorResolver`）。 |
| **常见误解** | ❌ “M4 一致 = 所有 user 接口都同等处理” — **否**；只读不写 Session scope。 |

### 8.2 正式语义：M4 在写接口上是什么

**端点：`POST /sys/users/current/active-scope`（会话状态写）**

| 维度 | 正式语义 |
| --- | --- |
| **契约类型** | **变更** 服务端 Session 中冻结的 `AUTH_ACTIVE_SCOPE_*`，并刷新 `UserDetails`；**不修改** JWT 本身。 |
| **session-first（持久化）** | **权威落点始终是 HttpSession**（`SESSION_ACTIVE_SCOPE_TYPE_KEY` / `SESSION_ACTIVE_SCOPE_ID_KEY`）。无论请求是否带 Bearer，**写入目标**都是 Session；**不是**“只写 JWT”。 |
| **M4 含义** | 在过滤器已通过 M4 的前提下，**允许**带 Bearer/JWT 完成同一次写；控制器校验 `userId` 与 `sub` 解析用户一致（见 `bearer_subject_user_mismatch`）。 |
| **写后 refresh 契约** | 若请求为 **Bearer 或 JWT 主体**，成功响应 **`tokenRefreshRequired: true`**，并带 **`newActiveScopeType` / `newActiveScopeId`**（及 `tokenRefreshReason`）。含义：**Access Token 不会随本接口自动改写**；若 JWT **显式携带**过时的 `activeScopeType`/`activeScopeId`，**下一请求**可能落入 **M5**（通常 **401** `invalid_active_scope`），直至 **refresh / 重新签发** 与 Session 再次对齐。若 JWT **未显式带** `activeScopeType`，成对 scope 可能仍由 Session 推导（§5.2），行为见矩阵。 |
| **纯 Session 主体写** | 无 Bearer、主体为 `SecurityUser` 时，成功响应 **`tokenRefreshRequired: false`**（无“必须刷新 token”的机器信号）。 |

### 8.3 Cursor / Codex / 实现者的默认口径

1. **`/sys/users/current`**：按 **只读 + M4 读契约**（§8.1）理解；不要要求“写后 refresh”。
2. **`/sys/users/current/active-scope`**：默认理解为 **会话 active scope 变更写接口**；持久化 **session-first**（§8.2）；在 **M4** 下 **额外**支持 Bearer 写，且 **必须**消费 **`tokenRefreshRequired`** 规划后续 token 更新。
3. **禁止**：从“§4 表格里 M4 放行”直接推出“所有 `/sys/users/**` 都可无差别带 Bearer”——须先区分 **读 / 写** 与本节。

### 8.4 前端（Vue 控制面）对 `tokenRefreshRequired` 的收口行为

- **`switchActiveScope` API**：只返回 POST 响应体，**不得**在成功后再隐式串联 `GET /sys/users/current` 并丢弃 `tokenRefreshRequired`。
- **`tokenRefreshRequired !== true`**：可直接拉取当前用户并广播作用域变更（纯 Session 写等）。
- **`tokenRefreshRequired === true`**：须先 **`signinSilent`（OIDC）** 刷新 access token，再拉取当前用户并广播；renew **失败**时不得提示“作用域已切换”成功，不得假定后续 Bearer 请求已与 Session 对齐（否则下一跳可能 **M5**）；应提示用户**重新登录**，且不将页面当作新 scope 已生效。
- 实现参考：`tiny-oauth-server/src/main/webapp` 中 `api/user.ts` 的 `switchActiveScope`、`auth.ts` 的 `refreshTokenAfterActiveScopeSwitch`、`layouts/HeaderBar.vue` 的切换确认流。
- **自动化证据**：`layouts/HeaderBar.test.ts`（`confirmSwitchScope` 三条路径）；`api/user.test.ts` / `auth/auth.test.ts` 为 API 与 renew helper。
- **Isolated real-link 抽样**：`e2e/real/active-scope-token-refresh.spec.ts`（Playwright **chromium** + globalSetup 派生的 `scheduling-tenant` storageState：`e2e/.auth/scheduling-tenant-user.json`）：断言 `POST /sys/users/current/active-scope` 响应 `tokenRefreshRequired: true`、浏览器发出 **`/oauth2/authorize?...prompt=none...`**（silent renew）、切换 modal 关闭且无 warning、Bearer 调用 `GET /sys/users/current` 非 `invalid_active_scope`。并且在进入写链路前强断言：token claims 与 `/sys/users/current` body 均需包含 `activeTenantId`（且不得是 PLATFORM）。**残留边界**：未断言 JWT claims 内 `activeScope*` 与 Session 的逐项字节级一致；若 issuer 短时复用同一 `access_token` 字符串，以网络 + HTTP 200 为主证据（用例内 `console.warn` 提示）。

### 8.5 字段与错误码（摘要）

**`GET /sys/users/current`**

- 始终返回 `permissionsVersion`（可为 `null`）。

**`POST /sys/users/current/active-scope` 成功时**

- `tokenRefreshRequired`、`newActiveScopeType`、`newActiveScopeId`、`tokenRefreshReason`（Bearer/JWT 写时）。

**相关错误**

- `bearer_subject_user_mismatch`：主体 `userId` 与绑定用户不一致。  
- 历史码 `active_scope_switch_requires_session_principal`：**保留兼容**；当前主线以 §8.2 **M4 写 + refresh 信号**为准。

---

## 9. 当前审计结论

基于当前实现，tiny-platform 的正式口径不是简单的：

- A：只有 Bearer
- B：只有 Session
- C：Bearer + Session

而应该是：

1. 区分认证主体来源与 active scope 来源
2. 对 Bearer + Session 并存再拆成“一致”和“冲突”两类
3. 对 `prompt=none` / silent renew 单独按授权服务器链处理

一句话：

**tiny-platform 当前不是“JWT 与 Session 二选一”，而是“请求主体来源 + active scope 成对来源 + 冲突 fail-closed”的矩阵模型。**
