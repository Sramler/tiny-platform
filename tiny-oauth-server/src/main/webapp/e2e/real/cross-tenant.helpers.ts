import { createHmac } from 'node:crypto'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, type Browser, type BrowserContext, type Page } from '@playwright/test'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const frontendBaseUrl = process.env.E2E_FRONTEND_BASE_URL ?? 'http://localhost:5173'
const backendBaseUrl = process.env.E2E_BACKEND_BASE_URL ?? 'http://localhost:9000'

export const primaryAuthStatePath = path.resolve(__dirname, '..', '.auth', 'scheduling-user.json')
export const secondaryAuthStatePath = path.resolve(__dirname, '..', '.auth', 'tenant-b-user.json')
export const readonlyAuthStatePath = path.resolve(
  __dirname,
  '..',
  '.auth',
  'scheduling-readonly-user.json',
)

export type AuthIdentityKind = 'primary' | 'secondary' | 'readonly' | 'platform'

function isPlaceholderValue(value: string): boolean {
  const normalized = value.trim()
  return normalized.startsWith('<') && normalized.endsWith('>')
}

function isConfiguredValue(value: string | undefined): boolean {
  if (!value || !value.trim()) {
    return false
  }
  return !isPlaceholderValue(value)
}

export function isCrossTenantIdentityConfigured(): boolean {
  return ['E2E_TENANT_CODE_B', 'E2E_USERNAME_B', 'E2E_PASSWORD_B', 'E2E_TOTP_SECRET_B'].every((name) =>
    isConfiguredValue(process.env[name]),
  )
}

export function isReadonlyIdentityConfigured(): boolean {
  return ['E2E_USERNAME_READONLY', 'E2E_PASSWORD_READONLY', 'E2E_TOTP_SECRET_READONLY'].every(
    (name) => isConfiguredValue(process.env[name]),
  )
}

function readConfiguredEnv(...names: string[]): string | undefined {
  for (const name of names) {
    const value = process.env[name]
    if (isConfiguredValue(value)) {
      return value!.trim()
    }
  }
  return undefined
}

