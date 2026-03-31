import request from '@/utils/request'

export type DataScope = {
  id: number
  tenantId: number
  roleId: number
  module: string
  scopeType: string
  accessType: string
  createdAt: string
  updatedAt: string
}

export type DataScopeItem = {
  id: number
  dataScopeId: number
  targetType: string
  targetId: string
}

export function getDataScopesByRole(roleId: number) {
  return request.get<DataScope[]>(`/sys/data-scope/role/${roleId}`)
}

export function upsertDataScope(data: Partial<DataScope>) {
  return request.put('/sys/data-scope', data, {
    idempotency: {
      scope: 'sys-data-scope:upsert',
      payload: data,
    },
  })
}

export function deleteDataScope(id: number) {
  return request.delete(`/sys/data-scope/${id}`, {
    idempotency: {
      scope: `sys-data-scope:delete:${id}`,
      payload: { id },
    },
  })
}

export function getDataScopeItems(dataScopeId: number) {
  return request.get<DataScopeItem[]>(`/sys/data-scope/${dataScopeId}/items`)
}
