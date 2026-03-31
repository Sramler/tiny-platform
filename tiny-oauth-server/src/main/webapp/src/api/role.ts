import request from '@/utils/request'

export type RoleAssignmentScopeType = 'TENANT' | 'ORG' | 'DEPT'

export type Role = {
  id: string
  code?: string
  name: string
  description?: string
  /**
   * 角色记录所属租户ID，不是当前活动租户上下文。
   */
  recordTenantId?: number
}

export type RoleUserAssignmentPayload = {
  scopeType?: RoleAssignmentScopeType
  scopeId?: number
  userIds: number[]
}

export function roleList(params: { current?: number; pageSize?: number; name?: string; code?: string }) {
  return request.get('/sys/roles', { params })
}

export function getRoleById(id: string) {
  return request.get<Role>(`/sys/roles/${id}`)
}

export function createRole(data: Partial<Role> & Record<string, unknown>) {
  return request.post('/sys/roles', data, {
    idempotency: {
      scope: 'sys-roles:create',
      payload: data,
    },
  })
}

export function updateRole(id: string, data: Partial<Role> & Record<string, unknown>) {
  return request.put(`/sys/roles/${id}`, data, {
    idempotency: {
      scope: `sys-roles:update:${id}`,
      payload: data,
    },
  })
}

export function deleteRole(id: string) {
  return request.delete(`/sys/roles/${id}`, {
    idempotency: {
      scope: `sys-roles:delete:${id}`,
      payload: { id },
    },
  })
}

// 获取所有角色（不分页，适用于a-transfer）
export function getAllRoles() {
  return request.get<Role[]>('/sys/roles/all')
}

// 获取某角色下已分配用户（返回用户ID数组或用户列表，需后端实现）
export function getRoleUsers(
  roleId: number,
  scope?: { scopeType?: RoleAssignmentScopeType; scopeId?: number | null },
) {
  // 向后端请求该角色下所有已分配用户
  return request.get(`/sys/roles/${roleId}/users`, {
    params: {
      scopeType: scope?.scopeType,
      scopeId: scope?.scopeId ?? undefined,
    },
  })
}

// 保存角色与用户的关系（需后端实现）
export function updateRoleUsers(roleId: number, payload: number[] | RoleUserAssignmentPayload) {
  // 向后端提交该角色分配的所有用户ID
  return request.post(`/sys/roles/${roleId}/users`, payload, {
    idempotency: {
      scope: `sys-roles:users:update:${roleId}`,
      payload,
    },
  })
}

// 获取某角色下已分配资源（返回资源ID数组，需后端实现）
export function getRoleResources(roleId: number) {
  // 向后端请求该角色下所有已分配资源
  return request.get(`/sys/roles/${roleId}/resources`)
}

// 保存角色与资源的关系（需后端实现）
export function updateRoleResources(
  roleId: number,
  payload: number[] | { permissionIds?: number[]; resourceIds?: number[] },
) {
  // 主契约: permissionIds；resourceIds 仅兼容 alias
  return request.post(`/sys/roles/${roleId}/resources`, payload, {
    idempotency: {
      scope: `sys-roles:resources:update:${roleId}`,
      payload,
    },
  })
}
