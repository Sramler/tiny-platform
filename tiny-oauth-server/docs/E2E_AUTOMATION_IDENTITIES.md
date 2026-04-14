# E2E 自动化测试身份与配置

## 目标

为真实前后端联动测试提供一套稳定、可复用、可轮换的自动化身份配置入口。

- 前端入口模板：`src/main/webapp/.env.e2e.example`
- 后端运行配置：`src/main/resources/application-e2e.yaml`
- 真实值来源：本地 `.env.e2e.local`、CI secrets、测试环境种子数据
- 加载优先级：Shell/CI 环境变量优先，其次是 `playwright.real.config.ts` 自动加载的 `.env.e2e.local`

---

## 当前链路最少需要的身份

当前 `playwright.real.config.ts` + `e2e/setup` 这一套真实 E2E 链路，至少需要一组专用测试身份：

- 专用测试租户：`E2E_TENANT_CODE`
- 专用管理员账号：`E2E_USERNAME`
- 专用账号密码：`E2E_PASSWORD`
- 专用 TOTP secret：`E2E_TOTP_SECRET`

这组身份必须满足：

- 只能用于测试环境或隔离租户
- 不能复用个人账号、共享人工管理员账号、生产账号
- 具备当前正向链路所需最小权限
- 可以脚本化初始化、重置和轮换

对于完整的跨租户 real-link 回归，还需要第二组专用测试身份：

- 第二测试租户：`E2E_TENANT_CODE_B`
- 第二管理员账号：`E2E_USERNAME_B`
- 第二账号密码：`E2E_PASSWORD_B`
- 第二账号 TOTP secret：`E2E_TOTP_SECRET_B`

这组身份只用于双身份跨租户拒绝场景，不应复用主自动化身份。

同时，租户管理接口 `/sys/tenants` 现已收口为平台管理员入口。凡是需要在 real-link 中动态创建第二租户、readonly 租户的场景，还需要一组平台自动化身份：

- 平台租户编码：`E2E_PLATFORM_TENANT_CODE`（**须显式配置**，与库内平台来源租户 `tenant.code` 一致；禁止在未配置时假定 `default`，见 CARD-13E / `real.global.setup.ts`）
- 平台管理员账号：`E2E_PLATFORM_USERNAME`
- 平台管理员密码：`E2E_PLATFORM_PASSWORD`
- 平台管理员 TOTP secret：`E2E_PLATFORM_TOTP_SECRET`

这组身份只用于通过真实 `/sys/tenants` API 创建测试租户，不参与普通租户内的业务操作链路。

---

## 推荐扩展的身份矩阵

当前真实 E2E 已稳定消费一组主测试身份，并支持第二租户身份用于跨租户拒绝场景。企业场景仍建议继续扩展以下矩阵：

- `automation-admin`：租户管理员，覆盖正向管理链路
- `automation-mfa`：启用 TOTP，覆盖绑定/校验链路
- `automation-viewer`：只读或低权限用户，覆盖 403/按钮显隐
- `automation-cross-tenant`：其他租户用户，覆盖租户隔离拒绝
- `automation-disabled`：禁用账号，覆盖登录失败与锁定场景

建议命名方式统一为环境变量前缀，例如：

- `E2E_ADMIN_USERNAME`
- `E2E_VIEWER_USERNAME`
- `E2E_CROSS_TENANT_USERNAME`
- `E2E_DISABLED_USERNAME`

当前已落地的第二租户身份变量为：

- `E2E_TENANT_CODE_B`
- `E2E_USERNAME_B`
- `E2E_PASSWORD_B`
- `E2E_TOTP_SECRET_B`
- `E2E_TOTP_CODE_B`（可选，仅用于 CI 注入一次性验证码）

调度 readonly real-link 当前还需要一组只读身份：

- `E2E_TENANT_CODE_READONLY`（可选，不配置时回退到主租户）
- `E2E_USERNAME_READONLY`
- `E2E_PASSWORD_READONLY`
- `E2E_TOTP_SECRET_READONLY`
- `E2E_TOTP_CODE_READONLY`（可选）

此外，为了支持“未绑定 TOTP 首绑 real-link”，还需要一组专用首绑身份：

- `E2E_TENANT_CODE_BIND`（可选，不配置时回退到 `E2E_TENANT_CODE`）
- `E2E_USERNAME_BIND`
- `E2E_PASSWORD_BIND`

这组身份只用于 `real/mfa-bind-flow.spec.ts` 所对应的“首绑 + 再次登录”链路，后端通过
`scripts/e2e/ensure-scheduling-e2e-auth.sh` 保证该用户每次测试前都没有本地 TOTP 绑定记录。

