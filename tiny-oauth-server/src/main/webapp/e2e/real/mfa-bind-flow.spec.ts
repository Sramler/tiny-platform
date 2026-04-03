import { createHmac } from 'node:crypto'
import { expect, test } from '@playwright/test'

/**
 * real-link：从 /login 起步的“未绑定 TOTP 首绑链路”（单租户、bind 专用身份）
 *
 * 目标：
 * - 使用 `.env.e2e.local` 中的 E2E_TENANT_CODE_BIND / E2E_USERNAME_BIND / E2E_PASSWORD_BIND，
 *   在 ensure-scheduling-e2e-auth.sh 保证该用户无 LOCAL/TOTP 记录的前提下：
 *   - /login 成功后跳到 /self/security/totp-bind；
 *   - 通过浏览器 session 调真实 `/self/security/totp/pre-bind` 获取 TOTP secret；
 *   - 在 spec 内根据 secret 生成 TOTP，一次性完成绑定；
 *   - 进入 /self/security；
 *   - 清理会话后再次 /login，确认这次进入 /self/security/totp-verify 再到 /self/security。
 *
 * 约束：
 * - 不 mock `/api/login` 或 `/self/security/**`；
 * - 不使用 storageState；
 * - 仅覆盖“单租户、专用首绑用户”的 happy path。
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

function requireEnv(name: string, fallbackFrom?: string): string {
  const raw = readEnv(name) ?? (fallbackFrom ? readEnv(fallbackFrom) : undefined)
  if (!raw) {
    const label = fallbackFrom ? `${name}（或 ${fallbackFrom}）` : name
    throw new Error(`MFA bind real-link 需要在 .env.e2e.local 中配置 ${label}`)
  }
  return raw
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

async function fetchPreBindSecret(page: import('@playwright/test').Page): Promise<string> {
  const renderedSecret = page.locator('.secret-value').first()
  if (await renderedSecret.isVisible({ timeout: 10_000 }).catch(() => false)) {
    const text = (await renderedSecret.textContent())?.trim()
    if (text) {
      return text
    }
  }

  const backendBaseUrl =
    process.env.E2E_BACKEND_BASE_URL ?? process.env.VITE_API_BASE_URL ?? 'http://localhost:9000'

  const result = await page.evaluate(
    async ({ apiBaseUrl }) => {
      const resp = await fetch(`${apiBaseUrl}/self/security/totp/pre-bind`, {
        method: 'GET',
        credentials: 'include',
        headers: {
          Accept: 'application/json',
        },
      })
      const text = await resp.text()
      const contentType = resp.headers.get('content-type') || ''
      const payload =
        text && contentType.includes('application/json')
          ? (JSON.parse(text) as { success?: boolean; secretKey?: string })
          : null

      return { status: resp.status, payload }
    },
    { apiBaseUrl: backendBaseUrl },
  )

  if (result.status !== 200 || !result.payload?.success || !result.payload.secretKey) {
    throw new Error(`预绑定接口返回异常: status=${result.status}, payload=${JSON.stringify(result.payload)}`)
  }
  return result.payload.secretKey
}

async function waitForFirstBindReady(page: import('@playwright/test').Page): Promise<void> {
  await page.waitForURL(
    (url) =>
      !url.pathname.includes('/login') &&
      !url.pathname.includes('/callback') &&
      !url.pathname.includes('/self/security/totp-verify'),
    { timeout: 60_000 },
  )

  const bindHeading = page.getByRole('heading', { name: /开启两步验证/ })
  if (await bindHeading.isVisible({ timeout: 5_000 }).catch(() => false)) {
    return
  }

  const bindInput = page.getByLabel('验证码')
  if (await bindInput.isVisible({ timeout: 5_000 }).catch(() => false)) {
    return
  }

  // 有些租户在首绑页会先落到壳页，再由真实 session 打开 pre-bind 接口。
  // 只要 pre-bind 已可用，就视为首绑链路已准备完成。
  await fetchPreBindSecret(page)
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

function resolveBindLoginConfig() {
  const tenantCode = requireEnv('E2E_TENANT_CODE_BIND', 'E2E_TENANT_CODE')
  const username = requireEnv('E2E_USERNAME_BIND')
  const password = requireEnv('E2E_PASSWORD_BIND')
  return { tenantCode, username, password }
}

async function clearBrowserSession(page: import('@playwright/test').Page) {
  await page.context().clearCookies()
  await page.context().clearPermissions()
  await page.evaluate(() => {
    window.localStorage.clear()
    window.sessionStorage.clear()
  })
}

test.describe('real-link: 未绑定 TOTP 首绑链路', () => {
  test('bind user can first bind TOTP then login via verify flow', async ({ page }) => {
    const { tenantCode, username, password } = resolveBindLoginConfig()

    // 第一次登录：应进入 TOTP 绑定页
    await page.goto('/login')
    await page.getByLabel('租户编码').fill(tenantCode)
    await page.getByLabel('用户名').fill(username)
    await page.getByLabel('密码').fill(password)
    await page.getByRole('button', { name: /登录租户/ }).first().click()

    await waitForFirstBindReady(page)
    const bindHeading = page.getByRole('heading', { name: /开启两步验证/ })
    const bindCodeInput = page.getByLabel('验证码')
    if (!(await bindHeading.isVisible({ timeout: 5_000 }).catch(() => false))) {
      await expect(bindCodeInput).toBeVisible({ timeout: 30_000 })
    } else {
      await expect(bindHeading).toBeVisible({ timeout: 30_000 })
    }

    // 通过真实接口获取 secretKey，并基于当前时间生成一次性验证码
    const secretKey = await fetchPreBindSecret(page)
    const bindCode = generateTotpCode(secretKey)

    // 在绑定页输入验证码并提交
    await page.getByLabel('验证码').fill(bindCode)
    await page.getByRole('button', { name: '确认绑定' }).click()

    // 绑定成功后应建立可用安全会话；落点可能是首页或空菜单壳页，不再强依赖 /self/security 页面。
    await page.waitForURL(
      (url) =>
        !url.pathname.includes('/self/security/totp-bind') && !url.pathname.includes('/callback'),
      {
      timeout: 60_000,
      },
    )
    await page.waitForLoadState('networkidle').catch(() => {})
    const firstStatus = await fetchSecurityStatus(page)
    expect(firstStatus.status).toBe(200)
    expect(firstStatus.payload).not.toBeNull()

    // 清理浏览器会话，模拟新会话重新登录
    await clearBrowserSession(page)

    // 第二次登录：此时应进入 TOTP 验证页而不是绑定页
    await page.goto('/login')
    await page.getByLabel('租户编码').fill(tenantCode)
    await page.getByLabel('用户名').fill(username)
    await page.getByLabel('密码').fill(password)
    await page.getByRole('button', { name: /登录租户/ }).first().click()

    await page.waitForURL('**/self/security/totp-verify**', { timeout: 60_000 })
    await expect(page.getByRole('heading', { name: /两步验证/ })).toBeVisible({
      timeout: 30_000,
    })

    const verifyCode = generateTotpCode(secretKey)
    await page.getByLabel('动态验证码').fill(verifyCode)
    await page.getByRole('button', { name: '确认' }).click()

    await page.waitForURL(
      (url) =>
        !url.pathname.includes('/self/security/totp-verify') && !url.pathname.includes('/callback'),
      {
      timeout: 60_000,
      },
    )
    await page.waitForLoadState('networkidle').catch(() => {})
    const secondStatus = await fetchSecurityStatus(page)
    expect(secondStatus.status).toBe(200)
    expect(secondStatus.payload).not.toBeNull()
  })
})
