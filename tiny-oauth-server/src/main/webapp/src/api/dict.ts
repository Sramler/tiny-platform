import request from '@/utils/request'

// 字典类型接口类型定义
export interface DictTypeItem {
  id?: number
  dictCode: string
  dictName: string
  description?: string
  recordTenantId?: number
  categoryId?: number
  isBuiltin?: boolean
  builtinLocked?: boolean
  enabled?: boolean
  sortOrder?: number
  extAttrs?: Record<string, any>
  createdAt?: string
  updatedAt?: string
  createdBy?: string
  updatedBy?: string
}

// 字典项接口类型定义
export interface DictItem {
  id?: number
  dictTypeId: number
  dictCode?: string
  value: string
  label: string
  description?: string
  recordTenantId?: number
  isBuiltin?: boolean
  enabled?: boolean
  sortOrder?: number
  extAttrs?: Record<string, any>
  createdAt?: string
  updatedAt?: string
  createdBy?: string
  updatedBy?: string
}

export interface PlatformDictOverrideSummary {
  tenantId: number
  tenantCode?: string
  tenantName?: string
  baselineCount: number
  overriddenCount: number
  inheritedCount: number
  orphanOverlayCount: number
}

export interface PlatformDictOverrideDetail {
  value: string
  status: 'INHERITED' | 'OVERRIDDEN' | 'ORPHAN_OVERLAY'
  baselineLabel?: string
  overlayLabel?: string
  effectiveLabel?: string
  labelChanged: boolean
}

// 字典类型查询参数
export interface DictTypeQuery {
  dictCode?: string
  dictName?: string
  enabled?: boolean
  page?: number
  size?: number
}

// 字典项查询参数
export interface DictItemQuery {
  dictTypeId?: number
  value?: string
  label?: string
  enabled?: boolean
  page?: number
  size?: number
}

// 字典类型创建/更新参数
export interface DictTypeCreateUpdateDto {
  id?: number
  dictCode: string
  dictName: string
  description?: string
  categoryId?: number
  enabled?: boolean
  sortOrder?: number
}

// 字典项创建/更新参数
export interface DictItemCreateUpdateDto {
  id?: number
  dictTypeId: number
  value: string
  label: string
  description?: string
  enabled?: boolean
  sortOrder?: number
}

// 分页响应类型
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  pageNumber: number
  pageSize: number
}

// ==================== 字典类型 API ====================

// 获取字典类型列表（分页）
export function getDictTypeList(params?: DictTypeQuery): Promise<PageResponse<DictTypeItem>> {
  return request.get('/dict/types', { params })
}

// 获取字典类型详情
export function getDictTypeDetail(id: number | string): Promise<DictTypeItem> {
  return request.get(`/dict/types/${id}`)
}

// 根据字典编码获取字典类型
export function getDictTypeByCode(dictCode: string): Promise<DictTypeItem> {
  return request.get(`/dict/types/code/${dictCode}`)
}

// 创建字典类型
export function createDictType(data: DictTypeCreateUpdateDto): Promise<DictTypeItem> {
  return request.post('/dict/types', data, {
    idempotency: {
      scope: 'dict-types:create',
      payload: data,
    },
  })
}

// 更新字典类型
export function updateDictType(
  id: number | string,
  data: DictTypeCreateUpdateDto,
): Promise<DictTypeItem> {
  return request.put(`/dict/types/${id}`, data, {
    idempotency: {
      scope: `dict-types:update:${id}`,
      payload: data,
    },
  })
}

// 删除字典类型
export function deleteDictType(id: number | string): Promise<void> {
  return request.delete(`/dict/types/${id}`, {
    idempotency: {
      scope: `dict-types:delete:${id}`,
      payload: { id },
    },
  })
}

// 批量删除字典类型
export function batchDeleteDictTypes(ids: (number | string)[]): Promise<void> {
  return request.post('/dict/types/batch/delete', ids, {
    idempotency: {
      scope: 'dict-types:batch-delete',
      payload: ids,
    },
  })
}

// 获取当前租户可见的字典类型列表（平台 + 当前租户）
export function getVisibleDictTypes(): Promise<DictTypeItem[]> {
  return request.get('/dict/types/current')
}

// ==================== 字典项 API ====================

// 获取字典项列表（分页）
export function getDictItemList(params?: DictItemQuery): Promise<PageResponse<DictItem>> {
  return request.get('/dict/items', { params })
}

// 获取字典项详情
export function getDictItemDetail(id: number | string): Promise<DictItem> {
  return request.get(`/dict/items/${id}`)
}

// 根据字典类型ID获取字典项列表
export function getDictItemsByType(dictTypeId: number): Promise<DictItem[]> {
  return request.get(`/dict/items/type/${dictTypeId}`)
}