---

## 本地运行方式

1. 在 `src/main/webapp` 下复制模板：

```bash
cp .env.e2e.example .env.e2e.local
```

2. 将 `.env.e2e.local` 中的占位符替换为真实测试环境值。

3. 默认情况下，`playwright.real.config.ts` 会自动加载 `src/main/webapp/.env.e2e.local`。

4. 如果 CI 或本地 Shell 已显式导出同名环境变量，这些值会优先覆盖 `.env.e2e.local`。

5. 直接运行：

```bash
cd src/main/webapp
npm run test:e2e:real
```

6. 如需临时覆盖某个变量，可在命令前显式导出，例如：

```bash
cd src/main/webapp
E2E_BACKEND_PROFILE=e2e E2E_USE_SQL_SEED=true npm run test:e2e:real
```

7. 如需启用专用后端配置，确保：

```bash
export E2E_BACKEND_PROFILE=e2e
```

---

## GitHub Actions secrets 与触发说明

当前仓库已提供独立的 real-link workflow：

- [verify-webapp-real-e2e.yml](../../.github/workflows/verify-webapp-real-e2e.yml)
- [GITHUB_ACTIONS_REAL_E2E_GUIDE.md](../../docs/GITHUB_ACTIONS_REAL_E2E_GUIDE.md)

该 workflow 的定位是：

- **不进入默认 PR 快速链路**
- 只用于：
  - `workflow_dispatch`
  - `schedule`

### 需要配置的 GitHub Actions secrets

最少需要以下 secrets：

- `E2E_DB_PASSWORD`
- `E2E_TENANT_CODE`
- `E2E_USERNAME`
- `E2E_PASSWORD`
- `E2E_TOTP_SECRET`
- `E2E_PLATFORM_USERNAME`
- `E2E_PLATFORM_PASSWORD`
- `E2E_PLATFORM_TOTP_SECRET`
- `E2E_TENANT_CODE_B`
- `E2E_USERNAME_B`
- `E2E_PASSWORD_B`
- `E2E_TOTP_SECRET_B`
- `E2E_USERNAME_READONLY`
- `E2E_PASSWORD_READONLY`
- `E2E_TOTP_SECRET_READONLY`
- `E2E_USERNAME_BIND`
- `E2E_PASSWORD_BIND`

可选：

- `E2E_TENANT_CODE_BIND`
  - 不配置时，首绑用户默认回退到主租户 `E2E_TENANT_CODE`

### secrets 对应关系

| GitHub secret                                     | 说明                            |
| ------------------------------------------------- | ------------------------------- |
| `E2E_DB_PASSWORD`                                 | MySQL root/test password        |
| `E2E_TENANT_CODE`                                 | 主自动化租户编码                |
| `E2E_USERNAME` / `E2E_PASSWORD`                   | 主自动化管理员账号              |
| `E2E_TOTP_SECRET`                                 | 主自动化管理员 TOTP secret      |
| `E2E_PLATFORM_USERNAME` / `E2E_PLATFORM_PASSWORD` | 平台自动化管理员账号            |
| `E2E_PLATFORM_TOTP_SECRET`                        | 平台自动化管理员 TOTP secret    |
| `E2E_TENANT_CODE_B`                               | 第二自动化租户编码              |
| `E2E_USERNAME_B` / `E2E_PASSWORD_B`               | 第二自动化管理员账号            |
| `E2E_TOTP_SECRET_B`                               | 第二自动化管理员 TOTP secret    |
| `E2E_USERNAME_READONLY` / `E2E_PASSWORD_READONLY` | readonly 自动化账号             |
| `E2E_TOTP_SECRET_READONLY`                        | readonly 自动化账号 TOTP secret |
| `E2E_TENANT_CODE_BIND`                            | 首绑用户所属租户，可选          |
| `E2E_USERNAME_BIND` / `E2E_PASSWORD_BIND`         | 未绑定 TOTP 的首绑专用用户      |

### 手动触发

1. 打开 GitHub Actions。
2. 选择 `Verify webapp real-link E2E`。
3. 点击 `Run workflow`。

运行前应确保：

- 对应测试环境允许 `tiny-oauth-server` 使用 MySQL 8.4 service 启动
- 上述 GitHub secrets 已配置完整
- 自动化身份在目标环境中是隔离账号，不复用人工账号

### 定时触发

当前 workflow 已配置 nightly：

- 每天 `02:00 UTC`

如果后续要改频率，应同步更新：

