import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'
import { openOidcDebug } from './cross-tenant.helpers'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const platformAuthStatePath = path.resolve(__dirname, '..', '.auth', 'platform-admin-user.json')

test.use({ storageState: platformAuthStatePath })

test.describe('real-link: 平台角色页', () => {
  test('登录后应取得 UI actions 与角色业务数据且不跳转 403', async ({ page }) => {
    await openOidcDebug(page, 'platform')
    const apiStatuses = new Map<string, number>()
    page.on('response', (response) => {
      const url = new URL(response.url())
      if (
        url.pathname === '/sys/resources/runtime/ui-actions' ||
        url.pathname === '/sys/roles'
      ) {
        apiStatuses.set(url.pathname, response.status())
      }
    })

    await page.goto('/system/role')
    await expect(page).toHaveURL(/\/system\/role(?:\?|$)/)
    await expect.poll(() => apiStatuses.get('/sys/resources/runtime/ui-actions')).toBe(200)
    await expect.poll(() => apiStatuses.get('/sys/roles')).toBe(200)
    await expect(page.locator('.ant-table')).toBeVisible()

    const roleRows = page.locator('.ant-table-tbody .ant-table-row')
    await expect(roleRows.first()).toBeVisible()
    expect(await roleRows.count()).toBeGreaterThan(0)
  })
})
