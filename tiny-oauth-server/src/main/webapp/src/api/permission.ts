import request from '@/utils/request'

export interface PermissionOptionItem {
  id: number
  permissionCode: string
  permissionName: string
}

export function getPermissionOptions(keyword?: string, limit = 50): Promise<PermissionOptionItem[]> {
  return request.get('/sys/permissions/options', {
    params: { keyword, limit },
  })
}

export interface PermissionListItem {
  id: number
  permissionCode: string
  permissionName: string
  moduleCode?: string
  enabled: boolean
  boundRoleCount: number
  updatedAt?: string
}

export interface PermissionRoleBinding {
  roleId: number
  roleCode: string
  roleName: string
}

export interface PermissionDetail {
  id: number
  permissionCode: string
  permissionName: string
  moduleCode?: string
  enabled: boolean
  updatedAt?: string
  boundRoles: PermissionRoleBinding[]
}

export function getPermissionList(params?: {
  keyword?: string
  moduleCode?: string
  enabled?: boolean
}) {
  return request.get<PermissionListItem[]>('/sys/permissions', { params })
}

export function getPermissionById(id: number) {
  return request.get<PermissionDetail>(`/sys/permissions/${id}`)
}

export function updatePermissionEnabled(id: number, enabled: boolean) {
  const payload = { enabled }
  return request.patch(`/sys/permissions/${id}/enabled`, payload, {
    idempotency: {
      scope: `sys-permissions:enabled:update:${id}`,
      payload,
    },
  })
}

