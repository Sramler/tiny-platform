import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  getTenantId: vi.fn(),
}))

vi.mock('@/utils/tenant', () => ({
  getTenantId: mocks.getTenantId,
}))

import {
  createIdempotencyFingerprint,
  createIdempotencyHeaders,
  createIdempotencyKey,
  createSubmitIdempotencyKey,
} from '@/utils/idempotency'

describe('idempotency.ts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getTenantId.mockReturnValue('101')
  })

  it('should generate stable keys for the same semantic payload', () => {
    const left = createIdempotencyKey('sys-users:create', {
      username: 'alice',
      enabled: true,
      roleIds: [2, 1],
    })
    const right = createIdempotencyKey('sys-users:create', {
      enabled: true,
      roleIds: [2, 1],
      username: 'alice',
    })

    expect(left).toBe(right)
    expect(left).toMatch(/^[0-9a-f]{16}$/)
    expect(createIdempotencyHeaders('sys-users:create', { username: 'alice' })).toEqual({
      'X-Idempotency-Key': createIdempotencyKey('sys-users:create', { username: 'alice' }),
    })
  })

  it('should change key when tenant or scope changes', () => {
    const tenant101 = createIdempotencyKey('sys-users:create', { username: 'alice' })

    mocks.getTenantId.mockReturnValue('102')
    const tenant102 = createIdempotencyKey('sys-users:create', { username: 'alice' })
    const updateKey = createIdempotencyKey('sys-users:update:1', { username: 'alice' })

    expect(tenant101).not.toBe(tenant102)
    expect(tenant102).not.toBe(updateKey)
  })

  it('should build stable fingerprints and random submit keys', () => {
    const left = createIdempotencyFingerprint({
      scope: 'process-instance:start',
      payload: { processKey: 'demo', variables: { orderId: 'A-1' } },
    })
    const right = createIdempotencyFingerprint({
      payload: { variables: { orderId: 'A-1' }, processKey: 'demo' },
      scope: 'process-instance:start',
    })

    expect(left).toBe(right)
    expect(createSubmitIdempotencyKey()).toMatch(/^[0-9a-f]{32}$/)
  })
})
