import request from '@/utils/request'

export type OrgUnit = {
  id: number
  tenantId: number
  parentId: number | null
  unitType: string
  code: string
  name: string
  sortOrder: number | null
  status: string
  createdAt: string
  createdBy: number | null
  updatedAt: string
  children?: OrgUnit[]
}

export type UserUnit = {
  id: number
  tenantId: number
  userId: number
  unitId: number
  unitCode: string
  unitName: string
  unitType: string
  isPrimary: boolean
  status: string
  joinedAt: string | null
  leftAt: string | null
  createdAt: string
  updatedAt: string
}

export function getOrgTree() {
  return request.get<OrgUnit[]>('/sys/org/tree')
}

export function getOrgList() {
  return request.get<OrgUnit[]>('/sys/org/list')
}

export function getOrgById(id: number) {
  return request.get<OrgUnit>(`/sys/org/${id}`)
}

export function createOrg(data: Partial<OrgUnit>) {
  return request.post<OrgUnit>('/sys/org', data, {
    idempotency: {
      scope: 'sys-org:create',
      payload: data,
    },
  })
}

export function updateOrg(id: number, data: Partial<OrgUnit>) {
  return request.put<OrgUnit>(`/sys/org/${id}`, data, {
    idempotency: {
      scope: `sys-org:update:${id}`,
      payload: data,
    },
  })
}

export function deleteOrg(id: number) {
  return request.delete(`/sys/org/${id}`, {
    idempotency: {
      scope: `sys-org:delete:${id}`,
      payload: { id },
    },
  })
}

export function listUnitMembers(unitId: number) {
  return request.get<UserUnit[]>(`/sys/org/${unitId}/users`)
}

export function listUserUnits(userId: number) {
  return request.get<UserUnit[]>(`/sys/org/user/${userId}/units`)
}

export function addUserToUnit(unitId: number, userId: number, isPrimary = false) {
  return request.post<UserUnit>(`/sys/org/${unitId}/users/${userId}?isPrimary=${isPrimary}`, null, {
    idempotency: {
      scope: `sys-org:user:add:${unitId}:${userId}`,
      payload: { unitId, userId, isPrimary },
    },
  })
}

export function removeUserFromUnit(unitId: number, userId: number) {
  return request.delete(`/sys/org/${unitId}/users/${userId}`, {
    idempotency: {
      scope: `sys-org:user:remove:${unitId}:${userId}`,
      payload: { unitId, userId },
    },
  })
}
