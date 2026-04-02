// user.ts 用户相关 API 封装
import request from '@/utils/request'
import { syncTenantContextFromClaims } from '@/utils/tenant'
// axios 已在 request.ts 中引入，此处不再需要
// import axios from 'axios'

export type UserListParams = {
  current?: number
  pageSize?: number
  username?: string
  nickname?: string
  sorterField?: string
  sorterOrder?: 'ascend' | 'descend'
}

export type UserSummary = {
  id: string
  username: string
  /**
   * 当前活动租户主字段。
   * 页面与运行时上下文应优先消费该字段。
   */
  activeTenantId?: number
  nickname?: string
  email?: string
  phone?: string
  enabled?: boolean
  accountNonExpired?: boolean
  accountNonLocked?: boolean
  credentialsNonExpired?: boolean
  failedLoginCount?: number
  lastLoginAt?: string
  lastFailedLoginAt?: string
  temporarilyLocked?: boolean
  lockRemainingMinutes?: number
}

export type RoleAssignmentScopeType = 'TENANT' | 'ORG' | 'DEPT'

export type UserRoleAssignmentPayload = {
  scopeType?: RoleAssignmentScopeType
  scopeId?: number
  roleIds: number[]
}

type PageResponse<T> = {
  content?: T[]
  totalElements?: number
}

// userList 方法，接收 params 参数，返回 Promise，适配后端分页和排序接口
export function userList(params: UserListParams) {
  // 构造新的请求参数，以适配后端 Spring Data JPA 的分页和排序规范
  const apiParams: Record<string, string | number | undefined> = {
    // antdv 的分页从 1 开始，后端 Spring Pageable 从 0 开始，所以需要减 1
    page: (params.current || 1) - 1,
    // antdv 的 pageSize 直接映射为后端的 size
    size: params.pageSize || 10,
    // 保留其它查询参数，如 username, nickname
    username: params.username,
    nickname: params.nickname,
  }

  // 处理排序参数
  if (params.sorterField && params.sorterOrder) {
    // 将 antdv 的排序 'ascend'/'descend' 转换为后端的 'asc'/'desc'
    const direction = params.sorterOrder === 'ascend' ? 'asc' : 'desc'
    // 拼接成后端需要的格式：'字段名,排序方向'
    apiParams.sort = `${params.sorterField},${direction}`
  }

  // 使用封装的 request 工具发起 GET 请求。
  // baseURL 'http://localhost:9000' 会被自动加上
  return request.get<PageResponse<UserSummary>>('/sys/users', { params: apiParams }).then((res) => {
    // request 拦截器已自动解析 data，所以 res 直接就是响应体
    return {
      // records 是表格的数据源
      records: res.content || [],
      // total 是数据总数，用于分页
      total: res.totalElements || 0,
    }
  })
}

// 获取用户详情
export function getUserById(id: string) {
  return request.get<UserSummary>(`/sys/users/${id}`)
}

export function getCurrentUser() {
  return request.get<UserSummary>('/sys/users/current').then((user) => {
    syncTenantContextFromClaims(user)
    return user
  })
}

export type ActiveScopeType = 'TENANT' | 'ORG' | 'DEPT'

export type ActiveScopeSwitchPayload = {
  scopeType: ActiveScopeType
  scopeId?: number
}

/**
 * `POST /sys/users/current/active-scope` 成功响应体（与后端 UserController 对齐）。
 * 调用方在 `tokenRefreshRequired === true` 时须先完成 OIDC silent renew，再调用 {@link getCurrentUser}，
 * 避免 Bearer 显式 claims 陈旧导致下一请求 M5 fail-closed。
 */
export type ActiveScopeSwitchResult = {
  success?: boolean
  tokenRefreshRequired?: boolean
  newActiveScopeType?: ActiveScopeType
  newActiveScopeId?: number | null
  tokenRefreshReason?: string
  activeTenantId?: number
  activeScopeType?: string
  activeScopeId?: number | null
}

/**
 * 切换当前会话 active scope。仅返回 POST 响应体，不隐式拉取 `/sys/users/current`。
 */
export function switchActiveScope(payload: ActiveScopeSwitchPayload) {
  return request.post<ActiveScopeSwitchResult>('/sys/users/current/active-scope', payload)
}

// 创建用户
export function createUser(data: Partial<UserSummary> & Record<string, unknown>) {
  return request.post('/sys/users', data, {
    idempotency: {
      scope: 'sys-users:create',
      payload: data,
    },
  })
}

// 更新用户信息
export function updateUser(id: string, data: Partial<UserSummary> & Record<string, unknown>) {
  return request.put(`/sys/users/${id}`, data, {
    idempotency: {
      scope: `sys-users:update:${id}`,
      payload: data,
    },
  })
}

// 删除用户
export function deleteUser(id: string) {
  return request.delete(`/sys/users/${id}`, {
    idempotency: {
      scope: `sys-users:delete:${id}`,
      payload: { id },
    },
  })
}

// 批量删除用户（使用真正的批量删除接口）
export function batchDeleteUsers(ids: string[]) {
  // 使用真正的批量删除接口，确保事务一致性
  return request.post<{ message?: string }>('/sys/users/batch/delete', ids, {
    idempotency: {
      scope: 'sys-users:batch-delete',
      payload: ids,
    },
  }).then((res) => {
    return { success: true, message: res.message || '批量删除成功' }
  })
}

// 批量启用用户（使用真正的批量启用接口）
export function batchEnableUsers(ids: string[]) {
  // 使用真正的批量启用接口，确保事务一致性
  return request.post<{ message?: string }>('/sys/users/batch/enable', ids, {
    idempotency: {
      scope: 'sys-users:batch-enable',
      payload: ids,
    },
  }).then((res) => {
    return { success: true, message: res.message || '批量启用成功' }
  })
}

// 批量禁用用户（使用真正的批量禁用接口）
export function batchDisableUsers(ids: string[]) {
  // 使用真正的批量禁用接口，确保事务一致性
  return request.post<{ message?: string }>('/sys/users/batch/disable', ids, {
    idempotency: {
      scope: 'sys-users:batch-disable',
      payload: ids,
    },
  }).then((res) => {
    return { success: true, message: res.message || '批量禁用成功' }
  })
}

// 重置用户密码（后端需要实现密码重置接口）
export function resetUserPassword(id: string, newPassword: string) {
  return updateUser(id, { password: newPassword })
}

// 检查用户名是否存在（后端需要实现用户名检查接口）
export function checkUsernameExists(username: string) {
  // 暂时使用用户列表接口来检查用户名是否存在
  return userList({ username, current: 1, pageSize: 1 }).then((res) => {
    const exists = res.records && res.records.length > 0
    return { exists, message: exists ? '用户名已存在' : '用户名可用' }
  })
}

// 获取指定用户已绑定的角色ID列表
export function getUserRoles(
  userId: number,
  scope?: { scopeType?: RoleAssignmentScopeType; scopeId?: number | null },
) {
  return request.get(`/sys/users/${userId}/roles`, {
    params: {
      scopeType: scope?.scopeType,
      scopeId: scope?.scopeId ?? undefined,
    },
  })
}

// 保存用户角色绑定
export function updateUserRoles(userId: number, payload: number[] | UserRoleAssignmentPayload) {
  return request.post(`/sys/users/${userId}/roles`, payload, {
    idempotency: {
      scope: `sys-users:roles:update:${userId}`,
      payload,
    },
  })
}
