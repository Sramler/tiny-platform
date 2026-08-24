# Vue Web 启动与 Session 认证编排

## 最终边界

Vue Web 固定使用同源 `HttpOnly Session + CSRF`。浏览器不加载 OIDC client，不持有 access/refresh token，不注册 OAuth callback 或 silent-renew 页面，也不注入 `Authorization`。

CLI、移动端、第三方和服务间调用继续由后端 `/oauth2/**` 提供 OAuth2/OIDC Bearer；该协议能力不进入 Vue 构建产物。

## 启动顺序

```text
main.ts
  -> /bootstrap
  -> authBootstrap: GET /sys/users/current (credentials: include)
  -> securityBootstrap: GET /self/security/status
  -> permissionBootstrap: GET /sys/menus/tree
  -> 注册动态业务路由
  -> redirect 到净化后的内部目标
```

登录页、`/bootstrap`、TOTP 安全流程页和异常页属于启动守卫 bypass。业务页在 boot 未 ready 时统一回到 `/bootstrap?redirect=...`，不得在 router guard 内请求菜单或重建权限。

## 请求契约

- GET/HEAD/OPTIONS 携带 Session Cookie（`credentials: include`）。
- POST/PUT/PATCH/DELETE 先读取当前 Session 对应的 CSRF token，再携带 Cookie 与 CSRF header。
- Axios/fetch 包装器必须删除调用方误传的 `Authorization`。
- 401 清理前端运行态并回登录页；`stale_permissions` 不刷新 token、不静默重试。
- active scope 切换后重读 `/sys/users/current` 并重建菜单/权限运行态。

## 验证

```bash
bash tiny-oauth-server/scripts/verify-web-session-only-boundary.sh
cd tiny-oauth-server/src/main/webapp
npm run type-check
npm run test:unit
npm run build-only
```

真实联动默认执行 `bash tiny-oauth-server/scripts/verify-platform-local-dev-stack.sh`。
