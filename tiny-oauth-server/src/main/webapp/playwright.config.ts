import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig, devices } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const webappRoot = path.resolve(__dirname)
const PORT = 4173
const baseURL = `http://127.0.0.1:${PORT}`

export default defineConfig({
  // 默认 E2E 只覆盖 mock-assisted UI（不依赖真实后端 / OIDC / 多租户语义）。
  // 真实链路请使用 playwright.real.config.ts + npm run test:e2e:real。
  testDir: './e2e',
  // 显式排除 real-link 套件，避免 `npm run test:e2e` 误跑真实链路用例。
  testIgnore: /e2e\/real\/.*\.spec\.ts/,
  timeout: 30_000,
  expect: {
    timeout: 5_000,
  },
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  webServer: {
    // 仅启动前端 dev server，后端由 mock / page.route 等方式模拟；
    // 避免默认 test:e2e 被误用为真实链路回归。
    command: `npm run dev -- --host 127.0.0.1 --port ${PORT}`,
    cwd: webappRoot,
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    env: {
      VITE_API_BASE_URL: `${baseURL}/api`,
      VITE_ENABLE_OIDC_TRACE: 'false',
    },
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
})
