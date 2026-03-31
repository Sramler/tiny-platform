import { createHmac } from 'node:crypto'
import { expect, test } from '@playwright/test'

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

type MenuNode = {
  title?: string
  name?: string
  url?: string
  children?: MenuNode[]
}

function flattenMenu(nodes: MenuNode[]): MenuNode[] {
  const result: MenuNode[] = []
  const stack = [...nodes]
  while (stack.length > 0) {
    const current = stack.shift()!
    result.push(current)
    if (Array.isArray(current.children) && current.children.length > 0) {
      stack.unshift(...current.children)
    }
  }
  return result
}

test.describe('real-link: 平台登录菜单树', () => {
  test('platform_admin 登录后 /sys/menus/tree 不能退化为单节点', async ({ page }) => {
    test.setTimeout(240_000)
    const cfg = resolvePlatformLoginConfig()
    test.skip(!cfg, '需要 E2E_PLATFORM_USERNAME/PASSWORD 与 E2E_PLATFORM_TOTP_SECRET 或 TOTP_CODE')

    // 与 platform-vue-login.spec.ts 保持一致，避免额外 CSRF 监听与页签顺序引入竞态。
    await page.goto('/login')
    await page.getByRole('button', { name: '平台登录' }).click()
    await page.getByLabel('用户名').fill(cfg!.username)
    await page.getByLabel('密码').fill(cfg!.password)
    // 须在提交前挂上监听：登录成功进入壳层后会拉菜单；同时超时需覆盖 MFA 绑定/验证耗时。
    const menuResponsePromise = page.waitForResponse(
      (response) => response.url().includes('/sys/menus/tree') && response.request().method() === 'GET',
      { timeout: 180_000 },
    )
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

    if (page.url().includes('/self/security/totp-bind')) {
      const skipButton = page.getByRole('button', { name: '跳过' })
      if (await skipButton.isVisible().catch(() => false)) {
        await skipButton.click()
      }
      await page.waitForURL(
        (u) => !u.pathname.includes('/self/security/totp-bind') && !u.pathname.includes('/callback'),
        { timeout: 60_000 },
      )
    }

    if (page.url().includes('/self/security/totp-verify')) {
      await page.getByLabel('动态验证码').fill(cfg!.totpCode)
      await page.getByRole('button', { name: '确认' }).click()
      await page.waitForURL(
        (u) => !u.pathname.includes('/self/security/totp-verify') && !u.pathname.includes('/callback'),
        { timeout: 60_000 },
      )
    }

    const menuResponse = await menuResponsePromise
    const menuStatus = menuResponse.status()
    const menuPayload = await menuResponse.json().catch(() => null)

    expect(menuStatus).toBe(200)
    expect(Array.isArray(menuPayload)).toBeTruthy()
    const menuTree = menuPayload as MenuNode[]
    const flattened = flattenMenu(menuTree)

    // 退化场景通常只剩一个工作台节点，这里要求至少出现 2 个可见菜单节点。
    expect(flattened.length).toBeGreaterThan(1)

    const hasSystemUserEntry = flattened.some((item) => item.url === '/system/user' || item.name === 'user')
    const hasTenantEntry = flattened.some((item) => item.url === '/system/tenant' || item.name === 'tenant')
    expect(hasSystemUserEntry || hasTenantEntry).toBeTruthy()
  })
})
