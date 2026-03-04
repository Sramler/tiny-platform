import { expect, test } from '@playwright/test'

function buildCsrfResponse() {
  return {
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({
      token: 'csrf-token',
      parameterName: '_csrf',
      headerName: 'X-XSRF-TOKEN',
    }),
  }
}

test.describe('auth flow pages', () => {
  test('router guard redirects protected route to login with internal redirect only', async ({ page }) => {
    await page.route('**/api/csrf', async (route) => {
      await route.fulfill(buildCsrfResponse())
    })

    await page.goto('/OIDCDebug')
    await expect(page.getByRole('heading', { name: '欢迎登录' })).toBeVisible()
    await expect(page).toHaveURL(/\/login\?redirect=(%2F|\/)OIDCDebug$/)
    await expect(page.locator('input[name="redirect"]')).toHaveValue('/OIDCDebug')
  })

  test('login page sanitizes redirect and posts normalized tenant before jumping to totp verify', async ({
    page,
  }) => {
    let posted: URLSearchParams | null = null

    await page.route('**/api/csrf', async (route) => {
      await route.fulfill(buildCsrfResponse())
    })

    await page.route('**/api/login', async (route) => {
      posted = new URLSearchParams(route.request().postData() ?? '')
      await route.fulfill({
        status: 302,
        headers: {
          location: '/self/security/totp-verify?redirect=%2F',
        },
        body: '',
      })
    })

    await page.goto('/login?redirect=https%3A%2F%2Fevil.com%2Fcallback')
    await page.getByLabel('租户编码').fill('Tiny-Prod')
    await page.getByLabel('用户名').fill('alice')
    await page.getByLabel('密码').fill('secret')
    await page.getByRole('button', { name: '登录' }).click()

    await page.waitForURL('**/self/security/totp-verify?redirect=%2F')
    await expect(page.getByRole('heading', { name: '两步验证' })).toBeVisible()

    expect(posted?.get('tenantCode')).toBe('tiny-prod')
    expect(posted?.get('username')).toBe('alice')
    expect(posted?.get('password')).toBe('secret')
    expect(posted?.get('authenticationProvider')).toBe('LOCAL')
    expect(posted?.get('authenticationType')).toBe('PASSWORD')
    expect(posted?.get('redirect')).toBe('/')
    expect(posted?.get('_csrf')).toBe('csrf-token')
  })

  test('totp bind page renders secret and skip submit keeps redirect internal', async ({ page }) => {
    let posted: URLSearchParams | null = null

    await page.route('**/api/csrf', async (route) => {
      await route.fulfill(buildCsrfResponse())
    })

    await page.route('**/api/self/security/status', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          disableMfa: false,
          forceMfa: false,
        }),
      })
    })

    await page.route('**/api/self/security/totp/pre-bind', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          secretKey: 'ABC123',
          qrCodeDataUrl: 'data:image/png;base64,totp',
        }),
      })
    })

    await page.route('**/api/self/security/totp/skip', async (route) => {
      posted = new URLSearchParams(route.request().postData() ?? '')
      await route.fulfill({
        status: 302,
        headers: {
          location: '/login?message=已跳过',
        },
        body: '',
      })
    })

    await page.goto('/self/security/totp-bind?redirect=https%3A%2F%2Fevil.com%2Fnext')

    await expect(page.locator('.secret-value')).toHaveText('ABC123')
    await expect(page.locator('.qrcode-img')).toHaveAttribute('src', 'data:image/png;base64,totp')

    await page.getByRole('button', { name: '跳过' }).click()
    await page.waitForURL('**/login?message=*')

    expect(posted?.get('redirect')).toBe('/')
    expect(posted?.get('_csrf')).toBe('csrf-token')
  })

  test('totp verify page keeps redirect internal when posting code', async ({ page }) => {
    let posted: URLSearchParams | null = null

    await page.route('**/api/csrf', async (route) => {
      await route.fulfill(buildCsrfResponse())
    })

    await page.route('**/api/self/security/totp/check-form', async (route) => {
      posted = new URLSearchParams(route.request().postData() ?? '')
      await route.fulfill({
        status: 302,
        headers: {
          location: '/self/security/totp-verify?error=%E9%AA%8C%E8%AF%81%E7%A0%81%E9%94%99%E8%AF%AF&redirect=%2F',
        },
        body: '',
      })
    })

    await page.goto('/self/security/totp-verify?redirect=https%3A%2F%2Fevil.com%2Fnext')
    await page.getByLabel('动态验证码').fill('123456')
    await page.getByRole('button', { name: '确认' }).click()

    await page.waitForURL('**/self/security/totp-verify?error=*')
    await expect(page.getByText('验证码错误')).toBeVisible()

    expect(posted?.get('totpCode')).toBe('123456')
    expect(posted?.get('redirect')).toBe('/')
    expect(posted?.get('_csrf')).toBe('csrf-token')
  })

  test('exception pages render details and can navigate back to safe routes', async ({ page }) => {
    await page.route('**/api/csrf', async (route) => {
      await route.fulfill(buildCsrfResponse())
    })

    await page.goto('/exception/401?path=%2Fapi%2Fsecure&message=expired&traceId=trace-123')
    await expect(page.getByRole('heading', { name: '登录状态已失效' })).toBeVisible()
    await expect(page.getByText('expired')).toBeVisible()
    await expect(page.getByText('trace-123')).toBeVisible()
    await page.getByRole('button', { name: '立即登录' }).click()
    await page.waitForURL('**/login')

    await page.goto('/unknown-page')
    await page.waitForURL('**/exception/404')
    await expect(page.getByRole('heading', { name: '页面未找到' })).toBeVisible()
    await page.getByRole('button', { name: '返回首页' }).click()
    await page.waitForURL('**/')
  })
})
