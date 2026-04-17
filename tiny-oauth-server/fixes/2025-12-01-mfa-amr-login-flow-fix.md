## OAuth2 MFA `amr` 显示问题修复记录（思路 A 落地）

### 一、问题概述

- 现象：
  - 用户完成「用户名密码 + TOTP」登录流程后，Access Token / ID Token 中的 `amr` 仍然经常是 `["password"]`。
  - 登录链路中出现“先 OIDC 回调（`/callback`），再进入二次验证（`/self/security/totp-verify`）”的异常顺序。
- 目标：
  - 按 OIDC 企业规范实现 **“思路 A”**：
    - 只有在本次会话完成所有必需因子（PASSWORD + TOTP）后，才创建授权码和最终 Token。
    - Token 中的 `amr` 精确反映已完成因子，如 `["password", "totp"]`。
    - 登录顺序为：**登录 → TOTP → OIDC 回调 → 主页/原页面**。

---

### 二、根因分析

#### 1. 授权快照中 `completedFactors` 丢失 TOTP

- 触发点：Spring Authorization Server 将 `Authentication`（`MultiFactorAuthenticationToken`）序列化到 `oauth2_authorization` 表，再从 DB 反序列化。
- 发现：
  - 通过在 `MultiFactorAuthenticationTokenJackson3Deserializer` 与 `JwtTokenCustomizer` 中增加调试日志，发现 DB 中反序列化后的 `token.completedFactors` 只包含 `[PASSWORD]`。
  - Jackson 对 `EnumSet` 的默认序列化结构为  
    `["java.util.Collections$UnmodifiableSet", ["PASSWORD", "TOTP"]]`，最初的自定义反序列化逻辑没有正确处理这种嵌套结构。
- 结果：
  - 即使用户在本次会话中已经完成了 TOTP，DB 中保存的授权快照里仍然只有 PASSWORD。
  - `JwtTokenCustomizer` 按授权快照计算 `amr` 时，只能得到 `["password"]`。

#### 2. 授权对象在“只完成密码”阶段就被创建

- 旧流程（问题版本）大致为：
  1. 前端 `oidc-client-ts` 调用 `signinRedirect()`，浏览器跳转到  
     `/oauth2/authorize?...redirect_uri=http://localhost:5173/callback...`。
  2. Spring Authorization Server 处理授权端点，并在用户完成表单登录后立即创建授权码 / 授权快照。
  3. 登录成功 Handler `CustomLoginSuccessHandler` 再根据 `security.mfa.mode` 和用户状态决定是否跳转到 `/self/security/totp-verify`。
  4. 此时授权码和授权快照已经创建，快照里的认证状态只包含 PASSWORD，后续 `/oauth2/token` 使用的是这份「不含 TOTP」的授权数据。
- 结果：
  - 授权码、Access Token、ID Token 的 `amr` 都建立在“只完成密码”的上下文之上。
  - 即使后续完成 TOTP，本次授权对应的 `amr` 仍然是 `["password"]`。

#### 3. 前端路由守卫将 TOTP 页面误判为“需要 OIDC 认证”

- 路由配置（问题版本）：
  - `/self/security/totp-bind`、`/self/security/totp-verify` 没有显式设置 `meta.requiresAuth`，被默认视为「需要 OIDC 登录」。
- 路由守卫 `authGuard` 逻辑：
  - 当访问的路由 `requiresAuth !== false` 且 `isAuthenticated=false` 时，会调用 `authContext.login()`。
- 实际现象：
  1. 后端登录成功后重定向到 `/self/security/totp-verify?redirect=原始/oauth2/authorize...`。
  2. 前端路由守卫认为「用户未 OIDC 认证，但路由需要认证」，再次调用 `authContext.login()`。
  3. 触发一轮新的 OIDC 授权：
     - `/oauth2/authorize?...redirect_uri=/callback...`
     - `/callback?code=...&state=...` → `OidcCallback.vue` 显示“正在处理登录回调…”。
  4. 然后才再次回到 TOTP 页，造成“先回调，再二次验证”的错乱体验。

