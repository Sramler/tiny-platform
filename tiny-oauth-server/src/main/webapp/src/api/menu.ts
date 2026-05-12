import request from '@/utils/request'

// 菜单接口类型定义
export interface MenuItem {
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
   * 派生字段（由 requiredPermissionId 对应权限主数据回填），不作为写链主入口。
   */
  permission?: string
  requiredPermissionId?: number
  parentId?: number | null
  leaf?: boolean | number
  children?: MenuItem[]
  createdAt?: string
  updatedAt?: string
  enabled?: boolean
}

export interface MenuTreeSnapshot {
  status: 'ok'
  menus: MenuItem[]
  etag?: string
  menuConfigVersion?: string
  permissionsVersion?: string
  cacheKey?: string
  cacheHit?: boolean
}

export interface MenuTreeNotModifiedSnapshot {
  status: 'not_modified'
  etag?: string
  menuConfigVersion?: string
  permissionsVersion?: string
  cacheKey?: string
  cacheHit?: boolean
}

export type MenuTreeFetchResult = MenuTreeSnapshot | MenuTreeNotModifiedSnapshot

// 查询参数类型
export interface MenuQuery {
  name?: string
  title?: string
  url?: string
  parentId?: number | null
  hidden?: boolean
  enabled?: boolean
}

// 创建/更新菜单参数
export interface MenuCreateUpdateDto {
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
  parentId?: number | null
}

// 获取菜单列表（返回list结构）
export function menuList(params: {
  name?: string
  title?: string
  parentId?: number | null
  enabled?: boolean
}) {
  // 只在 parentId 不为 undefined/null 时传递
  const queryParams: any = { ...params }
  if (queryParams.parentId === undefined || queryParams.parentId === null) {
    delete queryParams.parentId
  }
  return request.get('/sys/menus', { params: queryParams })
}

// 获取菜单树
export function menuTree() {
  return request.get('/sys/menus/tree')
}

function getApiBaseUrl(): string {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:9000'
  return apiBaseUrl.endsWith('/') ? apiBaseUrl.slice(0, -1) : apiBaseUrl
}

function readMenuTreeHeaders(response: Response) {
  return {
    etag: response.headers.get('etag') || undefined,
    menuConfigVersion: response.headers.get('x-menu-config-version') || undefined,
    permissionsVersion: response.headers.get('x-permissions-version') || undefined,
    cacheKey: response.headers.get('x-menu-cache-key') || undefined,
    cacheHit: response.headers.get('x-menu-cache-hit') === 'true',
  }
}

export async function fetchMenuTreeSnapshot(options: { ifNoneMatch?: string } = {}): Promise<MenuTreeFetchResult> {
  const { useAuth } = await import('@/auth/auth')
  const headers = new Headers()
  headers.set('Accept', 'application/json')
  if (options.ifNoneMatch) {
    headers.set('If-None-Match', options.ifNoneMatch)
  }
  const response = await useAuth().fetchWithAuth(`${getApiBaseUrl()}/sys/menus/tree`, {
    method: 'GET',
    cache: 'no-store',
    credentials: 'include',
    headers,
  })
  const metadata = readMenuTreeHeaders(response)
  if (response.status === 304) {
    return {
      status: 'not_modified',
      ...metadata,
    }
  }
  if (!response.ok) {
    throw new Error(`菜单权限加载失败：HTTP ${response.status}`)
  }
  const menus = (await response.json()) as MenuItem[]
  return {
    status: 'ok',
    menus,
    ...metadata,
  }
}

// 获取完整菜单树（包含隐藏/禁用/空目录）
export function menuTreeAll() {
  return request.get('/sys/menus/tree/all')
}

// 创建菜单
export function createMenu(data: {
  name: string
  title: string
  url: string
  uri: string
  method: string
  icon: string
  showIcon: boolean
  sort: number
  component: string
  redirect: string
  hidden: boolean
  keepAlive: boolean
  permission?: string
  requiredPermissionId?: number
  type: number // 0-目录，1-菜单
  parentId?: number
}) {
  return request.post('/sys/menus', data, {
    idempotency: {
      scope: 'sys-menus:create',
      payload: data,
    },
  })
}

// 更新菜单
export function updateMenu(
  id: string | number,
  data: {
    id?: number
    name: string
    title: string
    url: string
    uri: string
    method: string
    icon: string
    showIcon: boolean
    sort: number
    component: string
    redirect: string
    hidden: boolean
    keepAlive: boolean
    permission?: string
    requiredPermissionId?: number
    type: number // 0-目录，1-菜单
    parentId?: number
  },
) {
  return request.put(`/sys/menus/${id}`, data, {
    idempotency: {
      scope: `sys-menus:update:${id}`,
      payload: data,
    },
  })
}

// 删除菜单
export function deleteMenu(id: string | number) {
  return request.delete(`/sys/menus/${id}`, {
    idempotency: {
      scope: `sys-menus:delete:${id}`,
      payload: { id },
    },
  })
}

// 批量删除菜单
export function batchDeleteMenus(ids: (string | number)[]) {
  return request.post('/sys/menus/batch/delete', ids, {
    idempotency: {
      scope: 'sys-menus:batch-delete',
      payload: ids,
    },
  })
}

// 更新菜单排序
export function updateMenuSort(id: string | number, sort: number) {
  return request.put(`/sys/menus/${id}/sort`, null, {
    params: { sort },
    idempotency: {
      scope: `sys-menus:sort:${id}`,
      payload: { id, sort },
    },
  })
}

// 根据父级ID获取子菜单
export function getMenusByParentId(parentId: number): Promise<MenuItem[]> {
  return request.get(`/sys/menus/parent/${parentId}`)
}

// 检查菜单名称是否存在
export function checkMenuNameExists(name: string, excludeId?: string | number) {
  return request.get('/sys/menus/check-name', { params: { name, excludeId } })
}

// 检查菜单路径是否存在
export function checkMenuUrlExists(url: string, excludeId?: string | number) {
  return request.get('/sys/menus/check-url', { params: { url, excludeId } })
}

// 获取菜单类型选项
export function getMenuTypeOptions() {
  return [
    { label: '目录', value: 0 },
    { label: '菜单', value: 1 },
  ]
}
