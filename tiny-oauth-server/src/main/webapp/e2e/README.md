## tiny-oauth-server E2E 测试矩阵（草稿）

> 本文件描述当前 E2E 用例的**等级、职责、身份来源、seed/reset、mock 边界**，用于对齐 `50-testing.rules.md` 与 `90-tiny-platform.rules.md`。

### 1. 等级与命令

- **mock-assisted UI（本地/CI 快速链路）**
  - 命令：`npm run test:e2e`
  - 配置：默认 `playwright.config.ts`
  - 测试目录：`e2e/*.spec.ts`（不含 `e2e/real/**`）

- **isolated real-link（真实前后端联动，本地/CI）**
  - 命令：`npm run test:e2e:real`
  - 配置：`playwright.real.config.ts`
  - 测试目录：`e2e/real/*.spec.ts`
  - 特点：真实 Spring Boot + Vite dev server、真实 MySQL（可选种子）、真实 OIDC/MFA/多租户链路，不再 mock first-party API。
  - **端口与 `E2E_FRONTEND_BASE_URL`**：若在 shell 中设置了 `E2E_FRONTEND_PORT`（例如动态端口避免与本机 5173 冲突），但 `.env.e2e.local` 仍写死 `E2E_FRONTEND_BASE_URL=http://localhost:5173`，`playwright.real.config.ts` 会以**端口为准**重写 `baseURL`，并向后端进程传入对应的 `E2E_FRONTEND_BASE_URL`，避免误连本机旧 Vite、而后端未就绪时出现 `GET /csrf` `ERR_CONNECTION_REFUSED`。

> 当前没有单独的 shared-env smoke / nightly/full-chain 套件，后续如有新增应在本表中补充。

#### 调度 Nightly real-link 套件

- Workflow：`.github/workflows/verify-scheduling-real-e2e.yml`
- 当前固定清单：
  - `e2e/real/platform-vue-login.spec.ts`（`verify-scheduling-real-e2e.yml`）
  - `e2e/real/scheduling-dag-orchestration.spec.ts`
  - `e2e/real/cross-tenant-a-to-b.spec.ts`
  - `e2e/real/cross-tenant-b-to-a.spec.ts`
  - `e2e/real/scheduling-rbac-readonly.spec.ts`
- 覆盖意图：
  - DAG 创建 / 触发 / run-node 行为链路
  - 双向跨租户直读拒绝（404）
  - 伪造 `X-Active-Tenant-Id` 的 `tenant_mismatch` 拒绝（403）
  - 同租户只读调度身份的真实 RBAC 拒绝（只允许 `scheduling:console:view`，拒绝 `scheduling:console:config` / `scheduling:run:control` / `scheduling:audit:view` / `scheduling:cluster:view`）
- 该套件需要额外只读身份 secrets：
  - `E2E_PLATFORM_USERNAME`
  - `E2E_PLATFORM_PASSWORD`
  - `E2E_PLATFORM_TOTP_SECRET`
  - `E2E_USERNAME_READONLY`
  - `E2E_PASSWORD_READONLY`
  - `E2E_TOTP_SECRET_READONLY`
  - `E2E_TENANT_CODE_READONLY` 可选；未提供时默认复用主调度测试租户
- 失败证据：
  - Playwright `playwright-report/`
  - Playwright `test-results/`（trace / screenshot / retain-on-failure video）
  - 后端 / 前端启动日志 `e2e-artifacts/backend.log`、`e2e-artifacts/frontend.log`

---

### 2. mock-assisted UI 套件（`npm run test:e2e`）

| spec 文件                | 等级            | 场景概述                                                         | 身份来源 / mock 边界                                              |
|--------------------------|-----------------|------------------------------------------------------------------|-------------------------------------------------------------------|
| `auth-flow.spec.ts`      | mock-assisted UI | 登录页、重定向路径清洗、TOTP 绑定/校验页面的表单行为与重定向语义 | `page.route` 拦截 `/api/csrf`、`/api/login`、`/api/self/security/**`，完全依赖 mock，不声称覆盖真实认证链路 |
| `export-task.spec.ts`    | mock-assisted UI | 导出任务前端交互（列表、状态展示等）                             | 通过 `page.route` 模拟后台响应，仅验证前端行为与路由，不验证真实导出链路 |
| `idempotent-ops.spec.ts` | mock-assisted UI | 幂等操作前端交互（按钮禁用、重复点击提示等）                     | 通过 `page.route`/前端 stub，专注 UI 状态，不验证真实幂等引擎     |

约束（对应 50-testing）：

- 允许 mock first-party API，但必须**显式说明** mock 边界，仅验证 UI 行为/路由，不声称“真实链路已回归”。
- 不允许在此等级下验证 MySQL 方言、多租户隔离、真实 OIDC/MFA 等平台能力。

