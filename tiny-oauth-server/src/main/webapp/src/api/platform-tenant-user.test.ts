import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: requestMocks.get,
  },
}))

describe('platform-tenant-user API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should request tenant-scoped users through platform bridge API', async () => {
    requestMocks.get.mockResolvedValue({
      content: [{ id: 9, username: 'alice', nickname: 'Alice' }],
      totalElements: 1,
    })
    const { listPlatformTenantUsers } = await import('@/api/platform-tenant-user')

    const result = await listPlatformTenantUsers({
      tenantId: 7,
      current: 2,
      pageSize: 20,
      username: 'alice',
      nickname: 'Ali',
    })

    expect(requestMocks.get).toHaveBeenCalledWith('/platform/tenants/7/users', {
      params: {
        page: 1,
        size: 20,
        username: 'alice',
        nickname: 'Ali',
      },
    })
    expect(result.records).toHaveLength(1)
    expect(result.total).toBe(1)
  })

  it('should request tenant-scoped user detail through platform bridge API', async () => {
    requestMocks.get.mockResolvedValue({ id: 9, username: 'alice', nickname: 'Alice' })
    const { getPlatformTenantUserDetail } = await import('@/api/platform-tenant-user')

    await getPlatformTenantUserDetail(7, 9)

    expect(requestMocks.get).toHaveBeenCalledWith('/platform/tenants/7/users/9')
  })
})
