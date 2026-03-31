import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: requestMocks.get,
    post: requestMocks.post,
    put: requestMocks.put,
    delete: requestMocks.delete,
  },
}))

describe('role API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should request role list with params', async () => {
    requestMocks.get.mockResolvedValue({
      content: [{ id: '1', name: 'Admin', code: 'ROLE_ADMIN' }],
      totalElements: 1,
    })
    const { roleList } = await import('@/api/role')

    const result = await roleList({ current: 1, pageSize: 10, name: 'Admin' })

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/roles', {
      params: { current: 1, pageSize: 10, name: 'Admin' },
    })
    expect(result.content).toHaveLength(1)
    expect(result.content?.[0]?.name).toBe('Admin')
  })

  it('should request role by id', async () => {
    requestMocks.get.mockResolvedValue({ id: '2', name: 'User', code: 'ROLE_USER' })
    const { getRoleById } = await import('@/api/role')

    const result = await getRoleById('2')

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/roles/2')
    expect(result.name).toBe('User')
  })

  it('should create role with idempotency', async () => {
    requestMocks.post.mockResolvedValue({ id: '3', name: 'Editor' })
    const { createRole } = await import('@/api/role')
    const data = { name: 'Editor', code: 'ROLE_EDITOR' }

    await createRole(data)

    expect(requestMocks.post).toHaveBeenCalledWith('/sys/roles', data, {
      idempotency: {
        scope: 'sys-roles:create',
        payload: data,
      },
    })
  })

  it('should update role with idempotency', async () => {
    requestMocks.put.mockResolvedValue({ id: '4', name: 'Editor Updated' })
    const { updateRole } = await import('@/api/role')
    const data = { name: 'Editor Updated' }

    await updateRole('4', data)

    expect(requestMocks.put).toHaveBeenCalledWith('/sys/roles/4', data, {
      idempotency: {
        scope: 'sys-roles:update:4',
        payload: data,
      },
    })
  })

  it('should delete role with idempotency', async () => {
    requestMocks.delete.mockResolvedValue(undefined)
    const { deleteRole } = await import('@/api/role')

    await deleteRole('5')

    expect(requestMocks.delete).toHaveBeenCalledWith('/sys/roles/5', {
      idempotency: {
        scope: 'sys-roles:delete:5',
        payload: { id: '5' },
      },
    })
  })

  it('should request all roles', async () => {
    requestMocks.get.mockResolvedValue([{ id: '1', name: 'Admin' }])
    const { getAllRoles } = await import('@/api/role')

    const result = await getAllRoles()

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/roles/all')
    expect(result).toHaveLength(1)
  })

  it('should request role users and update with idempotency', async () => {
    requestMocks.get.mockResolvedValue([10, 11])
    requestMocks.post.mockResolvedValue(undefined)
    const { getRoleUsers, updateRoleUsers } = await import('@/api/role')

    const userResult = await getRoleUsers(1, { scopeType: 'ORG', scopeId: 88 })
    expect(requestMocks.get).toHaveBeenCalledWith('/sys/roles/1/users', {
      params: { scopeType: 'ORG', scopeId: 88 },
    })
    expect(userResult).toEqual([10, 11])

    const payload = { scopeType: 'ORG' as const, scopeId: 88, userIds: [10, 11, 12] }
    await updateRoleUsers(1, payload)
    expect(requestMocks.post).toHaveBeenCalledWith('/sys/roles/1/users', payload, {
      idempotency: {
        scope: 'sys-roles:users:update:1',
        payload,
      },
    })
  })

  it('should request role resources and update with idempotency', async () => {
    requestMocks.get.mockResolvedValue([20, 21])
    requestMocks.post.mockResolvedValue(undefined)
    const { getRoleResources, updateRoleResources } = await import('@/api/role')

    const resourceResult = await getRoleResources(1)
    expect(requestMocks.get).toHaveBeenCalledWith('/sys/roles/1/resources')
    expect(resourceResult).toEqual([20, 21])

    const payload = { permissionIds: [2001, 2002], resourceIds: [20, 21, 22] }
    await updateRoleResources(1, payload)
    expect(requestMocks.post).toHaveBeenCalledWith('/sys/roles/1/resources', payload, {
      idempotency: {
        scope: 'sys-roles:resources:update:1',
        payload,
      },
    })
  })
})