---

### 3. real-link 套件（`npm run test:e2e:real`）

#### 3.1 环境与 seed/reset

- **配置来源**
  - `.env.e2e.local`（本地私有，不提交）：由 `.env.e2e.example` 拷贝并填入自动化专用身份与数据库连接。
  - `playwright.real.config.ts` 在启动时加载 `.env.e2e.local` 并派生：
    - `E2E_FRONTEND_BASE_URL` / `E2E_BACKEND_BASE_URL`
    - `E2E_DB_HOST` / `E2E_DB_PORT` / `E2E_DB_NAME` / `E2E_DB_USER` / `E2E_DB_PASSWORD`
    - OIDC 客户端（`E2E_OIDC_CLIENT_ID`）

- **后端 / 前端启动**
  - 后端：`mvn -pl tiny-oauth-server spring-boot:run`，profile 由 `E2E_BACKEND_PROFILE` 控制（默认 `e2e`/`dev`）。
  - 前端：`npm run dev -- --host localhost --port $E2E_FRONTEND_PORT`。

- **种子与认证 bootstrap**
  - 全局 setup：`e2e/setup/real.global.setup.ts`
    - 清理并创建 `e2e/.auth` 目录。
    - 调用 `scripts/e2e/ensure-scheduling-e2e-auth.sh` 初始化主自动化身份；如配置第二租户变量，再初始化第二自动化身份。
    - `ensure-scheduling-e2e-auth.sh` 会先验证默认租户是否具备调度 bootstrap 模板（`ROLE_ADMIN` + `037` authority 资源 + `scheduling:*` 绑定），缺失时直接 fail fast，避免在 real-link 中把模板缺失误判成单条业务回归。
    - 当第二租户或 readonly 租户与主租户不同，`real.global.setup.ts` 会优先使用平台自动化身份（`E2E_PLATFORM_*`）的真实 bearer token 调 `/sys/tenants` 创建租户，再交给 `ensure-scheduling-e2e-auth.sh` 只补用户与认证方式；这样 cross-tenant/readonly 的租户创建会真实触发 `TenantServiceImpl.create() -> TenantBootstrapService`，且不依赖普通租户管理员越权访问租户管理接口。
    - 按需执行 `scripts/e2e/seed-scheduling-orchestration.sql`（由 `E2E_USE_SQL_SEED` 控制）。
    - 运行 `e2e/setup/generate-auth-state.mjs` 生成：
      - `e2e/.auth/scheduling-user.json`（主自动化身份）
      - `e2e/.auth/tenant-b-user.json`（仅在配置第二租户身份时生成）
  - **相关回归测试**
    - `src/e2e/realGlobalSetup.test.ts`
      - 锁住第二租户 auth-state 的环境覆盖顺序
      - 明确要求：tenant B 未显式提供 `E2E_TOTP_CODE_B` 时，不能继承 tenant A 的 `E2E_TOTP_CODE`
      - 该回归属于“real-link setup helper 的单元测试”，用于防止 review 中出现过的 TOTP 覆盖回退

- **测试身份**
  - `chromium` / `chromium-cross-tenant-a`
    - 使用 `storageState: e2e/.auth/scheduling-user.json`
    - 代表主自动化租户（`E2E_TENANT_CODE`）和主自动化管理员用户（`E2E_USERNAME` / `E2E_PASSWORD`）
  - `chromium-cross-tenant-b`
    - 使用 `storageState: e2e/.auth/tenant-b-user.json`
    - 代表第二自动化租户（`E2E_TENANT_CODE_B`）和第二自动化管理员用户（`E2E_USERNAME_B` / `E2E_PASSWORD_B`）
  - `chromium-mfa`
    - 不使用 `storageState`
    - 从 `/login` 起步：`mfa-login-flow`（租户登录 + MFA）、`platform-vue-login`（平台登录 + 平台 MFA）
  - 如启用 MFA，则由 `E2E_TOTP_SECRET` / `E2E_TOTP_CODE` 和 `E2E_TOTP_SECRET_B` / `E2E_TOTP_CODE_B` 驱动真实 TOTP 校验链路。

> 以上身份和数据库凭证均从环境变量注入；`.env.e2e.example` 仅提供占位符，禁止填入真实生产账号。

#### 3.2 现有 real-link 用例

