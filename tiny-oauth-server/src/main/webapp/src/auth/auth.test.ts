import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  createSigninRequest: vi.fn(),
  getUser: vi.fn(),
  removeUser: vi.fn(),
  signoutRedirect: vi.fn(),
  signoutRedirectCallback: vi.fn(),
  signinSilent: vi.fn(),
  addUserLoaded: vi.fn(),
  addUserUnloaded: vi.fn(),
  addSilentRenewError: vi.fn(),
  addUserSignedOut: vi.fn(),
  addAccessTokenExpiring: vi.fn(),
  logger: {
    warn: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    debug: vi.fn(),
  },
  persistentLogger: {
    warn: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    debug: vi.fn(),
  },
  createNewTraceId: vi.fn(),
  clearTraceId: vi.fn(),
  addTraceIdToFetchOptions: vi.fn((options) => options),
  getTenantCode: vi.fn(),
  getActiveTenantId: vi.fn(),
  getTenantId: vi.fn(),
  clearActiveTenantId: vi.fn(),
  syncTenantContextFromClaims: vi.fn(),
  syncTenantContextFromAccessToken: vi.fn(),
  jwtVerify: vi.fn(),
  createRemoteJWKSet: vi.fn(),
  sessionOnly: false,
}))

vi.mock('@/auth/oidc', () => ({
  bindUserManagerEvents: vi.fn(),
  ensureOidcAuthoritySynced: vi.fn().mockReturnValue('http://localhost:9000/tiny-prod'),
  settings: {
    redirect_uri: 'http://localhost:5173/callback',
    post_logout_redirect_uri: 'http://localhost:5173/',
  },
  oidcClient: {
    createSigninRequest: mocks.createSigninRequest,
  },
  userManager: {
    getUser: mocks.getUser,
    removeUser: mocks.removeUser,
    signoutRedirect: mocks.signoutRedirect,
    signoutRedirectCallback: mocks.signoutRedirectCallback,
    signinSilent: mocks.signinSilent,
    events: {
      addUserLoaded: mocks.addUserLoaded,
      addUserUnloaded: mocks.addUserUnloaded,
      addSilentRenewError: mocks.addSilentRenewError,
      addUserSignedOut: mocks.addUserSignedOut,
      addAccessTokenExpiring: mocks.addAccessTokenExpiring,
    },
  },
}))

vi.mock('@/auth/config', () => ({
  authRuntimeConfig: {
    get sessionOnly() {
      return mocks.sessionOnly
    },
    forceLogoutOnRenewFail: true,
    fetchTimeoutMs: 8000,
  },
}))

vi.mock('@/utils/logger', () => ({
  logger: mocks.logger,
  persistentLogger: mocks.persistentLogger,
  default: mocks.logger,
}))

vi.mock('@/utils/traceId', () => ({
  createNewTraceId: mocks.createNewTraceId,
  clearTraceId: mocks.clearTraceId,
  addTraceIdToFetchOptions: mocks.addTraceIdToFetchOptions,
}))

vi.mock('@/utils/tenant', () => ({
  getTenantCode: mocks.getTenantCode,
  getActiveTenantId: mocks.getActiveTenantId,
  getTenantId: mocks.getTenantId,
  clearActiveTenantId: mocks.clearActiveTenantId,
  syncTenantContextFromClaims: mocks.syncTenantContextFromClaims,
  syncTenantContextFromAccessToken: mocks.syncTenantContextFromAccessToken,
}))

vi.mock('jose', () => ({
  jwtVerify: mocks.jwtVerify,
  createRemoteJWKSet: mocks.createRemoteJWKSet,
}))

