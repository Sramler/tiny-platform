import { expect, test } from '@playwright/test'
import { openOidcDebug } from './cross-tenant.helpers'

/**
 * real-link（post-login 草稿）：已认证后的安全中心 + TOTP 信息读取（依赖 storageState + 真实后端）
 *
 * 约束：
 * - 不 mock `/self/security/**`、`/api/self/security/**` 等 first-party API。
 * - 使用 `playwright.real.config.ts` 中的 storageState（由 real.global.setup.ts 生成），代表“已登录”的自动化身份。
 * - 当前仅覆盖“登录之后”的安全状态与 TOTP 预绑定信息读取，不包含从 `/login` 起步的完整 MFA 链路。
 */

const backendBaseUrl = process.env.E2E_BACKEND_BASE_URL ?? 'http://localhost:9000'

async function fetchSelfSecurity<T>(
  page: import('@playwright/test').Page,
  path: string,
  options: { method?: 'GET' | 'POST'; body?: unknown } = {},
): Promise<{ status: number; payload: T | null }> {
  const { method = 'GET', body } = options
  return page.evaluate(
    async ({ apiBaseUrl, apiPath, apiMethod, apiBody }) => {
      const oidcKey = Object.keys(window.localStorage).find((key) => key.startsWith('oidc.user:'))
      if (!oidcKey) {
        throw new Error('未找到 OIDC 登录态，无法调用安全中心接口')
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

      const headers = new Headers({
        Accept: 'application/json',
        Authorization: `Bearer ${user.access_token}`,
      })
      if (activeTenantId) {
        headers.set('X-Active-Tenant-Id', activeTenantId)
      }
      if (apiMethod !== 'GET') {
        headers.set('Content-Type', 'application/json')
      }
      const resp = await fetch(`${apiBaseUrl}${apiPath}`, {
        method: apiMethod,
        headers,
        credentials: 'include',
        body: apiBody == null ? undefined : JSON.stringify(apiBody),
      })
      const text = await resp.text()
      const contentType = resp.headers.get('content-type') || ''
      const payload =
        text && contentType.includes('application/json') ? (JSON.parse(text) as T) : (null as T | null)
      return { status: resp.status, payload }
    },
    { apiBaseUrl: backendBaseUrl, apiPath: path, apiMethod: method, apiBody: body },
  )
}

async function expectAuthenticatedSecurityStatus(page: import('@playwright/test').Page) {
  const { status, payload } = await fetchSelfSecurity<Record<string, unknown>>(
    page,
    '/self/security/status',
  )
  expect(status).toBe(200)
  expect(payload).not.toBeNull()
  expect(Object.keys(payload ?? {}).length).toBeGreaterThan(0)
}

test.describe('real-link (post-login): 自助安全中心 + TOTP 信息读取', () => {
  test('authenticated user can load current security status from a real browser session', async ({
    page,
  }) => {
    await openOidcDebug(page)
    await expectAuthenticatedSecurityStatus(page)
  })

  test('authenticated user can start TOTP pre-bind flow via real backend', async ({ page }) => {
    await openOidcDebug(page)

    // 通过真实接口获取预绑定信息（secret / otpauthUri / qrCodeDataUrl）
    const { status, payload } = await fetchSelfSecurity<Record<string, unknown>>(
      page,
      '/self/security/totp/pre-bind',
    )
    expect([200, 400, 409]).toContain(status)

    if (status === 200) {
      expect(payload).not.toBeNull()
      const data = payload as Record<string, unknown>
      if (data.success === false) {
        expect(String(data.error ?? '')).toContain('已绑定')
      } else {
        // 在“可绑定”场景下，后端应返回 secretKey 或 otpauthUri，用于前端展示二维码
        expect(
          'secretKey' in data || 'otpauthUri' in data || 'qrCodeDataUrl' in data,
        ).toBe(true)
      }
    } else {
      expect(payload).not.toBeNull()
      expect((payload as Record<string, unknown>).success).toBe(false)
    }
  })
})
