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

describe('resource API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should request resource detail', async () => {
    requestMocks.get.mockResolvedValue({ id: 2, name: 'res2', type: 2 })
    const { getResourceDetail } = await import('@/api/resource')

    const result = await getResourceDetail(2)

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/resources/2')
    expect(result.name).toBe('res2')
  })

  it('should create resource with idempotency', async () => {
    requestMocks.post.mockResolvedValue({ id: 10, name: 'new-res' })
    const { createResource } = await import('@/api/resource')
    const data = { name: 'new-res', title: 'New Resource', type: 2, requiredPermissionId: 101 }

    await createResource(data)

    expect(requestMocks.post).toHaveBeenCalledWith('/sys/resources', data, {
      idempotency: {
        scope: 'sys-resources:create',
        payload: data,
      },
    })
  })

  it('should update resource with idempotency', async () => {
    requestMocks.put.mockResolvedValue({ id: 11, name: 'updated' })
    const { updateResource } = await import('@/api/resource')
    const data = { name: 'updated', title: 'Updated', type: 2, requiredPermissionId: 102 }

    await updateResource(11, data)

    expect(requestMocks.put).toHaveBeenCalledWith('/sys/resources/11', data, {
      idempotency: {
        scope: 'sys-resources:update:11',
        payload: data,
      },
    })
  })

  it('should delete resource with idempotency', async () => {
    requestMocks.delete.mockResolvedValue(undefined)
    const { deleteResource } = await import('@/api/resource')

    await deleteResource(12)

    expect(requestMocks.delete).toHaveBeenCalledWith('/sys/resources/12', {
      idempotency: {
        scope: 'sys-resources:delete:12',
        payload: { id: 12 },
      },
    })
  })

  it('should batch delete resources with idempotency', async () => {
    requestMocks.post.mockResolvedValue({ success: true, message: 'ok' })
    const { batchDeleteResources } = await import('@/api/resource')
    const ids = [1, 2, 3]

    await batchDeleteResources(ids)

    expect(requestMocks.post).toHaveBeenCalledWith('/sys/resources/batch/delete', ids, {
      idempotency: {
        scope: 'sys-resources:batch-delete',
        payload: ids,
      },
    })
  })



  it('should request runtime ui actions', async () => {
    requestMocks.get.mockResolvedValue([{ id: 11, permission: 'system:resource:create' }])
    const { getRuntimeUiActions } = await import('@/api/resource')

    const result = await getRuntimeUiActions('/system/resource')

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/resources/runtime/ui-actions', {
      params: { pagePath: '/system/resource' },
    })
    expect(result).toHaveLength(1)
  })

  it('should request runtime api access', async () => {
    requestMocks.get.mockResolvedValue({ allowed: true })
    const { getRuntimeApiAccess } = await import('@/api/resource')

    const result = await getRuntimeApiAccess('GET', '/sys/resources')

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/resources/runtime/api-access', {
      params: { method: 'GET', uri: '/sys/resources' },
    })
    expect(result.allowed).toBe(true)
  })

  it('should request resource tree', async () => {
    requestMocks.get.mockResolvedValue([{ id: 1, children: [] }])
    const { getResourceTree } = await import('@/api/resource')

    const result = await getResourceTree()

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/resources/tree')
    expect(result).toHaveLength(1)
  })
})
