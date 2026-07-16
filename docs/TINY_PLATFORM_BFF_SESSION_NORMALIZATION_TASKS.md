# tiny-platform BFF / HttpOnly Session 规范化任务清单

## 1. 目标

Web 浏览器统一采用同源 HttpOnly Session，不直接持有或发送 access token / refresh token；CLI、第三方客户端和服务间调用继续使用 Bearer JWT。外部接口保持真实领域路径，不增加统一 `/api` 前缀。

目标路由：

```text
/                         Vue 页面与前端路由
/login                    Vue 登录页（GET）
/auth/login               Session 登录处理（POST）
/auth/logout              Session 退出（POST）
/csrf                     CSRF token（GET）
/sys/**                   系统管理接口
/self/**                  当前用户接口
/scheduling/**            调度接口
/workflow/**              工作流接口
/oauth2/**                非浏览器 OAuth2 协议
/.well-known/**            OIDC 元数据
```

## 2. 不可破坏的边界

- 不增加 `/api` 伪前缀，不修改现有业务接口的领域路径。
- Web Session-only 不得静默回退 Bearer。
- Session Cookie 必须 HttpOnly；生产环境必须 Secure。
- POST、PUT、PATCH、DELETE 必须通过 CSRF 校验。
- `/sys/users/current` 只绕过普通业务 `api_endpoint` 载体判断，仍必须经过认证、用户状态和作用域校验。
- 业务接口继续 fail-closed；缺少 `api_endpoint` 注册时不得通过扩大过滤器豁免解决。
- PLATFORM、TENANT、ORG、DEPT 作用域及租户隔离语义不得弱化。

## 3. 执行任务

### BFF-01：登录与退出端点规范化

范围：

- `POST /login` 迁移为 `POST /auth/login`。
- Session 退出统一为 `POST /auth/logout`。
- `GET /login` 只表示 Vue 登录页面。
- 同步 Spring Security、CSRF matcher、Login.vue、认证工具、测试和文档。

验收：

- `/auth/login` 缺少 CSRF 时为 403；合法登录可建立并轮换 Session。
- `/auth/logout` 缺少 CSRF 时为 403；成功退出后旧 Session 不可访问受保护接口。
- `GET /login` 不被后端表单处理端点占用。

### BFF-02：无 `/api` 前缀的同源请求

范围：

- Web 默认 API base URL 使用当前 origin，不再默认 `http://localhost:9000`。
- Vite 按 `/sys`、`/self`、`/scheduling`、`/workflow`、`/auth`、`/csrf`、`/oauth2`、`/.well-known` 代理到后端。
- 生产网关沿用相同真实路径分流。

验收：

- 浏览器请求 URL 与前端页面同源。
- Web 请求带 Cookie、不带 Authorization。
- 本地开发不依赖 credentialed CORS 才能完成主链路。

### BFF-03：Session Cookie 配置收口

范围：

- 将错误的 `spring.servlet.session.cookie` 迁移到 `server.servlet.session.cookie`。
- 显式配置 Cookie name、path、HttpOnly、SameSite、Secure 和 Session timeout。
- dev/e2e 允许 HTTP；prod 强制 Secure。

验收：

- 配置绑定测试通过。
- 真实浏览器中 `JSESSIONID` 为 HttpOnly，且生产配置为 Secure。

### BFF-04：real-link 测试 Session 化

范围：

- `generate-auth-state.mjs` 以 `/sys/users/current` 200 判断认证成功。
- storageState 保存 HttpOnly Session Cookie。
- 移除生成阶段对 `oidc.user:*`、access token、JWT claim 和 silent renew 的硬依赖。

验收：

- 完整 real-link global setup 可在 Session-only 下生成登录态。
- storageState 不包含 access token / refresh token。

### BFF-05：前端身份模型去伪 JWT

范围：

- 建立 `SessionPrincipal` / `RuntimeIdentitySnapshot`。
- 权限、角色、租户和 active scope 直接读取当前用户快照。
- 删除 `session.<payload>.ui-only` 兼容 token。

验收：

- Session-only 模式不创建或解析任何伪 JWT。
- 菜单、路由守卫、权限组件和 active scope 正常工作。

### BFF-06：Web 与 Bearer 客户端分轨

范围：

- Web 固定 Session + CSRF。
- CLI、第三方和服务间调用保留 Bearer JWT。
- 删除 Web silent renew、refresh token 和 token debug 的运行时依赖；兼容入口必须显式启用。

验收：

- Web Session 失效直接回登录，不执行 silent retry 或 refresh token。
- Bearer API 回归测试继续通过。

### BFF-07：API 载体和首页权限治理

范围：

- 建立 Controller 映射与 `api_endpoint` 的漂移检查。
- 补齐 `GET /sys/tenants` 等缺失业务载体。
- 首页按权限快照决定是否发起监控类请求，避免预期内 403。

验收：

- 已纳管业务接口不存在“角色有权限但接口未注册”的漂移。
- 平台管理员登录首页无预期外 401/403。

### BFF-08：集群与生产安全

范围：

