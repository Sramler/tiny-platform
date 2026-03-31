import request from '@/utils/request'

export type RoleHierarchy = {
  id: number
  tenantId: number
  parentRoleId: number
  childRoleId: number
  createdAt: string
}

export type RoleMutex = {
  id: number
  tenantId: number
  roleId1: number
  roleId2: number
  mutexType: string
  createdAt: string
}

export type RolePrerequisite = {
  id: number
  tenantId: number
  roleId: number
  prerequisiteRoleId: number
  createdAt: string
}

export type RoleCardinality = {
  id: number
  tenantId: number
  roleId: number
  maxAssignments: number
  createdAt: string
}

export type RoleViolation = {
  id: number
  tenantId: number
  constraintType: string
  constraintId: number
  userId: number
  roleId: number
  detail: string
  createdAt: string
}

export function listHierarchies(params?: { page?: number; size?: number }) {
  return request.get<{ content: RoleHierarchy[]; totalElements: number }>('/sys/role-constraints/hierarchies', { params })
}

export function createHierarchy(data: { parentRoleId: number; childRoleId: number }) {
  return request.post('/sys/role-constraints/hierarchies', data, {
    idempotency: {
      scope: 'sys-role-constraints:hierarchy:create',
      payload: data,
    },
  })
}

export function deleteHierarchy(id: number) {
  return request.delete(`/sys/role-constraints/hierarchies/${id}`, {
    idempotency: {
      scope: `sys-role-constraints:hierarchy:delete:${id}`,
      payload: { id },
    },
  })
}

export function listMutexes(params?: { page?: number; size?: number }) {
  return request.get<{ content: RoleMutex[]; totalElements: number }>('/sys/role-constraints/mutexes', { params })
}

export function createMutex(data: { roleId1: number; roleId2: number; mutexType: string }) {
  return request.post('/sys/role-constraints/mutexes', data, {
    idempotency: {
      scope: 'sys-role-constraints:mutex:create',
      payload: data,
    },
  })
}

export function deleteMutex(id: number) {
  return request.delete(`/sys/role-constraints/mutexes/${id}`, {
    idempotency: {
      scope: `sys-role-constraints:mutex:delete:${id}`,
      payload: { id },
    },
  })
}

export function listPrerequisites(params?: { page?: number; size?: number }) {
  return request.get<{ content: RolePrerequisite[]; totalElements: number }>('/sys/role-constraints/prerequisites', { params })
}

export function createPrerequisite(data: { roleId: number; prerequisiteRoleId: number }) {
  return request.post('/sys/role-constraints/prerequisites', data, {
    idempotency: {
      scope: 'sys-role-constraints:prerequisite:create',
      payload: data,
    },
  })
}

export function deletePrerequisite(id: number) {
  return request.delete(`/sys/role-constraints/prerequisites/${id}`, {
    idempotency: {
      scope: `sys-role-constraints:prerequisite:delete:${id}`,
      payload: { id },
    },
  })
}

export function listCardinalities(params?: { page?: number; size?: number }) {
  return request.get<{ content: RoleCardinality[]; totalElements: number }>('/sys/role-constraints/cardinalities', { params })
}

export function createCardinality(data: { roleId: number; maxAssignments: number }) {
  return request.post('/sys/role-constraints/cardinalities', data, {
    idempotency: {
      scope: 'sys-role-constraints:cardinality:create',
      payload: data,
    },
  })
}

export function deleteCardinality(id: number) {
  return request.delete(`/sys/role-constraints/cardinalities/${id}`, {
    idempotency: {
      scope: `sys-role-constraints:cardinality:delete:${id}`,
      payload: { id },
    },
  })
}

export function listViolations(params?: { page?: number; size?: number }) {
  return request.get<{ content: RoleViolation[]; totalElements: number }>('/sys/role-constraints/violations', { params })
}
