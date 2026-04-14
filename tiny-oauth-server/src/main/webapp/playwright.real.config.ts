import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, devices } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const workspaceRoot = path.resolve(__dirname, '..', '..', '..', '..')
const webappRoot = path.resolve(__dirname)
const localE2EEnvPath = path.resolve(webappRoot, '.env.e2e.local')

// Allow callers (CI/local scripts) to fully control env via process.env.
// When E2E_IGNORE_LOCAL_ENV=true, we skip loading `.env.e2e.local`.
if (fs.existsSync(localE2EEnvPath) && process.env.E2E_IGNORE_LOCAL_ENV !== 'true') {
  process.loadEnvFile(localE2EEnvPath)
}

function readEnv(names: string[], fallback: string) {
  for (const name of names) {
    const value = process.env[name]
    if (value && value.trim() !== '') {
      return value
    }
  }
  return fallback
}

function readEnvOptional(names: string[]): string | undefined {
  for (const name of names) {
    const value = process.env[name]
    if (value && value.trim() !== '') {
      return value.trim()
    }
  }
  return undefined
}

/**
 * Playwright 会用 E2E_FRONTEND_PORT 启动 Vite；若 .env.e2e.local 里仍写着 E2E_FRONTEND_BASE_URL=http://localhost:5173，
 * 会与动态端口冲突并误复用本机 5173（API 仍指向 9000，后端未就绪时出现 CSRF connection refused）。
 * 当 BASE_URL 的端口与 FRONTEND_PORT 不一致时，以端口为准生成 baseURL。
 */
function resolveFrontendBaseURL(port: number, configuredBaseURL: string | undefined): string {
  const fallback = `http://localhost:${port}`
  if (!configuredBaseURL) {
    return fallback
  }
  try {
    const parsed = new URL(configuredBaseURL)
    const urlPort = parsed.port || (parsed.protocol === 'https:' ? '443' : '80')
    if (urlPort === String(port)) {
      return configuredBaseURL
    }
  } catch {
    return fallback
  }
  return fallback
}

const frontendPort = Number(readEnv(['E2E_FRONTEND_PORT'], '5173'))
const backendPort = Number(readEnv(['E2E_BACKEND_PORT'], '9000'))
const frontendBaseURL = resolveFrontendBaseURL(
  frontendPort,
  readEnvOptional(['E2E_FRONTEND_BASE_URL']),
)
const backendBaseURL = readEnv(['E2E_BACKEND_BASE_URL'], `http://localhost:${backendPort}`)
const authStatePath = path.resolve(webappRoot, 'e2e/.auth/scheduling-user.json')
const secondaryAuthStatePath = path.resolve(webappRoot, 'e2e/.auth/tenant-b-user.json')
const readonlyAuthStatePath = path.resolve(webappRoot, 'e2e/.auth/scheduling-readonly-user.json')
const backendProfile = readEnv(['E2E_BACKEND_PROFILE'], 'dev')
const dbHost = readEnv(['E2E_DB_HOST', 'E2E_MYSQL_HOST'], '127.0.0.1')
const dbPort = readEnv(['E2E_DB_PORT', 'E2E_MYSQL_PORT'], '3306')
const dbName = readEnv(['E2E_DB_NAME', 'E2E_MYSQL_DATABASE'], 'tiny_web')
const dbUser = readEnv(['E2E_DB_USER', 'E2E_MYSQL_USER'], 'root')
const dbPassword = readEnv(['E2E_DB_PASSWORD', 'E2E_MYSQL_PASSWORD', 'MYSQL_ROOT_PASSWORD'], '')
const skipRealSetup = process.env.E2E_SKIP_REAL_SETUP === 'true'
const skipWebServer = process.env.E2E_SKIP_WEBSERVER === 'true'

// CARD-13E：`real.global.setup.ts` 主链始终调用 `requireRealLinkPlatformTenantCode`；在加载本配置时尽早 fail-fast，避免启动 webServer 后才报错。
if (!skipRealSetup && !readEnvOptional(['E2E_PLATFORM_TENANT_CODE'])) {
  throw new Error(
    'playwright.real.config: real-link 需要显式 E2E_PLATFORM_TENANT_CODE（禁止静默 default）。请在 .env.e2e.local 或 CI secrets 中配置；仅跑 mock/跳过 globalSetup 时设 E2E_SKIP_REAL_SETUP=true。',
  )
}

