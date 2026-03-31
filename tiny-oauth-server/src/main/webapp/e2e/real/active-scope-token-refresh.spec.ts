/**
 * real-link：HeaderBar 切换 active scope 后 `tokenRefreshRequired: true` → OIDC silent renew（prompt=none）→ 稳定态
 *
 * 证据分层：
 * - 层 1：POST `/sys/users/current/active-scope` 成功且响应体 `tokenRefreshRequired === true`
 * - 层 2：真实请求 `GET /oauth2/authorize?...prompt=none...`（iframe silent renew，不 mock）
 * - 层 3：切换弹窗关闭、无 warning、Bearer 调 `GET /sys/users/current` 非 401；可选比对 `oidc.user:*` access_token 变化
 *
 * 身份与 storageState：
 * - Playwright project **chromium**（`playwright.real.config.ts`）
 * - `e2e/.auth/scheduling-user.json` 由 `e2e/setup/real.global.setup.ts` 调用 `generate-auth-state.mjs` 合法生成
 * - 依赖 `.env.e2e.local` 中 `E2E_TENANT_CODE` / `E2E_USERNAME` / `E2E_PASSWORD` / `E2E_TOTP_SECRET`（与调度 real e2e 主身份一致）
 *
 * 前置：本机 `localhost:9000` + Vite 前端；`vue-client` 与 `silent-renew.html` 由 webServer 注入。
 */
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test, type Page } from '@playwright/test'

const backendBaseUrl = process.env.E2E_BACKEND_BASE_URL ?? 'http://localhost:9000'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
// this file: e2e/real/*.spec.ts
// tenant auth: e2e/.auth/scheduling-tenant-user.json
const tenantScopedAuthStatePath = path.resolve(__dirname, '..', '.auth', 'scheduling-tenant-user.json')

function decodeJwtPayload(token: string): any {
  const parts = token.split('.')
  if (parts.length < 2) return {}
  const payloadB64Url = parts[1]
  const b64 = payloadB64Url.replace(/-/g, '+').replace(/_/g, '/')
  const padded = b64.padEnd(Math.ceil(b64.length / 4) * 4, '=')
  const json = Buffer.from(padded, 'base64').toString('utf8')
  return JSON.parse(json)
}

async function readOidcAccessToken(page: Page): Promise<{ key: string; access_token: string; expires_at?: number } | null> {
  return page.evaluate(() => {
    const key = Object.keys(localStorage).find((k) => k.startsWith('oidc.user:'))
    if (!key) return null
    const raw = localStorage.getItem(key)
    if (!raw) return null
    try {
      const u = JSON.parse(raw) as { access_token?: string; expires_at?: number }
      if (!u.access_token) return null
      return { key, access_token: u.access_token, expires_at: u.expires_at }
    } catch {
      return null
    }
  })
}

async function fetchCurrentUserWithBearer(page: Page): Promise<{ status: number; body: string }> {
  return page.evaluate(async (api) => {
    const key = Object.keys(localStorage).find((k) => k.startsWith('oidc.user:'))
    if (!key) return { status: 0, body: 'no-oidc-key' }
    const raw = localStorage.getItem(key)
    if (!raw) return { status: 0, body: 'no-oidc-raw' }
    let token: string
    try {
      token = (JSON.parse(raw) as { access_token?: string }).access_token ?? ''
    } catch {
      return { status: 0, body: 'parse-fail' }
    }
    if (!token) return { status: 0, body: 'no-token' }
    const r = await fetch(`${api}/sys/users/current`, {
      credentials: 'include',
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/json',
      },
    })
    const body = await r.text()
    return { status: r.status, body }
  }, backendBaseUrl)
}

