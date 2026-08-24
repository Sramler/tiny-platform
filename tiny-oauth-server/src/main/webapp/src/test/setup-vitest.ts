import { afterEach, vi } from 'vitest'

// Legacy component fixtures are migrated incrementally from encoded JWT users to flat Session principals.
// This test-only adapter keeps authorization assertions meaningful without reintroducing token parsing in Web runtime.
vi.mock('@/auth/runtimeIdentity', () => {
  const claims = (principal: Record<string, unknown> | null | undefined) => {
    if (!principal) return null
    if (Array.isArray(principal.authorities) || Array.isArray(principal.permissions)) return principal
    const token = typeof principal.access_token === 'string' ? principal.access_token : ''
    const payload = token.split('.')[1]
    if (!payload) return principal
    try {
      return { ...principal, ...JSON.parse(Buffer.from(payload, 'base64url').toString('utf8')) }
    } catch {
      return principal
    }
  }
  return {
    runtimeAuthorities: (principal: Record<string, unknown> | null | undefined) => {
      const value = claims(principal)
      const raw = value?.permissions ?? value?.authorities
      return Array.isArray(raw) ? raw.map(String) : typeof raw === 'string' ? raw.split(/[,\s]+/).filter(Boolean) : []
    },
    runtimeUserId: (principal: Record<string, unknown> | null | undefined) => {
      const value = claims(principal)
      const parsed = Number(value?.userId ?? value?.id)
      return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
    },
    isPlatformPrincipal: (principal: Record<string, unknown> | null | undefined) =>
      String(claims(principal)?.activeScopeType ?? '').toUpperCase() === 'PLATFORM',
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

/**
 * Ant Design Vue 4.x occasionally forwards vnode props such as `children` and
 * `prefix` to native DOM nodes in tests. jsdom exposes these as getter-only
 * properties, so Vue logs a patch warning and some component tests wait until
 * the default timeout. Keep the real getter behavior and add a noop setter only
 * for the unit-test DOM.
 */
function ensureReadonlyDomPropPatch(proto: object, key: string) {
  const descriptor = Object.getOwnPropertyDescriptor(proto, key)
  if (!descriptor || descriptor.set || !descriptor.configurable) {
    return
  }

  Object.defineProperty(proto, key, {
    configurable: true,
    enumerable: descriptor.enumerable,
    get: descriptor.get,
    set: noopReadonlyDomPropSetter,
  })
}

function noopReadonlyDomPropSetter() {}

ensureReadonlyDomPropPatch(Element.prototype, 'children')
ensureReadonlyDomPropPatch(Element.prototype, 'prefix')
