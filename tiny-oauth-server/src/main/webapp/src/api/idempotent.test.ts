import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: mocks.get,
  },
}))

describe('idempotent API', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
  })

  it('should normalize metrics and top key payloads', async () => {
    const { getIdempotentMetrics, getIdempotentMqMetrics, getIdempotentTopKeys } =
      await import('@/api/idempotent')

    mocks.get
      .mockResolvedValueOnce({
        windowMinutes: '60',
        windowStartEpochMillis: '1741431600000',
        windowEndEpochMillis: '1741435200000',
        passCount: '12',
        hitCount: 3,
        successCount: '10',
        failureCount: 2,
        storeErrorCount: '1',
        validationRejectCount: '4',
        rejectCount: '7',
        totalCheckCount: '20',
        conflictRate: '0.15',
        storageErrorRate: 0.05,
      })
      .mockResolvedValueOnce({
        topKeys: [
          { key: 'POST /sys/users', count: '8' },
          { key: 'POST /process/start', count: 3 },
        ],
      })
      .mockResolvedValueOnce({
        windowMinutes: '60',
        windowStartEpochMillis: '1741431600000',
        windowEndEpochMillis: '1741435200000',
        successCount: '18',
        failureCount: 2,
        duplicateRate: '0.08',
      })

    await expect(getIdempotentMetrics()).resolves.toEqual({
      windowMinutes: 60,
      windowStartEpochMillis: 1741431600000,
      windowEndEpochMillis: 1741435200000,
      passCount: 12,
      hitCount: 3,
      successCount: 10,
      failureCount: 2,
      storeErrorCount: 1,
      validationRejectCount: 4,
      rejectCount: 7,
      totalCheckCount: 20,
      conflictRate: 0.15,
      storageErrorRate: 0.05,
    })

    await expect(getIdempotentTopKeys(6)).resolves.toEqual([
      { key: 'POST /sys/users', count: 8 },
      { key: 'POST /process/start', count: 3 },
    ])

    await expect(getIdempotentMqMetrics()).resolves.toEqual({
      windowMinutes: 60,
      windowStartEpochMillis: 1741431600000,
      windowEndEpochMillis: 1741435200000,
      successCount: 18,
      failureCount: 2,
      duplicateRate: 0.08,
    })

    expect(mocks.get).toHaveBeenNthCalledWith(1, '/metrics/idempotent')
    expect(mocks.get).toHaveBeenNthCalledWith(2, '/metrics/idempotent/top-keys', {
      params: { limit: 6 },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(3, '/metrics/idempotent/mq')
  })

  it('should pass tenantId query params when requested', async () => {
    const { getIdempotentMetrics, getIdempotentMqMetrics, getIdempotentTopKeys } =
      await import('@/api/idempotent')

    mocks.get
      .mockResolvedValueOnce({})
      .mockResolvedValueOnce({ topKeys: [] })
      .mockResolvedValueOnce({})

    await getIdempotentMetrics(8)
    await getIdempotentTopKeys(10, 8)
    await getIdempotentMqMetrics(8)

    expect(mocks.get).toHaveBeenNthCalledWith(1, '/metrics/idempotent', {
      params: { tenantId: 8 },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(2, '/metrics/idempotent/top-keys', {
      params: { limit: 10, tenantId: 8 },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(3, '/metrics/idempotent/mq', {
      params: { tenantId: 8 },
    })
  })
})
