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

describe('menu API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should request menu list with params', async () => {
    requestMocks.get.mockResolvedValue([{ id: 1, name: 'sys', title: '系统' }])
    const { menuList } = await import('@/api/menu')

    const result = await menuList({ name: 'sys', enabled: true })

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/menus', { params: { name: 'sys', enabled: true } })
    expect(result).toHaveLength(1)
    expect(result[0]?.name).toBe('sys')
  })

  it('should request menu tree', async () => {
    requestMocks.get.mockResolvedValue([{ id: 1, children: [] }])
    const { menuTree } = await import('@/api/menu')

    const result = await menuTree()

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/menus/tree')
    expect(result).toHaveLength(1)
  })

  it('should request full menu tree', async () => {
    requestMocks.get.mockResolvedValue([])
    const { menuTreeAll } = await import('@/api/menu')

    await menuTreeAll()

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/menus/tree/all')
  })

  it('should request menus by parent id', async () => {
    requestMocks.get.mockResolvedValue([{ id: 2, parentId: 1 }])
    const { getMenusByParentId } = await import('@/api/menu')

    const result = await getMenusByParentId(1)

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/menus/parent/1')
    expect(result).toHaveLength(1)
  })

  it('should create menu with idempotency', async () => {
    requestMocks.post.mockResolvedValue({ id: 10, name: 'new-menu' })
    const { createMenu } = await import('@/api/menu')
    const data = {
      name: 'new-menu',
      title: 'New Menu',
      url: '/new',
      uri: '/new',
      method: 'GET',
      icon: '',
      showIcon: true,
      sort: 0,
      component: '',
      redirect: '',
      hidden: false,
      keepAlive: true,
      permission: '',
      type: 1,
    }

    await createMenu(data)

    expect(requestMocks.post).toHaveBeenCalledWith('/sys/menus', data, {
      idempotency: {
        scope: 'sys-menus:create',
        payload: data,
      },
    })
  })

  it('should update menu with idempotency', async () => {
    requestMocks.put.mockResolvedValue(undefined)
    const { updateMenu } = await import('@/api/menu')
    const data = {
      name: 'menu',
      title: 'Menu',
      url: '/m',
      uri: '/m',
      method: 'GET',
      icon: '',
      showIcon: true,
      sort: 0,
      component: '',
      redirect: '',
      hidden: false,
      keepAlive: true,
      permission: '',
      type: 1,
    }

    await updateMenu(5, data)

    expect(requestMocks.put).toHaveBeenCalledWith('/sys/menus/5', data, {
      idempotency: {
        scope: 'sys-menus:update:5',
        payload: data,
      },
    })
  })

  it('should delete menu with idempotency', async () => {
    requestMocks.delete.mockResolvedValue(undefined)
    const { deleteMenu } = await import('@/api/menu')

    await deleteMenu(3)

    expect(requestMocks.delete).toHaveBeenCalledWith('/sys/menus/3', {
      idempotency: {
        scope: 'sys-menus:delete:3',
        payload: { id: 3 },
      },
    })
  })

  it('should batch delete menus with idempotency', async () => {
    requestMocks.post.mockResolvedValue({ success: true })
    const { batchDeleteMenus } = await import('@/api/menu')
    const ids = [1, 2, 3]

    await batchDeleteMenus(ids)

    expect(requestMocks.post).toHaveBeenCalledWith('/sys/resources/menus/batch/delete', ids, {
      idempotency: {
        scope: 'sys-menus:batch-delete',
        payload: ids,
      },
    })
  })
})
