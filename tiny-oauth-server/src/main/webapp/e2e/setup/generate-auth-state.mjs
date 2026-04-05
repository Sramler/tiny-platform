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
const backendPort = Number(readEnv('E2E_BACKEND_PORT') ?? 9000)
const backendBaseURL = readEnv('E2E_BACKEND_BASE_URL') ?? `http://localhost:${backendPort}`
const tenantCode = requireEnv('E2E_TENANT_CODE')
const username = requireEnv('E2E_USERNAME')
const password = requireEnv('E2E_PASSWORD')
const loginMode = (readEnv('E2E_LOGIN_MODE') ?? 'TENANT').trim().toUpperCase()
const totpCodeOverride = readEnv('E2E_TOTP_CODE')
const totpSecret = totpCodeOverride ? readEnv('E2E_TOTP_SECRET') : requireEnv('E2E_TOTP_SECRET')
const authStatePath = process.env.E2E_AUTH_STATE_PATH
const landingPath = '/OIDCDebug'

if (!authStatePath) {
  throw new Error('缺少 E2E_AUTH_STATE_PATH，无法生成 Playwright 登录态')
}

if (loginMode !== 'TENANT' && loginMode !== 'PLATFORM') {
  throw new Error(`非法 E2E_LOGIN_MODE=${loginMode}，仅支持 TENANT 或 PLATFORM`)
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

/**
 * 持久化前强制对齐 app_active_tenant_id：initScript 会清空该键，若 HeaderBar 与菜单请求竞态，
 * storageState 会缺少租户上下文，导致 TenantContextFilter 拒绝 /sys/menus/tree 等首屏请求（real-link 401）。
 */
async function syncActiveTenantIdBeforeSave(page, apiBase) {
  await page.evaluate(async (api) => {
    const oidcKey = Object.keys(window.localStorage).find((key) => key.startsWith('oidc.user:'))
    if (!oidcKey) {
      return
    }
    const rawUser = window.localStorage.getItem(oidcKey)
    if (!rawUser) {
      return
    }
    let accessToken
    try {
      accessToken = JSON.parse(rawUser).access_token
    } catch {
      return
    }
    if (!accessToken) {
      return
    }

    function decodeJwtPayload(accessTokenInner) {
      try {
        const parts = accessTokenInner.split('.')
        if (parts.length < 2) {
          return null
        }
        const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
        const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
        const json = atob(padded)
        return JSON.parse(json)
      } catch {
        return null
      }
    }

    function pickTenantId(...candidates) {
      for (const candidate of candidates) {
        if (candidate == null) {
          continue
        }
        const text = String(candidate).trim()
        if (text !== '' && text !== 'undefined') {
          return text
        }
      }
      return ''
    }

    const user = JSON.parse(rawUser)
    const payload = decodeJwtPayload(accessToken)
    let tenantId = pickTenantId(
      window.localStorage.getItem('app_active_tenant_id'),
      user.profile?.activeTenantId,
      payload?.activeTenantId,
    )

    if (!tenantId) {
      const r = await fetch(`${api}/sys/users/current`, {
        credentials: 'include',
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
      })
      if (r.ok) {
        try {
          const body = await r.json()
          tenantId = pickTenantId(body.activeTenantId)
        } catch {
          // ignore
        }
      }
    }

    if (tenantId) {
      window.localStorage.setItem('app_active_tenant_id', tenantId)
    }
  }, apiBase)
}

/**
 * 租户登录模式下若换票得到 PLATFORM 作用域或缺少 activeTenantId，调度/menu real-link 会在首屏即失败。
 * 在持久化 storageState 前强断言，避免 CI 带着“假绿路径”的残缺 JWT 继续跑用例。
 */
async function assertTenantAccessTokenClaims(page) {
  if (loginMode !== 'TENANT') {
    return
  }
  const message = await page.evaluate(() => {
    function decodeJwtPayload(token) {
      try {
        const parts = token.split('.')
        if (parts.length < 2) {
          return null
        }
        const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
        const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=')
        return JSON.parse(atob(padded))
      } catch {
        return null
      }
    }

    const oidcKey = Object.keys(window.localStorage).find((key) => key.startsWith('oidc.user:'))
    if (!oidcKey) {
      return '未找到 oidc.user:* localStorage 项'
    }
    const rawUser = window.localStorage.getItem(oidcKey)
    if (!rawUser) {
      return 'OIDC 存储为空'
    }
    let accessToken
    try {
      accessToken = JSON.parse(rawUser).access_token
    } catch {
      return '无法解析 OIDC 用户 JSON'
    }
    if (!accessToken) {
      return '缺少 access_token'
    }
    const payload = decodeJwtPayload(accessToken)
    if (!payload) {
      return '无法解析 access_token JWT payload'
    }
    if (payload.activeScopeType === 'PLATFORM') {
      return 'access_token.activeScopeType 为 PLATFORM（期望非 PLATFORM 租户态）。请核对 E2E_TENANT_CODE、种子与登录流程。'
    }
    const tid = payload.activeTenantId
    if (tid == null || Number(tid) <= 0) {
      return `access_token 缺少有效 activeTenantId（当前=${String(tid)}）`
    }
    return null
  })
  if (message) {
    throw new Error(`generate-auth-state (E2E_LOGIN_MODE=TENANT): ${message}`)
  }
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
        window.localStorage.removeItem('app_active_tenant_id')
        window.localStorage.setItem('sider-collapsed', 'false')
      },
      { seedTenantCode: tenantCode }
    )

    await page.goto(`${frontendBaseURL}/login?redirect=${encodeURIComponent(landingPath)}`)
    await page.getByRole('heading', { name: '欢迎登录' }).waitFor({ timeout: 90_000 })

    if (loginMode === 'PLATFORM') {
      await page.getByRole('button', { name: '平台登录' }).click()
      await page.getByLabel('租户编码').waitFor({ state: 'detached', timeout: 10_000 }).catch(() => {})
    } else {
      await page.getByRole('button', { name: '租户登录' }).click()
      const tenantInput = page.getByLabel('租户编码')
      await tenantInput.fill(tenantCode, { force: true })
    }

    await page.getByLabel('用户名').fill(username)
    await page.getByLabel('密码').fill(password)
    await page.getByRole('button', { name: loginMode === 'PLATFORM' ? '登录平台' : '登录租户' }).click()

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
    await syncActiveTenantIdBeforeSave(page, backendBaseURL)

    if (!page.url().includes(landingPath)) {
      await page.goto(`${frontendBaseURL}${landingPath}`)
      const oidcDebugHeading = page.getByRole('heading', { name: 'OIDC 调试工具' })
      const oidcDebugVisible = await oidcDebugHeading.isVisible({ timeout: 5_000 }).catch(() => false)
      if (!oidcDebugVisible) {
        // 某些租户未初始化菜单资源时，/OIDCDebug 会退化到“菜单为空”的壳页；此时只要浏览器中已有真实 OIDC 登录态即可持久化 storageState。
        await waitForOidcIdentity(page)
      }
      await syncActiveTenantIdBeforeSave(page, backendBaseURL)
    }

    await assertTenantAccessTokenClaims(page)
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
