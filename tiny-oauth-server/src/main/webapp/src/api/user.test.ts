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
    const payload = { scopeType: 'DEPT' as const, scopeId: 200, roleIds: [3, 4] }
    mocks.post.mockResolvedValue({ message: 'ok' })

    await deleteUser('11')
    await batchDeleteUsers(ids)
    await batchEnableUsers(ids)
    await batchDisableUsers(ids)
    await updateUserRoles(9, payload)

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
    expect(mocks.post).toHaveBeenCalledWith('/sys/users/9/roles', payload, {
      idempotency: {
        scope: 'sys-users:roles:update:9',
        payload,
      },
    })
  })

  it('should request scoped user roles with query params', async () => {
    mocks.get.mockResolvedValue([3, 4])
    const { getUserRoles } = await import('@/api/user')

    const result = await getUserRoles(9, { scopeType: 'DEPT', scopeId: 200 })

    expect(mocks.get).toHaveBeenCalledWith('/sys/users/9/roles', {
      params: {
        scopeType: 'DEPT',
        scopeId: 200,
      },
    })
    expect(result).toEqual([3, 4])
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

  it('should switch active scope and return POST body without implicit getCurrentUser', async () => {
    const postBody = {
      success: true,
      tokenRefreshRequired: true,
      newActiveScopeType: 'DEPT' as const,
      newActiveScopeId: 200,
      activeTenantId: 9,
    }
    mocks.post.mockResolvedValue(postBody)

    const { switchActiveScope } = await import('@/api/user')
    const result = await switchActiveScope({ scopeType: 'DEPT', scopeId: 200 })

    expect(mocks.post).toHaveBeenCalledWith('/sys/users/current/active-scope', { scopeType: 'DEPT', scopeId: 200 })
    expect(mocks.get).not.toHaveBeenCalled()
    expect(result.tokenRefreshRequired).toBe(true)
    expect(result.newActiveScopeId).toBe(200)
  })
})
