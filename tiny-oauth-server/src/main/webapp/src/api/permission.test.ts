import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: requestMocks.get,
  },
}))

describe('permission API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should request permission options via /sys/permissions/options', async () => {
    requestMocks.get.mockResolvedValue([
      { id: 7001, permissionCode: 'system:resource:list', permissionName: '资源查看' },
    ])
    const { getPermissionOptions } = await import('@/api/permission')

    const result = await getPermissionOptions('resource', 20)

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/permissions/options', {
      params: { keyword: 'resource', limit: 20 },
    })
    expect(result).toHaveLength(1)
    expect(result[0]?.id).toBe(7001)
  })
})