- workflow 的 `schedule`
- 本文档
- [TINY_PLATFORM_TESTING_PLAYBOOK.md](../../docs/TINY_PLATFORM_TESTING_PLAYBOOK.md) 中的 CI 分层说明

### 失败时的第一检查项

如果 `Verify webapp real-link E2E` 失败，优先判断：

1. secrets 是否缺失
2. bind 用户是否仍然被错误地保留了旧 TOTP 绑定
3. tenant B 是否错误继承了 tenant A 的 `E2E_TOTP_CODE`
4. MySQL service 是否健康
5. OIDC redirect URI / client 配置是否仍与 `5173` 对齐

其中第 3 项已由：

- `src/e2e/realGlobalSetup.test.ts`

做回归保护，CI 会先跑它，再跑完整 real-link E2E。

---

## 使用约束

- `application-e2e.yaml` 中不允许写入真实密码、真实 client secret、真实 TOTP secret。
- `.env.e2e.local` 必须保持未提交状态；当前 `*.local` 已被前端 `.gitignore` 忽略。
- 若修改 `E2E_FRONTEND_PORT` 或 `E2E_FRONTEND_BASE_URL`，应同步确认 OIDC redirect URI 与后端客户端注册一致；默认推荐继续使用 `5173`。
- CI 中必须通过 secret 注入真实值，不能回退到仓库中的默认账号。

---

## 现状说明

当前真实 E2E 辅助脚本对数据库变量仍存在两套命名：

- `E2E_DB_*`
- `E2E_MYSQL_*`

`src/main/webapp/.env.e2e.example` 同时提供了两组模板，目的是在不改动现有脚本的前提下保持自动化可用。后续如果统一变量命名，可以再收敛为一套前缀。

## 本地派生资产治理

real-link 测试在以下场景会产生“派生资产”：

- 通过 `/sys/tenants` 动态创建租户时，租户名会写成 `E2E租户(<tenantCode>)`
- 同时会生成初始管理员账号：`e2e_init_<tenantCode>`
- 当前 keep-set 之外的 `e2e_*` 用户，还可能保留历史 membership / auth method

这些资产与固定长期身份（如 `E2E_USERNAME`、`E2E_USERNAME_BIND`）不同，不应无限积累在本地共享库中。

当前仓库内还存在一组“长期自动化身份兼容 allowlist”，用于避免审计脚本把已知长期身份误报为 stale `e2e_*`：

- `e2e_admin_b`
- `e2e_platform_admin`
- `e2e_scheduling_readonly`

如果本地/CI 还维护其它长期自动化身份，可以通过 `E2E_KEEP_USERS_EXTRA=user1,user2` 追加到审计 keep-set。

推荐审计命令：

```bash
bash tiny-oauth-server/scripts/verify-real-e2e-derived-assets.sh
```

受控清理生成型派生资产：

```bash
bash tiny-oauth-server/scripts/verify-real-e2e-derived-assets.sh --apply
```

如需把该检查接入门禁，可使用：

```bash
bash tiny-oauth-server/scripts/verify-real-e2e-derived-assets.sh --fail-on-stale
```

说明：

- 脚本会优先读取 shell 中已导出的 `E2E_*`，缺失时回退到 `src/main/webapp/.env.e2e.local`
- 固定 real-link 身份属于长期资产，脚本不会把它们直接判定为“应删除”
- `E2E租户(...)`、`e2e_init_*`、以及 keep 用户挂在生成型租户上的 stale membership，属于自动治理范围
- `playwright.real.config.ts` 对应的 `globalSetup/globalTeardown` 会默认执行：跑前 `--apply --fail-on-stale`，跑后按本轮派生租户 code 再做一次定向清理；若需人工保留现场，可显式设置 `E2E_SKIP_REAL_DERIVED_ASSET_CLEANUP=true`

---

## 当前 real-link 项目与身份映射

- `chromium`
  - 使用 `e2e/.auth/scheduling-user.json`
  - 覆盖调度编排、post-login 安全中心等主身份 real-link
- `chromium-mfa`
  - 不使用 `storageState`
  - 从 `/login` 起步，使用主身份走已绑定 TOTP 的真实 MFA 登录链路
- `chromium-mfa-bind`
  - 不使用 `storageState`
  - 从 `/login` 起步，使用首绑身份走“未绑定 → 首绑 → 再次登录验证”的真实 MFA 链路
- `chromium-cross-tenant-a`
  - 使用 `e2e/.auth/scheduling-user.json`
  - 以 tenant A 身份访问 tenant B 资源
- `chromium-cross-tenant-b`
  - 使用 `e2e/.auth/tenant-b-user.json`
  - 以 tenant B 身份访问 tenant A 资源