describe('auth login flow', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    window.sessionStorage.clear()
    window.history.replaceState({}, '', '/')
    mocks.getUser.mockResolvedValue(null)
    mocks.sessionOnly = false
    mocks.signoutRedirect.mockResolvedValue(undefined)
    mocks.signoutRedirectCallback.mockResolvedValue({ userState: null })
    mocks.getTenantCode.mockReturnValue('tiny-prod')
    mocks.getActiveTenantId.mockReturnValue(null)
    mocks.getTenantId.mockReturnValue(null)
    mocks.createNewTraceId.mockReturnValue('trace-123')
    mocks.createSigninRequest.mockResolvedValue({
      url: 'http://issuer.example/authorize?client_id=vue-client',
    })
    mocks.addTraceIdToFetchOptions.mockImplementation((options) => options)
  })

  afterEach(() => {
    window.sessionStorage.clear()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('should sanitize external return url and redirect to authorize endpoint', async () => {
    const assignSpy = vi.fn()
    vi.stubGlobal('location', {
      ...window.location,
      assign: assignSpy,
      href: window.location.href,
      pathname: window.location.pathname,
      search: window.location.search,
      origin: window.location.origin,
    })
    const authModule = await import('@/auth/auth')
    await authModule.initPromise

    await authModule.login('https://evil.com/callback')

    expect(mocks.createSigninRequest).toHaveBeenCalledWith({
      state: {
        returnUrl: '/',
        trace_id: 'trace-123',
      },
      extraQueryParams: {
        trace_id: 'trace-123',
      },
    })
    expect(assignSpy).toHaveBeenCalledWith('http://issuer.example/authorize?client_id=vue-client')
  })

  it('should restore authentication from the HttpOnly server session without an OAuth token', async () => {
    mocks.sessionOnly = true
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          id: '7',
          username: 'alice',
          activeTenantId: 101,
          permissions: ['system:user:view'],
          authorities: ['system:user:view'],
          roleCodes: [],
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )
    vi.stubGlobal('fetch', fetchMock)
    const authModule = await import('@/auth/auth')

    await authModule.ensureAuthInitialized({ force: true })

    expect(authModule.useAuth().isAuthenticated.value).toBe(true)
    expect(authModule.useAuth().user.value?.access_token).toBe('')
    await expect(authModule.useAuth().getAccessToken()).resolves.toBeNull()
    expect(fetchMock).toHaveBeenCalledWith(
      'http://test-api.example.com/sys/users/current',
      expect.objectContaining({ credentials: 'include', method: 'GET' }),
    )
  })

  it('should reject login when tenant context is missing', async () => {
    mocks.getTenantCode.mockReturnValue(null)
    const authModule = await import('@/auth/auth')
    await authModule.initPromise

    await expect(authModule.login('/dashboard')).rejects.toThrow('missing tenant context')
    expect(mocks.createSigninRequest).not.toHaveBeenCalled()
  })

  it('should skip redirect when current url is already an oidc callback', async () => {
    window.history.replaceState({}, '', '/callback?code=abc&state=xyz')
    const assignSpy = vi.fn()
    vi.stubGlobal('location', {
      ...window.location,
      assign: assignSpy,
      href: window.location.href,
      pathname: window.location.pathname,
      search: window.location.search,
      origin: window.location.origin,
    })
    const authModule = await import('@/auth/auth')
    await authModule.initPromise

    await authModule.login('/dashboard')

    expect(mocks.createSigninRequest).not.toHaveBeenCalled()
    expect(assignSpy).not.toHaveBeenCalled()
  })

  it('should sign out via oidc redirect when id_token exists', async () => {
    mocks.getUser.mockResolvedValue({
      id_token: 'id-token',
    })
    const authModule = await import('@/auth/auth')
    await authModule.initPromise

    await authModule.logout()

    expect(mocks.signoutRedirect).toHaveBeenCalledWith({
      id_token_hint: 'id-token',
      post_logout_redirect_uri: 'http://localhost:5173/',
      state: {
        postLogoutNonce: expect.any(String),
        trace_id: 'trace-123',
      },
      extraQueryParams: {
        trace_id: 'trace-123',
      },
    })
    expect(mocks.removeUser).toHaveBeenCalledTimes(1)
    expect(mocks.clearActiveTenantId).toHaveBeenCalledTimes(1)
    expect(mocks.clearTraceId).toHaveBeenCalled()
    expect(authModule.consumePostLogoutRedirectMarker()).toBe(true)
    expect(authModule.consumePostLogoutRedirectMarker()).toBe(false)
  })

  it('should fallback to local logout when oidc signout redirect fails', async () => {
    const locationHref = window.location.href
    mocks.getUser.mockResolvedValue({
      id_token: 'id-token',
    })
    mocks.signoutRedirect.mockRejectedValue(new Error('redirect failed'))
    const fetchSpy = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchSpy)
    vi.stubGlobal('location', {
      ...window.location,
      href: locationHref,
      pathname: window.location.pathname,
      search: window.location.search,
      origin: window.location.origin,
      assign: vi.fn(),
    })
    const authModule = await import('@/auth/auth')
    await authModule.initPromise

    await authModule.logout()

    expect(mocks.removeUser).toHaveBeenCalledTimes(1)
    expect(mocks.clearActiveTenantId).toHaveBeenCalledTimes(1)
    expect(mocks.clearTraceId).toHaveBeenCalled()
    expect(fetchSpy).toHaveBeenNthCalledWith(
      2,
      `${(import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')}/auth/logout`,
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        redirect: 'manual',
      }),
    )
    expect(window.location.href).toBe('http://localhost:5173/')
  })

  it('should ignore expired post logout marker', async () => {
    window.sessionStorage.setItem(
      'tiny-platform:oidc:post-logout-redirect',
      String(Date.now() - 31_000),
    )
    const authModule = await import('@/auth/auth')
    await authModule.initPromise

    expect(authModule.consumePostLogoutRedirectMarker()).toBe(false)
    expect(window.sessionStorage.getItem('tiny-platform:oidc:post-logout-redirect')).toBeNull()
  })

  it('should validate post logout redirect state before consuming marker', async () => {
    window.sessionStorage.setItem(
      'tiny-platform:oidc:post-logout-redirect',
      JSON.stringify({
        nonce: 'logout-nonce',
        createdAt: Date.now(),
      }),
    )
    mocks.signoutRedirectCallback.mockResolvedValue({
      userState: {
        postLogoutNonce: 'logout-nonce',
      },
    })
    const authModule = await import('@/auth/auth')
    await authModule.initPromise

    const completed = await authModule.completePostLogoutRedirect(
      'http://localhost:5173/?state=stored-signout-state',
    )

    expect(completed).toBe(true)
    expect(mocks.signoutRedirectCallback).toHaveBeenCalledWith(
      'http://localhost:5173/?state=stored-signout-state',
    )
    expect(window.sessionStorage.getItem('tiny-platform:oidc:post-logout-redirect')).toBeNull()
  })
})