| spec 文件                                   | 等级          | 场景概述                                                                                  | 身份 / 边界说明                                                                                         |
|---------------------------------------------|---------------|-------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `real/scheduling-dag-orchestration.spec.ts` | isolated real-link | 通过真实 OIDC 登录态 + 专用调度租户，创建 DAG、触发 run、观察并行归并/串行推进/重试/取消/暂停恢复等拓扑（当前断言偏行为与状态，不覆盖所有边界条件） | 使用真实 `/scheduling/**` API 与真实 MySQL/H2 schema，不再 mock first-party API；依赖真实 OIDC/JWT/tenant 头 |
| `real/mfa-login-flow.spec.ts`               | isolated real-link | 从 `/login` 起步，使用主自动化账号走 “/login → /self/security/totp-verify → /self/security” 的已绑定 TOTP 用户登录链路；当前仅覆盖单租户、单身份场景 | 通过真实 `/api/login` 与 `/self/security/**`，不使用 storageState；依赖 `.env.e2e.local` 中的 E2E_TENANT_CODE/E2E_USERNAME/E2E_PASSWORD 以及 `E2E_TOTP_CODE` 或 `E2E_TOTP_SECRET` |
| `real/platform-vue-login.spec.ts`           | isolated real-link | 从 `/login` 点击「平台登录」、提交「登录平台」，验证无 `tenantCode` 的真实 Session 登录（含平台身份 MFA）；断言不回到带错误的 `/login` | 依赖 `E2E_PLATFORM_USERNAME` / `E2E_PLATFORM_PASSWORD` 与 `E2E_PLATFORM_TOTP_SECRET`（或 `E2E_PLATFORM_TOTP_CODE`）；补全 Vitest 无法覆盖的 Login.vue 平台提交链路 |
| `real/mfa-bind-flow.spec.ts`                | isolated real-link | 从 `/login` 起步，使用专用首绑身份登录未绑定 TOTP 的用户，走 “/login → /self/security/totp-bind → /self/security → 清理会话 → /login → /self/security/totp-verify → /self/security” 的完整首绑 + 校验链路 | 通过真实 `/api/login` 与 `/self/security/totp/*`，不使用 storageState；依赖 `.env.e2e.local` 中的 E2E_USERNAME_BIND/E2E_PASSWORD_BIND 以及可选的 E2E_TENANT_CODE_BIND；TOTP secret 由真实 `/self/security/totp/pre-bind` 返回 |
| `real/cross-tenant-a-to-b.spec.ts`          | isolated real-link | 以 tenant A 身份登录，由 tenant B 先创建真实调度资源，再验证 tenant A 直接读取其他租户资源会得到 404，伪造 tenant B 头会得到 403 `tenant_mismatch` | 使用两套真实 storageState 与真实 `/scheduling/**` API，不再停留在单身份 forged header；要求第二租户身份已 bootstrap |
| `real/cross-tenant-b-to-a.spec.ts`          | isolated real-link | 以 tenant B 身份登录，由 tenant A 先创建真实调度资源，再验证 tenant B 直接读取其他租户资源会得到 404，伪造 tenant A 头会得到 403 `tenant_mismatch` | 使用两套真实 storageState 与真实 `/scheduling/**` API，覆盖反向跨租户拒绝路径；要求第二租户身份已 bootstrap |
| `real/security-mfa-flow.spec.ts`           | isolated real-link | 在已有 storageState 登录态基础上，通过 `/self/security` 与 `/self/security/totp/*` 真实接口获取安全状态和 TOTP 预绑定信息；当前未覆盖从 `/login` 开始的完整 MFA 链路，断言相对宽松 | 仅读取安全状态与预绑定信息，不做绑定/解绑动作；依赖由 `real.global.setup.ts` 生成的主自动化身份 storageState |

> 以上 real-link spec 均只在真实后端 + 真实 OIDC + 真实多租户过滤器基础上运行，不使用 `page.route()` 拦截 first-party 业务 API。

---

### 4. mock 边界约定（real-link 套件）

符合 `50-testing.rules.md` / `90-tiny-platform.rules.md` 的前提下：

- ✅ 允许在 real-link 套件中：
  - 使用 `page.route()` 拦截第三方资源、静态文件或与测试目标无关的外部依赖（如外链脚本）。
  - 在 `page.evaluate` 中直接使用 `fetch` 调用真实 first-party API，用于补充 UI 难以覆盖的拒绝路径（如精确构造串租户 header）。
- ❌ 不允许在 real-link 套件中：
  - 将 first-party 业务 API（如 `/scheduling/**`、`/self/security/**`、`/sys/**`）整体 mock 掉之后仍声称“真实链路 E2E 已覆盖”。
  - 伪造 JWT / `localStorage` 登录态绕过真实 OIDC/MFA/bootstrap；所有 storageState 必须来自 `real.global.setup.ts` 生成的登录态。

---

### 5. 后续扩展建议（占位）

- Nightly/full-chain E2E：在隔离环境中串联“登录/MFA → 调度/导出/幂等 → 审计/租户边界”的长链路。
- shared-env smoke：在共享测试环境做只读/幂等路径的轻量 smoke，不做破坏性写入。 
