import { expect, test, type Page } from '@playwright/test'
import { openOidcDebug } from './cross-tenant.helpers'

type CreateTenantPayload = {
  code: string
  name: string
  initialAdminUsername: string
  initialAdminPassword: string
}

function uniqueTenantCode(tag: string) {
  const stamp = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
  return `e2e-tw07-${tag}-${stamp}`.slice(0, 32).toLowerCase()
}

function tenantName(tenantCode: string) {
  return `E2E租户(${tenantCode})`
}

function buildInitAdminUsername(tenantCode: string) {
  // 编码前缀在同一组 entry0/entry1/entry2 场景中相同，保留随机时间戳尾部才能避免用户唯一键碰撞。
  const safeTenantCode = tenantCode.replace(/[^a-z0-9_]/g, '_').slice(-11)
  return `e2e_init_${safeTenantCode}`
}

async function createGeneratedTenantViaApi(page: Page, payload: CreateTenantPayload) {
  const result = await page.evaluate(async (tenant) => {
    const csrfResponse = await fetch('/csrf', {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!csrfResponse.ok) {
      return {
        ok: false,
        status: csrfResponse.status,
        body: '刷新 CSRF token 失败',
      }
    }
    const csrf = (await csrfResponse.json()) as { token?: string; headerName?: string }
    if (!csrf.token || !csrf.headerName) {
      return {
        ok: false,
        status: csrfResponse.status,
        body: 'CSRF 响应缺少 token/headerName',
      }
    }

    const headers = new Headers({
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Idempotency-Key': `tw07-precreate:${tenant.code}:${Date.now()}`,
    })
    headers.set(csrf.headerName, csrf.token)
    const response = await fetch('/sys/tenants', {
      method: 'POST',
      credentials: 'include',
      headers,
      body: JSON.stringify({
        code: tenant.code,
        name: tenant.name,
        enabled: true,
        initialAdminUsername: tenant.initialAdminUsername,
        initialAdminNickname: `E2E初始管理员(${tenant.code})`,
        initialAdminPassword: tenant.initialAdminPassword,
        initialAdminConfirmPassword: tenant.initialAdminPassword,
      }),
    })
    return {
      ok: response.ok,
      status: response.status,
      body: await response.text(),
    }
  }, payload)

  if (!result.ok) {
    throw new Error(`预创建租户失败: HTTP ${result.status} ${result.body}`)
  }
}

async function openCreateWizard(page: Page) {
  await openOidcDebug(page, 'platform')
  await page.goto('/system/tenant', { waitUntil: 'networkidle' })
  if (page.url().includes('/login')) {
    throw new Error(
      '租户初始化向导 real-link 未拿到有效 platform storageState；请确认 globalSetup 已成功生成 e2e/.auth/platform-admin-user.json，且平台管理员身份可进入 /system/tenant。',
    )
  }
  await expect(page.getByText('租户列表')).toBeVisible()
  await page.locator('[data-test="tenant-create-button"]').click()
  await expect(page.locator('[data-test="tenant-create-drawer"]')).toBeVisible()
  await expect(page.locator('[data-test="tenant-create-wizard"]')).toBeVisible()
}

async function fillWizardUntilConfirm(
  page: Page,
  payload: { code: string; name: string; adminUsername: string; password: string },
) {
  await page.locator('[data-test="wizard-tenant-code-input"]').fill(payload.code)
  await page.locator('[data-test="wizard-tenant-name-input"]').fill(payload.name)
  await page.locator('[data-test="wizard-next-step"]').click()
  await page.locator('[data-test="wizard-next-step"]').click()
  await page.locator('[data-test="wizard-admin-username-input"]').fill(payload.adminUsername)
  await page.locator('[data-test="wizard-admin-password-input"]').fill(payload.password)
  await page.locator('[data-test="wizard-admin-confirm-password-input"]').fill(payload.password)

  const precheckResponsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' && response.url().includes('/sys/tenants/precheck'),
  )
  await page.locator('[data-test="wizard-next-step"]').click()
  return precheckResponsePromise
}

