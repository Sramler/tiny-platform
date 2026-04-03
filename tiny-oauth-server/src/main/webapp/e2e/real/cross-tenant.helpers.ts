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

export async function waitForOidcIdentity(page: Page) {
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
  }, { timeout: 60_000 })
}

export async function openOidcDebug(page: Page) {
  await page.goto('/OIDCDebug')
  const oidcDebugHeading = page.getByRole('heading', { name: /OIDC 调试工具/ })
  const oidcDebugVisible = await oidcDebugHeading.isVisible({ timeout: 5_000 }).catch(() => false)
  if (!oidcDebugVisible || page.url().includes('/login') || page.url().includes('/callback')) {
    await page.goto('/OIDCDebug')
  }

  // 某些测试租户并未初始化完整菜单树，但只要浏览器已恢复真实 OIDC 登录态，
  // API 级 real-link 断言仍然可以继续。
  await waitForOidcIdentity(page)
  await page.waitForLoadState('networkidle').catch(() => {})
  await page.waitForTimeout(1_000)
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
): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext({
    storageState: storageStatePath,
    baseURL: frontendBaseUrl,
  })
  const page = await context.newPage()
  await openOidcDebug(page)
  return { context, page }
}

export function expectTenantMismatchPayload(payload: Record<string, unknown> | null) {
  expect(payload).not.toBeNull()
  const error = String((payload as { error?: unknown }).error ?? '')
  const errorDescription = String((payload as { error_description?: unknown }).error_description ?? '')
  expect(`${error} ${errorDescription}`.toLowerCase()).toContain('tenant')
}
