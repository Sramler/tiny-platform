import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
  patch: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: requestMocks.get,
    patch: requestMocks.patch,
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

  it('should request permission list/detail and toggle enabled', async () => {
    requestMocks.get.mockResolvedValueOnce([
      { id: 1, permissionCode: 'system:role:list', permissionName: '角色列表', enabled: true, boundRoleCount: 2 },
    ])
    requestMocks.get.mockResolvedValueOnce({
      id: 1,
      permissionCode: 'system:role:list',
      permissionName: '角色列表',
      enabled: true,
      boundRoles: [],
    })
    requestMocks.patch.mockResolvedValue(undefined)
    const { getPermissionList, getPermissionById, updatePermissionEnabled } = await import('@/api/permission')

    await getPermissionList({ keyword: 'role', enabled: true })
    await getPermissionById(1)
    await updatePermissionEnabled(1, false)

    expect(requestMocks.get).toHaveBeenNthCalledWith(1, '/sys/permissions', {
      params: { keyword: 'role', enabled: true },
    })
    expect(requestMocks.get).toHaveBeenNthCalledWith(2, '/sys/permissions/1')
    expect(requestMocks.patch).toHaveBeenCalledWith('/sys/permissions/1/enabled', { enabled: false }, {
      idempotency: {
        scope: 'sys-permissions:enabled:update:1',
        payload: { enabled: false },
      },
    })
  })
})

