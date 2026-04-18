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

describe('dict API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('dict type', () => {
    it('should request dict type list with params', async () => {
      requestMocks.get.mockResolvedValue({
        content: [{ id: 1, dictCode: 'STATUS', dictName: '状态' }],
        totalElements: 1,
        totalPages: 1,
        pageNumber: 0,
        pageSize: 10,
      })
      const { getDictTypeList } = await import('@/api/dict')

      const result = await getDictTypeList({ dictCode: 'STATUS', page: 0, size: 10 })

      expect(requestMocks.get).toHaveBeenCalledWith('/dict/types', { params: { dictCode: 'STATUS', page: 0, size: 10 } })
      expect(result.content).toHaveLength(1)
      expect(result.content[0]).toBeDefined()
      expect(result.content[0]?.dictCode).toBe('STATUS')
      expect(result.totalElements).toBe(1)
    })

    it('should request dict type detail by id', async () => {
      requestMocks.get.mockResolvedValue({ id: 2, dictCode: 'COLOR', dictName: '颜色' })
      const { getDictTypeDetail } = await import('@/api/dict')

      const result = await getDictTypeDetail(2)

      expect(requestMocks.get).toHaveBeenCalledWith('/dict/types/2')
      expect(result.dictCode).toBe('COLOR')
    })

    it('should request dict type by code', async () => {
      requestMocks.get.mockResolvedValue({ id: 3, dictCode: 'ENABLE_STATUS', dictName: '启用状态' })
      const { getDictTypeByCode } = await import('@/api/dict')

      const result = await getDictTypeByCode('ENABLE_STATUS')

      expect(requestMocks.get).toHaveBeenCalledWith('/dict/types/code/ENABLE_STATUS')
      expect(result.dictCode).toBe('ENABLE_STATUS')
    })

    it('should request visible dict types', async () => {
      requestMocks.get.mockResolvedValue([{ id: 1, dictCode: 'STATUS' }])
      const { getVisibleDictTypes } = await import('@/api/dict')

      const result = await getVisibleDictTypes()

      expect(requestMocks.get).toHaveBeenCalledWith('/dict/types/current')
      expect(result).toHaveLength(1)
      expect(result[0]).toBeDefined()
      expect(result[0]?.dictCode).toBe('STATUS')
    })

  })

  describe('dict item', () => {
    it('should request dict item list with params', async () => {
      requestMocks.get.mockResolvedValue({
        content: [{ id: 1, dictTypeId: 10, value: '1', label: '启用' }],
        totalElements: 1,
        totalPages: 1,
        pageNumber: 0,
        pageSize: 10,
      })
      const { getDictItemList } = await import('@/api/dict')

      const result = await getDictItemList({ dictTypeId: 10, page: 0, size: 10 })

      expect(requestMocks.get).toHaveBeenCalledWith('/dict/items', {
        params: { dictTypeId: 10, page: 0, size: 10 },
      })
      expect(result.content).toHaveLength(1)
      expect(result.content[0]).toBeDefined()
      expect(result.content[0]?.value).toBe('1')
    })

    it('should request dict item detail by id', async () => {
      requestMocks.get.mockResolvedValue({ id: 5, dictTypeId: 10, value: 'A', label: 'Alpha' })
      const { getDictItemDetail } = await import('@/api/dict')

      const result = await getDictItemDetail(5)

      expect(requestMocks.get).toHaveBeenCalledWith('/dict/items/5')
      expect(result.value).toBe('A')
    })

    it('should request dict items by type id', async () => {
      requestMocks.get.mockResolvedValue([
        { id: 1, value: '1', label: '启用' },
        { id: 2, value: '0', label: '禁用' },
      ])
      const { getDictItemsByType } = await import('@/api/dict')

      const result = await getDictItemsByType(10)

      expect(requestMocks.get).toHaveBeenCalledWith('/dict/items/type/10')
      expect(result).toHaveLength(2)
    })

    it('should request dict items by code', async () => {
      requestMocks.get.mockResolvedValue([{ id: 1, value: '1', label: '启用' }])
      const { getDictItemsByCode } = await import('@/api/dict')

      const result = await getDictItemsByCode('STATUS')

      expect(requestMocks.get).toHaveBeenCalledWith('/dict/items/code/STATUS')
      expect(result).toHaveLength(1)
    })

    it('should request dict map (value -> label)', async () => {
      requestMocks.get.mockResolvedValue({ '1': '启用', '0': '禁用' })
      const { getDictMap } = await import('@/api/dict')

      const result = await getDictMap('STATUS')

      expect(requestMocks.get).toHaveBeenCalledWith('/dict/items/map/STATUS')
      expect(result).toEqual({ '1': '启用', '0': '禁用' })
    })

    it('should request dict label by code and value', async () => {
      requestMocks.get.mockResolvedValue('启用')
      const { getDictLabel } = await import('@/api/dict')

      const result = await getDictLabel('STATUS', '1')

      expect(requestMocks.get).toHaveBeenCalledWith('/dict/items/label/STATUS/1')
      expect(result).toBe('启用')
    })
  })

  describe('platform dict', () => {
    it('should request platform dict type list', async () => {
      requestMocks.get.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, pageNumber: 0, pageSize: 10 })
      const { getPlatformDictTypeList } = await import('@/api/dict')

      await getPlatformDictTypeList({ dictCode: 'STATUS', page: 0, size: 10 })

      expect(requestMocks.get).toHaveBeenCalledWith('/platform/dict/types', {
        params: { dictCode: 'STATUS', page: 0, size: 10 },
      })
    })

    it('should request platform override summary and detail', async () => {
      requestMocks.get.mockResolvedValueOnce([{ tenantId: 7 }]).mockResolvedValueOnce([{ value: 'ENABLED' }])
      const { getPlatformDictOverrides, getPlatformDictOverrideDetails } = await import('@/api/dict')

      await getPlatformDictOverrides(10)
      await getPlatformDictOverrideDetails(10, 7)

      expect(requestMocks.get).toHaveBeenNthCalledWith(1, '/platform/dict/types/10/overrides')
      expect(requestMocks.get).toHaveBeenNthCalledWith(2, '/platform/dict/types/10/overrides/7')
    })

    it('should page through platform visible dict types with max page size 100', async () => {
      requestMocks.get
        .mockResolvedValueOnce({
          content: [{ id: 1, dictCode: 'A', dictName: 'A' }],
          totalElements: 101,
          totalPages: 2,
          pageNumber: 0,
          pageSize: 100,
        })
        .mockResolvedValueOnce({
          content: [{ id: 2, dictCode: 'B', dictName: 'B' }],
          totalElements: 101,
          totalPages: 2,
          pageNumber: 1,
          pageSize: 100,
        })
      const { getPlatformVisibleDictTypes } = await import('@/api/dict')

      const result = await getPlatformVisibleDictTypes()

      expect(requestMocks.get).toHaveBeenNthCalledWith(1, '/platform/dict/types', {
        params: { page: 0, size: 100 },
      })
      expect(requestMocks.get).toHaveBeenNthCalledWith(2, '/platform/dict/types', {
        params: { page: 1, size: 100 },
      })
      expect(result).toHaveLength(2)
      expect(result.map((item) => item.dictCode)).toEqual(['A', 'B'])
    })
  })
})
