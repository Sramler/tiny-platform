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

const BASE = '/platform/role-constraints'

export function listPlatformHierarchies() {
  return request.get<RoleHierarchy[]>(`${BASE}/hierarchy`)
}

export function createPlatformHierarchy(data: { parentRoleId: number; childRoleId: number }) {
  return request.post(`${BASE}/hierarchy`, data, {
    idempotency: {
      scope: 'platform-role-constraints:hierarchy:create',
      payload: data,
    },
  })
}

export function deletePlatformHierarchy(params: { childRoleId: number; parentRoleId: number }) {
  return request.delete(`${BASE}/hierarchy`, {
    params,
    idempotency: {
      scope: `platform-role-constraints:hierarchy:delete:${params.childRoleId}:${params.parentRoleId}`,
      payload: params,
    },
  })
}

export function listPlatformMutexes() {
  return request.get<RoleMutex[]>(`${BASE}/mutex`)
}

export function createPlatformMutex(data: { roleIdA: number; roleIdB: number }) {
  return request.post(`${BASE}/mutex`, data, {
    idempotency: {
      scope: 'platform-role-constraints:mutex:create',
      payload: data,
    },
  })
}

export function deletePlatformMutex(params: { roleIdA: number; roleIdB: number }) {
  return request.delete(`${BASE}/mutex`, {
    params,
    idempotency: {
      scope: `platform-role-constraints:mutex:delete:${params.roleIdA}:${params.roleIdB}`,
      payload: params,
    },
  })
}

export function listPlatformPrerequisites() {
  return request.get<RolePrerequisite[]>(`${BASE}/prerequisite`)
}

export function createPlatformPrerequisite(data: { roleId: number; requiredRoleId: number }) {
  return request.post(`${BASE}/prerequisite`, data, {
    idempotency: {
      scope: 'platform-role-constraints:prerequisite:create',
      payload: data,
    },
  })
}

export function deletePlatformPrerequisite(params: { roleId: number; requiredRoleId: number }) {
  return request.delete(`${BASE}/prerequisite`, {
    params,
    idempotency: {
      scope: `platform-role-constraints:prerequisite:delete:${params.roleId}:${params.requiredRoleId}`,
      payload: params,
    },
  })
}

export function listPlatformCardinalities() {
  return request.get<RoleCardinality[]>(`${BASE}/cardinality`)
}

export function createPlatformCardinality(data: { roleId: number; scopeType: string; maxAssignments: number }) {
  return request.post(`${BASE}/cardinality`, data, {
    idempotency: {
      scope: 'platform-role-constraints:cardinality:create',
      payload: data,
    },
  })
}

export function deletePlatformCardinality(params: { roleId: number; scopeType: string }) {
  return request.delete(`${BASE}/cardinality`, {
    params,
    idempotency: {
      scope: `platform-role-constraints:cardinality:delete:${params.roleId}:${params.scopeType}`,
      payload: params,
    },
  })
}

export function listPlatformViolations(params?: { page?: number; size?: number }) {
  return request.get<{ content: RoleViolation[]; totalElements: number }>(`${BASE}/violations`, { params })
}