async function createTenantInWizard(page: Page, codeTag: string) {
  const code = uniqueTenantCode(codeTag)
  const name = tenantName(code)
  const adminUsername = buildInitAdminUsername(code)
  const password = 'Tianye0903.'

  await openCreateWizard(page)
  const precheckResponse = await fillWizardUntilConfirm(page, {
    code,
    name,
    adminUsername,
    password,
  })
  const precheckBody = await precheckResponse.text()
  const identityResponse = await page.request.get('/sys/users/current')
  const identityBody = await identityResponse.text()
  expect(
    precheckResponse.ok(),
    `precheck failed: ${precheckResponse.status()} ${precheckBody}; identity=${identityResponse.status()} ${identityBody}; requestHeaders=${JSON.stringify(precheckResponse.request().headers())}`,
  ).toBeTruthy()

  const createButton = page.locator('[data-test="wizard-submit-create"]')
  await expect(createButton).toBeEnabled()

  const createResponsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST' && /\/sys\/tenants$/.test(response.url()),
  )
  await createButton.click()
  const createResponse = await createResponsePromise
  expect(createResponse.status(), 'create API should return 200').toBe(200)

  await expect(page.locator('[data-test="wizard-submit-success"]')).toBeVisible()
  await expect(page.locator('[data-test="wizard-submit-success"]')).toContainText(code)

  return { code }
}

test.describe.serial('real-link: tenant create wizard smoke', () => {
  test('success chain: wizard precheck/create -> result page -> close and list refresh', async ({
    page,
  }) => {
    const { code } = await createTenantInWizard(page, 'success')

    await page.locator('[data-test="wizard-complete-close"]').click()
    await expect(page.locator('[data-test="tenant-create-drawer"]')).toBeHidden()

    const searchResponsePromise = page.waitForResponse((response) => {
      if (response.request().method() !== 'GET') {
        return false
      }
      const url = new URL(response.url())
      return url.pathname === '/sys/tenants' && url.searchParams.get('code') === code
    })

    await page.locator('[data-test="tenant-search-code"]').fill(code)
    await page.locator('[data-test="tenant-search-submit"]').click()
    const searchResponse = await searchResponsePromise
    expect(searchResponse.ok(), `tenant search failed: ${searchResponse.status()}`).toBeTruthy()
    await expect(page.locator('.ant-table-tbody')).toContainText(code)
  })

  test('precheck blocking: duplicate tenant code must block final create', async ({ page }) => {
    const conflictCode = uniqueTenantCode('blocking')
    const conflictName = tenantName(conflictCode)
    const conflictPassword = 'Tianye0903.'
    await openCreateWizard(page)
    await createGeneratedTenantViaApi(page, {
      code: conflictCode,
      name: conflictName,
      initialAdminUsername: buildInitAdminUsername(conflictCode),
      initialAdminPassword: conflictPassword,
    })

    const precheckResponse = await fillWizardUntilConfirm(page, {
      code: conflictCode,
      name: conflictName,
      adminUsername: `${buildInitAdminUsername(conflictCode).slice(0, 19)}x`,
      password: conflictPassword,
    })
    expect(precheckResponse.ok(), `precheck failed: ${precheckResponse.status()}`).toBeTruthy()

    const finalCreateRequests: string[] = []
    page.on('request', (request) => {
      if (request.method() === 'POST' && /\/sys\/tenants$/.test(request.url())) {
        finalCreateRequests.push(request.url())
      }
    })

    await expect(page.locator('[data-test="wizard-precheck-blocking-banner"]')).toBeVisible()
    await expect(page.locator('[data-test="wizard-precheck-blocking-issues"]')).toContainText(
      '阻断项',
    )
    await expect(page.locator('[data-test="wizard-submit-create"]')).toBeDisabled()
    expect(finalCreateRequests.length).toBe(0)
  })

  test('result governance entries: keep query.from and focus expected detail sections', async ({
    page,
  }) => {
    const sectionCases = [
      {
        section: 'overview',
        actionSelector: '[data-test="wizard-go-overview"]',
        focusedSelector: '[data-test="section-overview"]',
      },
      {
        section: 'permission-summary',
        actionSelector: '[data-test="wizard-go-permission-summary"]',
        focusedSelector: '[data-test="section-permission-summary"]',
      },
      {
        section: 'template-diff',
        actionSelector: '[data-test="wizard-go-template-diff"]',
        focusedSelector: '[data-test="section-template-diff"]',
      },
    ] as const

    for (const [index, sectionCase] of sectionCases.entries()) {
      await createTenantInWizard(page, `entry${index}`)
      await page.locator(sectionCase.actionSelector).click()
      await page.waitForURL(/\/platform\/tenants\/\d+/)

      const currentUrl = new URL(page.url())
      expect(currentUrl.searchParams.get('from')).toContain('/system/tenant')
      expect(currentUrl.searchParams.get('section')).toBe(sectionCase.section)
      await expect(page.locator(sectionCase.focusedSelector)).toHaveClass(/section-focused/)

      await page.locator('[data-test="tenant-detail-go-back"]').click()
      await page.waitForURL(/\/system\/tenant/)
    }
  })
})
