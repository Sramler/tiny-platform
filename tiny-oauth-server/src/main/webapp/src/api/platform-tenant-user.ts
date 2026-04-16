import request from '@/utils/request'

export type PlatformTenantUserListParams = {
  tenantId: number
  current?: number
  pageSize?: number
  username?: string
  nickname?: string
}

export type PlatformTenantUserListItem = {
  id: number
  username: string
  nickname?: string
  enabled?: boolean
  accountNonExpired?: boolean
  accountNonLocked?: boolean
  credentialsNonExpired?: boolean
  lastLoginAt?: string
  failedLoginCount?: number
  lastFailedLoginAt?: string
  temporarilyLocked?: boolean
  lockRemainingMinutes?: number
}

export type PlatformTenantUserDetail = PlatformTenantUserListItem

type PageResponse<T> = {
  content?: T[]
  totalElements?: number
}

export function listPlatformTenantUsers(params: PlatformTenantUserListParams) {
  return request.get<PageResponse<PlatformTenantUserListItem>>(`/platform/tenants/${params.tenantId}/users`, {
    params: {
      page: (params.current || 1) - 1,
      size: params.pageSize || 10,
      username: params.username,
      nickname: params.nickname,
    },
  }).then((res) => ({
    records: res.content || [],
    total: res.totalElements || 0,
  }))
}

export function getPlatformTenantUserDetail(tenantId: number | string, userId: number | string) {
  return request.get<PlatformTenantUserDetail>(`/platform/tenants/${tenantId}/users/${userId}`)
}
