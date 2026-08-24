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
/platform/**              平台治理接口（与部分 Vue 深链重名，按请求类型分流）
/dict/**                  租户字典接口
/demo/**                  演示业务接口
/metrics/**               治理指标接口
/scheduling/**            调度接口
/process/**               工作流运行接口
/workflow/**              工作流兼容接口
/export/**                导出接口（下载子路径保持后端直达）
/idempotent/**            幂等兼容接口
/oauth2/**                非浏览器 OAuth2 协议
/connect/**               OIDC 会话协议接口
/.well-known/**            OIDC 元数据
```

## 2. 不可破坏的边界

- 不增加 `/api` 伪前缀，不修改现有业务接口的领域路径。
- Web Session-only 不得静默回退 Bearer。
- Session Cookie 必须 HttpOnly；生产环境必须 Secure。
- POST、PUT、PATCH、DELETE 必须通过 CSRF 校验。
- Spring Security SPA CSRF 同时兼容 Cookie plain token 与 `/csrf` envelope 的 XOR token；前端不得跨 Session 永久缓存 token。
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
- Vite 按 `/sys`、`/self`、`/platform`、`/dict`、`/demo`、`/metrics`、`/scheduling`、`/workflow`、`/process`、`/export`、`/idempotent`、`/auth`、`/csrf`、`/oauth2`、`/connect`、`/.well-known` 代理到后端。
- `/platform`、`/scheduling`、`/process`、`/export` 同时存在 Vue 深链与后端 API；仅已知 SPA 路径的 GET/HEAD 文档导航回退 `index.html`，fetch/JSON、写请求和下载/API 子路径继续代理后端。
- 生产网关沿用相同真实路径分流。

验收：

- 浏览器请求 URL 与前端页面同源。
- Web 请求带 Cookie、不带 Authorization。
- SPA 冲突深链可直接刷新，真实 API 与下载导航不会被 `index.html` 回退吞掉。
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
- [x] BFF-07 API 载体与首页治理（全仓 Controller 映射漂移门禁及 202–214 载体迁移已落地；本地 full-chain、一次性 fresh DB、existing MySQL SpringLiquibase 与真实浏览器回归均全绿；代码提交 `af9c340` 为 12/12，最终交付 HEAD `ee95964` 为 11/11）。
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

## 6.1 2026-08-01 API 载体闭包验证

- `verify-api-endpoint-controller-drift.sh` 在真实 MySQL 上启动 Spring 上下文，比较实际 MVC Controller 的 method/template 与当前 scope 的 `api_endpoint`、主 permission 和 requirement。
- changeset 210 显式闭合 `SchedulingController` 路由；changeset 211 闭合用户、组织、数据范围、角色兼容、字典控制面、导出与幂等治理路由，均未自动扩大角色授权。
- changeset 205a 在 196/197 前显式生成 fresh DB 缺失的 PLATFORM 基础读取权限及 `/system`、`/system/resource` 菜单；changeset 213 补齐平台租户用户列表和租户 Session active-scope 写载体，active-scope 使用当前规范权限 `system:user:edit`，不复活历史 `system:user:update`。
- changeset 214 修复 fresh DB 的历史迁移时序缺口：150 复制 PLATFORM `ui_action` 时 `/system/role`、`/system/menu` 尚不存在，205a 又只显式建立 `/system/resource`，导致角色页动态路由未注册、组件未挂载且业务接口根本不会发出。214 现显式闭合两页 `menu + requirement + ui_action + requirement + ROLE_PLATFORM_ADMIN`，不将“路由未找到”误判为 token/Session 或 API 403。
- 210/211 已在 existing DB 由 SpringLiquibase 实际执行；漂移集成测试 1/1 通过，当前未精确豁免的受保护 Controller 映射为 0 缺口。
- 一次性空库已从零执行 197 个 changeset，205a/206/212/213/214 均实际落库，Spring 上下文及 Controller 漂移门禁 1/1 通过；214 再次启动为 0 增量。existing DB 也已增量执行 214 并保持零漂移。
- 214 后在隔离 fresh DB 以真实 Chromium 完成平台密码 + TOTP + HttpOnly Session 登录；`/system/role` 动态路由、角色表格、新建/编辑操作均可见，`/sys/users/current`、`/sys/resources/runtime/ui-actions?pagePath=/system/role`、`/sys/roles` 全部返回 200，浏览器存储中无 access/refresh token，JS 仅可见 `XSRF-TOKEN`，浏览器控制台 0 error。
- `ProcessDisabledFallbackController` 是 Camunda 关闭时返回 503 的占位 envelope，不生成权限载体；运行态字典 lookup、当前用户头像/登录历史和 process health 仅按精确 method/path 作为已认证基础设施豁免。
- 漂移门禁已接入 Web、Scheduling 与 Scheduling cross-tenant 三条 real E2E workflow；本地 full-chain、fresh DB CI 及最终交付 HEAD 矩阵均已全绿，BFF-07 已收口。

## 6.2 2026-08-01 Session/CSRF 与真实 E2E 复盘

- Spring Boot 4.1.1 / Spring Security 7.1.1 的 SPA CSRF 链现已显式接入 request handler，按 Header 值选择 plain Cookie token 或 XOR envelope token 解析，CSRF 防护范围保持不变。
- 前端 `ensureCsrfToken` 不再永久缓存 token，只合并同一时刻的并发获取，避免登录、MFA、Session fixation 或登出轮换后发送旧 token。
- 匿名 `GET /sys/users/current` 仅在没有 SecurityContext、Bearer 和活动租户 Session 时交还 Spring Security，登出探测恢复为 401/403；已认证请求仍执行完整租户校验。
- changeset 212 为 `ROLE_TENANT_ADMIN` 模板绑定调度细粒度权限；real-link bootstrap 同步补齐既有/派生租户身份，避免只有 `scheduling:*` 而细粒度 requirement fail-closed。
- 双租户 setup 改为比较派生后的实际主租户与 tenant B，配置相同时 fail-fast；动态创建租户使用每次运行唯一的幂等键，初始管理员用户名在 20 位内保留唯一后缀。
- 平台治理、租户生命周期、Session 管理和租户向导在用例开始前显式建立各自真实登录 Session，避免长套件中权限版本变化使 global setup 的旧 Session 失效。
- `user_session` 首次并发登记改为失败事务之外重读唯一键胜出记录，消除同一 Session 并发首请求触发 `session_id` 唯一约束 500 的竞争窗口。
- 最终本地回归：Playwright real-link 30/30、零跳过；Vitest 127 个文件/651 项；Maven 1361 项、0 失败、0 错误（1 项条件跳过）；前端 type-check/build、默认本地全栈门禁及真实 API 载体漂移门禁均通过。
- GitHub 首轮回归暴露 Axios 1.15 干净依赖树的 `AxiosResponseResult` 类型包装，以及 fresh DB 不具备存量 PLATFORM 模板行的顺序假设；请求封装已在响应拦截器边界显式收口为 `Promise<T>`，迁移假设已由 205a/213 和 fresh-DB 门禁修复。

## 7. 问题复盘与防复发规则

### 7.1 根因链

本次 Token 切换为 Session 后出现的路由 403，并不是 Session Cookie 本身改变了业务权限。真实链路是：浏览器不再用 Bearer 绕过历史差异后，页面进入时并发调用的部分调度接口没有形成完整 `api_endpoint` 载体闭包；部分 migration 又在 permission 创建前执行关联插入，SQL 合法但写入 0 行；同时通配权限与具体权限被误认为天然蕴含，最终由统一守卫以 `api_endpoint requirement denied` 拒绝。前端全局错误处理把任一后台请求的 403 转成 `/exception/403`，因此表象像“修改 token 导致路由不能访问”。

另一个放大因素是验证时序：本地后端可能仍运行旧 commit、旧 `target/classes` 或启动期缓存，修复数据库后未重启会继续复现旧 403；串行 E2E 在首个失败后跳过后续场景，也会让“修好一个接口”被误报为“全量完成”。

### 7.2 固定诊断顺序

1. 记录浏览器实际失败的 HTTP method/path、`traceId` 与是否跳转 `/exception/403`，不要先修改 token。
2. 查看后端同一 `traceId`：`URI_TEMPLATE_NOT_MATCHED`/`candidateCount=0` 修 method/template 载体；载体匹配但 requirement 不满足才检查 permission/role/scope。
3. 查询 `api_endpoint`、requirement、permission、role binding，并确认 migration 顺序没有产生 0 行关联。
4. 盘点目标页面全部请求闭包，包括 bootstrap、列表、统计、历史、详情、弹窗预载、轮询和所有动作接口；一次补齐并增加漂移/回归测试。
5. 重启或显式失效缓存，确认进程 commit/profile/changelog 后，再从真实登录开始运行完整本地 full-chain；随后在 fresh DB Nightly 从头重跑。

### 7.3 完成定义

- 浏览器业务请求同源、携带 HttpOnly Session Cookie、不带 Authorization，storageState 不含 access/refresh token。
- unsafe method 通过 CSRF；登出后旧 Session 失效。
- 页面及其全部 API 依赖无预期外 401/403，允许路径与拒绝路径均有证据。
- fresh DB、既有库升级、现有租户回填、late-created tenant 克隆均验证通过。
- 本地真实 E2E 与 GitHub Nightly/full-chain 均为全量 green；任一未执行、skipped、exit 2 或红灯都必须明确标为缺口。

### 7.4 2026-07-17 认证审计遗留 403

- 现象：`GET /sys/audit/authentication/summary` 返回 `api_endpoint requirement denied` 并跳转 `/exception/403`。
- 根因：`AuthenticationAuditController` 已存在 list/summary/export 三个映射，但历史初始化只维护菜单与 ui_action，未创建对应 `api_endpoint`；既有 real-controller 守卫测试也只模拟 list，未覆盖 summary 页面依赖。
- 修复：changeset 202 首次补齐 list/summary/export；随后以不可改已执行 changeset 为前提追加 changeset 205，按 scope + method + URI 校正存量载体、权限和 requirement。认证审计 list/summary/export 均补齐独立允许/拒绝 real-controller 测试。
- 系统性门禁：`verify-api-endpoint-controller-drift.sh` 已比较实际 Controller 映射与真实数据库载体，并接入 real E2E CI；后续新增映射若未补载体或精确基础设施豁免将直接失败。

### 7.5 2026-07-17 授权审计遗留 403

- 现象：认证审计修复后切换 `/system/audit/authorization`，`GET /sys/audit/authorization` 继续被统一守卫拒绝。
- 根因：授权审计 Controller 与认证审计具有相同历史缺口，菜单、ui_action 和方法守卫已经存在，但 list/summary/export 及两个历史只读查询均没有 `api_endpoint` 初始化记录。
- 修复：changeset 203 首次补齐五个 GET 载体；追加 changeset 205 补齐高敏感 `DELETE /sys/audit/authorization/purge`，并为 PLATFORM 模板及所有现有 TENANT scope 建立五类审计权限主数据、九个精确 endpoint 和唯一 requirement。该 changeset 不给角色自动授予审计权限，也不重新启用既有 disabled permission。授权审计 list/summary/export/by-event-type/by-user/{userId}/purge 均覆盖独立允许/拒绝 real-controller 测试。

### 7.6 2026-07-19 资源管理表格与操作列遗留

- 现象：资源名称/标题列文本互相覆盖或被挤成两行，操作列为空；仅修改列宽无法解释操作按钮消失。
- 根因：表格长文本列没有稳定宽度与省略策略，形成视觉重影；同时平台管理员虽能读取资源树，但四项 `ui_action`、五个写接口及其 requirement/role binding 没有作为同一个页面业务闭包验证，按钮因此被运行时载体过滤。
- 修复：资源名称、标题、路由、URI 和权限列采用稳定宽度与 ellipsis，操作列固定在右侧；changeset 204 先补平台管理员权限绑定，changeset 206 闭合四项 PLATFORM `ui_action` 与四个 CRUD endpoint，已执行 206 不再改写，changeset 207 单独补齐遗漏的 `PUT /sys/resources/{id}/sort`。
- 数据门禁：`verify-platform-template-row-counts.sh` 现在要求资源动作/requirement 为 4/4、写 endpoint/requirement 为 5/5、平台管理员绑定为 4/4；同时要求认证与授权审计九个 endpoint 在 PLATFORM + 全部现有 TENANT scope 中均为唯一精确 requirement。

### 7.7 2026-08-24 fresh DB 资源树无限递归

- 现象：fresh DB 上平台角色页已恢复，但 webapp real-link 最后一项仍在 `/system/resource` 超时；`/sys/resources/runtime/ui-actions` 已响应，`/sys/resources/tree` 始终不返回，后端日志重复停留在 `ResourceServiceImpl.populateChildrenRecursively`。
- 根因：拆分后的 `menu`、`ui_action`、`api_endpoint` 各自使用独立主键序列，数值 ID 可以相同。历史聚合树对所有 DTO 都继续按裸 ID 查询子节点，按钮 ID 一旦等于某个菜单 ID，就会被再次当作菜单 parent ID，最终把同一按钮反复挂到自己下面。存量库因序列已错开而未稳定复现，fresh DB 从小 ID 起步后必现。
- 修复：资源树只允许 `directory/menu` 递归；`ui_action/api_endpoint` 固定为叶子；菜单递归携带祖先 ID 集，遇到异常环时记录结构化 warning 并截断，不让一个坏节点拖垮整个接口。
- 防复发：单测显式构造“按钮 ID = 父菜单 ID”，并断言按钮不会触发额外 carrier child 查询；授权规则明确载体类型是聚合节点身份的一部分，禁止跨表按裸 ID 推导父子关系。
- 验证：`ResourceServiceImplTest` 34/34 通过；默认本地全栈门禁通过；真实 MySQL + Spring Boot + Vite 的定向 `session-management-pages.spec.ts` 1/1 及与 GitHub webapp real-link 一致的全套 15/15 均通过，覆盖资源、认证审计和授权审计深链、无 Bearer、无 401/403、浏览器无 token storage；派生数据 teardown 后无残留。
- GitHub 收口：提交 `af9c340` 的 11 条手动工作流与 push 自动触发的 migration smoke 共 12/12 全绿；其中 webapp real-link 15/15 通过，确认 fresh DB `/sys/resources/tree` 不再递归挂死，BFF-07 达到本节完成定义。

### 7.8 2026-08-24 验证编排与证据可诊断性

- 多 API 等待：本轮 `session-management-pages.spec.ts` 同时等待 runtime ui-actions 与资源树，最初只显示整条 `page.waitForResponse` 超时，无法直接指出缺少哪个响应。后续规则要求每个 method/path 具有独立等待边界，并在失败时区分请求未发出、未响应与非 2xx，避免用 120 秒总超时掩盖真实挂点。
- 服务生命周期：默认本地全栈门禁会在退出时清理自己启动的 Vite/后端；门禁通过后再以 `E2E_SKIP_WEBSERVER=true` 启动 Playwright 会得到 `localhost:5173 connection refused`。该现象属于编排前置，不是 Session 或业务失败；本轮改由 Playwright 自行托管服务并重新 readiness 后，定向 1/1 与全套 15/15 均通过。
- 双租户 preflight：本地 `.env.e2e.local` 的 tenant B 与派生后的实际主租户同为 `bench-1m-t`，global setup 按 fail-fast 规则拒绝伪跨租户验证；本轮使用隔离的 `bench-1m-b` 完成验证并由 teardown 清理。环境校验必须比较派生后的实际 code，且应在昂贵 seed/auth-state 准备前尽早执行。
- 失败日志：CI 只输出 backend log 尾部时，大量 `populateChildrenRecursively` 重复帧挤掉了异常头。Nightly 失败证据必须同时保留异常命中窗口和日志尾部，以便一次看清异常类型、请求路径与首个业务栈帧。
- SHA 证据：代码修复提交 `af9c340` 的 11 条手动矩阵加 push 自动 migration smoke 为 12/12 全绿；文档收口提交 `ee95964` 因 path filter 未产生额外自动 smoke，随后显式重跑 11 条规定矩阵并 11/11 全绿。两组证据分别对应代码承载 SHA 与最终交付 HEAD，不再混写。

### 7.9 2026-08-24 TOTP 首绑后 Session 强制失效误判

- 现象：真实浏览器在“首次绑定 TOTP -> 清理浏览器会话 -> 重新密码 + TOTP 登录”后，`/self/security/status` 间歇返回 `token_revoked: session token security state is outdated`；另一时序下会在 bootstrap 未完成时短暂返回 `missing_tenant`。
- 根因：旧实现用 `HttpSession.getCreationTime()` 对比 `tokenNotBefore`。密码 -> TOTP 是多阶段认证，Spring Security 的 `changeSessionId()` 只旋转 id，不把 Session 创建时间改成完整认证时间，因此创建时间不是可靠的强失效快照。
- 修复：登录成功时按 active scope 将当前 `tokenSecurityVersion` 写入 HttpSession；后续请求精确比对版本，不一致立即失效。旧 Session 在缺少快照时仍用 `tokenNotBefore` 做一次兼容检查，通过后补写快照。快照是可序列化 Session attribute，memory/JDBC/Redis 共用同一语义。
- E2E 同步收口：绑定后清理旧会话前先离开业务页停止并发请求；完整 TOTP 验证后以真实安全状态端点 200 作为 Session + active tenant 就绪条件，不再把“URL 已离开验证页”当作完成条件。
