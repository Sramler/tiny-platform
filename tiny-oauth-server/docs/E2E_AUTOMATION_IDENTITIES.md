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

---

## 推荐扩展的身份矩阵

当前脚本只消费一组主测试身份，但企业场景建议继续扩展以下矩阵：

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
