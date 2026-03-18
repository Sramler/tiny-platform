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

describe('demoExportUsage API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should request list and normalize to records/total', async () => {
    requestMocks.get.mockResolvedValue({
      content: [{ id: 1, recordTenantId: 7 }],
      totalElements: 12,
    })
    const { demoExportUsageList } = await import('@/api/demoExportUsage')

    const result = await demoExportUsageList({
      current: 2,
      pageSize: 10,
      activeTenantId: 7,
      productCode: 'P-1',
      status: 'OK',
    })

    expect(requestMocks.get).toHaveBeenCalledWith('/demo/export-usage', {
      params: {
        page: 1,
        size: 10,
        activeTenantId: 7,
        productCode: 'P-1',
        status: 'OK',
      },
    })
    expect(result).toEqual({ records: [{ id: 1, recordTenantId: 7 }], total: 12 })
  })

  it('should request detail by id', async () => {
    requestMocks.get.mockResolvedValue({ id: 2 })
    const { getDemoExportUsage } = await import('@/api/demoExportUsage')

    await getDemoExportUsage(2)

    expect(requestMocks.get).toHaveBeenCalledWith('/demo/export-usage/2')
  })

  it('should create/update/delete', async () => {
    requestMocks.post.mockResolvedValue({ id: 1 })
    requestMocks.put.mockResolvedValue({ id: 1 })
    requestMocks.delete.mockResolvedValue(undefined)
    const { createDemoExportUsage, updateDemoExportUsage, deleteDemoExportUsage } = await import(
      '@/api/demoExportUsage',
    )

    await createDemoExportUsage({ recordTenantId: 7 })
    await updateDemoExportUsage(1, { status: 'OK' })
    await deleteDemoExportUsage(1)

    expect(requestMocks.post).toHaveBeenCalledWith('/demo/export-usage', { recordTenantId: 7 })
    expect(requestMocks.put).toHaveBeenCalledWith('/demo/export-usage/1', { status: 'OK' })
    expect(requestMocks.delete).toHaveBeenCalledWith('/demo/export-usage/1')
  })

  it('should generate and clear with params', async () => {
    requestMocks.post.mockResolvedValue({ message: 'ok' })
    const { generateDemoExportUsage, clearDemoExportUsage } = await import('@/api/demoExportUsage')

    await generateDemoExportUsage({ activeTenantId: 7, days: 3, rowsPerDay: 100, targetRows: 0, clearExisting: true })
    await clearDemoExportUsage({ activeTenantId: 7 })

    expect(requestMocks.post).toHaveBeenCalledWith('/demo/export-usage/generate', null, {
      params: { activeTenantId: 7, days: 3, rowsPerDay: 100, targetRows: 0, clearExisting: true },
    })
    expect(requestMocks.post).toHaveBeenCalledWith('/demo/export-usage/clear', null, { params: { activeTenantId: 7 } })
  })
})
