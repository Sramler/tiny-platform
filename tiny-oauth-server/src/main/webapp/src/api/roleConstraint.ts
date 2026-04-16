import request from '@/utils/request'

export type RoleHierarchy = {
  childRoleId: number
  parentRoleId: number
}

export type RoleMutex = {
  leftRoleId: number
  rightRoleId: number
}

export type RolePrerequisite = {
  roleId: number
  requiredRoleId: number
}

export type RoleCardinality = {
  roleId: number
  scopeType: string
  maxAssignments: number
}

export type RoleViolation = {
  id: number
  principalType: string
  principalId: number | null
  scopeType: string | null
  scopeId: number | null
  violationType: string
  violationCode: string
  directRoleIds: string | null
  effectiveRoleIds: string | null
  details: string | null
  createdAt: string
}

export function listHierarchies() {
  return request.get<RoleHierarchy[]>('/sys/role-constraints/hierarchy')
}

export function createHierarchy(data: { parentRoleId: number; childRoleId: number }) {
  return request.post('/sys/role-constraints/hierarchy', data, {
    idempotency: {
      scope: 'sys-role-constraints:hierarchy:create',
      payload: data,
    },
  })
}

export function deleteHierarchy(params: { childRoleId: number; parentRoleId: number }) {
  return request.delete('/sys/role-constraints/hierarchy', {
    params,
    idempotency: {
      scope: `sys-role-constraints:hierarchy:delete:${params.childRoleId}:${params.parentRoleId}`,
      payload: params,
    },
  })
}

export function listMutexes() {
  return request.get<RoleMutex[]>('/sys/role-constraints/mutex')
}

export function createMutex(data: { roleIdA: number; roleIdB: number }) {
  return request.post('/sys/role-constraints/mutex', data, {
    idempotency: {
      scope: 'sys-role-constraints:mutex:create',
      payload: data,
    },
  })
}

export function deleteMutex(params: { roleIdA: number; roleIdB: number }) {
  return request.delete('/sys/role-constraints/mutex', {
    params,
    idempotency: {
      scope: `sys-role-constraints:mutex:delete:${params.roleIdA}:${params.roleIdB}`,
      payload: params,
    },
  })
}

export function listPrerequisites() {
  return request.get<RolePrerequisite[]>('/sys/role-constraints/prerequisite')
}

export function createPrerequisite(data: { roleId: number; requiredRoleId: number }) {
  return request.post('/sys/role-constraints/prerequisite', data, {
    idempotency: {
      scope: 'sys-role-constraints:prerequisite:create',
      payload: data,
    },
  })
}

export function deletePrerequisite(params: { roleId: number; requiredRoleId: number }) {
  return request.delete('/sys/role-constraints/prerequisite', {
    params,
    idempotency: {
      scope: `sys-role-constraints:prerequisite:delete:${params.roleId}:${params.requiredRoleId}`,
      payload: params,
    },
  })
}

export function listCardinalities() {
  return request.get<RoleCardinality[]>('/sys/role-constraints/cardinality')
}

export function createCardinality(data: { roleId: number; scopeType: string; maxAssignments: number }) {
  return request.post('/sys/role-constraints/cardinality', data, {
    idempotency: {
      scope: 'sys-role-constraints:cardinality:create',
      payload: data,
    },
  })
}

export function deleteCardinality(params: { roleId: number; scopeType: string }) {
  return request.delete('/sys/role-constraints/cardinality', {
    params,
    idempotency: {
      scope: `sys-role-constraints:cardinality:delete:${params.roleId}:${params.scopeType}`,
      payload: params,
    },
  })
}

export function listViolations(params?: { page?: number; size?: number }) {
  return request.get<{ content: RoleViolation[]; totalElements: number }>('/sys/role-constraints/violations', { params })
}