export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: {
    timeout: 10_000,
  },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  globalSetup: skipRealSetup ? undefined : './e2e/setup/real.global.setup.ts',
  globalTeardown: skipRealSetup ? undefined : './e2e/setup/real.global.teardown.ts',
  use: {
    baseURL: frontendBaseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: process.env.CI ? 'retain-on-failure' : 'off',
  },
  webServer: skipRealSetup || skipWebServer
    ? undefined
    : [
        {
          command: 'mvn -pl tiny-oauth-server spring-boot:run',
          cwd: workspaceRoot,
          // /csrf is a stable 200 endpoint once the backend is HTTP-ready.
          // /login may redirect or vary by auth state, which can stall Playwright's readiness probe.
          url: `${backendBaseURL}/csrf`,
          timeout: 360_000,
          reuseExistingServer: true,
          env: {
            ...process.env,
            SPRING_PROFILES_ACTIVE: backendProfile,
            // 与 Vite webServer 端口对齐，避免 dev profile 硬编码 5173 导致 MFA/登录重定向走错前端
            E2E_FRONTEND_BASE_URL: frontendBaseURL,
            E2E_DB_HOST: dbHost,
            E2E_DB_PORT: dbPort,
            E2E_DB_NAME: dbName,
            E2E_DB_USER: dbUser,
            E2E_DB_PASSWORD: dbPassword,
            MYSQL_ROOT_PASSWORD: dbPassword,
          },
        },
        {
          command: `npm run dev -- --host localhost --port ${frontendPort}`,
          cwd: webappRoot,
          url: `${frontendBaseURL}/login`,
          timeout: 120_000,
          reuseExistingServer: true,
          env: {
            ...process.env,
            VITE_API_BASE_URL: backendBaseURL,
            VITE_OIDC_AUTHORITY: backendBaseURL,
            VITE_OIDC_CLIENT_ID: process.env.E2E_OIDC_CLIENT_ID ?? 'vue-client',
            VITE_OIDC_REDIRECT_URI: `${frontendBaseURL}/callback`,
            VITE_OIDC_POST_LOGOUT_REDIRECT_URI: `${frontendBaseURL}/`,
            VITE_OIDC_SILENT_REDIRECT_URI: `${frontendBaseURL}/silent-renew.html`,
            VITE_ENABLE_OIDC_TRACE: process.env.E2E_ENABLE_OIDC_TRACE ?? 'false',
          },
        },
      ],
  projects: [
    {
      name: 'chromium',
      // 依赖主自动化身份 storageState 的 real-link 用例（调度、HeaderBar active-scope + silent renew 等）
      testMatch:
        /real\/(?!mfa-login-flow|mfa-bind-flow|platform-vue-login|cross-tenant-a-to-b|cross-tenant-b-to-a|scheduling-rbac-readonly).*\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        storageState: authStatePath,
      },
    },
    {
      name: 'chromium-mfa',
      // 从 /login 起步的 MFA 真实链路，不依赖预生成 storageState。
      testMatch: /real\/(mfa-login-flow|platform-vue-login)\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        storageState: undefined,
      },
    },
    {
      name: 'chromium-mfa-bind',
      // 从 /login 起步的“未绑定 TOTP 首绑链路”，不依赖预生成 storageState。
      testMatch: /real\/mfa-bind-flow\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        storageState: undefined,
      },
    },
    {
      name: 'chromium-cross-tenant-a',
      // A 租户身份访问 B 租户资源的真实跨租户回归。
      testMatch: /real\/cross-tenant-a-to-b\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        storageState: authStatePath,
      },
    },
    {
      name: 'chromium-cross-tenant-b',
      // B 租户身份访问 A 租户资源的真实跨租户回归。
      testMatch: /real\/cross-tenant-b-to-a\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        storageState: secondaryAuthStatePath,
      },
    },
    {
      name: 'chromium-scheduling-readonly',
      // 同租户只读调度身份的真实 RBAC 拒绝回归。
      testMatch: /real\/scheduling-rbac-readonly\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        storageState: readonlyAuthStatePath,
      },
    },
  ],
})
