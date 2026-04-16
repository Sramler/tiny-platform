import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  patch: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: requestMocks.get,
    post: requestMocks.post,
    patch: requestMocks.patch,
  },
}))

describe('platform-user API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should request paged platform users with platform query params', async () => {
    requestMocks.get.mockResolvedValue({
      content: [{ userId: 1001, username: 'platform_admin', platformStatus: 'ACTIVE' }],
      totalElements: 1,
    })
    const { listPlatformUsers } = await import('@/api/platform-user')

    const result = await listPlatformUsers({
      current: 2,
      pageSize: 20,
      keyword: 'admin',
      enabled: true,
      status: 'ACTIVE',
    })

    expect(requestMocks.get).toHaveBeenCalledWith('/platform/users', {
      params: {
        page: 1,
        size: 20,
        keyword: 'admin',
        enabled: true,
        status: 'ACTIVE',
      },
    })
    expect(result.records).toHaveLength(1)
    expect(result.total).toBe(1)
  })

  it('should request detail, create profile and update platform user status', async () => {
    requestMocks.get.mockResolvedValueOnce({ userId: 1001, username: 'platform_admin', platformStatus: 'ACTIVE' })
    requestMocks.post.mockResolvedValueOnce({ userId: 1001, username: 'platform_admin', platformStatus: 'ACTIVE' })
    requestMocks.patch.mockResolvedValueOnce(undefined)
    const { createPlatformUser, getPlatformUserDetail, updatePlatformUserStatus } = await import('@/api/platform-user')

    await getPlatformUserDetail(1001)
    await createPlatformUser({ userId: 1001, displayName: 'Platform Admin', status: 'ACTIVE' })
    await updatePlatformUserStatus(1001, 'DISABLED')

    expect(requestMocks.get).toHaveBeenNthCalledWith(1, '/platform/users/1001')
    expect(requestMocks.post).toHaveBeenCalledWith('/platform/users', {
      userId: 1001,
      displayName: 'Platform Admin',
      status: 'ACTIVE',
    }, {
      idempotency: {
        scope: 'platform-users:create:1001',
        payload: {
          userId: 1001,
          displayName: 'Platform Admin',
          status: 'ACTIVE',
        },
      },
    })
    expect(requestMocks.patch).toHaveBeenCalledWith('/platform/users/1001/status', {
      status: 'DISABLED',
    }, {
      idempotency: {
        scope: 'platform-users:status:update:1001',
        payload: { status: 'DISABLED' },
      },
    })
  })
})
