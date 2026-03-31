import request from '@/utils/request'

export type AuditLog = {
  id: number
  tenantId: number
  eventType: string
  actorUserId: number
  targetUserId: number
  scopeType: string
  scopeId: string
  roleId: number
  module: string
  resourcePermission: string
  eventDetail: string
  result: string
  resultReason: string
  ipAddress: string
  createdAt: string
}

export type AuditEventTypeCount = {
  eventType: string
  count: number
}

export type AuthorizationAuditSummary = {
  totalCount: number
  successCount: number
  deniedCount: number
  eventTypeCounts: AuditEventTypeCount[]
}

export type AuthenticationAuditLog = {
  id: number
  tenantId: number | null
  userId: number | null
  username: string
  eventType: string
  success: boolean
  authenticationProvider: string | null
  authenticationFactor: string | null
  ipAddress: string | null
  userAgent: string | null
  sessionId: string | null
  tokenId: string | null
  tenantResolutionCode: string | null
  tenantResolutionSource: string | null
  createdAt: string
}

export type AuthenticationAuditSummary = {
  totalCount: number
  successCount: number
  failureCount: number
  loginSuccessCount: number
  loginFailureCount: number
  eventTypeCounts: AuditEventTypeCount[]
}

export type AuthorizationAuditQueryParams = {
  tenantId?: number | string
  page?: number
  size?: number
  eventType?: string
  actorUserId?: number | string
  targetUserId?: number | string
  result?: string
  resourcePermission?: string
  detailReason?: string
  carrierType?: string
  requirementGroup?: number
  decision?: string
  reason?: string
  ticketId?: string
  startTime?: string
  endTime?: string
}

export type AuthenticationAuditQueryParams = {
  page?: number
  size?: number
  tenantId?: number | string
  userId?: number | string
  username?: string
  eventType?: string
  success?: boolean
  reason?: string
  ticketId?: string
  startTime?: string
  endTime?: string
}

export function listAuditLogs(params: AuthorizationAuditQueryParams) {
  return request.get<{ content: AuditLog[]; totalElements: number }>('/sys/audit/authorization', { params })
}

export function getAuthorizationAuditSummary(params: Omit<AuthorizationAuditQueryParams, 'page' | 'size'>) {
  return request.get<AuthorizationAuditSummary>('/sys/audit/authorization/summary', { params })
}

export function exportAuthorizationAuditLogs(params: Omit<AuthorizationAuditQueryParams, 'page' | 'size'>) {
  return request.get<Blob>('/sys/audit/authorization/export', {
    params,
    responseType: 'blob' as any,
  })
}

export function listAuthenticationAuditLogs(params: AuthenticationAuditQueryParams) {
  return request.get<{ content: AuthenticationAuditLog[]; totalElements: number }>(
    '/sys/audit/authentication',
    { params },
  )
}

export function getAuthenticationAuditSummary(params: Omit<AuthenticationAuditQueryParams, 'page' | 'size'>) {
  return request.get<AuthenticationAuditSummary>('/sys/audit/authentication/summary', { params })
}

export function exportAuthenticationAuditLogs(params: Omit<AuthenticationAuditQueryParams, 'page' | 'size'>) {
  return request.get<Blob>('/sys/audit/authentication/export', {
    params,
    responseType: 'blob' as any,
  })
}

export function purgeAuditLogs(retentionDays: number) {
  return request.delete('/sys/audit/authorization/purge', {
    params: { retentionDays },
    idempotency: {
      scope: 'sys-audit:purge',
      payload: { retentionDays },
    },
  })
}