---

### 三、具体修复步骤

#### 步骤 1：修复 `MultiFactorAuthenticationToken` 的序列化/反序列化

- 文件：`MultiFactorAuthenticationToken.java`
  - 添加 `@JsonIgnoreProperties(ignoreUnknown = true)`，防止未知字段干扰反序列化。
  - 将 `credentials` 字段标记为 `@JsonIgnore`，避免被输出到前端或日志。
  - 提供 `@JsonCreator` 静态工厂方法 `createForJackson(...)`，专门用于 DB 中授权快照的反序列化。
  - 使用 `EnumSet<AuthenticationFactorType>` 表示 `completedFactors`，提供：
    - `getCompletedFactors()`、`hasCompletedFactor(...)`；
    - `promoteToFullyAuthenticated(...)` 用于从 PASSWORD 升级到 PASSWORD+TOTP。

- 文件：`MultiFactorAuthenticationTokenJackson3Deserializer.java`
  - 增强对 `completedFactors` 字段的解析：
    - 既能处理 `["PASSWORD", "TOTP"]`；
    - 也能处理 `["java.util.Collections$UnmodifiableSet", ["PASSWORD", "TOTP"]]` 这种 Jackson + JDK 组合产生的结构。
  - 增加详细日志输出：
    - 原始 JSON 结构；
    - 反序列化后的 `token.completedFactors`；
    - `details` 用于恢复 `SecurityUser`。

> 效果：新产生的授权对象在 DB round-trip 后，`completedFactors` 不再丢失 TOTP，`SecurityUser` 能正确从 `details` 恢复。

#### 步骤 2：在 `/oauth2/authorize` 之前强制执行 MFA 检查（思路 A 核心）

- 新增授权端点 MFA gate：`AuthorizationEndpointMfaAuthorizationManager` + `AuthorizationEndpointMfaEntryPoint`
  - 挂载在 Authorization Server 的授权规则与 missing-authority 入口点上，只作用于 `/oauth2/authorize`。
  - 基本流程：
    1. 获取 `SecurityContext` 当前认证对象：
       - 支持 `MultiFactorAuthenticationToken` 的“部分认证”（`isAuthenticated=false` 但 `completedFactors` 中已包含 PASSWORD）。
    2. 通过 `SecurityService.getSecurityStatus(user)` 获取：
       - `totpBound`、`totpActivated`、`forceMfa`、`requireTotp`。
    3. 策略：
       - **强制 MFA 模式（REQUIRED）且用户未绑定/未激活 TOTP**  
         → 重定向到 TOTP 绑定页：  
           `/self/security/totp-bind?redirect=原始/oauth2/authorize...`
       - **本次会话不需要 TOTP（NONE 或 OPTIONAL+未绑定）**  
         → 直接放行 `/oauth2/authorize`；
       - **本次会话需要 TOTP 且当前认证（`MultiFactorAuthenticationToken`）已包含 TOTP 因子**  
         → 放行 `/oauth2/authorize` 创建授权码；
       - **本次会话需要 TOTP 且当前认证缺少 TOTP 因子**  
         → 重定向到 TOTP 验证页：  
           `/self/security/totp-verify?redirect=原始/oauth2/authorize...`，并阻止授权码创建。

> 效果：任何会被用来创建授权码 / Token 的 `/oauth2/authorize` 请求，都只能在「本次会话已完成必需因子」的前提下通过，保证后续 `amr` 计算有完整的上下文。

#### 步骤 3：通过 Session Manager 在 TOTP 验证成功后升级认证状态

- 文件：`MultiFactorAuthenticationSessionManager.java`
  - 对外方法：`promoteToFullyAuthenticated(User user, HttpServletRequest request, HttpServletResponse response)`
  - 核心逻辑：
    - 如果当前认证是 `MultiFactorAuthenticationToken mfaToken`：
      - 调用 `mfaToken.promoteToFullyAuthenticated(authorities)`：
        - 扩展 `completedFactors` 至少包含 `PASSWORD` + `TOTP`；
        - 设置 `authenticated=true`；
        - 继承 `details`（包含 `SecurityUser`）。
      - 通过 `SecurityContextHolder` 和 `SecurityContextRepository` 将新的认证对象写回 session。

