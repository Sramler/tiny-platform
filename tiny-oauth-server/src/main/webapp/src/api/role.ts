import request from '@/utils/request'

export type Role = {
  id: string
  code?: string
  name: string
  description?: string
  tenantId?: number
}

export function roleList(params: { current?: number; pageSize?: number; name?: string; code?: string }) {
  return request.get('/sys/roles', { params })
}

export function getRoleById(id: string) {
  return request.get<Role>(`/sys/roles/${id}`)
}

export function createRole(data: Partial<Role> & Record<string, unknown>) {
  return request.post('/sys/roles', data)
}

export function updateRole(id: string, data: Partial<Role> & Record<string, unknown>) {
  return request.put(`/sys/roles/${id}`, data)
}

export function deleteRole(id: string) {
  return request.delete(`/sys/roles/${id}`)
}

// 获取所有角色（不分页，适用于a-transfer）
export function getAllRoles() {
  return request.get<Role[]>('/sys/roles/all')
}

// 获取某角色下已分配用户（返回用户ID数组或用户列表，需后端实现）
export function getRoleUsers(roleId: number) {
  // 向后端请求该角色下所有已分配用户
  return request.get(`/sys/roles/${roleId}/users`)
}

// 保存角色与用户的关系（需后端实现）
export function updateRoleUsers(roleId: number, userIds: number[]) {
  // 向后端提交该角色分配的所有用户ID
  return request.post(`/sys/roles/${roleId}/users`, userIds)
}

// 获取某角色下已分配资源（返回资源ID数组，需后端实现）
export function getRoleResources(roleId: number) {
  // 向后端请求该角色下所有已分配资源
  return request.get(`/sys/roles/${roleId}/resources`)
}

// 保存角色与资源的关系（需后端实现）
export function updateRoleResources(roleId: number, resourceIds: number[]) {
  // 向后端提交该角色分配的所有资源ID
  return request.post(`/sys/roles/${roleId}/resources`, resourceIds)
}
