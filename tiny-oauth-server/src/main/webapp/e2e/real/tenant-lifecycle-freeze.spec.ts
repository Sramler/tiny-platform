import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test, type Page } from '@playwright/test'
import {
  deriveTenantCodeForTenantScope,
  requireRealLinkPlatformTenantCode,
} from '../setup/real.global.setup'
import { openOidcDebug } from './cross-tenant.helpers'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const platformAuthPath = path.resolve(__dirname, '..', '.auth', 'platform-admin-user.json')

type SessionApiResult<T> = {
  ok: boolean
  status: number
  body: string
  payload: T | null
}

type SessionApiOptions = {
  method?: 'GET' | 'POST'
  body?: unknown
  idempotencyKey?: string
}

async function fetchSameOriginSessionApi<T>(
  page: Page,
  apiPath: string,
  options: SessionApiOptions = {},
): Promise<SessionApiResult<T>> {
  const { method = 'GET', body, idempotencyKey } = options
  return page.evaluate(
    async ({ path: requestPath, requestMethod, requestBody, idemKey }) => {
      const headers = new Headers({ Accept: 'application/json' })
      const activeTenantId = window.localStorage.getItem('app_active_tenant_id')
      if (activeTenantId) {
        headers.set('X-Active-Tenant-Id', activeTenantId)
      }
      if (idemKey) {
        headers.set('X-Idempotency-Key', idemKey)
      }
      if (requestMethod !== 'GET') {
        headers.set('Content-Type', 'application/json')
        const csrfResponse = await fetch('/csrf', {
          credentials: 'include',
          headers: { Accept: 'application/json' },
        })
        if (!csrfResponse.ok) {
          return {
            ok: false,
            status: csrfResponse.status,
            body: '刷新 CSRF token 失败',
            payload: null,
          }
        }
        const csrf = (await csrfResponse.json()) as { token?: string; headerName?: string }
        if (!csrf.token || !csrf.headerName) {
          return {
            ok: false,
            status: csrfResponse.status,
            body: 'CSRF 响应缺少 token/headerName',
            payload: null,
          }
        }
        headers.set(csrf.headerName, csrf.token)
      }

      const response = await fetch(requestPath, {
        method: requestMethod,
        credentials: 'include',
        headers,
        body: requestBody == null ? undefined : JSON.stringify(requestBody),
      })
      const responseBody = await response.text()
      const contentType = response.headers.get('content-type') ?? ''
      let payload: T | null = null
      if (responseBody && contentType.includes('application/json')) {
        try {
          payload = JSON.parse(responseBody) as T
        } catch {
          payload = null
        }
      }
      return {
        ok: response.ok,
        status: response.status,
        body: responseBody,
        payload,
      }
    },
    {
      path: apiPath,
      requestMethod: method,
      requestBody: body,
      idemKey: idempotencyKey,
    },
  )
}

function watchAuthorizationHeaders(page: Page, identityLabel: string) {
  const leaks: string[] = []
  page.on('request', (request) => {
    if (request.headers().authorization) {
      leaks.push(`${identityLabel}: ${request.method()} ${request.url()}`)
    }
  })
  return () => {
    expect(leaks, '纯 Session real-link 不得发送 Authorization header').toEqual([])
  }
}

