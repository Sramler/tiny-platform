import request from '@/utils/request'

// 资源接口类型定义
export interface ResourceItem {
  id?: number
  /**
   * 资源记录所属租户ID，不是当前活动租户上下文。
   */
  recordTenantId?: number
  name: string
  title?: string
  url?: string
  uri?: string
  method?: string
  icon?: string
  showIcon?: boolean
  sort?: number
  component?: string
  redirect?: string
  hidden?: boolean
  keepAlive?: boolean
  /**
   * 派生字段（由 requiredPermissionId 对应权限主数据回填），不作为写链主入口。
   */
  permission?: string
  requiredPermissionId?: number
  type: number
  typeName?: string
  carrierKind?: 'menu' | 'ui_action' | 'api_endpoint'
  parentId?: number | null
  children?: ResourceItem[]
  createdAt?: string
  updatedAt?: string
  /**
   * 是否为叶子节点（用于树形表格控制展开按钮，true 表示无子节点，不显示展开按钮）
   */
  leaf?: boolean
  /**
   * 是否启用（资源状态）
   */
  enabled?: boolean
}

// 创建/更新资源参数
export interface ResourceCreateUpdateDto {
  id?: number
  name: string
  title: string
  url?: string
  uri?: string
  method?: string
  icon?: string
  showIcon?: boolean
  sort?: number
  component?: string
  redirect?: string
  hidden?: boolean
  keepAlive?: boolean
  /**
   * 只读/展示字段；提交时不再依赖手输 permission。
   */
  permission?: string
  /**
   * 写链主字段：显式权限主数据ID。
   */
  requiredPermissionId?: number
  type: number
  parentId?: number | null
}

// 资源排序参数
export interface ResourceSortDto {
  id: number
  sort: number
  parentId?: number | null
}

// 分页响应类型
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  numberOfElements: number
}

// 资源类型枚举
export enum ResourceType {
  DIRECTORY = 0,
  MENU = 1,
  BUTTON = 2,
  API = 3,
}

// 创建资源
export function createResource(data: ResourceCreateUpdateDto): Promise<ResourceItem> {
  return request.post('/sys/resources', data, {
    idempotency: {
      scope: 'sys-resources:create',
      payload: data,
    },
  })
}

// 更新资源
export function updateResource(
  id: number | string,
  data: ResourceCreateUpdateDto,
): Promise<ResourceItem> {
  return request.put(`/sys/resources/${id}`, data, {
    idempotency: {
      scope: `sys-resources:update:${id}`,
      payload: data,
    },
  })
}

// 删除资源
export function deleteResource(id: number | string): Promise<void> {
  return request.delete(`/sys/resources/${id}`, {
    idempotency: {
      scope: `sys-resources:delete:${id}`,
      payload: { id },
    },
  })
}

// 获取资源详情
export function getResourceDetail(id: number | string): Promise<ResourceItem> {
  return request.get(`/sys/resources/${id}`)
}

// 批量删除资源
export function batchDeleteResources(
  ids: (number | string)[],
): Promise<{ success: boolean; message: string }> {
  return request.post('/sys/resources/batch/delete', ids, {
    idempotency: {
      scope: 'sys-resources:batch-delete',
      payload: ids,
    },
  })
}

// 更新资源排序
export function updateResourceSort(id: number | string, sort: number): Promise<ResourceItem> {
  return request.put(`/sys/resources/${id}/sort`, null, {
    params: { sort },
    idempotency: {
      scope: `sys-resources:sort:${id}`,
      payload: { id, sort },
    },
  })
}

// 获取资源树
export function getResourceTree(): Promise<ResourceItem[]> {
  return request.get('/sys/resources/tree')
}

// 获取当前页面运行时可见按钮
export function getRuntimeUiActions(pagePath: string): Promise<ResourceItem[]> {
  return request.get('/sys/resources/runtime/ui-actions', { params: { pagePath } })
}

// 检查当前用户是否可访问指定 API 载体
export function getRuntimeApiAccess(method: string, uri: string): Promise<{ allowed: boolean }> {
  return request.get('/sys/resources/runtime/api-access', { params: { method, uri } })
}

// 根据资源类型获取资源列表
export function getResourcesByType(type: ResourceType): Promise<ResourceItem[]> {
  return request.get(`/sys/resources/type/${type}`)
}

// 根据父级ID获取子资源列表
export function getResourcesByParentId(parentId: number): Promise<ResourceItem[]> {
  return request.get(`/sys/resources/parent/${parentId}`)
}

// 获取顶级资源列表
export function getTopLevelResources(): Promise<ResourceItem[]> {
  return request.get('/sys/resources/top-level')
}

// 获取资源类型列表
export function getResourceTypes(): Promise<ResourceType[]> {
  return request.get('/sys/resources/types')
}

// 检查资源名称是否存在
export function checkResourceNameExists(
  name: string,
  excludeId?: number,
): Promise<{ exists: boolean }> {
  return request.get('/sys/resources/check-name', { params: { name, excludeId } })
}

// 检查资源URL是否存在
export function checkResourceUrlExists(
  url: string,
  excludeId?: number,
): Promise<{ exists: boolean }> {
  return request.get('/sys/resources/check-url', { params: { url, excludeId } })
}

// 检查资源URI是否存在
export function checkResourceUriExists(
  uri: string,
  excludeId?: number,
): Promise<{ exists: boolean }> {
  return request.get('/sys/resources/check-uri', { params: { uri, excludeId } })
}
