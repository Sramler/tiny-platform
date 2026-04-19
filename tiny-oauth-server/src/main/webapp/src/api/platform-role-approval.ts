import request from '@/utils/request'

export type PlatformRoleAssignmentRequestItem = {
  id: number
  targetUserId: number
  roleId: number
  roleCode?: string | null
  roleName?: string | null
  actionType: string
  status: string
  requestedBy: number
  requestedAt?: string
  reviewedBy?: number | null
  reviewedAt?: string | null
  reason?: string | null
  reviewComment?: string | null
  appliedAt?: string | null
  applyError?: string | null
}

type PageResponse<T> = {
  content?: T[]
  totalElements?: number
  totalPages?: number
  pageNumber?: number
  pageSize?: number
}

export type ListPlatformRoleAssignmentRequestsParams = {
  targetUserId?: number
  status?: string
  current?: number
  pageSize?: number
}

export async function listPlatformRoleAssignmentRequests(params: ListPlatformRoleAssignmentRequestsParams) {
  const current = params.current ?? 1
  const pageSize = params.pageSize ?? 20
  const res = await request.get<PageResponse<PlatformRoleAssignmentRequestItem>>('/platform/role-assignment-requests', {
    params: {
      page: current - 1,
      size: pageSize,
      targetUserId: params.targetUserId,
      status: params.status,
    },
  })
  return {
    records: res.content ?? [],
    total: res.totalElements ?? 0,
    pageSize: res.pageSize ?? pageSize,
    pageNumber: res.pageNumber ?? current - 1,
  }
}

export type SubmitPlatformRoleAssignmentRequestPayload = {
  targetUserId: number
  roleId: number
  actionType: 'GRANT' | 'REVOKE'
  reason?: string
}

export function submitPlatformRoleAssignmentRequest(payload: SubmitPlatformRoleAssignmentRequestPayload) {
  return request.post<PlatformRoleAssignmentRequestItem>('/platform/role-assignment-requests', payload, {
    idempotency: {
      scope: `platform-role-assignment-requests:submit:${payload.targetUserId}:${payload.roleId}:${payload.actionType}`,
      payload,
    },
  })
}

export function approvePlatformRoleAssignmentRequest(id: number, comment?: string) {
  const body = { comment: comment ?? null }
  return request.post<PlatformRoleAssignmentRequestItem>(`/platform/role-assignment-requests/${id}/approve`, body, {
    idempotency: {
      scope: `platform-role-assignment-requests:approve:${id}`,
      payload: body,
    },
  })
}

export function rejectPlatformRoleAssignmentRequest(id: number, comment?: string) {
  const body = { comment: comment ?? null }
  return request.post<PlatformRoleAssignmentRequestItem>(`/platform/role-assignment-requests/${id}/reject`, body, {
    idempotency: {
      scope: `platform-role-assignment-requests:reject:${id}`,
      payload: body,
    },
  })
}

export function cancelPlatformRoleAssignmentRequest(id: number) {
  return request.post<void>(`/platform/role-assignment-requests/${id}/cancel`, undefined, {
    idempotency: {
      scope: `platform-role-assignment-requests:cancel:${id}`,
      payload: {},
    },
  })
}