- 生产采用 Spring Session Redis/JDBC，或在部署设计中明确单实例限制。
- 验证 Session fixation 防护、超时、全局退出和强制失效。
- 网关配置 HTTPS、HSTS、CSP、`X-Forwarded-*`、登录/MFA 限流与认证响应禁缓存。

验收：

- 多节点切换不丢 Session。
- 用户禁用、密码重置、TOTP 变更可强制终止旧 Session。

存储选择当前态：

```yaml
tiny:
  session:
    store-type: ${TINY_SESSION_STORE_TYPE:memory} # memory | jdbc | redis
```

- `memory`：Tomcat 原生内存 Session，供 dev/test 单实例使用。
- `jdbc`：Spring Session JDBC，表结构由 Liquibase `189-spring-session-jdbc.yaml` 管理。
- `redis`：Spring Session Data Redis，namespace 默认 `tiny-platform:session`。
- `prod` 默认 `jdbc`；若显式选择 `memory`，启动策略直接失败。
- JDBC/Redis 继续使用 `JSESSIONID`，保持 HttpOnly、path、Secure 与 SameSite 契约一致。

## 4. 验证矩阵

每阶段至少执行：

```text
前端：type-check + 认证/请求定向 Vitest
后端：SecurityFilterChain + CSRF + current-user + API requirement 定向测试
规则：.agent build + validate
真实链路：MySQL + Spring Boot + Vite + Playwright
```

最终真实链路：

```text
Vue /login
  → POST /auth/login
  → TOTP（需要时）
  → HttpOnly JSESSIONID
  → GET /self/security/status
  → GET /sys/users/current
  → GET /sys/menus/tree
  → 受权限保护的业务接口
  → POST /auth/logout
  → 旧 Session 访问失败
```

## 5. 当前进度

- [x] Web 默认 Session-only，请求不发送 Bearer。
- [x] unsafe method 使用 CSRF。
- [x] `/sys/users/current` 返回 authorities / permissions / roleCodes。
- [x] `/sys/users/current` 作为认证基础设施端点精确绕过普通业务 API 载体过滤。
- [x] 真实 MySQL 浏览器链路已验证登录、TOTP、Session、current-user 和菜单树。
- [x] BFF-01 登录/退出端点规范化。
- [x] BFF-02 同源相对请求和真实路径代理。
- [x] BFF-03 Cookie 配置收口。
- [x] BFF-04 real-link Session 化；global setup 使用 HttpOnly `JSESSIONID` + CSRF 完成派生租户初始化，storageState 不再依赖 OIDC token。
- [x] BFF-05 去伪 JWT；Session 身份直接使用 `/sys/users/current` 内存快照，`access_token` 为空，不再生成 `session.<payload>.ui-only`。
- [x] BFF-06 Web/Bearer 默认分轨；Web 默认 Session-only，只有显式设置 `VITE_AUTH_SESSION_ONLY=false` 才进入 OIDC/Bearer 兼容轨。
- [ ] BFF-07 API 载体与首页治理（已补齐平台租户控制面载体并完成真实 Liquibase 验证；Controller 映射漂移门禁待补）。
- [ ] BFF-08 集群和生产安全（memory/jdbc/redis 参数化、prod memory 禁用、JDBC/Redis 单节点真实链路已完成；多节点切换及强制失效联动待验证）。

## 6. 2026-07-16 实际验证记录

- `npm run type-check`：通过。
- 认证/JWT 快照/请求/Login 定向 Vitest：4 个测试文件、41 个测试通过。
- Spring Boot `dev` profile + 本地 MySQL：Liquibase changeset 188 实际执行成功。
- Playwright real-link `platform-vue-login.spec.ts`：1/1 通过；链路覆盖 `/auth/login`、TOTP、HttpOnly Session、`/sys/users/current`、`/sys/menus/tree` 及派生租户业务初始化。
- real-link 派生数据治理 teardown：通过，测试生成租户及 membership 已清理。
- Maven compile：通过，Spring Session JDBC/Redis 模块在 Boot 4.1 snapshot 下编译成功。
- Session store 生产策略测试：3/3 通过。
- JDBC Session 真实启动：Liquibase 189 成功创建 `SPRING_SESSION` / `SPRING_SESSION_ATTRIBUTES`。
- JDBC Session Playwright：1/1 通过；真实登录后确认数据库写入 4 条 Session、12 条属性记录，随后已按 `e2e_admin` 精确清理。
- Redis Session 真实启动：通过。应用入口原有 Redis 自动配置排除规则已通过 Session `redis` 分支的条件导入适配，不影响 memory/JDBC。
- Redis Session Playwright：1/1 通过，覆盖登录、TOTP、current-user、菜单和业务初始化。
- Redis 持久化检查：产生 4 个 `tiny-platform:session:sessions:*` hash，TTL 为 1742–1772 秒，与 30 分钟 Session timeout 一致；测试 key 已精确清理。
- Redis 多节点切换尚未验证；本机 Homebrew LaunchAgent 与旧 `redis.conf` 存在环境问题，本次使用无附加模块的临时标准 Redis 8.8 实例完成验收。
