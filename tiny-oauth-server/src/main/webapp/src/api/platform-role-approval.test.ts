import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: requestMocks,
}))

describe('platform-role-approval API', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
  })

  it('lists assignment requests with Spring page params', async () => {
    requestMocks.get.mockResolvedValue({
      content: [{ id: 1, targetUserId: 9, roleId: 2, actionType: 'GRANT', status: 'PENDING', requestedBy: 3 }],
      totalElements: 4,
      pageNumber: 0,
      pageSize: 20,
    })
    const { listPlatformRoleAssignmentRequests } = await import('@/api/platform-role-approval')
    const res = await listPlatformRoleAssignmentRequests({ targetUserId: 9, status: 'PENDING', current: 2, pageSize: 20 })
    expect(requestMocks.get).toHaveBeenCalledWith('/platform/role-assignment-requests', {
      params: { page: 1, size: 20, targetUserId: 9, status: 'PENDING' },
    })
    expect(res.total).toBe(4)
    expect(res.records).toHaveLength(1)
  })

  it('submits with idempotency scope', async () => {
    requestMocks.post.mockResolvedValue({ id: 5 })
    const { submitPlatformRoleAssignmentRequest } = await import('@/api/platform-role-approval')
    const payload = { targetUserId: 1, roleId: 2, actionType: 'GRANT' as const, reason: 'x' }
    await submitPlatformRoleAssignmentRequest(payload)
    expect(requestMocks.post).toHaveBeenCalledWith('/platform/role-assignment-requests', payload, {
      idempotency: {
        scope: 'platform-role-assignment-requests:submit:1:2:GRANT',
        payload,
      },
    })
  })
})
