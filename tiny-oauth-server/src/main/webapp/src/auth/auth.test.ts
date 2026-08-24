import { beforeEach, describe, expect, it, vi } from 'vitest'
import { refreshSessionPrincipal, useAuth } from './auth'

vi.mock('@/utils/csrf', () => ({
  ensureCsrfToken: vi.fn().mockResolvedValue({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }),
  isUnsafeHttpMethod: (method?: string) => !['GET', 'HEAD', 'OPTIONS'].includes((method || 'GET').toUpperCase()),
}))

describe('Web Session auth', () => {
  beforeEach(() => {
    useAuth().user.value = null
    vi.stubGlobal('fetch', vi.fn())
  })

  it('loads the current principal from the HttpOnly Session', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(JSON.stringify({
      userId: 7,
      username: 'admin',
      authorities: ['system:user:list'],
      activeScopeType: 'PLATFORM',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))

    await expect(refreshSessionPrincipal()).resolves.toMatchObject({ username: 'admin' })
    expect(vi.mocked(fetch)).toHaveBeenCalledWith(expect.stringContaining('/sys/users/current'), {
      method: 'GET',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    expect(useAuth().isAuthenticated.value).toBe(true)
  })

  it('clears the principal when the Session is unauthorized', async () => {
    useAuth().user.value = { userId: 7 }
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 401 }))
    await expect(refreshSessionPrincipal()).resolves.toBeNull()
    expect(useAuth().user.value).toBeNull()
  })

  it('never forwards Authorization and attaches CSRF to unsafe requests', async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }))
    await useAuth().fetchWithAuth('/sys/example', {
      method: 'POST',
      headers: { Authorization: 'Bearer must-not-leak' },
    })
    const init = vi.mocked(fetch).mock.calls[0]?.[1]
    const headers = new Headers(init?.headers)
    expect(headers.has('Authorization')).toBe(false)
    expect(headers.get('X-CSRF-TOKEN')).toBe('csrf')
    expect(init?.credentials).toBe('include')
  })
})