function deriveReadonlyTenantCode(): string | undefined {
  const explicitReadonlyTenantCode = readConfiguredEnv('E2E_TENANT_CODE_READONLY')
  if (explicitReadonlyTenantCode) {
    return explicitReadonlyTenantCode
  }

  const primaryTenantCode = readConfiguredEnv('E2E_TENANT_CODE')
  if (!primaryTenantCode) {
    return undefined
  }

  const platformTenantCode = (readConfiguredEnv('E2E_PLATFORM_TENANT_CODE') ?? 'default').toLowerCase()
  if (primaryTenantCode.toLowerCase() !== platformTenantCode) {
    return primaryTenantCode
  }

  const candidate = `${primaryTenantCode.toLowerCase()}-t`
  return candidate.length <= 32 ? candidate : candidate.slice(0, 32)
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

type LoginIdentity = {
  mode: 'TENANT' | 'PLATFORM'
  tenantCode?: string
  username: string
  password: string
  totpCode?: string
  totpSecret?: string
}

function resolveLoginIdentity(kind: AuthIdentityKind): LoginIdentity | null {
  if (kind === 'secondary') {
    const tenantCode = readConfiguredEnv('E2E_TENANT_CODE_B')
    const username = readConfiguredEnv('E2E_USERNAME_B')
    const password = readConfiguredEnv('E2E_PASSWORD_B')
    const totpSecret = readConfiguredEnv('E2E_TOTP_SECRET_B')
    const totpCode = readConfiguredEnv('E2E_TOTP_CODE_B')
    if (!tenantCode || !username || !password || (!totpSecret && !totpCode)) {
      return null
    }
    return { mode: 'TENANT', tenantCode, username, password, totpCode, totpSecret }
  }

  if (kind === 'readonly') {
    const tenantCode = deriveReadonlyTenantCode()
    const username = readConfiguredEnv('E2E_USERNAME_READONLY')
    const password = readConfiguredEnv('E2E_PASSWORD_READONLY')
    const totpSecret = readConfiguredEnv('E2E_TOTP_SECRET_READONLY')
    const totpCode = readConfiguredEnv('E2E_TOTP_CODE_READONLY')
    if (!tenantCode || !username || !password || (!totpSecret && !totpCode)) {
      return null
    }
    return { mode: 'TENANT', tenantCode, username, password, totpCode, totpSecret }
  }

  if (kind === 'platform') {
    const username = readConfiguredEnv('E2E_PLATFORM_USERNAME')
    const password = readConfiguredEnv('E2E_PLATFORM_PASSWORD')
    const totpSecret = readConfiguredEnv('E2E_PLATFORM_TOTP_SECRET')
    const totpCode = readConfiguredEnv('E2E_PLATFORM_TOTP_CODE')
    if (!username || !password || (!totpSecret && !totpCode)) {
      return null
    }
    return { mode: 'PLATFORM', username, password, totpCode, totpSecret }
  }

  const tenantCode = readConfiguredEnv('E2E_TENANT_CODE')
  const username = readConfiguredEnv('E2E_USERNAME')
  const password = readConfiguredEnv('E2E_PASSWORD')
  const totpSecret = readConfiguredEnv('E2E_TOTP_SECRET')
  const totpCode = readConfiguredEnv('E2E_TOTP_CODE')
  if (!tenantCode || !username || !password || (!totpSecret && !totpCode)) {
    return null
  }
  return { mode: 'TENANT', tenantCode, username, password, totpCode, totpSecret }
}

async function hasOidcIdentity(page: Page): Promise<boolean> {
  return page
    .evaluate(() => {
      const oidcKey = Object.keys(window.localStorage).find((key) => key.startsWith('oidc.user:'))
      if (!oidcKey) {
        return false
      }
      const rawUser = window.localStorage.getItem(oidcKey)
      if (!rawUser) {
        return false
      }
      try {
        const user = JSON.parse(rawUser) as { access_token?: string }
        return Boolean(user.access_token)
      } catch {
        return false
      }
    })
    .catch(() => false)
}

export async function waitForOidcIdentity(page: Page, timeout = 60_000) {
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
      const user = JSON.parse(rawUser) as { access_token?: string }
      return Boolean(user.access_token)
    } catch {
      return false
    }
  }, { timeout })
}

