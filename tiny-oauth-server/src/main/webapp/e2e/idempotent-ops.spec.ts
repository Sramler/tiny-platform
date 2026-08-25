import { expect, test, type Page } from '@playwright/test'

const TENANT_CODE = 'default'

function buildMenuTreeResponse() {
  return [
    {
      id: 100,
      name: 'system',
      title: '系统管理',
      url: '/system',
      component: '',
      hidden: false,
      enabled: true,
      showIcon: true,
      children: [
        {
          id: 101,
          name: 'idempotentOps',
          title: '幂等治理',
          url: '/ops/idempotent',
          component: '/views/idempotent/Overview.vue',
          hidden: false,
          enabled: true,
          showIcon: false,
          children: [],
        },
      ],
    },
  ]
}

function buildTenantListResponse() {
  return {
    content: [
      { id: 1, code: 'default', name: '平台租户' },
      { id: 7, code: 'demo', name: '演示租户' },
    ],
    totalElements: 2,
  }
}

async function seedAuthenticatedSession(page: Page, _authorities: string[]) {
  await page.addInitScript(
    ({ tenantCode }) => {
      window.localStorage.setItem('app_tenant_code', tenantCode)
      window.localStorage.setItem('app_active_tenant_id', '1')
      window.localStorage.setItem('sider-collapsed', 'false')
    },
    {
      tenantCode: TENANT_CODE,
    },
  )
}

async function mockAuthenticatedApis(
  page: Page,
  authorities: string[],
  metricsRequestCounter?: { count: number },
  metricsRequestUrls?: string[],
) {
  await page.route(/\/sys\/menus\/tree(?:\?.*)?$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(buildMenuTreeResponse()),
    })
  })

  await page.route(/\/sys\/users\/current(?:\?.*)?$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 1,
        username: 'alice',
        nickname: 'Alice',
        activeTenantId: 1,
        activeScopeType: 'PLATFORM',
        authorities,
      }),
    })
  })

  await page.route(/\/self\/security\/status(?:\?.*)?$/, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ totpBound: true, totpActivated: true, requireTotp: false }),
    })
  })

  await page.route('**/sys/tenants**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(buildTenantListResponse()),
    })
  })

  await page.route('**/metrics/idempotent**', async (route) => {
    if (metricsRequestCounter) {
      metricsRequestCounter.count += 1
    }
    if (metricsRequestUrls) {
      metricsRequestUrls.push(route.request().url())
    }

    const url = route.request().url()
    if (url.includes('/metrics/idempotent/top-keys')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          topKeys: [{ key: 'POST /sys/users', count: 6 }],
        }),
      })
      return
    }

    if (url.includes('/metrics/idempotent/mq')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          windowMinutes: 60,
          windowStartEpochMillis: 1741431600000,
          windowEndEpochMillis: 1741435200000,
          successCount: 11,
          failureCount: 1,
          duplicateRate: 0.09,
        }),
      })
      return
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        windowMinutes: 60,
        windowStartEpochMillis: 1741431600000,
        windowEndEpochMillis: 1741435200000,
        passCount: 18,
        hitCount: 3,
        successCount: 15,
        failureCount: 0,
        storeErrorCount: 0,
        validationRejectCount: 1,
        rejectCount: 4,
        totalCheckCount: 22,
        conflictRate: 0.17,
        storageErrorRate: 0,
      }),
    })
  })
}

test.describe('idempotent ops access', () => {
  test('platform metrics operator can open governance page from home', async ({ page }) => {
    await seedAuthenticatedSession(page, ['ROLE_ADMIN', 'idempotent:ops:view'])
    await mockAuthenticatedApis(page, ['ROLE_ADMIN', 'idempotent:ops:view'])

    await page.goto('/')
    await page.waitForURL((url) => url.pathname === '/bootstrap', { timeout: 30_000 })
    await page.waitForURL((url) => url.pathname === '/', { timeout: 30_000 })
    await expect(page.getByRole('button', { name: '进入治理页' })).toBeVisible()
    await expect(page.getByText('通过请求')).toBeVisible()

    await page.getByRole('button', { name: '进入治理页' }).click()
    await page.waitForURL(/\/ops\/idempotent(?:\?activeTenantId=\d+)?$/)

    await expect(page.getByRole('heading', { name: '幂等治理页' })).toBeVisible()
    await expect(page).toHaveURL(/\/ops\/idempotent(?:\?activeTenantId=\d+)?$/)
    await expect(page.getByText('POST /sys/users')).toBeVisible()
    await expect(page.getByText('消费成功')).toBeVisible()
  })

  test('users without idempotent:ops:view authority should not fetch or show governance entry', async ({
    page,
  }) => {
    const metricsRequestCounter = { count: 0 }
    await seedAuthenticatedSession(page, ['ROLE_ADMIN'])
    await mockAuthenticatedApis(page, ['ROLE_ADMIN'], metricsRequestCounter)

    await page.goto('/')

    await expect(page.getByText('幂等治理指标仅对平台作用域下具备平台级幂等治理权限的用户开放')).toBeVisible()
    await expect(page.getByRole('button', { name: '进入治理页' })).toHaveCount(0)
    expect(metricsRequestCounter.count).toBe(0)
  })

  test('platform metrics operator can switch governance metrics to a tenant scope', async ({
    page,
  }) => {
    const metricsRequestUrls: string[] = []
    await seedAuthenticatedSession(page, ['ROLE_ADMIN', 'idempotent:ops:view'])
    await mockAuthenticatedApis(page, ['ROLE_ADMIN', 'idempotent:ops:view'], undefined, metricsRequestUrls)

    await page.goto('/ops/idempotent')
    await expect(page.getByRole('heading', { name: '幂等治理页' })).toBeVisible()

    await page.locator('[data-testid="idempotent-tenant-filter"]').click()
    await page.getByText('演示租户 (demo)', { exact: true }).click()

    await expect(page).toHaveURL(/activeTenantId=7/)
    await expect
      .poll(() => metricsRequestUrls.filter((url) => url.includes('activeTenantId=7')).length)
      .toBeGreaterThanOrEqual(3)
  })

  test('platform metrics operator can open governance page from a tenant-scoped URL', async ({
    page,
  }) => {
    const metricsRequestUrls: string[] = []
    await seedAuthenticatedSession(page, ['ROLE_ADMIN', 'idempotent:ops:view'])
    await mockAuthenticatedApis(page, ['ROLE_ADMIN', 'idempotent:ops:view'], undefined, metricsRequestUrls)

    await page.goto('/ops/idempotent?activeTenantId=7')
    await expect(page.getByRole('heading', { name: '幂等治理页' })).toBeVisible()
    await expect(page).toHaveURL(/activeTenantId=7/)
    await expect(page.locator('.ops-filter-copy strong')).toHaveText('演示租户 (demo)')

    await expect
      .poll(() => metricsRequestUrls.filter((url) => url.includes('activeTenantId=7')).length)
      .toBeGreaterThanOrEqual(3)
  })
})
