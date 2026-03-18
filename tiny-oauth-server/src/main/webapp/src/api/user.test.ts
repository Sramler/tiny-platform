import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
  put: vi.fn(),
  get: vi.fn(),
  delete: vi.fn(),
  syncTenantContextFromClaims: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    post: mocks.post,
    put: mocks.put,
    get: mocks.get,
    delete: mocks.delete,
  },
}))

vi.mock('@/utils/tenant', () => ({
  syncTenantContextFromClaims: mocks.syncTenantContextFromClaims,
}))

describe('user.ts', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
  })

  it('should attach idempotency header when creating a user', async () => {
    const { createUser } = await import('@/api/user')
    const data = { username: 'alice', nickname: 'Alice' }

    await createUser(data)

    expect(mocks.post).toHaveBeenCalledWith('/sys/users', data, {
      idempotency: {
        scope: 'sys-users:create',
        payload: data,
      },
    })
  })

  it('should attach idempotency header when updating a user', async () => {
    const { updateUser } = await import('@/api/user')
    const data = { nickname: 'Alice 2' }

    await updateUser('12', data)

    expect(mocks.put).toHaveBeenCalledWith('/sys/users/12', data, {
      idempotency: {
        scope: 'sys-users:update:12',
        payload: data,
      },
    })
  })

  it('should attach idempotency config for user destructive and batch mutations', async () => {
    const { deleteUser, batchDeleteUsers, batchEnableUsers, batchDisableUsers, updateUserRoles } =
      await import('@/api/user')
    const ids = ['11', '12']
    const roleIds = [3, 4]
    mocks.post.mockResolvedValue({ message: 'ok' })

    await deleteUser('11')
    await batchDeleteUsers(ids)
    await batchEnableUsers(ids)
    await batchDisableUsers(ids)
    await updateUserRoles(9, roleIds)

    expect(mocks.delete).toHaveBeenCalledWith('/sys/users/11', {
      idempotency: {
        scope: 'sys-users:delete:11',
        payload: { id: '11' },
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/sys/users/batch/delete', ids, {
      idempotency: {
        scope: 'sys-users:batch-delete',
        payload: ids,
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/sys/users/batch/enable', ids, {
      idempotency: {
        scope: 'sys-users:batch-enable',
        payload: ids,
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/sys/users/batch/disable', ids, {
      idempotency: {
        scope: 'sys-users:batch-disable',
        payload: ids,
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/sys/users/9/roles', roleIds, {
      idempotency: {
        scope: 'sys-users:roles:update:9',
        payload: roleIds,
      },
    })
  })

  it('should request current user and sync active tenant context', async () => {
    const payload = { id: '7', username: 'alice', activeTenantId: 9 }
    mocks.get.mockResolvedValue(payload)

    const { getCurrentUser } = await import('@/api/user')
    const result = await getCurrentUser()

    expect(mocks.get).toHaveBeenCalledWith('/sys/users/current')
    expect(mocks.syncTenantContextFromClaims).toHaveBeenCalledWith(payload)
    expect(result.activeTenantId).toBe(9)
  })
})
