import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  createSigninRequest: vi.fn(),
  getUser: vi.fn(),
  removeUser: vi.fn(),
  signoutRedirect: vi.fn(),
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
  getTenantCode: vi.fn(),
  getActiveTenantId: vi.fn(),
  getTenantId: vi.fn(),
  clearActiveTenantId: vi.fn(),
  syncTenantContextFromClaims: vi.fn(),
  syncTenantContextFromAccessToken: vi.fn(),
  jwtVerify: vi.fn(),
  createRemoteJWKSet: vi.fn(),
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
    window.history.replaceState({}, '', '/')
    mocks.getUser.mockResolvedValue(null)
    mocks.signoutRedirect.mockResolvedValue(undefined)
    mocks.getTenantCode.mockReturnValue('tiny-prod')
    mocks.getActiveTenantId.mockReturnValue(null)
    mocks.getTenantId.mockReturnValue(null)
    mocks.createNewTraceId.mockReturnValue('trace-123')
    mocks.createSigninRequest.mockResolvedValue({
      url: 'http://issuer.example/authorize?client_id=vue-client',
    })
  })

  afterEach(() => {
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
      extraQueryParams: {
        trace_id: 'trace-123',
      },
    })
    expect(mocks.clearTraceId).toHaveBeenCalled()
    expect(mocks.removeUser).not.toHaveBeenCalled()
  })

  it('should fallback to local logout when oidc signout redirect fails', async () => {
    const locationHref = window.location.href
    mocks.getUser.mockResolvedValue({
      id_token: 'id-token',
    })
    mocks.signoutRedirect.mockRejectedValue(new Error('redirect failed'))
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
    expect(window.location.href).toBe('http://localhost:5173/')
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
