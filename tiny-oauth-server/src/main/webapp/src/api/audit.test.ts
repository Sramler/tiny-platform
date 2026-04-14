import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
  delete: vi.fn(),
  post: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: requestMocks.get,
    delete: requestMocks.delete,
    post: requestMocks.post,
  },
}))

describe('audit API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should request authorization audit logs with params', async () => {
    requestMocks.get.mockResolvedValue({ content: [], totalElements: 0 })
    const { listAuditLogs } = await import('@/api/audit')

    await listAuditLogs({
      eventType: 'ROLE_ASSIGNED',
      page: 1,
      size: 20,
      carrierType: 'carrier-type-1',
      requirementGroup: 5,
      decision: 'ALLOW',
    })

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/audit/authorization', {
      params: {
        eventType: 'ROLE_ASSIGNED',
        page: 1,
        size: 20,
        carrierType: 'carrier-type-1',
        requirementGroup: 5,
        decision: 'ALLOW',
      },
    })
  })

  it('should request authorization audit summary with params', async () => {
    requestMocks.get.mockResolvedValue({ totalCount: 0, successCount: 0, deniedCount: 0, eventTypeCounts: [] })
    const { getAuthorizationAuditSummary } = await import('@/api/audit')

    await getAuthorizationAuditSummary({
      eventType: 'ROLE_ASSIGNMENT_GRANT',
      targetUserId: 7,
      carrierType: 'carrier-type-1',
      requirementGroup: 5,
      decision: 'ALLOW',
    })

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/audit/authorization/summary', {
      params: {
        eventType: 'ROLE_ASSIGNMENT_GRANT',
        targetUserId: 7,
        carrierType: 'carrier-type-1',
        requirementGroup: 5,
        decision: 'ALLOW',
      },
    })
  })

  it('should export authorization audit logs as blob', async () => {
    requestMocks.get.mockResolvedValue(new Blob(['csv']))
    const { exportAuthorizationAuditLogs } = await import('@/api/audit')

    await exportAuthorizationAuditLogs({
      tenantId: 9,
      eventType: 'ROLE_ASSIGNMENT_GRANT',
      reason: 'incident-check',
      ticketId: 'TICKET-9',
      carrierType: 'carrier-type-1',
      requirementGroup: 5,
      decision: 'ALLOW',
    })

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/audit/authorization/export', {
      params: {
        tenantId: 9,
        eventType: 'ROLE_ASSIGNMENT_GRANT',
        reason: 'incident-check',
        ticketId: 'TICKET-9',
        carrierType: 'carrier-type-1',
        requirementGroup: 5,
        decision: 'ALLOW',
      },
      responseType: 'blob',
    })
  })

  it('should request authentication audit logs with params', async () => {
    requestMocks.get.mockResolvedValue({ content: [], totalElements: 0 })
    const { listAuthenticationAuditLogs } = await import('@/api/audit')

    await listAuthenticationAuditLogs({
      tenantId: 9,
      userId: 1,
      username: 'alice',
      eventType: 'LOGIN',
      success: true,
      page: 0,
      size: 10,
    })

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/audit/authentication', {
      params: {
        tenantId: 9,
        userId: 1,
        username: 'alice',
        eventType: 'LOGIN',
        success: true,
        page: 0,
        size: 10,
      },
    })
  })

  it('should request authentication audit summary with params', async () => {
    requestMocks.get.mockResolvedValue({
      totalCount: 0,
      successCount: 0,
      failureCount: 0,
      loginSuccessCount: 0,
      loginFailureCount: 0,
      eventTypeCounts: [],
    })
    const { getAuthenticationAuditSummary } = await import('@/api/audit')

    await getAuthenticationAuditSummary({
      tenantId: 9,
      username: 'alice',
      eventType: 'LOGIN',
      success: true,
    })

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/audit/authentication/summary', {
      params: {
        tenantId: 9,
        username: 'alice',
        eventType: 'LOGIN',
        success: true,
      },
    })
  })

  it('should export authentication audit logs as blob', async () => {
    requestMocks.get.mockResolvedValue(new Blob(['csv']))
    const { exportAuthenticationAuditLogs } = await import('@/api/audit')

    await exportAuthenticationAuditLogs({
      tenantId: 9,
      eventType: 'LOGIN',
      reason: 'incident-check',
      ticketId: 'TICKET-8',
    })

    expect(requestMocks.get).toHaveBeenCalledWith('/sys/audit/authentication/export', {
      params: {
        tenantId: 9,
        eventType: 'LOGIN',
        reason: 'incident-check',
        ticketId: 'TICKET-8',
      },
      responseType: 'blob',
    })
  })

  it('should purge authorization audit logs with idempotency', async () => {
    requestMocks.delete.mockResolvedValue({ deleted: 3 })
    const { purgeAuditLogs } = await import('@/api/audit')

    await purgeAuditLogs(90)

    expect(requestMocks.delete).toHaveBeenCalledWith('/sys/audit/authorization/purge', {
      params: { retentionDays: 90 },
      idempotency: {
        scope: 'sys-audit:purge',
        payload: { retentionDays: 90 },
      },
    })
  })

  it('should decode platform token with readonly endpoint', async () => {
    requestMocks.post.mockResolvedValue({
      authorities: ['system:audit:auth:view'],
      permissions: ['system:audit:auth:view'],
      roleCodes: ['ROLE_ADMIN'],
      permissionsVersion: 'perm-v-1',
      activeScopeType: 'PLATFORM',
      activeTenantId: null,
      claims: {},
    })
    const { decodePlatformToken } = await import('@/api/audit')

    await decodePlatformToken('token-value')

    expect(requestMocks.post).toHaveBeenCalledWith('/sys/platform/token-debug/decode', { token: 'token-value' })
  })
})
