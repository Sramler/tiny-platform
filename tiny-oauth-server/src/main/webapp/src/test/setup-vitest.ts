import { afterEach, vi } from 'vitest'

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
