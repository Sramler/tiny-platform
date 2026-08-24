import { expect, test, type Page, type Response } from '@playwright/test'
import { openSessionApp } from './cross-tenant.helpers'

type ObservedBusinessRequest = {
  method: string
  origin: string
  pathname: string
  authorization?: string
}

function isBusinessPath(pathname: string): boolean {
  return (
    pathname === '/csrf' ||
    pathname.startsWith('/auth/') ||
    pathname.startsWith('/self/') ||
    pathname.startsWith('/sys/')
  )
}

function observeSessionBoundary(page: Page) {
  const requests: ObservedBusinessRequest[] = []
  const deniedResponses: Array<{ pathname: string; status: number }> = []

  page.on('request', (request) => {
    const url = new URL(request.url())
    if (!isBusinessPath(url.pathname)) return
    requests.push({
      method: request.method(),
      origin: url.origin,
      pathname: url.pathname,
      authorization: request.headers().authorization,
    })
  })
  page.on('response', (response) => {
    const url = new URL(response.url())
    if (!isBusinessPath(url.pathname) || ![401, 403].includes(response.status())) return
    deniedResponses.push({ pathname: url.pathname, status: response.status() })
  })

  return { requests, deniedResponses }
}

async function navigateAndWaitForApis(page: Page, routePath: string, apiPaths: string[]) {
  const responsePromises = apiPaths.map((apiPath) =>
    page.waitForResponse((response) => {
      const url = new URL(response.url())
      return url.pathname === apiPath
    }),
  )

  await page.goto(routePath)
  const responses = await Promise.all(responsePromises)
  for (const response of responses) {
    expect(
      response.status(),
      `${response.request().method()} ${new URL(response.url()).pathname}`,
    ).toBe(200)
  }
  expect(new URL(page.url()).pathname).toBe(routePath)
  await expect(page).not.toHaveURL(/\/exception\/403/)
  return responses as Response[]
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

test.describe('real-link: 纯 Session 管理页依赖闭包', () => {
  test('平台管理员深链访问资源与审计页应无 Bearer、403 或 token storage', async ({
    page,
    context,
  }, testInfo) => {
    const configuredBaseURL = testInfo.project.use.baseURL
    if (typeof configuredBaseURL !== 'string') {
      throw new Error('session-management-pages real-link 需要 Playwright baseURL')
    }
    const frontendOrigin = new URL(configuredBaseURL).origin
    await openSessionApp(page, 'platform')
    const observed = observeSessionBoundary(page)

    await navigateAndWaitForApis(page, '/system/resource', [
      '/sys/resources/runtime/ui-actions',
      '/sys/resources/tree',
    ])
    await expect(page.getByRole('button', { name: /新建资源/ })).toBeVisible()
    const firstResourceRow = page.locator('.ant-table-tbody .ant-table-row').first()
    await expect(firstResourceRow).toBeVisible()
    const editButton = firstResourceRow.getByRole('button', { name: /编辑/ })
    const deleteButton = firstResourceRow.getByRole('button', { name: /删除/ })
    await editButton.scrollIntoViewIfNeeded()
    await expect(editButton).toBeVisible()
    await expect(deleteButton).toBeVisible()

    await navigateAndWaitForApis(page, '/system/audit/authentication', [
      '/sys/audit/authentication',
      '/sys/audit/authentication/summary',
    ])
    await expect(page.getByText('认证审计日志', { exact: true })).toBeVisible()
    await expect(page.locator('.ant-table')).toBeVisible()

    await navigateAndWaitForApis(page, '/system/audit/authorization', [
      '/sys/audit/authorization',
      '/sys/audit/authorization/summary',
    ])
    await expect(page.getByText('授权审计日志', { exact: true })).toBeVisible()
    await expect(page.locator('.ant-table')).toBeVisible()

    expect(new URL(page.url()).origin, '管理页不得离开前端同源').toBe(frontendOrigin)
    const browserCookies = await context.cookies(frontendOrigin)
    const sessionCookie = browserCookies.find((cookie) => cookie.name === 'JSESSIONID')
    expect(sessionCookie, '平台 storageState 必须承载 JSESSIONID').toBeDefined()
    expect(sessionCookie?.httpOnly, 'JSESSIONID 必须是 HttpOnly Cookie').toBe(true)
    expect(
      browserCookies.filter((cookie) =>
        /(^|[.:_-])(access|refresh|id)[._-]?token($|[.:_-])/i.test(cookie.name),
      ),
      '平台浏览器 Cookie 不得承载 access/refresh/id token',
    ).toEqual([])
    expect(observed.deniedResponses, '管理页业务请求不得出现 401/403').toEqual([])
    expect(
      observed.requests.filter((request) => request.origin !== frontendOrigin),
      '浏览器业务请求必须通过前端同源 BFF 路径',
    ).toEqual([])
    expect(
      observed.requests.filter((request) => request.authorization),
      '浏览器业务请求不得发送 Authorization/Bearer',
    ).toEqual([])
    expect(
      await readBrowserTokenStorage(page),
      '浏览器存储不得持有 OIDC/access/refresh token',
    ).toEqual([])
  })
})
