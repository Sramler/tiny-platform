import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'
import { openOidcDebug } from './cross-tenant.helpers'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const platformAuthStatePath = path.resolve(__dirname, '..', '.auth', 'platform-admin-user.json')

test.use({ storageState: platformAuthStatePath })

type MenuNode = {
  title?: string
  name?: string
  url?: string
  children?: MenuNode[]
}

function flattenMenu(nodes: MenuNode[]): MenuNode[] {
  const result: MenuNode[] = []
  const stack = [...nodes]
  while (stack.length > 0) {
    const current = stack.shift()!
    result.push(current)
    if (Array.isArray(current.children) && current.children.length > 0) {
      stack.unshift(...current.children)
    }
  }
  return result
}

function readBackendBaseUrl(): string {
  return process.env.E2E_BACKEND_BASE_URL ?? process.env.VITE_API_BASE_URL ?? 'http://localhost:9000'
}

test.describe('real-link: 平台登录菜单树', () => {
  test('platform_admin 登录后 /sys/menus/tree 不能退化为单节点', async ({ page }) => {
    test.setTimeout(240_000)

    await openOidcDebug(page, 'platform')
    if (page.url().includes('/login')) {
      throw new Error(
        '平台菜单树 real-link 未拿到有效 platform storageState；请确认 globalSetup 已生成 e2e/.auth/platform-admin-user.json，并且后端已补齐平台模板菜单载体。',
      )
    }

    const backendBaseUrl = readBackendBaseUrl()
    const menuResult = await page.evaluate(async ({ apiBaseUrl }) => {
      const oidcKey = Object.keys(window.localStorage).find((key) => key.startsWith('oidc.user:'))
      if (!oidcKey) {
        throw new Error('未找到平台 OIDC 登录态')
      }
      const rawUser = window.localStorage.getItem(oidcKey)
      if (!rawUser) {
        throw new Error(`平台 OIDC 存储为空: ${oidcKey}`)
      }
      const oidcUser = JSON.parse(rawUser) as {
        access_token?: string
        profile?: { activeTenantId?: number | string }
      }
      if (!oidcUser.access_token) {
        throw new Error('平台 OIDC 用户缺少 access_token')
      }

      const headers = new Headers({
        Accept: 'application/json',
        Authorization: `Bearer ${oidcUser.access_token}`,
      })
      const activeTenantId =
        window.localStorage.getItem('app_active_tenant_id') ??
        String(oidcUser.profile?.activeTenantId ?? '')
      if (activeTenantId) {
        headers.set('X-Active-Tenant-Id', activeTenantId)
      }

      const response = await fetch(`${apiBaseUrl}/sys/menus/tree`, {
        method: 'GET',
        credentials: 'include',
        headers,
      })
      const text = await response.text()
      const contentType = response.headers.get('content-type') || ''
      return {
        status: response.status,
        payload:
          text && contentType.includes('application/json')
            ? (JSON.parse(text) as MenuNode[])
            : null,
        text,
      }
    }, { apiBaseUrl: backendBaseUrl })

    expect(menuResult.status, menuResult.text).toBe(200)
    expect(Array.isArray(menuResult.payload), menuResult.text).toBeTruthy()
    const menuTree = menuResult.payload as MenuNode[]
    const flattened = flattenMenu(menuTree)

    expect(flattened.length).toBeGreaterThan(1)

    const hasSystemUserEntry = flattened.some((item) => item.url === '/system/user' || item.name === 'user')
    const hasTenantEntry = flattened.some((item) => item.url === '/system/tenant' || item.name === 'tenant')
    expect(hasSystemUserEntry || hasTenantEntry).toBeTruthy()
  })
})
