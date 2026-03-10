import { createHmac } from 'node:crypto'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test as setup, type Page } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const authStatePath = path.resolve(__dirname, '../.auth/scheduling-user.json')

const tenantCode = process.env.E2E_TENANT_CODE ?? 'default'
const username = process.env.E2E_USERNAME ?? 'admin'
const password = process.env.E2E_PASSWORD ?? 'admin'
const totpSecret = process.env.E2E_TOTP_SECRET ?? 'JBSWY3DPEHPK3PXP'
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
      window.localStorage.removeItem('app_tenant_id')
      window.localStorage.setItem('sider-collapsed', 'false')
    },
    { seedTenantCode: tenantCode }
  )

  await page.goto(`/login?redirect=${encodeURIComponent(landingPath)}`)
  await expect(page.getByRole('heading', { name: '欢迎登录' })).toBeVisible()

  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录' }).click()

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
