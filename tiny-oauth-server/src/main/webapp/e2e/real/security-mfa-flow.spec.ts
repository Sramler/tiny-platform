import { expect, test } from '@playwright/test'
import { fetchSchedulingApi, openOidcDebug } from './cross-tenant.helpers'

/**
 * real-link（post-login 草稿）：已认证后的安全中心 + TOTP 信息读取（依赖 storageState + 真实后端）
 *
 * 约束：
 * - 不 mock `/self/security/**`、`/api/self/security/**` 等 first-party API。
 * - 使用 `playwright.real.config.ts` 中的 storageState（由 real.global.setup.ts 生成），代表“已登录”的自动化身份。
 * - 当前仅覆盖“登录之后”的安全状态与 TOTP 预绑定信息读取，不包含从 `/login` 起步的完整 MFA 链路。
 */

async function fetchSelfSecurity<T>(
  page: import('@playwright/test').Page,
  path: string,
  options: { method?: 'GET' | 'POST'; body?: unknown } = {},
): Promise<{ status: number; payload: T | null }> {
  const { method = 'GET', body } = options
  return fetchSchedulingApi<T>(page, path, { method, body })
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
    await openOidcDebug(page, 'primary')
    await expectAuthenticatedSecurityStatus(page)
  })

  test('authenticated user can start TOTP pre-bind flow via real backend', async ({ page }) => {
    await openOidcDebug(page, 'primary')

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