// 根据字典编码获取字典项列表
export function getDictItemsByCode(dictCode: string): Promise<DictItem[]> {
  return request.get(`/dict/items/code/${dictCode}`)
}

// 根据字典编码获取字典映射（value -> label）
export function getDictMap(dictCode: string): Promise<Record<string, string>> {
  return request.get(`/dict/items/map/${dictCode}`)
}

// 根据字典编码和值获取标签
export function getDictLabel(dictCode: string, value: string): Promise<string> {
  return request.get(`/dict/items/label/${dictCode}/${value}`)
}

// 创建字典项
export function createDictItem(data: DictItemCreateUpdateDto): Promise<DictItem> {
  return request.post('/dict/items', data, {
    idempotency: {
      scope: 'dict-items:create',
      payload: data,
    },
  })
}

// 更新字典项
export function updateDictItem(
  id: number | string,
  data: DictItemCreateUpdateDto,
): Promise<DictItem> {
  return request.put(`/dict/items/${id}`, data, {
    idempotency: {
      scope: `dict-items:update:${id}`,
      payload: data,
    },
  })
}

// 删除字典项
export function deleteDictItem(id: number | string): Promise<void> {
  return request.delete(`/dict/items/${id}`, {
    idempotency: {
      scope: `dict-items:delete:${id}`,
      payload: { id },
    },
  })
}

// 批量删除字典项
export function batchDeleteDictItems(ids: (number | string)[]): Promise<void> {
  return request.post('/dict/items/batch/delete', ids, {
    idempotency: {
      scope: 'dict-items:batch-delete',
      payload: ids,
    },
  })
}

// ==================== 平台字典 API ====================

export function getPlatformDictTypeList(params?: DictTypeQuery): Promise<PageResponse<DictTypeItem>> {
  return request.get('/platform/dict/types', { params })
}

export async function getPlatformVisibleDictTypes(): Promise<DictTypeItem[]> {
  const pageSize = 100
  const result: DictTypeItem[] = []
  let pageNumber = 0

  while (true) {
    const page = await getPlatformDictTypeList({ page: pageNumber, size: pageSize })
    const content = page.content || []
    result.push(...content)

    const totalPages = Number(page.totalPages)
    if (content.length === 0) {
      break
    }
    if (Number.isFinite(totalPages)) {
      if (pageNumber + 1 >= totalPages) {
        break
      }
    } else if (content.length < pageSize) {
      break
    }
    pageNumber += 1
  }

  return result
}

export function createPlatformDictType(data: DictTypeCreateUpdateDto): Promise<DictTypeItem> {
  return request.post('/platform/dict/types', data, {
    idempotency: {
      scope: 'platform-dict-types:create',
      payload: data,
    },
  })
}

export function updatePlatformDictType(
  id: number | string,
  data: DictTypeCreateUpdateDto,
): Promise<DictTypeItem> {
  return request.put(`/platform/dict/types/${id}`, data, {
    idempotency: {
      scope: `platform-dict-types:update:${id}`,
      payload: data,
    },
  })
}

export function deletePlatformDictType(id: number | string): Promise<void> {
  return request.delete(`/platform/dict/types/${id}`, {
    idempotency: {
      scope: `platform-dict-types:delete:${id}`,
      payload: { id },
    },
  })
}

export function getPlatformDictItemList(params?: DictItemQuery): Promise<PageResponse<DictItem>> {
  return request.get('/platform/dict/items', { params })
}

export function createPlatformDictItem(data: DictItemCreateUpdateDto): Promise<DictItem> {
  return request.post('/platform/dict/items', data, {
    idempotency: {
      scope: 'platform-dict-items:create',
      payload: data,
    },
  })
}

export function updatePlatformDictItem(
  id: number | string,
  data: DictItemCreateUpdateDto,
): Promise<DictItem> {
  return request.put(`/platform/dict/items/${id}`, data, {
    idempotency: {
      scope: `platform-dict-items:update:${id}`,
      payload: data,
    },
  })
}

export function deletePlatformDictItem(id: number | string): Promise<void> {
  return request.delete(`/platform/dict/items/${id}`, {
    idempotency: {
      scope: `platform-dict-items:delete:${id}`,
      payload: { id },
    },
  })
}

export function getPlatformDictOverrides(
  dictTypeId: number | string,
): Promise<PlatformDictOverrideSummary[]> {
  return request.get(`/platform/dict/types/${dictTypeId}/overrides`)
}

export function getPlatformDictOverrideDetails(
  dictTypeId: number | string,
  tenantId: number | string,
): Promise<PlatformDictOverrideDetail[]> {
  return request.get(`/platform/dict/types/${dictTypeId}/overrides/${tenantId}`)
}
