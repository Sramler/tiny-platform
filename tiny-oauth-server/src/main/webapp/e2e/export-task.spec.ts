import { expect, test, type Page } from '@playwright/test'

const TENANT_CODE = 'tiny'
const AUTHORITY = `http://localhost:9000/${TENANT_CODE}`
const CLIENT_ID = 'vue-client'
const OIDC_USER_KEYS = [
  `oidc.user:${AUTHORITY}:${CLIENT_ID}`,
  `oidc.user:${AUTHORITY}/:${CLIENT_ID}`,
  `oidc.user:http://localhost:9000:${CLIENT_ID}`,
  `oidc.user:http://localhost:9000/:${CLIENT_ID}`,
]

type ExportTask = {
  taskId: string
  userId: string
  username: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELED'
  progress?: number
  totalRows?: number
  processedRows?: number
  downloadUrl?: string
  sheetCount?: number
  attempt?: number
  workerId?: string
  createdAt?: string
  updatedAt?: string
  lastHeartbeat?: string
  expireAt?: string
  errorCode?: string | null
  errorMsg?: string | null
}

function buildFakeJwtPayload() {
  return {
    activeTenantId: 1,
    iss: AUTHORITY,
  }
}

function encodeBase64Url(value: string) {
  return Buffer.from(value).toString('base64url')
}

function buildFakeAccessToken() {
  const header = encodeBase64Url(JSON.stringify({ alg: 'none', typ: 'JWT' }))
  const payload = encodeBase64Url(JSON.stringify(buildFakeJwtPayload()))
  return `${header}.${payload}.signature`
}

function buildOidcUser() {
  return {
    id_token: 'fake-id-token',
    session_state: 'session-1',
    access_token: buildFakeAccessToken(),
    refresh_token: 'refresh-token',
    token_type: 'Bearer',
    scope: 'openid profile offline_access',
    profile: {
      sub: 'user-1',
      preferred_username: 'alice',
      activeTenantId: 1,
      iss: AUTHORITY,
    },
    expires_at: Math.floor(Date.now() / 1000) + 3600,
  }
}

function buildMenuTreeResponse() {
  return [
    {
      id: 100,
      name: 'export-task',
      title: '导出任务',
      url: '/export/task',
      component: '/views/export/ExportTask.vue',
      hidden: false,
      enabled: true,
      showIcon: false,
      children: [],
    },
  ]
}

async function seedAuthenticatedSession(page: Page) {
  await page.addInitScript(
    ({ oidcUserKeys, oidcUser, tenantCode }) => {
      for (const key of oidcUserKeys) {
        window.localStorage.setItem(key, JSON.stringify(oidcUser))
        window.sessionStorage.setItem(key, JSON.stringify(oidcUser))
      }
      window.localStorage.setItem('app_tenant_code', tenantCode)
      window.localStorage.setItem('app_active_tenant_id', '1')
      window.localStorage.setItem('sider-collapsed', 'false')
      ;(
        window as Window & {
          __openedUrls__?: Array<{ url: string; target: string | null }>
          __anchorDownloads__?: Array<{ href: string; download: string }>
        }
      ).__openedUrls__ = []
      ;(
        window as Window & {
          __openedUrls__?: Array<{ url: string; target: string | null }>
          __anchorDownloads__?: Array<{ href: string; download: string }>
        }
      ).__anchorDownloads__ = []
      window.open = ((url?: string | URL, target?: string) => {
        ;(
          window as Window & {
            __openedUrls__?: Array<{ url: string; target: string | null }>
            __anchorDownloads__?: Array<{ href: string; download: string }>
          }
        )
          .__openedUrls__?.push({
            url: String(url ?? ''),
            target: target ?? null,
          })
        return null
      }) as typeof window.open

      const createObjectURL = URL.createObjectURL.bind(URL)
      URL.createObjectURL = ((object: Blob | MediaSource) => {
        const href = createObjectURL(object)
        ;(
          window as Window & {
            __openedUrls__?: Array<{ url: string; target: string | null }>
            __anchorDownloads__?: Array<{ href: string; download: string }>
          }
        ).__anchorDownloads__?.push({
          href,
          download: '',
        })
        return href
      }) as typeof URL.createObjectURL

      const originalAnchorClick = HTMLAnchorElement.prototype.click
      HTMLAnchorElement.prototype.click = function patchedAnchorClick() {
        ;(
          window as Window & {
            __openedUrls__?: Array<{ url: string; target: string | null }>
            __anchorDownloads__?: Array<{ href: string; download: string }>
          }
        ).__anchorDownloads__?.push({
          href: this.href,
          download: this.download,
        })
        return originalAnchorClick.call(this)
      }
    },
    {
      oidcUserKeys: OIDC_USER_KEYS,
      oidcUser: buildOidcUser(),
      tenantCode: TENANT_CODE,
    },
  )
}

