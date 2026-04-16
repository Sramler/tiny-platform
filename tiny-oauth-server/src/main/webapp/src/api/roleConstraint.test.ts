import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: requestMocks.get,
    post: requestMocks.post,
    delete: requestMocks.delete,
  },
}))

describe('role constraint API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should use singular backend routes for list and create', async () => {
    requestMocks.get.mockResolvedValue([{ childRoleId: 2, parentRoleId: 1 }])
    requestMocks.post.mockResolvedValue(undefined)
    const { listHierarchies, createHierarchy } = await import('@/api/roleConstraint')

    await listHierarchies()
    await createHierarchy({ parentRoleId: 1, childRoleId: 2 })

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/role-constraints/hierarchy')
    expect(requestMocks.post).toHaveBeenCalledWith('/sys/role-constraints/hierarchy', {
      parentRoleId: 1,
      childRoleId: 2,
    }, {
      idempotency: {
        scope: 'sys-role-constraints:hierarchy:create',
        payload: {
          parentRoleId: 1,
          childRoleId: 2,
        },
      },
    })
  })

  it('should delete hierarchy with query params instead of path id', async () => {
    requestMocks.delete.mockResolvedValue(undefined)
    const { deleteHierarchy } = await import('@/api/roleConstraint')

    await deleteHierarchy({ childRoleId: 2, parentRoleId: 1 })

    expect(requestMocks.delete).toHaveBeenCalledWith('/sys/role-constraints/hierarchy', {
      params: {
        childRoleId: 2,
        parentRoleId: 1,
      },
      idempotency: {
        scope: 'sys-role-constraints:hierarchy:delete:2:1',
        payload: {
          childRoleId: 2,
          parentRoleId: 1,
        },
      },
    })
  })

  it('should align mutex, prerequisite and cardinality payloads with backend contract', async () => {
    requestMocks.post.mockResolvedValue(undefined)
    requestMocks.delete.mockResolvedValue(undefined)
    const {
      createMutex,
      deleteMutex,
      createPrerequisite,
      deletePrerequisite,
      createCardinality,
      deleteCardinality,
    } = await import('@/api/roleConstraint')

    await createMutex({ roleIdA: 3, roleIdB: 9 })
    await deleteMutex({ roleIdA: 3, roleIdB: 9 })
    await createPrerequisite({ roleId: 10, requiredRoleId: 11 })
    await deletePrerequisite({ roleId: 10, requiredRoleId: 11 })
    await createCardinality({ roleId: 12, scopeType: 'TENANT', maxAssignments: 2 })
    await deleteCardinality({ roleId: 12, scopeType: 'TENANT' })

    expect(requestMocks.post).toHaveBeenNthCalledWith(1, '/sys/role-constraints/mutex', {
      roleIdA: 3,
      roleIdB: 9,
    }, {
      idempotency: {
        scope: 'sys-role-constraints:mutex:create',
        payload: {
          roleIdA: 3,
          roleIdB: 9,
        },
      },
    })
    expect(requestMocks.delete).toHaveBeenNthCalledWith(1, '/sys/role-constraints/mutex', {
      params: {
        roleIdA: 3,
        roleIdB: 9,
      },
      idempotency: {
        scope: 'sys-role-constraints:mutex:delete:3:9',
        payload: {
          roleIdA: 3,
          roleIdB: 9,
        },
      },
    })
    expect(requestMocks.post).toHaveBeenNthCalledWith(2, '/sys/role-constraints/prerequisite', {
      roleId: 10,
      requiredRoleId: 11,
    }, {
      idempotency: {
        scope: 'sys-role-constraints:prerequisite:create',
        payload: {
          roleId: 10,
          requiredRoleId: 11,
        },
      },
    })
    expect(requestMocks.delete).toHaveBeenNthCalledWith(2, '/sys/role-constraints/prerequisite', {
      params: {
        roleId: 10,
        requiredRoleId: 11,
      },
      idempotency: {
        scope: 'sys-role-constraints:prerequisite:delete:10:11',
        payload: {
          roleId: 10,
          requiredRoleId: 11,
        },
      },
    })
    expect(requestMocks.post).toHaveBeenNthCalledWith(3, '/sys/role-constraints/cardinality', {
      roleId: 12,
      scopeType: 'TENANT',
      maxAssignments: 2,
    }, {
      idempotency: {
        scope: 'sys-role-constraints:cardinality:create',
        payload: {
          roleId: 12,
          scopeType: 'TENANT',
          maxAssignments: 2,
        },
      },
    })
    expect(requestMocks.delete).toHaveBeenNthCalledWith(3, '/sys/role-constraints/cardinality', {
      params: {
        roleId: 12,
        scopeType: 'TENANT',
      },
      idempotency: {
        scope: 'sys-role-constraints:cardinality:delete:12:TENANT',
        payload: {
          roleId: 12,
          scopeType: 'TENANT',
        },
      },
    })
  })
})
