# Auth Real-Link E2E Example

```text
请为 tiny-platform 的认证改动补 real-link E2E，并遵守：
- docs/TINY_PLATFORM_TESTING_PLAYBOOK.md
- .agent/src/rules/50-testing.rules.md
- .agent/src/rules/90-tiny-platform.rules.md
- .agent/src/rules/91-tiny-platform-auth.rules.md

变更点：
- 登录 / 登出 / MFA / OIDC / Session-JWT 切换相关改动

风险点：
- 真实认证链路
- 权限拒绝
- 跨租户拒绝
- client 配置错误

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

断言：
- 登录成功后的用户可见结果
- 登录失败或拒绝提示
- MFA 二次校验
- 跨租户拒绝
- Session/JWT/OIDC 中本次变更涉及的真实结果

交付：
- 测试文件清单
- 执行命令
- 还未覆盖的剩余风险
```
