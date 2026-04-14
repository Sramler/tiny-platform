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

export type RolePermissionAssignmentPayload = {
  permissionIds: number[]
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
/** POST `/sys/roles/{id}/users` 仅接受对象体 `{ userIds, scopeType?, scopeId? }`（CARD-14C）。 */
export function updateRoleUsers(roleId: number, payload: RoleUserAssignmentPayload) {
  return request.post(`/sys/roles/${roleId}/users`, payload, {
    idempotency: {
      scope: `sys-roles:users:update:${roleId}`,
      payload,
    },
  })
}

// 获取某角色下已分配权限
export function getRolePermissions(roleId: number) {
  return request.get<number[]>(`/sys/roles/${roleId}/permissions`)
}

// 保存角色权限分配（主契约）
export function updateRolePermissions(roleId: number, payload: RolePermissionAssignmentPayload) {
  return request.post(`/sys/roles/${roleId}/permissions`, payload, {
    idempotency: {
      scope: `sys-roles:permissions:update:${roleId}`,
      payload,
    },
  })
}

/**
 * 兼容历史入口：`/sys/roles/{id}/resources` 仍以 permissionIds 语义传输。
 */
export function getRoleResources(roleId: number) {
  return request.get<number[]>(`/sys/roles/${roleId}/resources`)
}

export function updateRoleResources(roleId: number, payload: RolePermissionAssignmentPayload) {
  return request.post(`/sys/roles/${roleId}/resources`, payload, {
    idempotency: {
      scope: `sys-roles:resources:update:${roleId}`,
      payload,
    },
  })
}