- 文件：`SecurityController.java`
  - 在 `checkTotpForm`（`/self/security/totp/check-form`）中：
    - TOTP 验证通过后调用：  
      `sessionManager.promoteToFullyAuthenticated(user, request, response);`
    - 然后重定向回 `redirect` 参数指向的原始 `/oauth2/authorize?...`。

> 效果：当用户通过 TOTP 验证后，session 中的认证对象已经是「PASSWORD+TOTP」的完全认证，下一次访问 `/oauth2/authorize` 时，授权端点 MFA gate 会放行创建授权码/授权快照，后续 `/oauth2/token` 再生成 Token，`amr` 就会是 `["password","totp"]`。

#### 步骤 4：前端路由层面避免二次触发 OIDC 授权

- 文件：`src/router/index.ts`
  - 修复前：
    - `TotpBind` / `TotpVerify` 未显式设置 `meta.requiresAuth`，被默认视为需要 OIDC 认证。
    - 路由守卫 `authGuard` 在 `isAuthenticated=false` 时，会调用 `authContext.login()`，触发一轮新的 OIDC 授权（`/oauth2/authorize` → `/callback`）。
  - 修复后：

    ```ts
    {
      path: '/self/security/totp-bind',
      name: 'TotpBind',
      component: TotpBind,
      meta: { title: '绑定二步验证', requiresAuth: false },
    },
    {
      path: '/self/security/totp-verify',
      name: 'TotpVerify',
      component: TotpVerify,
      meta: { title: '二步验证', requiresAuth: false },
    },
    ```

- 文件：`src/auth/auth.ts` / `OidcCallback.vue`
  - 保留现有 OIDC 流程：
    - `authContext.login()` 只在真正需要 OIDC 登录时触发；
    - `OidcCallback.vue` 仅在 URL 带 `code` + `state` 时处理回调。

> 效果：访问 `/self/security/totp-verify` / `/self/security/totp-bind` 时，前端不再触发新的 OIDC 登录，只依赖后端 session。用户体验顺序恢复为：  
> **表单登录 → TOTP 验证 → `/oauth2/authorize` → `/callback` → 主页 / 原页面**。

---

### 四、验证结果

1. 手动执行完整登录流程（`security.mfa.mode=OPTIONAL/REQUIRED`，用户已绑定并激活 TOTP）：
   - 表单登录 → TOTP 验证页 → 验证通过 → OIDC 回调 → 主页。
2. 检查日志：
   - `MultiFactorAuthenticationTokenJackson3Deserializer` 输出：`token.completedFactors=[PASSWORD, TOTP]`。
   - `JwtTokenCustomizer` 输出：`completedFactors=[PASSWORD, TOTP]`，`amr=["password","totp"]`。
3. 解码新的 Access Token / ID Token：
   - `amr` 字段为：`["password","totp"]`，与当前会话实际完成的认证因子一致。

---

### 五、小结

- 原因本质上是**「授权快照在只完成密码时就被创建 + `completedFactors` 在 DB 往返中丢失 TOTP + 前端在 TOTP 页重复触发 OIDC 登录」**三者叠加。
- 通过：
  1. 修复 `MultiFactorAuthenticationToken` 的序列化/反序列化；
  2. 在 `/oauth2/authorize` 前强制执行 MFA 检查（授权端点 MFA gate）；
  3. 在 TOTP 成功后通过 `MultiFactorAuthenticationSessionManager` 升级当前认证，并写回 session；
  4. 前端将 TOTP 绑定/验证页标记为 `requiresAuth: false`，避免重复 OIDC 授权；
- 最终实现了思路 A 的目标：**只有在本次会话完成所有必需的 MFA 因子之后，才创建授权码和 Token，Token 中的 `amr` 与实际认证上下文完全一致。**