async function assertSessionOnlyBrowserState(page: Page, identityLabel: string) {
  await page.goto('/', { waitUntil: 'domcontentloaded' })
  if (page.url().includes('/login')) {
    throw new Error(`${identityLabel} storageState 未能恢复有效 HttpOnly Session`)
  }

  const identity = await fetchSameOriginSessionApi<Record<string, unknown>>(
    page,
    '/sys/users/current',
  )
  expect(identity.status, `${identityLabel} Session 身份校验失败: ${identity.body}`).toBe(200)

  const cookies = await page.context().cookies()
  const sessionCookie = cookies.find((cookie) => cookie.name === 'JSESSIONID')
  expect(sessionCookie, `${identityLabel} 缺少 JSESSIONID`).toBeDefined()
  expect(sessionCookie?.httpOnly, `${identityLabel} JSESSIONID 必须为 HttpOnly`).toBe(true)
  expect(
    cookies.filter((cookie) =>
      /(^|[.:_-])(access|refresh|id)[._-]?token($|[.:_-])/i.test(cookie.name),
    ),
    `${identityLabel} Cookie 不得承载 access/refresh/id token`,
  ).toEqual([])

  const tokenEntries = await page.evaluate(() => {
    const entries: string[] = []
    for (const storage of [window.localStorage, window.sessionStorage]) {
      for (let index = 0; index < storage.length; index += 1) {
        const name = storage.key(index)
        if (!name) continue
        const value = storage.getItem(name) ?? ''
        if (
          name.toLowerCase().startsWith('oidc.user:') ||
          /(^|[.:_-])(access|refresh|id)[._-]?token($|[.:_-])/i.test(name) ||
          /"(?:access_token|refresh_token|id_token)"\s*:/i.test(value)
        ) {
          entries.push(name)
        }
      }
    }
    return entries
  })
  expect(tokenEntries, `${identityLabel} 浏览器存储不得持有 token`).toEqual([])
}

type TenantLookupPayload = {
  content?: Array<{ id?: number }>
}

async function getTenantIdByCode(page: Page, tenantCode: string) {
  return fetchSameOriginSessionApi<TenantLookupPayload>(
    page,
    `/sys/tenants?code=${encodeURIComponent(tenantCode)}&page=0&size=1`,
  )
}

async function getTenant(page: Page, tenantId: number) {
  return fetchSameOriginSessionApi<Record<string, unknown>>(page, `/sys/tenants/${tenantId}`)
}

async function transitionTenantLifecycle(
  page: Page,
  tenantId: number,
  action: 'freeze' | 'unfreeze',
) {
  const requestId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  return fetchSameOriginSessionApi<Record<string, unknown>>(
    page,
    `/sys/tenants/${tenantId}/${action}`,
    {
      method: 'POST',
      idempotencyKey: `e2e-tenant-lifecycle:${tenantId}:${action}:${requestId}`,
    },
  )
}

