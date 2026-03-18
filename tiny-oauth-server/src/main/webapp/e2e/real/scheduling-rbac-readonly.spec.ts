import { expect, test } from '@playwright/test'
import {
  fetchSchedulingApi,
  isReadonlyIdentityConfigured,
  openOidcDebug,
} from './cross-tenant.helpers'

test.describe('real-link: scheduling readonly RBAC', () => {
  test('readonly identity can read scheduling lists but cannot mutate or operate', async ({
    page,
  }) => {
    test.skip(
      !isReadonlyIdentityConfigured(),
      '缺少 E2E_USERNAME_READONLY / E2E_PASSWORD_READONLY / E2E_TOTP_SECRET_READONLY，跳过调度只读 RBAC 回归',
    )

    await openOidcDebug(page)

    const listTaskTypes = await fetchSchedulingApi<Record<string, unknown>>(
      page,
      '/scheduling/task-type/list?page=0&size=5',
    )
    expect(listTaskTypes.status).toBe(200)

    const createTaskType = await fetchSchedulingApi<Record<string, unknown>>(page, '/scheduling/task-type', {
      method: 'POST',
      body: {
        code: `readonly-blocked-${Date.now()}`,
        name: 'readonly blocked',
        description: 'readonly identity should be denied by RBAC',
        executor: 'loggingTaskExecutor',
        enabled: true,
        defaultTimeoutSec: 0,
        defaultMaxRetry: 0,
      },
      idempotencyKey: `readonly-create-${Date.now()}`,
    })
    expect(createTaskType.status).toBe(403)

    const triggerDag = await fetchSchedulingApi<Record<string, unknown>>(page, '/scheduling/dag/999999/trigger', {
      method: 'POST',
      idempotencyKey: `readonly-trigger-${Date.now()}`,
    })
    expect(triggerDag.status).toBe(403)

    const listAudits = await fetchSchedulingApi<Record<string, unknown>>(
      page,
      '/scheduling/audit/list?page=0&size=5',
    )
    expect(listAudits.status).toBe(403)

    const clusterStatus = await fetchSchedulingApi<Record<string, unknown>>(
      page,
      '/scheduling/quartz/cluster-status',
    )
    expect(clusterStatus.status).toBe(403)
  })
})
