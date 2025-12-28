import request from '@/utils/request'

// 字典类型接口类型定义
export interface DictTypeItem {
  id?: number
  dictCode: string
  dictName: string
  description?: string
  tenantId?: number
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
  tenantId?: number
  isBuiltin?: boolean
  enabled?: boolean
  sortOrder?: number
  extAttrs?: Record<string, any>
  createdAt?: string
  updatedAt?: string
  createdBy?: string
  updatedBy?: string
}

// 字典类型查询参数
export interface DictTypeQuery {
  dictCode?: string
  dictName?: string
  tenantId?: number
  enabled?: boolean
  page?: number
  size?: number
}

// 字典项查询参数
export interface DictItemQuery {
  dictTypeId?: number
  value?: string
  label?: string
  tenantId?: number
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
  tenantId?: number
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
  tenantId?: number
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
  return request.post('/dict/types', data)
}

// 更新字典类型
export function updateDictType(
  id: number | string,
  data: DictTypeCreateUpdateDto,
): Promise<DictTypeItem> {
  return request.put(`/dict/types/${id}`, data)
}

// 删除字典类型
export function deleteDictType(id: number | string): Promise<void> {
  return request.delete(`/dict/types/${id}`)
}

// 批量删除字典类型
export function batchDeleteDictTypes(ids: (number | string)[]): Promise<void> {
  return request.post('/dict/types/batch/delete', ids)
}

// 根据租户ID获取字典类型列表
export function getDictTypesByTenant(tenantId: number): Promise<DictTypeItem[]> {
  return request.get(`/dict/types/tenant/${tenantId}`)
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

// 根据字典编码获取字典项列表（支持多租户）
export function getDictItemsByCode(dictCode: string, tenantId?: number): Promise<DictItem[]> {
  return request.get(`/dict/items/code/${dictCode}`, {
    params: { tenantId: tenantId || 0 },
  })
}

// 根据字典编码获取字典映射（value -> label）
export function getDictMap(dictCode: string, tenantId?: number): Promise<Record<string, string>> {
  return request.get(`/dict/items/map/${dictCode}`, {
    params: { tenantId: tenantId || 0 },
  })
}

// 根据字典编码和值获取标签
export function getDictLabel(dictCode: string, value: string, tenantId?: number): Promise<string> {
  return request.get(`/dict/items/label/${dictCode}/${value}`, {
    params: { tenantId: tenantId || 0 },
  })
}

// 创建字典项
export function createDictItem(data: DictItemCreateUpdateDto): Promise<DictItem> {
  return request.post('/dict/items', data)
}

// 更新字典项
export function updateDictItem(
  id: number | string,
  data: DictItemCreateUpdateDto,
): Promise<DictItem> {
  return request.put(`/dict/items/${id}`, data)
}

// 删除字典项
export function deleteDictItem(id: number | string): Promise<void> {
  return request.delete(`/dict/items/${id}`)
}

// 批量删除字典项
export function batchDeleteDictItems(ids: (number | string)[]): Promise<void> {
  return request.post('/dict/items/batch/delete', ids)
}
