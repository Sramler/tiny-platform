# Vue Web 环境配置

Vue Web 的认证模式不可配置：固定为同源 HttpOnly Session + CSRF。不得新增 OIDC authority/client/redirect/silent-renew 或 Session/Bearer 切换变量。

| 变量 | 用途 | 推荐值 |
|---|---|---|
| `VITE_APP_ENV` | 环境标识 | `dev` / `test` / `prod` |
| `VITE_API_BASE_URL` | 浏览器请求基址 | 生产和本地联动均为空，使用同源真实路径 |
| `VITE_DEV_BACKEND_TARGET` | Vite 服务端代理目标 | `http://localhost:9000` |
| `VITE_LOG_LEVEL` | 前端日志级别 | `debug` / `info` / `warn` / `error` / `none` |
| `VITE_ENABLE_CONSOLE_*` | 控制台日志开关 | 开发按需，生产关闭 |
| `VITE_ENABLE_PERSISTENT_LOG` | 浏览器持久日志 | 默认关闭 |

生产拓扑示例：前端为 `https://app.example.com/`，BFF 使用同一 origin 下的 `/auth/**`、`/sys/**`、`/process/**` 等真实领域路径；不增加 `/api` 伪前缀。

本地开发由 Vite proxy 在服务端把真实路径转发到 `VITE_DEV_BACKEND_TARGET`。浏览器看到的仍是 `http://localhost:5173/<真实路径>`，Cookie/CSRF 保持同源。

边界门禁：`bash tiny-oauth-server/scripts/verify-web-session-only-boundary.sh`。
