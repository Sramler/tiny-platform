/**
 * BFF Session real-link：切换 active scope 后由服务端 Session 直接承接新作用域，
 * 浏览器不得依赖 access token 或触发 OIDC silent renew。
 */
import { expect, test } from '@playwright/test'
import { loadIdentitySnapshot, openOidcDebug } from './cross-tenant.helpers'

test.describe('real-link: active scope + BFF Session', () => {
  test('should_keep_session_stable_after_active_scope_switch', async ({ page }) => {
    await openOidcDebug(page, 'primary')
    const before = await loadIdentitySnapshot(page)
    expect(Number(before.activeTenantId)).toBeGreaterThan(0)
    expect(before.activeScopeType).not.toBe('PLATFORM')

    let silentRenewRequested = false
    page.on('request', (request) => {
      const decoded = decodeURIComponent(request.url())
      if (decoded.includes('/oauth2/authorize') && decoded.includes('prompt=none')) {
        silentRenewRequested = true
      }
    })

    await page.locator('.header-bar .dropdown').click()
    await page.getByText('切换作用域', { exact: true }).click()
    const scopeModal = page.locator('.ant-modal:visible')
    await expect(scopeModal.locator('.ant-modal-title').filter({ hasText: '切换作用域' })).toBeVisible()

    const [postResp] = await Promise.all([
      page.waitForResponse(
        (response) =>
          response.url().includes('/sys/users/current/active-scope') &&
          response.request().method() === 'POST',
      ),
      scopeModal.locator('.ant-modal-footer button.ant-btn-primary').click(),
    ])
    const postText = await postResp.text()
    expect(postResp.status(), postText).toBe(200)
    const postJson = JSON.parse(postText) as { tokenRefreshRequired?: boolean; success?: boolean }
    expect(postJson.success).not.toBe(false)
    expect(postJson.tokenRefreshRequired).toBe(false)

    await expect(scopeModal).toHaveCount(0)
    const after = await loadIdentitySnapshot(page)
    expect(after.username).toBe(before.username)
    expect(after.activeTenantId).toBe(before.activeTenantId)
    expect(silentRenewRequested).toBe(false)
  })
})
