# 前端环境变量配置说明

## 概述

项目支持多环境配置，通过环境变量文件（`.env`）来管理不同环境的配置。默认启用 `dev` 环境。

## 环境文件

项目包含以下环境变量文件：

- `.env` - **所有环境的通用默认配置**（推荐保留）
- `.env.development` - 开发环境特定配置（覆盖 `.env`）
- `.env.test` - 测试环境特定配置（覆盖 `.env`）
- `.env.production` - 生产环境特定配置（覆盖 `.env`）
- `env.example` - **环境变量模板**，复制为 `.env.local` 或 `.env.[mode].local` 后按需修改

> **最佳实践**：将所有环境共用的配置放在 `.env` 中，环境特定的配置放在对应的 `.env.[mode]` 文件中，避免重复配置。

## 初始化环境文件

1. 复制 `env.example` 为 `.env.local`（或 `.env.development.local`、`.env.test.local` 等）。
2. 根据实际部署修改 OIDC 地址、客户端 ID、回调地址等敏感项。
3. 开发/测试环境可保留默认日志与调试设置，生产环境请调整：
   - 调低 `VITE_PERSISTENT_LOG_SAMPLE_RATE`
   - 仅保留必要的 `VITE_PERSISTENT_LOG_LEVELS`
   - 视情况关闭 `VITE_ENABLE_OIDC_TRACE`

## 环境变量说明

### 基础配置

| 变量名              | 说明         | 默认值                  | 可选值                    |
| ------------------- | ------------ | ----------------------- | ------------------------- |
| `VITE_APP_ENV`      | 应用环境标识 | `dev`                   | `dev` \| `test` \| `prod` |
| `VITE_API_BASE_URL` | API 基础 URL | `http://localhost:9000` | 任意 URL                  |

### 日志配置

| 变量名                            | 说明                                       | 默认值                                  | 可选值                                           |
| --------------------------------- | ------------------------------------------ | --------------------------------------- | ------------------------------------------------ |
| `VITE_ENABLE_CONSOLE_LOG`         | 是否启用 console.log                       | `true`                                  | `true` \| `false`                                |
| `VITE_ENABLE_CONSOLE_DEBUG`       | 是否启用 console.debug                     | 非生产环境                              | `true` \| `false`                                |
| `VITE_ENABLE_CONSOLE_WARN`        | 是否启用 console.warn                      | `true`                                  | `true` \| `false`                                |
| `VITE_ENABLE_CONSOLE_ERROR`       | 是否启用 console.error                     | `true`                                  | `true` \| `false`                                |
| `VITE_LOG_LEVEL`                  | 日志级别                                   | 非生产环境: `debug`<br>生产环境: `info` | `debug` \| `info` \| `warn` \| `error` \| `none` |
| `VITE_ENABLE_PERSISTENT_LOG`      | 是否启用持久化日志                         | 非生产环境                              | `true` \| `false`                                |
| `VITE_PERSISTENT_LOG_LEVELS`      | 允许写入持久化日志的级别（逗号分隔）       | `debug,info,warn,error`                 | `debug`/`info`/`warn`/`error` 任意组合           |
| `VITE_PERSISTENT_LOG_SAMPLE_RATE` | 持久化日志采样率，避免 localStorage 被打爆 | `1`（生产推荐 `0.3`）                   | `0` ~ `1`                                        |

### OIDC 客户端配置

| 变量名                                     | 说明                           | 默认值                                    | 可选值                                                     |
| ------------------------------------------ | ------------------------------ | ----------------------------------------- | ---------------------------------------------------------- |
| `VITE_OIDC_AUTHORITY`                      | 授权服务器地址                 | `http://localhost:9000`                   | 任意合法 URL                                               |
| `VITE_OIDC_CLIENT_ID`                      | 客户端 ID                      | `vue-client`                              | 与 Spring Authorization Server `RegisteredClient` 保持一致 |
| `VITE_OIDC_REDIRECT_URI`                   | 登录回调地址                   | `http://localhost:5173/callback`          | 任意合法 URL                                               |
| `VITE_OIDC_POST_LOGOUT_REDIRECT_URI`       | 退出后回调地址                 | `http://localhost:5173/`                  | 任意合法 URL                                               |
| `VITE_OIDC_SILENT_REDIRECT_URI`            | 静默续期页面                   | `http://localhost:5173/silent-renew.html` | 已登记的独立 silent renew 回调 URL                         |
| `VITE_OIDC_SILENT_REQUEST_TIMEOUT_SECONDS` | 静默续期 iframe 超时时间（秒） | `10`                                      | `1` ~ `30`，弱网建议 `10` ~ `15`                           |
| `VITE_OIDC_SCOPES`                         | 请求的 scope（空格分隔）       | `openid profile offline_access`           | 与服务器允许的 scope 匹配                                  |
| `VITE_OIDC_STORAGE`                        | OIDC state/storage 的存储介质  | `local`                                   | `local` \| `session`                                       |

