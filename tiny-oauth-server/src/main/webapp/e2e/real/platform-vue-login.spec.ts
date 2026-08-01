import { createHmac } from 'node:crypto'
import { expect, request as playwrightRequest, test, type Page } from '@playwright/test'

/**
 * real-link：从 /login 的「平台登录」页签走完整 Session 登录（无 tenantCode）
 *
 * 覆盖 Login.vue 平台模式与后端 TenantContextFilter 对无租户 POST /auth/login 的语义；
 * Vitest 单测无法替代本链路。
 *
 * 依赖：与 global setup 一致，使用 E2E_PLATFORM_USERNAME / E2E_PLATFORM_PASSWORD /
 * E2E_PLATFORM_TOTP_SECRET（或 E2E_PLATFORM_TOTP_CODE）。
 */

function isPlaceholderValue(value: string): boolean {
  const normalized = value.trim()
  return normalized.startsWith('<') && normalized.endsWith('>')
}

function readEnv(name: string): string | undefined {
  const value = process.env[name]
  if (!value || !value.trim() || isPlaceholderValue(value)) {
    return undefined
  }
  return value.trim()
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

function resolvePlatformLoginConfig() {
  const username = readEnv('E2E_PLATFORM_USERNAME')
  const password = readEnv('E2E_PLATFORM_PASSWORD')
  const totpCode = readEnv('E2E_PLATFORM_TOTP_CODE')
  const totpSecret = readEnv('E2E_PLATFORM_TOTP_SECRET')

  if (!username || !password) {
    return null
  }
  if (!totpCode && !totpSecret) {
    return null
  }

  return {
    username,
    password,
    totpCode: totpCode ?? generateTotpCode(totpSecret!),
  }
}

type ObservedBusinessRequest = {
  method: string
  origin: string
  pathname: string
  headers: Record<string, string>
}

function isBusinessPath(pathname: string): boolean {
  return (
    pathname === '/csrf' ||
    pathname.startsWith('/auth/') ||
    pathname.startsWith('/self/') ||
    pathname.startsWith('/sys/')
  )
}

function observeBusinessRequests(page: Page): ObservedBusinessRequest[] {
  const observed: ObservedBusinessRequest[] = []
  page.on('request', (request) => {
    const url = new URL(request.url())
    if (!isBusinessPath(url.pathname)) return
    observed.push({
      method: request.method(),
      origin: url.origin,
      pathname: url.pathname,
      headers: request.headers(),
    })
  })
  return observed
}

async function readBrowserTokenStorage(page: Page) {
  return page.evaluate(() => {
    const readArea = (area: Storage, areaName: 'localStorage' | 'sessionStorage') =>
      Array.from({ length: area.length }, (_, index) => {
        const key = area.key(index) ?? ''
        return { area: areaName, key, value: area.getItem(key) ?? '' }
      })

    const tokenKeyPattern = /oidc\.user:|(?:^|[._:-])(?:access|refresh|id)[_-]?token(?:$|[._:-])/i
    const tokenValuePattern = /"(?:access_token|refresh_token|id_token)"\s*:/i
    return [
      ...readArea(window.localStorage, 'localStorage'),
      ...readArea(window.sessionStorage, 'sessionStorage'),
    ]
      .filter(({ key, value }) => tokenKeyPattern.test(key) || tokenValuePattern.test(value))
      .map(({ area, key }) => `${area}:${key}`)
  })
}

async function fetchSecurityStatus(page: Page) {
  return page.evaluate(async () => {
    const response = await fetch('/self/security/status', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    const text = await response.text()
    const contentType = response.headers.get('content-type') || ''
    return {
      status: response.status,
      payload:
        text && contentType.includes('application/json')
          ? (JSON.parse(text) as Record<string, unknown>)
          : null,
    }
  })
}

async function fetchCurrentUser(page: Page) {
  return page.evaluate(async () => {
    const response = await fetch('/sys/users/current', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    const text = await response.text()
    const contentType = response.headers.get('content-type') || ''
    return {
      status: response.status,
      payload:
        text && contentType.includes('application/json')
          ? (JSON.parse(text) as Record<string, unknown>)
          : null,
    }
  })
}

async function fetchRuntimeMenuTree(page: Page) {
  return page.evaluate(async () => {
    const response = await fetch('/sys/menus/tree', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    const text = await response.text()
    const contentType = response.headers.get('content-type') || ''
    return {
      status: response.status,
      payload:
        text && contentType.includes('application/json')
          ? (JSON.parse(text) as Record<string, unknown>)
          : null,
    }
  })
}

async function logoutWithFreshCsrf(page: Page) {
  return page.evaluate(async () => {
    const csrfResponse = await fetch('/csrf', {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    const csrf = (await csrfResponse.json()) as { token?: string; headerName?: string }
    if (!csrfResponse.ok || !csrf.token || !csrf.headerName) {
      throw new Error(`无法取得 logout CSRF: HTTP ${csrfResponse.status}`)
    }

    const logoutResponse = await fetch('/auth/logout', {
      method: 'POST',
      credentials: 'include',
      redirect: 'manual',
      headers: {
        Accept: 'application/json',
        [csrf.headerName]: csrf.token,
      },
    })
    return {
      csrfHeaderName: csrf.headerName.toLowerCase(),
      csrfToken: csrf.token,
      logoutStatus: logoutResponse.status,
    }
  })
}

test.describe('real-link: Login.vue 平台登录', () => {
  test('平台页签应建立纯 HttpOnly Session，并在带 CSRF 注销后彻底失效', async ({
    page,
    context,
  }, testInfo) => {
    const cfg = resolvePlatformLoginConfig()
    if (!cfg) {
      throw new Error(
        'platform-vue-login real-link 缺少 E2E_PLATFORM_USERNAME/PASSWORD 与 E2E_PLATFORM_TOTP_SECRET 或 TOTP_CODE；纯 Session 登录门禁不允许以 skip 代替验证。',
      )
    }

    const configuredBaseURL = testInfo.project.use.baseURL
    if (typeof configuredBaseURL !== 'string') {
      throw new Error('platform-vue-login real-link 需要 Playwright baseURL')
    }
    const frontendOrigin = new URL(configuredBaseURL).origin
    const observedRequests = observeBusinessRequests(page)

    await page.goto('/login')

    // 先取得匿名 CSRF；若容器同时建立了匿名 Session，登录必须轮换其 id。
    const anonymousCsrfStatus = await page.evaluate(async () => {
      const response = await fetch('/csrf', {
        credentials: 'include',
        headers: { Accept: 'application/json' },
      })
      return response.status
    })
    expect(anonymousCsrfStatus).toBe(200)
    const anonymousSessionCookie = (await context.cookies(frontendOrigin)).find(
      (cookie) => cookie.name === 'JSESSIONID',
    )
    // CookieCsrfTokenRepository 本身不需要创建 HttpSession。若容器已经存在匿名 Session，
    // 登录必须轮换 id；若尚无 Session，则登录直接创建新的 HttpOnly Session，同样不存在
    // 可被固定的旧 id。不要为了测试强制制造匿名 Session，避免放大匿名 Session 存储压力。
    if (anonymousSessionCookie) {
      expect(anonymousSessionCookie.httpOnly).toBe(true)
    }

    await page.getByRole('button', { name: '平台登录' }).click()
    await expect(page.getByLabel('租户编码')).toHaveCount(0)

    await page.getByLabel('用户名').fill(cfg.username)
    await page.getByLabel('密码').fill(cfg.password)
    await page.getByRole('button', { name: '登录平台' }).click()

    await page.waitForURL(
      (url) => {
        if (url.pathname.includes('/self/security/totp-verify')) return true
        if (url.pathname.includes('/login')) {
          return url.searchParams.get('error') != null || url.searchParams.get('message') != null
        }
        return !url.pathname.includes('/login')
      },
      { timeout: 60_000 },
    )

    const url = page.url()
    if (url.includes('/login')) {
      throw new Error(`平台登录仍停留在 /login 且带错误 query: ${url}`)
    }

    if (url.includes('/self/security/totp-verify')) {
      await page.getByLabel('动态验证码').fill(cfg.totpCode)
      await page.getByRole('button', { name: '确认' }).click()
      await page.waitForURL(
        (u) =>
          !u.pathname.includes('/self/security/totp-verify') &&
          !u.pathname.includes('/callback') &&
          !u.pathname.includes('/login'),
        { timeout: 60_000 },
      )
    }

    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => {})
    const securityStatus = await fetchSecurityStatus(page)
    expect(securityStatus.status).toBe(200)
    expect(securityStatus.payload).not.toBeNull()
    expect(typeof securityStatus.payload?.totpBound).toBe('boolean')
    expect(typeof securityStatus.payload?.totpActivated).toBe('boolean')
    expect(typeof securityStatus.payload?.requireTotp).toBe('boolean')

    const currentUser = await fetchCurrentUser(page)
    expect(currentUser.status, JSON.stringify(currentUser.payload)).toBe(200)
    expect(currentUser.payload).not.toBeNull()
    expect(currentUser.payload?.activeScopeType).toBe('PLATFORM')

    const runtimeMenuTree = await fetchRuntimeMenuTree(page)
    expect(runtimeMenuTree.status).toBe(200)
    expect(Array.isArray(runtimeMenuTree.payload)).toBe(true)
    const countMenuNodes = (nodes: unknown[]): number =>
      nodes.reduce((total, node) => {
        const children =
          node &&
          typeof node === 'object' &&
          Array.isArray((node as { children?: unknown[] }).children)
            ? (node as { children: unknown[] }).children
            : []
        return total + 1 + countMenuNodes(children)
      }, 0)
    expect(countMenuNodes(runtimeMenuTree.payload as unknown[])).toBeGreaterThan(1)

    expect(new URL(page.url()).origin, '登录后不得离开前端同源').toBe(frontendOrigin)
    const cookiesAfterLogin = await context.cookies(frontendOrigin)
    const sessionCookie = cookiesAfterLogin.find((cookie) => cookie.name === 'JSESSIONID')
    expect(sessionCookie, '登录后必须由浏览器持有 JSESSIONID').toBeDefined()
    expect(sessionCookie?.httpOnly, 'JSESSIONID 必须是 HttpOnly Cookie').toBe(true)
    expect(sessionCookie?.value).toBeTruthy()
    if (anonymousSessionCookie) {
      expect(sessionCookie?.value, '登录成功必须轮换已有匿名 Session id').not.toBe(
        anonymousSessionCookie.value,
      )
    }
    expect(
      cookiesAfterLogin.filter((cookie) =>
        /(^|[.:_-])(access|refresh|id)[._-]?token($|[.:_-])/i.test(cookie.name),
      ),
      '浏览器 Cookie 不得承载 access/refresh/id token',
    ).toEqual([])
    expect(
      await readBrowserTokenStorage(page),
      '浏览器存储不得持有 OIDC/access/refresh token',
    ).toEqual([])

    const logout = await logoutWithFreshCsrf(page)
    expect([0, 200, 204, 302]).toContain(logout.logoutStatus)
    const logoutRequest = observedRequests.find(
      (request) => request.method === 'POST' && request.pathname === '/auth/logout',
    )
    expect(logoutRequest, '必须真实发送 POST /auth/logout').toBeDefined()
    expect(logoutRequest?.headers[logout.csrfHeaderName]).toBe(logout.csrfToken)

    const cookiesAfterLogout = await context.cookies(frontendOrigin)
    expect(
      cookiesAfterLogout.some(
        (cookie) => cookie.name === 'JSESSIONID' && cookie.value === sessionCookie?.value,
      ),
      '注销后浏览器不得继续持有原 JSESSIONID',
    ).toBe(false)

    const currentUserAfterLogout = await fetchCurrentUser(page)
    expect([401, 403]).toContain(currentUserAfterLogout.status)

    const staleSessionClient = await playwrightRequest.newContext({
      baseURL: frontendOrigin,
      extraHTTPHeaders: {
        Accept: 'application/json',
        Cookie: `JSESSIONID=${sessionCookie!.value}`,
      },
    })
    try {
      const staleSessionResponse = await staleSessionClient.get('/sys/users/current', {
        maxRedirects: 0,
      })
      expect([401, 403]).toContain(staleSessionResponse.status())
    } finally {
      await staleSessionClient.dispose()
    }

    expect(
      observedRequests.filter((request) => request.origin !== frontendOrigin),
      '浏览器业务请求必须使用前端同源相对路径',
    ).toEqual([])
    expect(
      observedRequests.filter((request) => request.headers.authorization),
      '浏览器一方业务请求不得发送 Authorization',
    ).toEqual([])
    expect(await readBrowserTokenStorage(page), '注销后浏览器存储仍不得持有 token').toEqual([])
  })
})
