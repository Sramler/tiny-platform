import { afterEach, vi } from 'vitest'

// ---- Global mocks to keep unit tests deterministic ----
// Prevent OIDC module side effects (env warnings, metadata fetch, silent renew).
vi.mock('@/auth/oidc', () => {
  const noop = () => {}
  return {
    settings: { authority: 'http://localhost:9000' },
    oidcClient: {},
    userManager: {
      metadataService: {
        getMetadata: vi.fn().mockResolvedValue({ jwks_uri: 'http://localhost:9000/jwks' }),
      },
      getUser: vi.fn().mockResolvedValue(null),
      removeUser: vi.fn().mockResolvedValue(undefined),
      signinSilent: vi.fn().mockResolvedValue(null),
      signoutRedirect: vi.fn().mockResolvedValue(undefined),
      events: {
        addUserLoaded: noop,
        addUserUnloaded: noop,
        addSilentRenewError: noop,
        addUserSignedOut: noop,
        addAccessTokenExpiring: noop,
      },
    },
  }
})

// Silence logger output in unit tests.
vi.mock('@/utils/logger', () => {
  const noop = () => {}
  return {
    logger: { debug: noop, info: noop, warn: noop, error: noop },
    persistentLogger: { debug: noop, info: noop, warn: noop, error: noop },
  }
})


afterEach(() => {
  window.localStorage.clear()
  window.sessionStorage.clear()
  vi.unstubAllGlobals()
})

if (!window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  })
}
