import { createHmac } from 'node:crypto'
import { expect, test } from '@playwright/test'

/**
 * real-link：从 /login 的「平台登录」页签走完整 Session 登录（无 tenantCode）
 *
 * 覆盖 Login.vue 平台模式与后端 TenantContextFilter 对无租户 POST /login 的语义；
 * Vitest 单测无法替代本链路。
 *
 * 依赖：与 global setup 一致，使用 E2E_PLATFORM_USERNAME / E2E_PLATFORM_PASSWORD /
 * E2E_PLATFORM_TOTP_SECRET（或 E2E_PLATFORM_TOTP_CODE）。
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

function resolvePlatformLoginConfig() {
  const username = readEnv('E2E_PLATFORM_USERNAME')
  const password = readEnv('E2E_PLATFORM_PASSWORD')
  const totpCode = readEnv('E2E_PLATFORM_TOTP_CODE')
  const totpSecret = readEnv('E2E_PLATFORM_TOTP_SECRET')

  if (!username || !password) {
    return null
  }
  if (!totpCode && !totpSecret) {
    return null
  }

  return {
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

async function fetchCurrentUser(page: import('@playwright/test').Page) {
  const backendBaseUrl =
    process.env.E2E_BACKEND_BASE_URL ?? process.env.VITE_API_BASE_URL ?? 'http://localhost:9000'
  return page.evaluate(async ({ apiBaseUrl }) => {
    const response = await fetch(`${apiBaseUrl}/sys/users/current`, {
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

async function fetchTenantControlPlane(page: import('@playwright/test').Page) {
  const backendBaseUrl =
    process.env.E2E_BACKEND_BASE_URL ?? process.env.VITE_API_BASE_URL ?? 'http://localhost:9000'
  return page.evaluate(async ({ apiBaseUrl }) => {
    const response = await fetch(`${apiBaseUrl}/sys/tenants?page=0&size=5`, {
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

test.describe('real-link: Login.vue 平台登录', () => {
  test('平台页签提交后应离开 /login 且不因缺少租户失败', async ({ page }) => {
    const cfg = resolvePlatformLoginConfig()
    test.skip(!cfg, '需要 E2E_PLATFORM_USERNAME/PASSWORD 与 E2E_PLATFORM_TOTP_SECRET 或 TOTP_CODE')

    await page.goto('/login')

    await page.getByRole('button', { name: '平台登录' }).click()
    await expect(page.getByLabel('租户编码')).toHaveCount(0)

    await page.getByLabel('用户名').fill(cfg!.username)
    await page.getByLabel('密码').fill(cfg!.password)
    await page.getByRole('button', { name: '登录平台' }).click()

    await page.waitForURL(
      (url) => {
        if (url.pathname.includes('/self/security/totp-verify')) return true
        if (url.pathname.includes('/login')) {
          return url.searchParams.get('error') != null || url.searchParams.get('message') != null
        }
        return !url.pathname.includes('/login')
      },
      { timeout: 60_000 },
    )

    const url = page.url()
    if (url.includes('/login')) {
      throw new Error(`平台登录仍停留在 /login 且带错误 query: ${url}`)
    }

    if (url.includes('/self/security/totp-verify')) {
      await page.getByLabel('动态验证码').fill(cfg!.totpCode)
      await page.getByRole('button', { name: '确认' }).click()
      await page.waitForURL(
        (u) => !u.pathname.includes('/self/security/totp-verify') && !u.pathname.includes('/callback'),
        { timeout: 60_000 },
      )
    }

    await page.waitForLoadState('networkidle').catch(() => {})
    const securityStatus = await fetchSecurityStatus(page)
    expect(securityStatus.status).toBe(200)
    expect(securityStatus.payload).not.toBeNull()
    expect(typeof securityStatus.payload?.totpBound).toBe('boolean')
    expect(typeof securityStatus.payload?.totpActivated).toBe('boolean')
    expect(typeof securityStatus.payload?.requireTotp).toBe('boolean')

    const currentUser = await fetchCurrentUser(page)
    expect(currentUser.status).toBe(200)
    expect(currentUser.payload).not.toBeNull()
    expect(currentUser.payload?.activeScopeType).toBe('PLATFORM')

    const tenantControlPlane = await fetchTenantControlPlane(page)
    expect(tenantControlPlane.status).toBe(200)
    expect(tenantControlPlane.payload).not.toBeNull()
    expect(Array.isArray(tenantControlPlane.payload?.content)).toBe(true)
  })
})
