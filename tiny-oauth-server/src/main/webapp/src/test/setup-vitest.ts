import { afterEach, vi } from 'vitest'

// ---- Global mocks to keep unit tests deterministic ----
// Prevent OIDC module side effects (env warnings, metadata fetch, silent renew).
vi.mock('@/auth/oidc', () => {
  const noop = () => {}
  return {
    bindUserManagerEvents: vi.fn(),
    ensureOidcAuthoritySynced: vi.fn().mockReturnValue('http://localhost:9000'),
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
  ensureLegacyMatchMediaShim()
})

function ensureLegacyMatchMediaShim() {
  const currentMatchMedia = window.matchMedia?.bind(window)
  const noop = () => {}
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => {
      const mediaQueryList = currentMatchMedia?.(query)
      const addEventListener =
        typeof mediaQueryList?.addEventListener === 'function'
          ? mediaQueryList.addEventListener.bind(mediaQueryList)
          : noop
      const removeEventListener =
        typeof mediaQueryList?.removeEventListener === 'function'
          ? mediaQueryList.removeEventListener.bind(mediaQueryList)
          : noop
      const dispatchEvent =
        typeof mediaQueryList?.dispatchEvent === 'function'
          ? mediaQueryList.dispatchEvent.bind(mediaQueryList)
          : () => false

      return {
        matches: mediaQueryList?.matches ?? false,
        media: mediaQueryList?.media ?? query,
        onchange: mediaQueryList?.onchange ?? null,
        addListener:
          typeof mediaQueryList?.addListener === 'function'
            ? mediaQueryList.addListener.bind(mediaQueryList)
            : (listener: EventListenerOrEventListenerObject) =>
                addEventListener('change', listener),
        removeListener:
          typeof mediaQueryList?.removeListener === 'function'
            ? mediaQueryList.removeListener.bind(mediaQueryList)
            : (listener: EventListenerOrEventListenerObject) =>
                removeEventListener('change', listener),
        addEventListener,
        removeEventListener,
        dispatchEvent,
      }
    },
  })
}

ensureLegacyMatchMediaShim()
