import request from '@/utils/request'

export interface DemoExportUsage {
  id?: number
  /**
   * 导出记录所属租户ID，不是当前活动租户上下文。
   */
  recordTenantId?: number
  tenantName?: string
  loginName?: string
  createdAt?: string
  userId?: number
  clientId?: string
  grantType?: string
  payload?: string
}

export function demoExportUsageList(params: {
  current?: number
  pageSize?: number
  activeTenantId?: number
  productCode?: string
  status?: string
}) {
  const apiParams: { [key: string]: any } = {
    page: (params.current || 1) - 1,
    size: params.pageSize || 10,
    activeTenantId: params.activeTenantId,
    productCode: params.productCode,
    status: params.status,
  }

  return request.get('/demo/export-usage', { params: apiParams }).then((res: any) => {
    return {
      records: res.content || [],
      total: Number(res.totalElements) || 0,
    }
  })
}

export function getDemoExportUsage(id: number) {
  return request.get(`/demo/export-usage/${id}`)
}

export function createDemoExportUsage(data: any) {
  return request.post('/demo/export-usage', data)
}

export function updateDemoExportUsage(id: number, data: any) {
  return request.put(`/demo/export-usage/${id}`, data)
}

export function deleteDemoExportUsage(id: number) {
  return request.delete(`/demo/export-usage/${id}`)
}

export function generateDemoExportUsage(
  params?: {
    activeTenantId?: number
    days?: number
    rowsPerDay?: number
    targetRows?: number
    clearExisting?: boolean
  },
  config?: any,
) {
  return request.post('/demo/export-usage/generate', null, {
    params,
    ...(config || {}),
  })
}

export function clearDemoExportUsage(params?: { activeTenantId?: number }) {
  return request.post('/demo/export-usage/clear', null, { params })
}
