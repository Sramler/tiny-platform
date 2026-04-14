import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
  put: vi.fn(),
  get: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    post: mocks.post,
    put: mocks.put,
    get: mocks.get,
    delete: mocks.delete,
  },
}))

describe('role/resource/menu API idempotency', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
  })

  it('should attach idempotency config for role mutations', async () => {
    const { createRole, updateRole, deleteRole, updateRoleUsers, updateRoleResources } =
      await import('@/api/role')
    const createData = { name: 'admin' }
    const updateData = { name: 'admin-2' }
    const userPayload = { scopeType: 'TENANT' as const, userIds: [10, 11] }
    const rolePermissionPayload = { permissionIds: [2001, 2002] }

    await createRole(createData)
    await updateRole('9', updateData)
    await deleteRole('9')
    await updateRoleUsers(9, userPayload)
    await updateRoleResources(9, rolePermissionPayload)

    expect(mocks.post).toHaveBeenCalledWith('/sys/roles', createData, {
      idempotency: {
        scope: 'sys-roles:create',
        payload: createData,
      },
    })
    expect(mocks.put).toHaveBeenCalledWith('/sys/roles/9', updateData, {
      idempotency: {
        scope: 'sys-roles:update:9',
        payload: updateData,
      },
    })
    expect(mocks.delete).toHaveBeenCalledWith('/sys/roles/9', {
      idempotency: {
        scope: 'sys-roles:delete:9',
        payload: { id: '9' },
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/sys/roles/9/users', userPayload, {
      idempotency: {
        scope: 'sys-roles:users:update:9',
        payload: userPayload,
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/sys/roles/9/resources', rolePermissionPayload, {
      idempotency: {
        scope: 'sys-roles:resources:update:9',
        payload: rolePermissionPayload,
      },
    })
  })

  it('should attach idempotency config for resource mutations', async () => {
    const { createResource, updateResource, deleteResource, batchDeleteResources, updateResourceSort } =
      await import('@/api/resource')
    const createData = { name: 'resource-a', title: 'A', type: 1 }
    const updateData = { name: 'resource-b', title: 'B', type: 1 }
    const ids = [7, 8]

    await createResource(createData)
    await updateResource(7, updateData)
    await deleteResource(7)
    await batchDeleteResources(ids)
    await updateResourceSort(7, 99)

    expect(mocks.post).toHaveBeenCalledWith('/sys/resources', createData, {
      idempotency: {
        scope: 'sys-resources:create',
        payload: createData,
      },
    })
    expect(mocks.put).toHaveBeenCalledWith('/sys/resources/7', updateData, {
      idempotency: {
        scope: 'sys-resources:update:7',
        payload: updateData,
      },
    })
    expect(mocks.delete).toHaveBeenCalledWith('/sys/resources/7', {
      idempotency: {
        scope: 'sys-resources:delete:7',
        payload: { id: 7 },
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/sys/resources/batch/delete', ids, {
      idempotency: {
        scope: 'sys-resources:batch-delete',
        payload: ids,
      },
    })
    expect(mocks.put).toHaveBeenCalledWith('/sys/resources/7/sort', null, {
      params: { sort: 99 },
      idempotency: {
        scope: 'sys-resources:sort:7',
        payload: { id: 7, sort: 99 },
      },
    })
  })

  it('should attach idempotency config for menu mutations', async () => {
    const { createMenu, updateMenu, deleteMenu, batchDeleteMenus, updateMenuSort } =
      await import('@/api/menu')
    const createData = {
      name: 'menu-a',
      title: 'Menu A',
      url: '/a',
      uri: '/a',
      method: 'GET',
      icon: 'A',
      showIcon: true,
      sort: 1,
      component: 'A',
      redirect: '',
      hidden: false,
      keepAlive: false,
      permission: 'menu:a',
      type: 1,
    }
    const updateData = { ...createData, title: 'Menu B' }
    const ids = [5, 6]

    await createMenu(createData)
    await updateMenu(5, updateData)
    await deleteMenu(5)
    await batchDeleteMenus(ids)
    await updateMenuSort(5, 8)

    expect(mocks.post).toHaveBeenCalledWith('/sys/menus', createData, {
      idempotency: {
        scope: 'sys-menus:create',
        payload: createData,
      },
    })
    expect(mocks.put).toHaveBeenCalledWith('/sys/menus/5', updateData, {
      idempotency: {
        scope: 'sys-menus:update:5',
        payload: updateData,
      },
    })
    expect(mocks.delete).toHaveBeenCalledWith('/sys/menus/5', {
      idempotency: {
        scope: 'sys-menus:delete:5',
        payload: { id: 5 },
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/sys/menus/batch/delete', ids, {
      idempotency: {
        scope: 'sys-menus:batch-delete',
        payload: ids,
      },
    })
    expect(mocks.put).toHaveBeenCalledWith('/sys/menus/5/sort', null, {
      params: { sort: 8 },
      idempotency: {
        scope: 'sys-menus:sort:5',
        payload: { id: 5, sort: 8 },
      },
    })
  })
})
