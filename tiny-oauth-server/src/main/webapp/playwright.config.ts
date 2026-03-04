import { defineConfig, devices } from '@playwright/test'

const PORT = 4173
const baseURL = `http://127.0.0.1:${PORT}`

export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: {
    timeout: 5_000,
  },
  use: {
    baseURL,
    trace: 'on-first-retry',
  },
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${PORT}`,
    cwd: '/Users/bliu/code/tiny-platform/tiny-oauth-server/src/main/webapp',
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
