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

    await expect(getSecurityStatus()).rejects.toThrow('无法获取安全状态')
    expect(mocks.syncTenantContextFromClaims).not.toHaveBeenCalled()
  })
})