describe('refreshTokenAfterActiveScopeSwitch', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    window.history.replaceState({}, '', '/')
    mocks.getUser.mockResolvedValue(null)
    mocks.signinSilent.mockResolvedValue(null)
    mocks.getTenantCode.mockReturnValue('tiny-prod')
    mocks.getActiveTenantId.mockReturnValue(null)
  })

  it('should return ok when signinSilent yields a non-expired user', async () => {
    const fakeUser = {
      expired: false,
      expires_at: 9999999999,
      access_token: 'at',
      refresh_token: 'rt',
      profile: { sub: 'u1' },
    }
    mocks.signinSilent.mockResolvedValue(fakeUser)
    const authModule = await import('@/auth/auth')
    await authModule.initPromise

    const result = await authModule.refreshTokenAfterActiveScopeSwitch()

    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.user).toBe(fakeUser)
    }
    expect(mocks.syncTenantContextFromClaims).toHaveBeenCalled()
    expect(mocks.syncTenantContextFromAccessToken).toHaveBeenCalledWith('at')
  })

  it('should return ok false on silent renew failure without forcing login redirect', async () => {
    mocks.signinSilent.mockRejectedValue(new Error('iframe blocked'))
    const assignSpy = vi.fn()
    vi.stubGlobal('location', {
      ...window.location,
      href: 'http://localhost:5173/app',
      assign: assignSpy,
    })
    const authModule = await import('@/auth/auth')
    await authModule.initPromise

    const result = await authModule.refreshTokenAfterActiveScopeSwitch()

    expect(result.ok).toBe(false)
    expect(window.location.href).toBe('http://localhost:5173/app')
  })
})