async function loginWithIdentity(page: Page, identity: LoginIdentity) {
  await page.goto(`/login?redirect=${encodeURIComponent('/OIDCDebug')}`)

  if (page.url().includes('/login')) {
    const loginHeading = page.getByRole('heading', { name: '欢迎登录' })
    const tenantCodeInput = page.getByLabel('租户编码')
    const usernameInput = page.getByLabel('用户名')
    const passwordInput = page.getByLabel('密码')
    const loginSurfaceReady = await Promise.race([
      Promise.any([
        loginHeading.waitFor({ timeout: 15_000 }),
        usernameInput.waitFor({ timeout: 15_000 }),
        tenantCodeInput.waitFor({ timeout: 15_000 }),
        passwordInput.waitFor({ timeout: 15_000 }),
      ])
        .then(() => true)
        .catch(() => false),
      page
        .waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15_000 })
        .then(() => false)
        .catch(() => false),
      waitForOidcIdentity(page, 15_000)
        .then(() => false)
        .catch(() => false),
    ])

    if (!loginSurfaceReady && page.url().includes('/login') && !(await hasOidcIdentity(page))) {
      throw new Error(`实时登录页未就绪: ${page.url()}`)
    }
  }

  if (page.url().includes('/login')) {
    if (identity.mode === 'PLATFORM') {
      await page.getByRole('button', { name: '平台登录' }).click()
      await page.getByLabel('租户编码').waitFor({ state: 'detached', timeout: 10_000 }).catch(() => {})
    } else {
      await page.getByRole('button', { name: '租户登录' }).click()
      await page.getByLabel('租户编码').fill(identity.tenantCode!, { force: true })
    }

    await page.getByLabel('用户名').fill(identity.username)
    await page.getByLabel('密码').fill(identity.password)
    await page
      .getByRole('button', { name: identity.mode === 'PLATFORM' ? '登录平台' : '登录租户' })
      .click()
  }

  await Promise.race([
    page.waitForURL(/\/(callback|self\/security\/totp-(bind|verify)|OIDCDebug|exception\/403)/, {
      timeout: 90_000,
    }),
    page
      .waitForURL((url) => url.pathname.includes('/login') && url.searchParams.has('error'), {
        timeout: 90_000,
      })
      .then(async () => {
        const errorMessage =
          (await page
            .locator('.ant-alert-message,.ant-message-notice-content,.login-form-error')
            .first()
            .textContent()
            .catch(() => null)) ??
          decodeURIComponent(new URL(page.url()).searchParams.get('message') ?? '')
        throw new Error(`实时登录失败: ${errorMessage || page.url()}`)
      }),
  ])

  if (page.url().includes('/self/security/totp-bind')) {
    const skipButton = page.getByRole('button', { name: '跳过' })
    if (await skipButton.isVisible().catch(() => false)) {
      await skipButton.click()
    }
  }

  if (page.url().includes('/self/security/totp-verify')) {
    const code = identity.totpCode ?? generateTotpCode(identity.totpSecret!)
    await page.getByLabel('动态验证码').fill(code)
    await page.getByRole('button', { name: '确认' }).click()
  }

  await page.waitForURL(
    (url) => !url.pathname.includes('/callback') && !url.pathname.includes('/self/security/totp-verify'),
    {
      timeout: 90_000,
    },
  )
}

async function gotoOidcDebug(page: Page) {
  try {
    await page.goto('/OIDCDebug', { waitUntil: 'domcontentloaded' })
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    if (!message.includes('ERR_ABORTED')) {
      throw error
    }
  }
}

export async function openOidcDebug(page: Page, kind: AuthIdentityKind = 'primary') {
  const loginIdentity = resolveLoginIdentity(kind)

  for (let attempt = 0; attempt < 3; attempt += 1) {
    await gotoOidcDebug(page)
    const oidcDebugHeading = page.getByRole('heading', { name: /OIDC 调试工具/ })
    const oidcDebugVisible = await oidcDebugHeading.isVisible({ timeout: 5_000 }).catch(() => false)
    const existingIdentity = await hasOidcIdentity(page)

    if (
      !existingIdentity &&
      (!oidcDebugVisible || page.url().includes('/login') || page.url().includes('/callback')) &&
      loginIdentity
    ) {
      await loginWithIdentity(page, loginIdentity)
      await gotoOidcDebug(page)
    } else if (!oidcDebugVisible || page.url().includes('/login') || page.url().includes('/callback')) {
      await gotoOidcDebug(page)
    }

    await waitForOidcIdentity(page, 90_000)
    await page.waitForLoadState('networkidle').catch(() => {})
    await page.waitForTimeout(1_000)
    if (await hasOidcIdentity(page)) {
      return
    }
  }

  await waitForOidcIdentity(page, 90_000)
}

type OidcIdentitySnapshot = {
  accessToken: string
  activeTenantId: string
}

async function loadIdentitySnapshot(page: Page): Promise<OidcIdentitySnapshot> {
  return page.evaluate(() => {
    const oidcKey = Object.keys(window.localStorage).find((key) => key.startsWith('oidc.user:'))
    if (!oidcKey) {
      throw new Error('未找到 OIDC 登录态，无法构造跨租户请求')
    }
    const rawUser = window.localStorage.getItem(oidcKey)
    if (!rawUser) {
      throw new Error(`OIDC 存储为空: ${oidcKey}`)
    }

    const user = JSON.parse(rawUser) as {
      access_token?: string
      profile?: { activeTenantId?: number | string }
    }
    if (!user.access_token) {
      throw new Error('OIDC 用户缺少 access_token')
    }

    const activeTenantId =
      window.localStorage.getItem('app_active_tenant_id') ?? String(user.profile?.activeTenantId ?? '')
    if (!activeTenantId) {
      throw new Error('OIDC 用户缺少 activeTenantId')
    }

    return {
      accessToken: user.access_token,
      activeTenantId,
    }
  })
}

