import { expect, test } from '@playwright/test'
import {
  createTaskType,
  expectTenantMismatchPayload,
  fetchSchedulingApi,
  isCrossTenantIdentityConfigured,
  openOidcDebug,
  openSecondaryAuthenticatedPage,
  primaryAuthStatePath,
} from './cross-tenant.helpers'

/**
 * real-link：B 租户身份访问 A 租户资源应被隔离
 *
 * 目标：
 * - 使用第二租户身份（tenant B）作为当前浏览器 storageState。
 * - 使用主自动化身份（tenant A）创建真实资源，再验证 tenant B 无法读取。
 * - 额外验证 tenant B 若伪造 `X-Tenant-Id = tenant A`，后端会返回 tenant_mismatch。
 */

test.describe('real-link: tenant B is isolated from tenant A resources', () => {
  test('tenant B cannot read tenant A task type and spoofing tenant A header is rejected', async ({
    browser,
    page,
  }) => {
    test.skip(
      !isCrossTenantIdentityConfigured(),
      '缺少 E2E_TENANT_CODE_B / E2E_USERNAME_B / E2E_PASSWORD_B / E2E_TOTP_SECRET_B，跳过双身份跨租户回归',
    )

    await openOidcDebug(page)
    const tenantA = await openSecondaryAuthenticatedPage(browser, primaryAuthStatePath)

    try {
      const ownedByTenantA = await createTaskType(tenantA.page, 'tenant-a-owned')
      expect(ownedByTenantA.ownerTenantId).toBeTruthy()

      const ownerRead = await fetchSchedulingApi<{ id?: number }>(
        tenantA.page,
        `/scheduling/task-type/${ownedByTenantA.id}`,
      )
      expect(ownerRead.status).toBe(200)
      expect(Number(ownerRead.payload?.id)).toBe(ownedByTenantA.id)

      const directCrossTenantRead = await fetchSchedulingApi<Record<string, unknown>>(
        page,
        `/scheduling/task-type/${ownedByTenantA.id}`,
      )
      expect(directCrossTenantRead.status).toBe(404)

      const spoofedTenantHeaderRead = await fetchSchedulingApi<Record<string, unknown>>(
        page,
        `/scheduling/task-type/${ownedByTenantA.id}`,
        {
          overrideTenantId: ownedByTenantA.ownerTenantId ?? undefined,
        },
      )
      expect(spoofedTenantHeaderRead.status).toBe(403)
      expectTenantMismatchPayload(spoofedTenantHeaderRead.payload)
    } finally {
      await tenantA.context.close()
    }
  })
})
