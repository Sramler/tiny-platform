import { createHmac } from 'node:crypto'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test as setup, type Page } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const authStatePath = path.resolve(__dirname, '../.auth/scheduling-user.json')

function requireEnv(name: string) {
  const value = process.env[name]
  if (!value || value.trim() === '') {
    throw new Error(`缺少 ${name}，请通过环境变量或 .env.e2e.local 提供真实测试值`)
  }
  const trimmed = value.trim()
  if (trimmed.startsWith('<') && trimmed.endsWith('>')) {
    throw new Error(`缺少 ${name}，当前仍为占位符值: ${trimmed}`)
  }
  return trimmed
}

const tenantCode = requireEnv('E2E_TENANT_CODE')
const username = requireEnv('E2E_USERNAME')
const password = requireEnv('E2E_PASSWORD')
const totpSecret = requireEnv('E2E_TOTP_SECRET')

if (tenantCode.trim().toLowerCase() === 'default') {
  throw new Error(
    'E2E_TENANT_CODE 不允许使用 default：当前环境 default 租户可能处于 FROZEN 状态，会导致 /login 被拒绝。请使用专用未冻结测试租户编码。'
  )
}
const landingPath = '/OIDCDebug'

function decodeBase32(secret: string) {
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

function generateTotpCode(secret: string, timestampMs = Date.now()) {
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

async function handleTotpIfRequired(page: Page) {
  if (page.url().includes('/self/security/totp-bind')) {
    const skipButton = page.getByRole('button', { name: '跳过' })
    if (await skipButton.isVisible().catch(() => false)) {
      await skipButton.click()
    }
  }

  if (page.url().includes('/self/security/totp-verify')) {
    const currentTotpCode = process.env.E2E_TOTP_CODE ?? generateTotpCode(totpSecret)
    await page.getByLabel('动态验证码').fill(currentTotpCode)
    await page.getByRole('button', { name: '确认' }).click()
  }
}

setup('authenticate real scheduling e2e user', async ({ page }) => {
  await fs.mkdir(path.dirname(authStatePath), { recursive: true })

  await page.addInitScript(
    ({ seedTenantCode }) => {
      window.localStorage.setItem('app_tenant_code', seedTenantCode)
      window.localStorage.removeItem('app_active_tenant_id')
      window.localStorage.setItem('sider-collapsed', 'false')
    },
    { seedTenantCode: tenantCode }
  )

  await page.goto(`/login?redirect=${encodeURIComponent(landingPath)}`)
  await expect(page.getByRole('heading', { name: '欢迎登录' })).toBeVisible()

  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(password)
  await page.locator('button[type="submit"]').click()

  await page.waitForURL(/\/(callback|self\/security\/totp-(bind|verify)|OIDCDebug)/, {
    timeout: 90_000,
  })
  await handleTotpIfRequired(page)

  await page.waitForURL(/\/(callback|OIDCDebug|exception\/(403|404)|$)/, {
    timeout: 90_000,
  })
  if (!page.url().includes(landingPath)) {
    await page.goto(landingPath)
  }
  await expect(page.getByRole('heading', { name: 'OIDC 调试工具' })).toBeVisible({
    timeout: 90_000,
  })

  await page.context().storageState({ path: authStatePath })
})
