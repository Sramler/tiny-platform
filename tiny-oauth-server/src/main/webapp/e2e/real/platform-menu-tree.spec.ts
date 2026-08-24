import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'
import { fetchSchedulingApi, openSessionApp } from './cross-tenant.helpers'

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

test.describe('real-link: 平台登录菜单树', () => {
  test('platform_admin 登录后 /sys/menus/tree 不能退化为单节点', async ({ page }) => {
    test.setTimeout(240_000)

    await openSessionApp(page, 'platform')
    if (page.url().includes('/login')) {
      throw new Error(
        '平台菜单树 real-link 未拿到有效 platform storageState；请确认 globalSetup 已生成 e2e/.auth/platform-admin-user.json，并且后端已补齐平台模板菜单载体。',
      )
    }

    const menuResult = await fetchSchedulingApi<MenuNode[]>(page, '/sys/menus/tree')

    expect(menuResult.status, JSON.stringify(menuResult.payload)).toBe(200)
    expect(Array.isArray(menuResult.payload), JSON.stringify(menuResult.payload)).toBeTruthy()
    const menuTree = menuResult.payload as MenuNode[]
    const flattened = flattenMenu(menuTree)

    expect(flattened.length).toBeGreaterThan(1)

    expect(flattened.some((item) => Boolean(item.url || item.name || item.title))).toBeTruthy()
  })
})
