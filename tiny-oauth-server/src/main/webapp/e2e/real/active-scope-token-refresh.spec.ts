/**
 * BFF Session real-link：切换 active scope 后由服务端 Session 直接承接新作用域，
 * 浏览器不得依赖 access token，也不得请求任何 OAuth2/OIDC 授权端点。
 */
import { expect, test } from '@playwright/test'
import { fetchSchedulingApi, loadIdentitySnapshot, openSessionApp } from './cross-tenant.helpers'

test.describe('real-link: active scope + BFF Session', () => {
  test('should_keep_session_stable_after_active_scope_switch', async ({ page }) => {
    await openSessionApp(page, 'primary')
    const before = await loadIdentitySnapshot(page)
    expect(Number(before.activeTenantId)).toBeGreaterThan(0)
    expect(before.activeScopeType).not.toBe('PLATFORM')

    let browserOAuthRequested = false
    page.on('request', (request) => {
      const decoded = decodeURIComponent(request.url())
      if (decoded.includes('/oauth2/authorize') && decoded.includes('prompt=none')) {
        browserOAuthRequested = true
      }
    })

    const switchResult = await fetchSchedulingApi<{
      tokenRefreshRequired?: boolean
      success?: boolean
    }>(page, '/sys/users/current/active-scope', {
      method: 'POST',
      body: { scopeType: 'TENANT' },
    })
    expect(switchResult.status, JSON.stringify(switchResult.payload)).toBe(200)
    const postJson = switchResult.payload ?? {}
    expect(postJson.success).not.toBe(false)
    expect(postJson.tokenRefreshRequired).toBe(false)

    const after = await loadIdentitySnapshot(page)
    expect(after.username).toBe(before.username)
    expect(after.activeTenantId).toBe(before.activeTenantId)
    expect(browserOAuthRequested).toBe(false)
  })
})
