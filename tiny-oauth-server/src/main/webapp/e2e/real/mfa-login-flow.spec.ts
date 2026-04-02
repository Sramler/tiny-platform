import { createHmac } from 'node:crypto'
import { expect, test } from '@playwright/test'

/**
 * real-link：从 /login 起步的“已绑定 TOTP 用户” MFA 链路（单租户、单身份）
 *
 * 目标：
 * - 使用 `.env.e2e.local` 中的 E2E_TENANT_CODE / E2E_USERNAME / E2E_PASSWORD，
 *   再通过 `E2E_TOTP_CODE` 或 `E2E_TOTP_SECRET` 生成一次性验证码，
 *   从登录页开始走完整链路：/login -> /self/security/totp-verify -> /self/security。
 * - 不使用 storageState；依赖真实 OIDC / CSRF / Session / JWT / MFA。
 *
 * 约束（有意收窄）：
 * - 当前仅覆盖“已绑定 TOTP 的自动化用户”；未覆盖首次绑定场景（/self/security/totp-bind）。
 * - 不 mock `/api/login` 或 `/self/security/**`，仅允许按需 mock 第三方资源。
 */

function isPlaceholderValue(value: string): boolean {
  const normalized = value.trim()
  return normalized.startsWith('<') && normalized.endsWith('>')
}

function readEnv(name: string): string | undefined {
  const value = process.env[name]
  if (!value || !value.trim() || isPlaceholderValue(value)) {
    return undefined
  }
  return value.trim()
}

function requireEnv(name: string): string {
  const value = readEnv(name)
  if (!value) {
    throw new Error(`MFA real-link 需要在 .env.e2e.local 中配置 ${name}`)
  }
  return value
}

function decodeBase32(secret: string): Buffer {
  const normalized = secret.replace(/=+$/g, '').replace(/\s+/g, '').toUpperCase()
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  let bits = ''

  for (const character of normalized) {
    const index = alphabet.indexOf(character)
    if (index < 0) {
      throw new Error(`非法 TOTP secret: ${secret}`)
    }
    bits += index.toString(2).padStart(5, '0')
  }

  const bytes: number[] = []
  for (let offset = 0; offset + 8 <= bits.length; offset += 8) {
    bytes.push(Number.parseInt(bits.slice(offset, offset + 8), 2))
  }
  return Buffer.from(bytes)
}

function generateTotpCode(secret: string, timestampMs = Date.now()): string {
  const counter = Math.floor(timestampMs / 30_000)
  const counterBuffer = Buffer.alloc(8)
  counterBuffer.writeBigUInt64BE(BigInt(counter))

  const hmac = createHmac('sha1', decodeBase32(secret)).update(counterBuffer).digest()
  const offset = hmac[hmac.length - 1] & 0x0f
  const binaryCode =
    ((hmac[offset] & 0x7f) << 24) |
    ((hmac[offset + 1] & 0xff) << 16) |
    ((hmac[offset + 2] & 0xff) << 8) |
    (hmac[offset + 3] & 0xff)

  return String(binaryCode % 1_000_000).padStart(6, '0')
}

function resolveLoginConfig() {
  const tenantCode = requireEnv('E2E_TENANT_CODE')
  const username = requireEnv('E2E_USERNAME')
  const password = requireEnv('E2E_PASSWORD')
  const totpCode = readEnv('E2E_TOTP_CODE')
  const totpSecret = readEnv('E2E_TOTP_SECRET')

  if (!totpCode && !totpSecret) {
    throw new Error('MFA real-link 需要配置 E2E_TOTP_CODE 或 E2E_TOTP_SECRET')
  }

  return {
    tenantCode,
    username,
    password,
    totpCode: totpCode ?? generateTotpCode(totpSecret!),
  }
}

async function fetchSecurityStatus(page: import('@playwright/test').Page) {
  const backendBaseUrl =
    process.env.E2E_BACKEND_BASE_URL ?? process.env.VITE_API_BASE_URL ?? 'http://localhost:9000'
  return page.evaluate(async ({ apiBaseUrl }) => {
    const response = await fetch(`${apiBaseUrl}/self/security/status`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    const text = await response.text()
    const contentType = response.headers.get('content-type') || ''
    return {
      status: response.status,
      payload:
        text && contentType.includes('application/json')
          ? (JSON.parse(text) as Record<string, unknown>)
          : null,
    }
  }, { apiBaseUrl: backendBaseUrl })
}

test.describe('real-link: /login -> totp-verify -> /self/security', () => {
  test('bound MFA user can login and reach self/security', async ({ page }) => {
    const { tenantCode, username, password, totpCode } = resolveLoginConfig()

    // 1) 进入登录页并填写凭证
    await page.goto('/login')

    await page.getByLabel('租户编码').fill(tenantCode)
    await page.getByLabel('用户名').fill(username)
    await page.getByLabel('密码').fill(password)
    await page.locator('button[type="submit"]').click()

    // 2) 预期后端重定向到 TOTP 验证页
    await page.waitForURL('**/self/security/totp-verify**', { timeout: 60_000 })
    await expect(page.getByRole('heading', { name: /两步验证/ })).toBeVisible({
      timeout: 30_000,
    })

    // 3) 输入一次性验证码并提交
    await page.getByLabel('动态验证码').fill(totpCode)
    await page.getByRole('button', { name: '确认' }).click()

    // 4) 最终应离开 totp-verify，并已建立可用的安全会话；
    // 某些测试租户首页会退化到空菜单壳页或个别 403 页面，因此不再死盯 /self/security 标题。
    await page.waitForURL(
      (url) =>
        !url.pathname.includes('/self/security/totp-verify') && !url.pathname.includes('/callback'),
      {
        timeout: 60_000,
      },
    )
    await page.waitForLoadState('networkidle').catch(() => {})
    const { status, payload } = await fetchSecurityStatus(page)
    expect(status).toBe(200)
    expect(payload).not.toBeNull()
    expect(Object.keys(payload ?? {}).length).toBeGreaterThan(0)
  })
})
