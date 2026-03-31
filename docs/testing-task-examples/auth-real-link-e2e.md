# Auth Real-Link E2E Example

```text
请为 tiny-platform 的认证改动补 real-link E2E，并遵守：
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

变更点：
- 登录 / 登出 / MFA / OIDC / Session-JWT 切换相关改动
- Session / Bearer 来源裁决、active scope 成对解析、`prompt=none` / silent renew 相关改动

风险点：
- 真实认证链路
- 权限拒绝
- 跨租户拒绝
- client 配置错误
- Bearer + Session 并存冲突
- `prompt=none` 被误判成普通表单登录失败

测试层级：
- 集成测试
- isolated real-link E2E

身份矩阵：
- e2e-user
- e2e-tenant-admin
- e2e-deny
- e2e-cross-tenant-deny
- e2e-mfa-user

禁止：
- 不要伪造 JWT
- 不要手写 cookie
- 不要手写 storageState 冒充登录

E2E 必填：
- 身份来源
- 测试租户 / 测试 client
- seed/reset
- 允许 mock 的边界
- 对照 `docs/TINY_PLATFORM_SESSION_BEARER_AUTH_MATRIX.md` 标注本次覆盖的是 M0~M5 哪几类

断言：
- 登录成功后的用户可见结果
- 登录失败或拒绝提示
- MFA 二次校验
- 跨租户拒绝
- Session/JWT/OIDC 中本次变更涉及的真实结果
- 若涉及 OIDC 静默续期：明确断言 `prompt=none` 未登录时返回 `login_required` 到 redirect_uri，而不是 `/login?error=true...`
- 若涉及 Bearer + Session 并存：明确断言“一致时可通过、冲突时 fail-closed”

交付：
- 测试文件清单
- 执行命令
- 还未覆盖的剩余风险

补充（active-scope + `tokenRefreshRequired`）：
- **API + auth**：`src/api/user.test.ts`、`src/auth/auth.test.ts`。
- **HeaderBar 编排**：`src/layouts/HeaderBar.test.ts` 直接断言 `confirmSwitchScope` 三条路径（无 renew / renew 成功顺序 / renew 失败不 success、不广播）。
- **Real-link 抽样**：`e2e/real/active-scope-token-refresh.spec.ts`，project **`chromium`**，身份为 **globalSetup 派生租户态身份**（`e2e/.auth/scheduling-tenant-user.json`）。用例进入前强断言：access_token claims 与 `GET /sys/users/current` body 必须同时包含 `activeTenantId`（且不得是 PLATFORM）。前置：本机 `9000`+`5173`、`.env.e2e.local`、OIDC `vue-client` 与 `silent-renew.html`。
- 执行示例（在 `tiny-oauth-server/src/main/webapp`）：
  `npx playwright test -c playwright.real.config.ts --project=chromium e2e/real/active-scope-token-refresh.spec.ts`
```