> **生产环境要求**：`VITE_OIDC_AUTHORITY`、`VITE_OIDC_CLIENT_ID`、`VITE_OIDC_REDIRECT_URI`、`VITE_OIDC_POST_LOGOUT_REDIRECT_URI`、`VITE_OIDC_SILENT_REDIRECT_URI` 必须显式配置，否则启动会抛出错误。

> **silent renew 入口约束**：`VITE_OIDC_SILENT_REDIRECT_URI` 默认且推荐指向 `silent-renew.html`。该页面是隐藏 iframe 使用的极小独立 HTML 入口，只执行 `signinSilentCallback()`；不要配置为 Vue Router 路由，避免 iframe 回调进入完整应用启动、`/bootstrap` 编排或业务守卫链路。

### 认证运行时配置

| 变量名                                           | 说明                                              | 默认值 | 可选值            |
| ------------------------------------------------ | ------------------------------------------------- | ------ | ----------------- |
| `VITE_AUTH_FORCE_LOGOUT_ON_RENEW_FAIL`           | 静默续期失败时是否强制登出                        | `true` | `true` \| `false` |
| `VITE_AUTH_FETCH_TIMEOUT_MS`                     | `fetchWithAuth` 的超时时间（毫秒）                | `8000` | `3000` ~ `60000`  |
| `VITE_AUTH_ENABLE_PLATFORM_SESSION_SILENT_LOGIN` | 是否允许平台 Session 桥接静默恢复前端 OIDC 登录态 | `true` | `true` \| `false` |

> 当前启动链路中，平台账号完成表单登录、TOTP 绑定/跳过等后端 Session 流程后，前端会进入 `/bootstrap`，再通过 `prompt=none` 尝试把平台 Session 桥接成前端 OIDC token。弱网或浏览器限制导致静默恢复失败时，应展示可见的启动错误与重试/返回登录操作，而不是白屏。

### 启动编排入口

前端启动不再由 `main.ts` 或普通路由守卫阻塞执行认证恢复。标准职责如下：

| 阶段                  | 责任                                                                   |
| --------------------- | ---------------------------------------------------------------------- |
| `index.html` preboot  | Vue 资源加载前展示静态启动壳，避免首屏白屏                             |
| `main.ts`             | 只创建、安装并挂载 Vue 应用                                            |
| `/bootstrap`          | 展示启动状态，并统一编排认证、安全状态、菜单权限与动态路由             |
| `authBootstrap`       | 恢复本地 OIDC user；必要时执行平台 Session 静默桥接                    |
| `securityBootstrap`   | 检查 TOTP/MFA 状态，并分流到安全流程页                                 |
| `permissionBootstrap` | 加载 `/sys/menus/tree`、校验菜单路由、注册动态路由；失败时 fail-closed |
| router guard          | 只做轻量分流，不再承担 `signinSilent()`、菜单请求或动态路由注册        |

详细设计见 `docs/features/STARTUP_AUTH_BOOTSTRAP.md`。

### 调试/追踪开关

| 变量名                   | 说明                                                     | 默认值                                  | 可选值            |
| ------------------------ | -------------------------------------------------------- | --------------------------------------- | ----------------- |
| `VITE_ENABLE_OIDC_TRACE` | 是否强制启用 OIDC 相关调试日志（默认非生产环境自动开启） | 非生产环境: `true`<br>生产环境: `false` | `true` \| `false` |

## 使用方法

### 开发环境（默认）

```bash
# 使用默认配置（.env 或 .env.development）
npm run dev

# 或显式指定开发环境
npm run dev:dev
```

### 测试环境

```bash
# 开发模式运行测试环境
npm run dev:test

# 构建测试环境
npm run build:test

# 预览测试环境构建
npm run preview:test
```

### 生产环境

```bash
# 开发模式运行生产环境（用于本地测试生产配置）
npm run dev:prod

# 构建生产环境
npm run build:prod

# 预览生产环境构建
npm run preview:prod
```

## 环境配置对比

