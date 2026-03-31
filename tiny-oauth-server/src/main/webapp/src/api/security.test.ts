import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  fetchWithTraceId: vi.fn(),
  syncTenantContextFromClaims: vi.fn(),
}))

vi.mock('@/utils/traceId', () => ({
  fetchWithTraceId: mocks.fetchWithTraceId,
}))

vi.mock('@/utils/tenant', () => ({
  syncTenantContextFromClaims: mocks.syncTenantContextFromClaims,
}))

describe('security.ts', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
  })

  it('should fetch security status and sync active tenant context', async () => {
    mocks.fetchWithTraceId.mockResolvedValue(
      new Response(JSON.stringify({ activeTenantId: 9, disableMfa: false, forceMfa: true }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const { getSecurityStatus } = await import('@/api/security')
    const result = await getSecurityStatus()

    expect(mocks.fetchWithTraceId).toHaveBeenCalledWith(`${import.meta.env.VITE_API_BASE_URL}/self/security/status`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    expect(mocks.syncTenantContextFromClaims).toHaveBeenCalledWith({
      activeTenantId: 9,
      disableMfa: false,
      forceMfa: true,
    })
    expect(result.activeTenantId).toBe(9)
  })

  it('should throw when security status response is not ok', async () => {
    mocks.fetchWithTraceId.mockResolvedValue(new Response('boom', { status: 500 }))

    const { getSecurityStatus } = await import('@/api/security')

    await expect(getSecurityStatus()).rejects.toThrow('请求失败')
    expect(mocks.syncTenantContextFromClaims).not.toHaveBeenCalled()
  })

  it('should fetch active security sessions', async () => {
    mocks.fetchWithTraceId.mockResolvedValue(
      new Response(JSON.stringify({
        activeTenantId: 9,
        currentSessionId: 'sid-current',
        content: [{ sessionId: 'sid-current', current: true }],
      }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const { getSecuritySessions } = await import('@/api/security')
    const result = await getSecuritySessions()

    expect(mocks.fetchWithTraceId).toHaveBeenCalledWith(`${import.meta.env.VITE_API_BASE_URL}/self/security/sessions`, {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    expect(result.content).toHaveLength(1)
    expect(result.currentSessionId).toBe('sid-current')
  })

  it('should revoke a specific session', async () => {
    mocks.fetchWithTraceId.mockResolvedValue(
      new Response(JSON.stringify({ success: true, message: '会话已强制下线' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const { revokeSecuritySession } = await import('@/api/security')
    const result = await revokeSecuritySession('sid-2')

    expect(mocks.fetchWithTraceId).toHaveBeenCalledWith(`${import.meta.env.VITE_API_BASE_URL}/self/security/sessions/sid-2`, {
      method: 'DELETE',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    expect(result.success).toBe(true)
  })

  it('should revoke other sessions', async () => {
    mocks.fetchWithTraceId.mockResolvedValue(
      new Response(JSON.stringify({ success: true, revokedCount: 2 }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )

    const { revokeOtherSecuritySessions } = await import('@/api/security')
    const result = await revokeOtherSecuritySessions()

    expect(mocks.fetchWithTraceId).toHaveBeenCalledWith(`${import.meta.env.VITE_API_BASE_URL}/self/security/sessions/revoke-others`, {
      method: 'POST',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    expect(result.revokedCount).toBe(2)
  })
})
