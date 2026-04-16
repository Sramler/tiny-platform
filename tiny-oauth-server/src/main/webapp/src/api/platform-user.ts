import request from '@/utils/request'

export type PlatformUserStatus = 'ACTIVE' | 'DISABLED'

export type PlatformUserListParams = {
  current?: number
  pageSize?: number
  keyword?: string
  enabled?: boolean
  status?: PlatformUserStatus
}

export type PlatformUserListItem = {
  userId: number
  username: string
  nickname?: string
  displayName?: string
  userEnabled?: boolean
  platformStatus: PlatformUserStatus
  hasPlatformRoleAssignment?: boolean
  updatedAt?: string
}

export type PlatformUserDetail = {
  userId: number
  username: string
  nickname?: string
  displayName?: string
  email?: string
  phone?: string
  userEnabled?: boolean
  accountNonExpired?: boolean
  accountNonLocked?: boolean
  credentialsNonExpired?: boolean
  platformStatus: PlatformUserStatus
  hasPlatformRoleAssignment?: boolean
  lastLoginAt?: string
  createdAt?: string
  updatedAt?: string
}

export type PlatformUserCreatePayload = {
  userId: number
  displayName?: string
  status?: PlatformUserStatus
}

type PageResponse<T> = {
  content?: T[]
  totalElements?: number
}

export function listPlatformUsers(params: PlatformUserListParams) {
  return request.get<PageResponse<PlatformUserListItem>>('/platform/users', {
    params: {
      page: (params.current || 1) - 1,
      size: params.pageSize || 10,
      keyword: params.keyword,
      enabled: params.enabled,
      status: params.status,
    },
  }).then((res) => ({
    records: res.content || [],
    total: res.totalElements || 0,
  }))
}

export function getPlatformUserDetail(userId: number | string) {
  return request.get<PlatformUserDetail>(`/platform/users/${userId}`)
}

export function createPlatformUser(payload: PlatformUserCreatePayload) {
  return request.post<PlatformUserDetail>('/platform/users', payload, {
    idempotency: {
      scope: `platform-users:create:${payload.userId}`,
      payload,
    },
  })
}

export function updatePlatformUserStatus(userId: number | string, status: PlatformUserStatus) {
  return request.patch<void>(`/platform/users/${userId}/status`, { status }, {
    idempotency: {
      scope: `platform-users:status:update:${userId}`,
      payload: { status },
    },
  })
}
