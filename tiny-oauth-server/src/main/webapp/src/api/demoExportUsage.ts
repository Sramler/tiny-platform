import request from '@/utils/request'

export interface DemoExportUsage {
  id?: number
  tenantId?: number
  tenantName?: string
  loginName?: string
  createdAt?: string
  // 对应 demo_export_usage 表中的字段
  tenantIdRef?: string
  tenantNameRef?: string
  userId?: number
  clientId?: string
  grantType?: string
  payload?: string
  createdAtRaw?: string
}

export function demoExportUsageList(params: any) {
  const apiParams: { [key: string]: any } = {
    page: (params.current || 1) - 1,
    size: params.pageSize || 10,
    // 与后端 DemoExportUsageController.list 参数保持一致
    tenantId: params.tenantId,
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
    tenantId?: number
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

export function clearDemoExportUsage(params?: { tenantId?: number }) {
  return request.post('/demo/export-usage/clear', null, { params })
}
