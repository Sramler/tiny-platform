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

describe('dict/tenant API idempotency', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
  })

  it('should attach idempotency config for dict type and item mutations', async () => {
    const {
      createDictType,
      updateDictType,
      deleteDictType,
      batchDeleteDictTypes,
      createDictItem,
      updateDictItem,
      deleteDictItem,
      batchDeleteDictItems,
    } = await import('@/api/dict')
    const dictType = { dictCode: 'status', dictName: '状态' }
    const dictItem = { dictTypeId: 9, value: 'enabled', label: '启用' }
    const dictTypeIds = [3, 4]
    const dictItemIds = [7, 8]

    await createDictType(dictType)
    await updateDictType(3, dictType)
    await deleteDictType(3)
    await batchDeleteDictTypes(dictTypeIds)
    await createDictItem(dictItem)
    await updateDictItem(7, dictItem)
    await deleteDictItem(7)
    await batchDeleteDictItems(dictItemIds)

    expect(mocks.post).toHaveBeenCalledWith('/dict/types', dictType, {
      idempotency: {
        scope: 'dict-types:create',
        payload: dictType,
      },
    })
    expect(mocks.put).toHaveBeenCalledWith('/dict/types/3', dictType, {
      idempotency: {
        scope: 'dict-types:update:3',
        payload: dictType,
      },
    })
    expect(mocks.delete).toHaveBeenCalledWith('/dict/types/3', {
      idempotency: {
        scope: 'dict-types:delete:3',
        payload: { id: 3 },
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/dict/types/batch/delete', dictTypeIds, {
      idempotency: {
        scope: 'dict-types:batch-delete',
        payload: dictTypeIds,
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/dict/items', dictItem, {
      idempotency: {
        scope: 'dict-items:create',
        payload: dictItem,
      },
    })
    expect(mocks.put).toHaveBeenCalledWith('/dict/items/7', dictItem, {
      idempotency: {
        scope: 'dict-items:update:7',
        payload: dictItem,
      },
    })
    expect(mocks.delete).toHaveBeenCalledWith('/dict/items/7', {
      idempotency: {
        scope: 'dict-items:delete:7',
        payload: { id: 7 },
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/dict/items/batch/delete', dictItemIds, {
      idempotency: {
        scope: 'dict-items:batch-delete',
        payload: dictItemIds,
      },
    })
  })

  it('should attach idempotency config for tenant mutations', async () => {
    const { createTenant, updateTenant, deleteTenant } = await import('@/api/tenant')
    const createData = { code: 'tenant-a', name: 'Tenant A' }
    const updateData = { name: 'Tenant B' }

    await createTenant(createData)
    await updateTenant(11, updateData)
    await deleteTenant(11)

    expect(mocks.post).toHaveBeenCalledWith('/sys/tenants', createData, {
      idempotency: {
        scope: 'sys-tenants:create',
        payload: createData,
      },
    })
    expect(mocks.put).toHaveBeenCalledWith('/sys/tenants/11', updateData, {
      idempotency: {
        scope: 'sys-tenants:update:11',
        payload: updateData,
      },
    })
    expect(mocks.delete).toHaveBeenCalledWith('/sys/tenants/11', {
      idempotency: {
        scope: 'sys-tenants:delete:11',
        payload: { id: 11 },
      },
    })
  })
})