async function mockAuthenticatedApis(page: Page, tasks: ExportTask[]) {
  await page.route('**/sys/menus/tree', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(buildMenuTreeResponse()),
    })
  })

  await page.route('**/sys/users/current', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 1,
        username: 'alice',
        nickname: 'Alice',
      }),
    })
  })

  await page.route('**/export/task', async (route) => {
    if (route.request().resourceType() === 'document') {
      await route.continue()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(tasks),
    })
  })
}

async function openExportTaskPage(page: Page) {
  await page.goto('/')
  const exportMenu = page.locator('.menu-item, .submenu-item').filter({ hasText: '导出任务' }).first()
  await expect(exportMenu).toBeVisible()
  await exportMenu.click()
  await page.waitForURL('**/export/task')
  await expect(page.getByText('导出任务').first()).toBeVisible()
}

test.describe('export task page', () => {
  test('search filters tasks and keeps unfinished download disabled', async ({
    page,
  }) => {
    await seedAuthenticatedSession(page)
    await mockAuthenticatedApis(page, [
      {
        taskId: 'task-1',
        userId: 'u-1',
        username: 'alice',
        status: 'SUCCESS',
        progress: 100,
      },
      {
        taskId: 'task-2',
        userId: 'u-2',
        username: 'bob',
        status: 'RUNNING',
        progress: 67,
      },
    ])

    await openExportTaskPage(page)
    await page.getByPlaceholder('请输入任务ID').fill('task-2')

    await expect(page.getByText('task-2')).toBeVisible()
    await expect(page.getByText('task-1')).toHaveCount(0)
    await expect(page.getByRole('button', { name: '下载' })).toBeDisabled()
  })

  test('successful task can open detail drawer and trigger browser download url', async ({
    page,
  }) => {
    await seedAuthenticatedSession(page)
    await mockAuthenticatedApis(page, [
      {
        taskId: 'task-1',
        userId: 'u-1',
        username: 'alice',
        status: 'SUCCESS',
        progress: 100,
        totalRows: 1000,
        processedRows: 1000,
        sheetCount: 2,
        attempt: 1,
        workerId: 'worker-a',
        createdAt: '2026-03-06T10:00:00',
        updatedAt: '2026-03-06T10:01:00',
        lastHeartbeat: '2026-03-06T10:00:59',
        expireAt: '2026-03-13T10:00:00',
        downloadUrl: '/export/task/task-1/download',
      },
    ])

    await openExportTaskPage(page)

    const row = page.locator('tbody tr').filter({ hasText: 'task-1' })
    await expect(row).toHaveCount(1)

    await row.getByRole('button', { name: '详情' }).click()
    const drawer = page.locator('.ant-drawer').filter({ hasText: '任务详情' })
    await expect(drawer.getByText('worker-a').first()).toBeVisible()
    await expect(drawer.getByText('/export/task/task-1/download')).toBeVisible()
    await drawer.getByRole('button', { name: 'Close' }).click()

    await row.getByRole('button', { name: '下载' }).click()

    const openedUrls = await page.evaluate(
      () =>
        (
          window as Window & {
            __openedUrls__?: Array<{ url: string; target: string | null }>
          }
        ).__openedUrls__ ?? [],
    )

    expect(openedUrls).toEqual([
      expect.objectContaining({
        url: expect.stringMatching(/\/export\/task\/task-1\/download$/),
        target: '_blank',
      }),
    ])
  })

  test('example drawer can trigger sync blob download and async task creation', async ({
    page,
  }) => {
    await seedAuthenticatedSession(page)
    await mockAuthenticatedApis(page, [])

    await page.route('**/export/sync', async (route) => {
      await route.fulfill({
        status: 200,
        contentType:
          'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        body: 'demo-xlsx',
      })
    })

    await page.route('**/export/async', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ taskId: 'task-example-1' }),
      })
    })

    await openExportTaskPage(page)

    await page.getByRole('button', { name: /示例/ }).click()
    const drawer = page.locator('.ant-drawer').filter({ hasText: '导出任务示例' })
    await expect(drawer.getByText('同步导出（小数据量）')).toBeVisible()
    await expect(drawer.getByText('异步导出（大数据量）')).toBeVisible()

    await drawer.getByRole('button', { name: /发起同步导出/ }).click()
    await expect(page.getByText('已发起同步导出（demo_export_usage），请查看下载的文件')).toBeVisible()

    const anchorDownloads = await page.evaluate(
      () =>
        (
          window as Window & {
            __anchorDownloads__?: Array<{ href: string; download: string }>
          }
        ).__anchorDownloads__ ?? [],
    )

    expect(anchorDownloads).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          href: expect.stringMatching(/^blob:/),
          download: 'demo_export_usage.xlsx',
        }),
      ]),
    )

    await drawer.getByRole('button', { name: /发起异步导出任务/ }).click()
    await expect(
      page.getByText('异步导出任务已创建，taskId=task-example-1，可在列表中刷新查看'),
    ).toBeVisible()
  })
})