| 配置项                       | 开发环境 (dev)          | 测试环境 (test)               | 生产环境 (prod)           |
| ---------------------------- | ----------------------- | ----------------------------- | ------------------------- |
| `VITE_APP_ENV`               | `dev`                   | `test`                        | `prod`                    |
| `VITE_API_BASE_URL`          | `http://localhost:9000` | `http://test-api.example.com` | `https://api.example.com` |
| `VITE_ENABLE_CONSOLE_LOG`    | `true`                  | `true`                        | `false`                   |
| `VITE_ENABLE_CONSOLE_DEBUG`  | `true`                  | `true`                        | `false`                   |
| `VITE_LOG_LEVEL`             | `debug`                 | `info`                        | `error`                   |
| `VITE_ENABLE_PERSISTENT_LOG` | `true`                  | `true`                        | `false`                   |

## 自定义环境变量

### 方式 1: 修改环境文件

直接编辑对应的 `.env.*` 文件：

```bash
# .env.development
VITE_API_BASE_URL=http://localhost:9000
VITE_ENABLE_PERSISTENT_LOG=true
```

### 方式 2: 命令行覆盖

```bash
# 临时覆盖环境变量
VITE_API_BASE_URL=http://custom-api.com npm run dev
```

### 方式 3: 创建自定义环境文件

创建 `.env.local` 文件（会被 git 忽略，适合本地配置）：

```bash
# .env.local
VITE_API_BASE_URL=http://localhost:8080
VITE_ENABLE_PERSISTENT_LOG=false
```

## 环境变量优先级

Vite 会按以下顺序加载环境变量（后面的会覆盖前面的）：

1. `.env` - 所有环境的通用默认值
2. `.env.local` - 本地覆盖（会被 git 忽略，所有环境通用）
3. `.env.[mode]` - 特定模式（如 `.env.development`）
4. `.env.[mode].local` - 特定模式的本地覆盖（会被 git 忽略）

### 为什么保留 `.env`？

1. **避免重复配置**：如果多个环境有相同的配置值，可以放在 `.env` 中
2. **符合 Vite 最佳实践**：`.env` 是 Vite 推荐的基础配置文件
3. **便于维护**：修改通用配置时只需修改一处
4. **更规范**：遵循 Vite 的标准环境变量加载机制

### 配置示例

**`.env`**（通用配置）：

```bash
# 所有环境都启用 warn 和 error 日志
VITE_ENABLE_CONSOLE_WARN=true
VITE_ENABLE_CONSOLE_ERROR=true
```

**`.env.development`**（开发环境特定配置）：

```bash
# 开发环境启用所有日志
VITE_ENABLE_CONSOLE_LOG=true
VITE_ENABLE_CONSOLE_DEBUG=true
VITE_LOG_LEVEL=debug
```

**`.env.production`**（生产环境特定配置）：

```bash
# 生产环境禁用大部分日志
VITE_ENABLE_CONSOLE_LOG=false
VITE_ENABLE_CONSOLE_DEBUG=false
VITE_LOG_LEVEL=error
```

## 在代码中使用

```typescript
// 获取环境标识
const appEnv = import.meta.env.VITE_APP_ENV // 'dev' | 'test' | 'prod'

// 获取 API 基础 URL
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL

// 检查是否为开发环境
const isDev = import.meta.env.VITE_APP_ENV === 'dev'

// 检查是否为生产环境
const isProd = import.meta.env.VITE_APP_ENV === 'prod'
```

## 注意事项

1. **环境变量必须以 `VITE_` 开头**：只有以 `VITE_` 开头的变量才会暴露给客户端代码
2. **重启开发服务器**：修改 `.env` 文件后需要重启开发服务器才能生效
3. **生产环境构建**：构建时会根据 `--mode` 参数加载对应的环境文件
4. **敏感信息**：不要在环境变量中存储敏感信息（如密码、密钥），这些信息会暴露在客户端代码中

## 示例场景

### 场景 1: 本地开发

```bash
# 使用默认开发环境配置
npm run dev
```

### 场景 2: 连接测试服务器

```bash
# 修改 .env.test 中的 VITE_API_BASE_URL
# 然后运行
npm run dev:test
```

### 场景 3: 生产环境构建

```bash
# 修改 .env.production 中的配置
# 然后构建
npm run build:prod
```

### 场景 4: 临时调试生产配置

```bash
# 使用生产环境配置运行开发服务器
npm run dev:prod
```

## 相关文件

- `package.json` - 包含所有环境相关的 npm scripts
- `vite.config.ts` - Vite 配置文件
- `env.d.ts` - TypeScript 环境变量类型定义
- `env.example` - 复制后即可快速初始化 `.env.local`
- `src/utils/logger.ts` - 日志工具，使用环境变量控制日志输出
- `src/auth/oidc.ts` - OIDC 客户端配置与环境变量校验
- `src/auth/config.ts` - Auth 运行时行为配置（静默续期、请求超时等）
