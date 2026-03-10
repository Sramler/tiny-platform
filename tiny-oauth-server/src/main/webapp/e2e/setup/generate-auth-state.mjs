import { createHmac } from 'node:crypto'
import path from 'node:path'
import { chromium } from '@playwright/test'

function readEnv(name) {
  const value = process.env[name]
  return value && value.trim() !== '' ? value : undefined
}

function isPlaceholderValue(value) {
  const normalized = value.trim()
  return normalized.startsWith('<') && normalized.endsWith('>')
}

function requireEnv(name) {
  const value = readEnv(name)
  if (!value || isPlaceholderValue(value)) {
    throw new Error(`缺少 ${name}，请在 .env.e2e.local 或 CI secrets 中提供真实测试值`)
  }
  return value
}

const frontendPort = Number(readEnv('E2E_FRONTEND_PORT') ?? 5173)
const frontendBaseURL = readEnv('E2E_FRONTEND_BASE_URL') ?? `http://localhost:${frontendPort}`
const tenantCode = requireEnv('E2E_TENANT_CODE')
const username = requireEnv('E2E_USERNAME')
const password = requireEnv('E2E_PASSWORD')
const totpCodeOverride = readEnv('E2E_TOTP_CODE')
const totpSecret = totpCodeOverride ? readEnv('E2E_TOTP_SECRET') : requireEnv('E2E_TOTP_SECRET')
const authStatePath = process.env.E2E_AUTH_STATE_PATH
const landingPath = '/OIDCDebug'

if (!authStatePath) {
  throw new Error('缺少 E2E_AUTH_STATE_PATH，无法生成 Playwright 登录态')
}

function decodeBase32(secret) {
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

  const bytes = []
  for (let offset = 0; offset + 8 <= bits.length; offset += 8) {
    bytes.push(Number.parseInt(bits.slice(offset, offset + 8), 2))
  }
  return Buffer.from(bytes)
}

function generateTotpCode(secret, timestampMs = Date.now()) {
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

async function waitForOidcIdentity(page) {
  await page.waitForFunction(() => {
    const oidcKey = Object.keys(window.localStorage).find((key) => key.startsWith('oidc.user:'))
    if (!oidcKey) {
      return false
    }
    const rawUser = window.localStorage.getItem(oidcKey)
    if (!rawUser) {
      return false
    }
    try {
      const user = JSON.parse(rawUser)
      return Boolean(user?.access_token)
    } catch {
      return false
    }
  }, { timeout: 90_000 })
}

async function main() {
  const browser = await chromium.launch({ headless: true })
  const context = await browser.newContext()
  const page = await context.newPage()

  try {
    await page.addInitScript(
      ({ seedTenantCode }) => {
        window.localStorage.setItem('app_tenant_code', seedTenantCode)
        window.localStorage.removeItem('app_tenant_id')
        window.localStorage.setItem('sider-collapsed', 'false')
      },
      { seedTenantCode: tenantCode }
    )

    await page.goto(`${frontendBaseURL}/login?redirect=${encodeURIComponent(landingPath)}`)
    await page.getByRole('heading', { name: '欢迎登录' }).waitFor({ timeout: 90_000 })

    await page.getByLabel('用户名').fill(username)
    await page.getByLabel('密码').fill(password)
    await page.getByRole('button', { name: '登录' }).click()

    await page.waitForURL(/\/(callback|self\/security\/totp-(bind|verify)|OIDCDebug)/, {
      timeout: 90_000,
    })

    if (page.url().includes('/self/security/totp-bind')) {
      const skipButton = page.getByRole('button', { name: '跳过' })
      if (await skipButton.isVisible().catch(() => false)) {
        await skipButton.click()
      }
    }

    if (page.url().includes('/self/security/totp-verify')) {
      const currentTotpCode = totpCodeOverride ?? generateTotpCode(totpSecret)
      await page.getByLabel('动态验证码').fill(currentTotpCode)
      await page.getByRole('button', { name: '确认' }).click()
    }

    await page.waitForURL(/\/(callback|OIDCDebug|exception\/(403|404)|$)/, {
      timeout: 90_000,
    })

    await waitForOidcIdentity(page)

    if (!page.url().includes(landingPath)) {
      await page.goto(`${frontendBaseURL}${landingPath}`)
      const oidcDebugHeading = page.getByRole('heading', { name: 'OIDC 调试工具' })
      const oidcDebugVisible = await oidcDebugHeading.isVisible({ timeout: 5_000 }).catch(() => false)
      if (!oidcDebugVisible) {
        // 某些租户未初始化菜单资源时，/OIDCDebug 会退化到“菜单为空”的壳页；此时只要浏览器中已有真实 OIDC 登录态即可持久化 storageState。
        await waitForOidcIdentity(page)
      }
    }

    await context.storageState({ path: path.resolve(authStatePath) })
  } finally {
    await context.close()
    await browser.close()
  }
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
