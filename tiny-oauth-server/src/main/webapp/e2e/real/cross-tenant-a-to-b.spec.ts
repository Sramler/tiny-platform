import { expect, test } from '@playwright/test'
import {
  createTaskType,
  expectTenantMismatchPayload,
  fetchSchedulingApi,
  isCrossTenantIdentityConfigured,
  openOidcDebug,
  openSecondaryAuthenticatedPage,
  secondaryAuthStatePath,
} from './cross-tenant.helpers'

/**
 * real-link：A 租户身份访问 B 租户资源应被隔离
 *
 * 目标：
 * - 使用主自动化身份（tenant A）作为当前浏览器 storageState。
 * - 使用第二租户身份（tenant B）创建真实资源，再验证 tenant A 无法读取。
 * - 额外验证 tenant A 若伪造 `X-Active-Tenant-Id = tenant B`，后端会返回 tenant_mismatch。
 */

test.describe('real-link: tenant A is isolated from tenant B resources', () => {
  test('tenant A cannot read tenant B task type and spoofing tenant B header is rejected', async ({
    browser,
    page,
  }) => {
    test.skip(
      !isCrossTenantIdentityConfigured(),
      '缺少 E2E_TENANT_CODE_B / E2E_USERNAME_B / E2E_PASSWORD_B / E2E_TOTP_SECRET_B，跳过双身份跨租户回归',
    )

    await openOidcDebug(page)
    const tenantB = await openSecondaryAuthenticatedPage(browser, secondaryAuthStatePath)

    try {
      const ownedByTenantB = await createTaskType(tenantB.page, 'tenant-b-owned')
      expect(ownedByTenantB.ownerTenantId).toBeTruthy()

      const ownerRead = await fetchSchedulingApi<{ id?: number }>(
        tenantB.page,
        `/scheduling/task-type/${ownedByTenantB.id}`,
      )
      expect(ownerRead.status).toBe(200)
      expect(Number(ownerRead.payload?.id)).toBe(ownedByTenantB.id)

      const directCrossTenantRead = await fetchSchedulingApi<Record<string, unknown>>(
        page,
        `/scheduling/task-type/${ownedByTenantB.id}`,
      )
      expect(directCrossTenantRead.status).toBe(404)

      const spoofedTenantHeaderRead = await fetchSchedulingApi<Record<string, unknown>>(
        page,
        `/scheduling/task-type/${ownedByTenantB.id}`,
        {
          overrideTenantId: ownedByTenantB.ownerTenantId ?? undefined,
        },
      )
      expect(spoofedTenantHeaderRead.status).toBe(403)
      expectTenantMismatchPayload(spoofedTenantHeaderRead.payload)
    } finally {
      await tenantB.context.close().catch(() => {})
    }
  })
})