test.describe('real-link: active scope + token refresh', () => {
  test.use({ storageState: tenantScopedAuthStatePath })

  test('should_silent_renew_after_active_scope_switch_when_backend_requires_token_refresh', async ({ page }) => {
    const currentUserRespPromise = page.waitForResponse(
      (r) =>
        r.url().includes('/sys/users/current') &&
        r.request().method() === 'GET' &&
        r.status() === 200,
      { timeout: 90_000 },
    )
    await page.goto('/OIDCDebug', { waitUntil: 'domcontentloaded' })
    if (page.url().includes('/login')) {
      throw new Error(
        '仍在 /login：请确认 chromium 使用 globalSetup 生成的 e2e/.auth/scheduling-tenant-user.json（租户态身份），且本机 5173/9000 可用（勿在无 storageState 时 E2E_SKIP_REAL_SETUP 空跑）。',
      )
    }
    await expect(page.locator('h1').filter({ hasText: 'OIDC 调试工具' })).toBeVisible({ timeout: 90_000 })

    // 等待 HeaderBar loadUserInfo：同步 activeTenantId（token claims + /sys/users/current body 必须齐备）
    const currentUserResp = await currentUserRespPromise
    const currentUserJson = (await currentUserResp.json()) as {
      activeTenantId?: unknown
      activeScopeType?: string
    }

    const activeTenantId = Number(currentUserJson.activeTenantId)
    expect(Number.isFinite(activeTenantId) && activeTenantId > 0, '租户态身份必须有有效 activeTenantId（来自 /sys/users/current body）').toBe(true)
    expect(currentUserJson.activeScopeType, '租户态身份必须不是 PLATFORM').not.toBe('PLATFORM')

    const localActiveTenantId = await page.evaluate(() => window.localStorage.getItem('app_active_tenant_id'))
    expect(localActiveTenantId, 'localStorage.app_active_tenant_id 必须建立').not.toBeNull()
    expect(localActiveTenantId, 'local activeTenantId 必须与后端 /sys/users/current body 一致').toBe(String(activeTenantId))

    const before = await readOidcAccessToken(page)
    expect(before, '需要已登录 OIDC localStorage（globalSetup 生成的 storageState）').not.toBeNull()
    const payload = decodeJwtPayload(before!.access_token)
    expect(payload.activeTenantId, 'access_token claims 必须包含 activeTenantId').toBeGreaterThan(0)
    expect(payload.activeScopeType, 'access_token claims 必须不是 PLATFORM').not.toBe('PLATFORM')

    await page.locator('.header-bar .dropdown').click()
    await page.getByText('切换作用域', { exact: true }).click()
    const scopeModal = page.locator('.ant-modal:visible')
    await expect(scopeModal.locator('.ant-modal-title').filter({ hasText: '切换作用域' })).toBeVisible({
      timeout: 30_000,
    })

    // 记录本次写请求，用于断言 X-Active-Tenant-Id / body.scopeType 非 PLATFORM。
    let activeScopePostReq: any = null
    const silentRenewPromise = page.waitForRequest((req) => {
      const url = req.url()
      if (!url.includes('/oauth2/authorize')) return false
      const decoded = decodeURIComponent(url)
      return decoded.includes('prompt=none')
    }, { timeout: 90_000 })

    // 与 click 并发注册 waitForResponse，避免 OK 已触发但响应早于 listener；主按钮优先于 accessible name（避免「切 换」空格导致不稳定）
    const okPrimary = scopeModal.locator('.ant-modal-footer button.ant-btn-primary')
    const [postResp] = await Promise.all([
      page.waitForResponse(
        (r) => {
          const match = r.url().includes('/sys/users/current/active-scope') && r.request().method() === 'POST'
          if (match) activeScopePostReq = r.request()
          return match
        },
        { timeout: 90_000 },
      ),
      okPrimary.click(),
    ])
    const postText = await postResp.text()
    expect(
      postResp.status(),
      `POST active-scope 期望 200，实际 ${postResp.status()}，body 开头: ${postText.slice(0, 500)}`,
    ).toBe(200)
    const postJson = JSON.parse(postText) as { tokenRefreshRequired?: boolean; success?: boolean }
    expect(postJson.tokenRefreshRequired, 'Bearer 写场景下后端应返回 tokenRefreshRequired: true').toBe(true)

    if (activeScopePostReq) {
      const headers = activeScopePostReq.headers?.() ?? {}
      const headerTenantId =
        headers['x-active-tenant-id'] ?? headers['X-Active-Tenant-Id'] ?? headers['X-Active-Tenant-Id'.toLowerCase()]
      expect(headerTenantId, 'POST /active-scope 必须带 X-Active-Tenant-Id').toBe(String(activeTenantId))

      const bodyJson = activeScopePostReq.postDataJSON?.() ?? null
      if (bodyJson?.scopeType) {
        expect(bodyJson.scopeType, 'active-scope 写入 scopeType 不得为 PLATFORM').not.toBe('PLATFORM')
      }
    }

    await silentRenewPromise

    // 真实浏览器里 Ant Design message 受挂载时序与动画影响，success toast 不是稳定证据。
    // 这里改用“modal 已关闭 + 无 warning + Bearer 读链稳定”作为最终断言。
    await expect(scopeModal).toHaveCount(0, { timeout: 60_000 })
    await expect(page.getByText('未能刷新访问令牌')).toHaveCount(0)
    await expect(page.getByText('未能加载当前用户信息')).toHaveCount(0)

    const after = await readOidcAccessToken(page)
    expect(after, 'silent renew 后仍应有 oidc.user').not.toBeNull()

    const me = await fetchCurrentUserWithBearer(page)
    expect(me.status, `切换后 Bearer 拉当前用户不应 M5（期望 200，实际 body 开头: ${me.body.slice(0, 200)}）`).toBe(200)
    expect(me.body).not.toContain('invalid_active_scope')

    // 最佳努力：多数环境下 silent renew 会轮换 access_token；若 issuer 短时复用同一字符串，仍以 prompt=none + GET 200 为主证据
    if (after!.access_token === before!.access_token) {
      console.warn(
        '[active-scope e2e] access_token 字符串与切换前相同；主证据为 /oauth2/authorize prompt=none 与 GET /sys/users/current 200',
      )
    }
  })
})