type SchedulingFetchOptions = {
  method?: 'GET' | 'POST'
  body?: unknown
  overrideTenantId?: string
  idempotencyKey?: string
}

type SchedulingFetchResponse<T> = {
  status: number
  payload: T | null
}

export async function fetchSchedulingApi<T>(
  page: Page,
  apiPath: string,
  options: SchedulingFetchOptions = {},
): Promise<SchedulingFetchResponse<T>> {
  await waitForOidcIdentity(page)
  const identity = await loadIdentitySnapshot(page)
  const { method = 'GET', body, overrideTenantId, idempotencyKey } = options

  return page.evaluate(
    async ({ apiBaseUrl, path, auth, activeTenantId, apiMethod, apiBody, idemKey }) => {
      const headers = new Headers({
        Accept: 'application/json',
        Authorization: `Bearer ${auth.accessToken}`,
        'X-Active-Tenant-Id': activeTenantId,
      })
      if (idemKey) {
        headers.set('X-Idempotency-Key', idemKey)
      }
      if (apiMethod !== 'GET') {
        headers.set('Content-Type', 'application/json')
      }

      const response = await fetch(`${apiBaseUrl}${path}`, {
        method: apiMethod,
        headers,
        credentials: 'include',
        body: apiBody == null ? undefined : JSON.stringify(apiBody),
      })
      const text = await response.text()
      const contentType = response.headers.get('content-type') || ''
      return {
        status: response.status,
        payload:
          text && contentType.includes('application/json')
            ? (JSON.parse(text) as T)
            : (null as T | null),
      }
    },
    {
      apiBaseUrl: backendBaseUrl,
      path: apiPath,
      auth: identity,
      activeTenantId: overrideTenantId ?? identity.activeTenantId,
      apiMethod: method,
      apiBody: body,
      idemKey: idempotencyKey,
    },
  )
}

export async function createTaskType(page: Page, codePrefix: string) {
  const uniqueSuffix = `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
  const payload = {
    code: `${codePrefix}-${uniqueSuffix}`,
    name: `Cross tenant ${codePrefix} ${uniqueSuffix}`,
    description: 'real-link cross-tenant ownership fixture',
    executor: 'loggingTaskExecutor',
    enabled: true,
    defaultTimeoutSec: 0,
    defaultMaxRetry: 0,
  }

  const response = await fetchSchedulingApi<{ id?: number; recordTenantId?: number | string }>(
    page,
    '/scheduling/task-type',
    {
      method: 'POST',
      body: payload,
      idempotencyKey: `cross-tenant-${codePrefix}-${uniqueSuffix}`,
    },
  )

  expect(response.status).toBe(200)
  expect(response.payload?.id).toBeTruthy()
  return {
    id: Number(response.payload!.id),
    ownerTenantId:
      response.payload?.recordTenantId == null ? null : String(response.payload.recordTenantId),
  }
}

export async function openSecondaryAuthenticatedPage(
  browser: Browser,
  storageStatePath: string,
  kind: AuthIdentityKind = 'secondary',
): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext({
    storageState: storageStatePath,
    baseURL: frontendBaseUrl,
  })
  const page = await context.newPage()
  await openOidcDebug(page, kind)
  return { context, page }
}

export function expectTenantMismatchPayload(payload: Record<string, unknown> | null) {
  expect(payload).not.toBeNull()
  const error = String((payload as { error?: unknown }).error ?? '')
  const errorDescription = String((payload as { error_description?: unknown }).error_description ?? '')
  expect(`${error} ${errorDescription}`.toLowerCase()).toContain('tenant')
}
