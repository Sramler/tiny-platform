import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  userManagerAuthorities: [] as string[],
  oidcClientAuthorities: [] as string[],
}))

vi.mock('oidc-client-ts', () => {
  class MockWebStorageStateStore {
    constructor(_options: unknown) {}
  }

  class MockUserManager {
    settings: { authority: string }
    metadataService = {
      getMetadata: vi.fn().mockResolvedValue({ jwks_uri: 'http://localhost:9000/oauth2/jwks' }),
    }
    events = {
      addUserLoaded: vi.fn(),
      removeUserLoaded: vi.fn(),
      addUserUnloaded: vi.fn(),
      removeUserUnloaded: vi.fn(),
      addSilentRenewError: vi.fn(),
      removeSilentRenewError: vi.fn(),
      addUserSignedOut: vi.fn(),
      removeUserSignedOut: vi.fn(),
      addAccessTokenExpiring: vi.fn(),
      removeAccessTokenExpiring: vi.fn(),
    }

    constructor(settings: { authority: string }) {
      this.settings = settings
      mocks.userManagerAuthorities.push(settings.authority)
    }

    getUser = vi.fn()
    removeUser = vi.fn()
    signinSilent = vi.fn()
    signoutRedirect = vi.fn()
    signinRedirectCallback = vi.fn()
  }

  class MockOidcClient {
    settings: { authority: string }

    constructor(settings: { authority: string }) {
      this.settings = settings
      mocks.oidcClientAuthorities.push(settings.authority)
    }

    createSigninRequest = vi.fn()
  }

  return {
    OidcClient: MockOidcClient,
    UserManager: MockUserManager,
    WebStorageStateStore: MockWebStorageStateStore,
  }
})

describe('oidc runtime rebinding', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.unmock('@/auth/oidc')
    mocks.userManagerAuthorities.length = 0
    mocks.oidcClientAuthorities.length = 0
    window.localStorage.clear()
    window.sessionStorage.clear()
  })

  it('should rebind runtime authority from tenant to platform', async () => {
    const tenantUtils = await import('@/utils/tenant')
    tenantUtils.setLoginMode('TENANT')
    tenantUtils.setTenantCode('acme')

    const oidc = await import('@/auth/oidc')
    expect(oidc.ensureOidcAuthoritySynced()).toBe('http://localhost:9000/acme')

    tenantUtils.setLoginMode('PLATFORM')
    tenantUtils.clearTenantCode()

    expect(oidc.ensureOidcAuthoritySynced()).toBe('http://localhost:9000/platform')
    expect(mocks.userManagerAuthorities).toEqual([
      'http://localhost:9000/acme',
      'http://localhost:9000/platform',
    ])
    expect(mocks.oidcClientAuthorities).toEqual([
      'http://localhost:9000/acme',
      'http://localhost:9000/platform',
    ])
  })
})