async function createTaskType(page: Page, codePrefix: string) {
  const suffix = `${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
  return fetchSameOriginSessionApi<{ id?: number }>(page, '/scheduling/task-type', {
    method: 'POST',
    idempotencyKey: `tenant-freeze:${codePrefix}:${suffix}`,
    body: {
      code: `${codePrefix}-${suffix}`,
      name: `Tenant freeze ${codePrefix} ${suffix}`,
      description: 'real-link tenant lifecycle fixture',
      executor: 'loggingTaskExecutor',
      enabled: true,
      defaultTimeoutSec: 0,
      defaultMaxRetry: 0,
    },
  })
}

function isConfigured(value: string | undefined): boolean {
  if (!value?.trim()) return false
  const normalized = value.trim()
  return !(normalized.startsWith('<') && normalized.endsWith('>'))
}

function requireFreezeIdentityConfiguration() {
  const required = [
    'E2E_PLATFORM_TENANT_CODE',
    'E2E_TENANT_CODE',
    'E2E_USERNAME',
    'E2E_PASSWORD',
    'E2E_TOTP_SECRET',
  ]
  const missing = required.filter((name) => !isConfigured(process.env[name]))
  if (missing.length > 0) {
    throw new Error(`租户冻结 real-link 缺少必需身份配置: ${missing.join(', ')}`)
  }
}

/**
 * real-link：租户生命周期冻结行为。平台治理请求由独立浏览器上下文使用
 * platform-admin storageState 的 HttpOnly Session；所有写请求先获取 CSRF，不读取或发送 token。
 */
test.describe('real-link: tenant lifecycle freeze', () => {
  test('frozen tenant rejects login and write is denied or guarded', async ({
    page,
    browser,
    baseURL,
  }) => {
    requireFreezeIdentityConfiguration()
    if (!baseURL) {
      throw new Error('租户冻结 real-link 缺少 Playwright baseURL')
    }

    const platformContext = await browser.newContext({
      baseURL,
      storageState: platformAuthPath,
    })
    const platformPage = await platformContext.newPage()
    const assertPlatformHasNoAuthorization = watchAuthorizationHeaders(platformPage, '平台管理员')
    const assertTenantHasNoAuthorization = watchAuthorizationHeaders(page, '租户用户')
    let tenantId: number | null = null
    let tenantCanBeRestored = false

    try {
      await openOidcDebug(platformPage, 'platform')
      await assertSessionOnlyBrowserState(platformPage, '平台管理员')
      const tenantCode = deriveTenantCodeForTenantScope(
        process.env.E2E_TENANT_CODE!.trim(),
        requireRealLinkPlatformTenantCode(process.env),
      )
      const tenantLookup = await getTenantIdByCode(platformPage, tenantCode)
      expect(tenantLookup.status, `平台管理员查询租户失败: ${tenantLookup.body}`).toBe(200)
      tenantId = tenantLookup.payload?.content?.[0]?.id ?? null
      if (tenantId === null) {
        throw new Error(`未找到租户: ${tenantCode}`)
      }

      const tenantResult = await getTenant(platformPage, tenantId)
      expect(tenantResult.status, `平台管理员读取租户 ${tenantId} 失败: ${tenantResult.body}`).toBe(
        200,
      )
      const tenantPayload = tenantResult.payload
      if (!tenantPayload) {
        throw new Error(`租户 ${tenantId} 详情响应不是 JSON`)
      }
      if (String(tenantPayload.lifecycleStatus ?? 'ACTIVE').toUpperCase() === 'DECOMMISSIONED') {
        throw new Error(`测试租户 ${tenantCode} 已下线，无法执行冻结/恢复回归`)
      }
      tenantCanBeRestored = true

      // 1) 确保 ACTIVE。
      if (String(tenantPayload.lifecycleStatus ?? 'ACTIVE').toUpperCase() === 'FROZEN') {
        const unfreezeResult = await transitionTenantLifecycle(platformPage, tenantId, 'unfreeze')
        expect(unfreezeResult.status, `unfreeze failed: ${unfreezeResult.body}`).toBe(200)
      }

      // 2) 租户用户的纯 Session 登录态下，写操作必须成功。
      // 前序安全用例可能有意触发 Session/权限版本轮换；本场景以真实登录恢复自己的身份，
      // 不把 globalSetup 时的同一个服务端 JSESSIONID 当成跨用例永久凭证。
      await openOidcDebug(page, 'primary')
      await assertSessionOnlyBrowserState(page, '租户用户')
      const created = await createTaskType(page, 'freeze-smoke')
      expect(created.status, `创建调度任务类型失败: ${created.body}`).toBe(200)
      expect(created.payload?.id).toBeTruthy()

      // 3) 平台管理员通过 Session + CSRF 冻结租户。
      const freezeResult = await transitionTenantLifecycle(platformPage, tenantId, 'freeze')
      expect(freezeResult.status, `freeze failed: ${freezeResult.body}`).toBe(200)

      // 3a) 平台治理只读白名单仍可用。
      const tenantAfterFreeze = await getTenant(platformPage, tenantId)
      expect(tenantAfterFreeze.status, tenantAfterFreeze.body).toBe(200)
      expect(tenantAfterFreeze.payload?.id).toBe(tenantId)
      expect(String(tenantAfterFreeze.payload?.lifecycleStatus ?? '')).toBe('FROZEN')

      const summaryAfterFreeze = await fetchSameOriginSessionApi<Record<string, unknown>>(
        platformPage,
        `/sys/audit/authentication/summary?tenantId=${tenantId}`,
      )
      expect(summaryAfterFreeze.status, summaryAfterFreeze.body).toBe(200)

      const exportAfterFreeze = await fetchSameOriginSessionApi<never>(
        platformPage,
        `/sys/audit/authentication/export?tenantId=${tenantId}&reason=${encodeURIComponent('freeze-e2e-check')}&ticketId=${encodeURIComponent('E2E-FREEZE-1')}`,
      )
      expect(exportAfterFreeze.status, exportAfterFreeze.body).toBe(200)

      // 4a) 冻结后的租户写操作必须被治理守卫拒绝。
      const writeAfterFreeze = await fetchSameOriginSessionApi<Record<string, unknown>>(
        page,
        '/scheduling/task-type',
        {
          method: 'POST',
          body: {
            code: `freeze-after-${Date.now()}`,
            name: 'after freeze',
            description: 'should be denied when tenant is frozen',
            executor: 'loggingTaskExecutor',
            enabled: true,
            defaultTimeoutSec: 0,
            defaultMaxRetry: 0,
          },
          idempotencyKey: `freeze-write-${Date.now()}`,
        },
      )
      expect([403, 409]).toContain(writeAfterFreeze.status)

      const freezeUserPassword = 'FreezeUser#2026'
      const freezeUserSuffix = String(Date.now() % 100_000_000)
      const createUserAfterFreeze = await fetchSameOriginSessionApi<Record<string, unknown>>(
        page,
        '/sys/users',
        {
          method: 'POST',
          body: {
            username: `fzu_${freezeUserSuffix}`,
            nickname: 'freeze user after tenant frozen',
            password: freezeUserPassword,
            confirmPassword: freezeUserPassword,
            enabled: true,
            accountNonExpired: true,
            accountNonLocked: true,
            credentialsNonExpired: true,
            roleIds: [],
          },
          idempotencyKey: `freeze-user-create-${Date.now()}`,
        },
      )
      expect([403, 409]).toContain(createUserAfterFreeze.status)

      // 4b) 无态浏览器重新登录已冻结租户，必须被拒绝。
      const loginContext = await browser.newContext({
        baseURL,
        storageState: undefined,
      })
      const loginPage = await loginContext.newPage()
      try {
        await loginPage.goto('/login')
        await loginPage.getByLabel(/租户编码/i).fill(tenantCode)
        await loginPage.getByLabel(/用户名/i).fill(process.env.E2E_USERNAME!.trim())
        await loginPage.getByLabel(/密码/i).fill(process.env.E2E_PASSWORD!.trim())
        await loginPage.getByRole('button', { name: '登录租户', exact: true }).click()
        await loginPage
          .waitForURL(/\/(login|callback|error)/, { waitUntil: 'networkidle', timeout: 15_000 })
          .catch(() => {})
        const url = loginPage.url()
        const stillOnLogin = url.includes('/login')
        const hasError = await loginPage
          .locator('.error-message')
          .isVisible()
          .catch(() => false)
        expect(
          stillOnLogin || hasError || url.includes('error'),
          '冻结租户用户登录应被拒绝（停留在登录页或显示错误）',
        ).toBe(true)
      } finally {
        await loginContext.close()
      }
    } finally {
      // 5) 无论中间断言是否失败，都用同一平台 Session 恢复 ACTIVE。
      if (tenantCanBeRestored && tenantId !== null) {
        const currentTenant = await getTenant(platformPage, tenantId).catch(() => null)
        if (
          currentTenant?.status === 200 &&
          String(currentTenant.payload?.lifecycleStatus ?? '').toUpperCase() === 'FROZEN'
        ) {
          const restoreResult = await transitionTenantLifecycle(platformPage, tenantId, 'unfreeze')
          expect(restoreResult.status, `restore unfreeze failed: ${restoreResult.body}`).toBe(200)
        }
      }
      assertPlatformHasNoAuthorization()
      assertTenantHasNoAuthorization()
      await platformContext.close()
    }
  })
})
