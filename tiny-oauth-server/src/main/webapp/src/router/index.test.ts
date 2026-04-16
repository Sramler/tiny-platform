import { beforeEach, describe, expect, it, vi } from 'vitest'

const routerMocks = vi.hoisted(() => ({
  isAuthenticated: false,
  tenantCode: 'default' as string | null,
  login: vi.fn<(...args: unknown[]) => Promise<void>>(),
  trySilentLoginFromPlatformSession: vi.fn<() => Promise<boolean>>(),
  menuTree: vi.fn<() => Promise<unknown[]>>(),
  logger: {
    log: vi.fn(),
    debug: vi.fn(),
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
  },
  message: {
    destroy: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  },
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: { value: null },
    isAuthenticated: {
      get value() {
        return routerMocks.isAuthenticated
      },
    },
    login: routerMocks.login,
    logout: vi.fn(),
    getAccessToken: vi.fn(),
    fetchWithAuth: vi.fn(),
  }),
  initPromise: Promise.resolve(),
  trySilentLoginFromPlatformSession: routerMocks.trySilentLoginFromPlatformSession,
}))

vi.mock('@/api/menu', () => ({
  menuTree: routerMocks.menuTree,
}))

vi.mock('ant-design-vue', () => ({
  message: routerMocks.message,
}))

vi.mock('@/utils/logger', () => ({
  default: routerMocks.logger,
}))

vi.mock('@/utils/traceId', () => ({
  getCurrentTraceId: () => 'trace-test',
}))

vi.mock('@/utils/tenant', () => ({
  getTenantCode: () => routerMocks.tenantCode,
}))

async function loadRouterModule() {
  vi.resetModules()
  return import('./index')
}

describe('router guards', () => {
  beforeEach(() => {
    routerMocks.isAuthenticated = false
    routerMocks.tenantCode = 'default'
    routerMocks.login.mockReset().mockResolvedValue(undefined)
    routerMocks.trySilentLoginFromPlatformSession.mockReset().mockResolvedValue(false)
    routerMocks.menuTree.mockReset().mockResolvedValue([])
    routerMocks.logger.log.mockReset()
    routerMocks.logger.debug.mockReset()
    routerMocks.logger.info.mockReset()
    routerMocks.logger.warn.mockReset()
    routerMocks.logger.error.mockReset()
    routerMocks.message.destroy.mockReset()
    routerMocks.message.warning.mockReset()
    routerMocks.message.error.mockReset()
    window.history.replaceState({}, '', '/')
  })

  it('keeps a real catch-all route instead of redirecting immediately to /exception/404', async () => {
    const { default: router } = await loadRouterModule()

    const notFoundRoute = router.getRoutes().find((route) => route.name === 'NotFound')

    expect(notFoundRoute).toBeTruthy()
    expect(notFoundRoute?.redirect).toBeUndefined()
    expect(notFoundRoute?.components?.default).toBeTypeOf('function')
  })

  it('aborts current navigation after triggering tenant login redirect', async () => {
    const { authGuard } = await loadRouterModule()

    const result = await authGuard(
      {
        path: '/system/menu',
        fullPath: '/system/menu',
        meta: {},
        query: {},
      } as any,
      {} as any,
      undefined as any,
    )

    expect(routerMocks.login).toHaveBeenCalledWith('/system/menu')
    expect(result).toBe(false)
  })

  it('retries a direct dynamic route refresh after menu routes are loaded', async () => {
    const module = await loadRouterModule()
    const router = module.default

    routerMocks.isAuthenticated = true
    routerMocks.menuTree.mockResolvedValue([
      {
        id: 200,
        title: '菜单管理',
        url: '/system/menu',
        component: '/views/menu/Menu.vue',
        enabled: true,
        hidden: false,
        children: [],
      },
    ])

    const unresolvedTarget = router.resolve('/system/menu')
    expect(unresolvedTarget.name).toBe('NotFound')

    const result = await module.dynamicRoutesGuard(
      unresolvedTarget,
      {
        fullPath: '/',
        path: '/',
      } as any,
      undefined as any,
    )

    expect(routerMocks.menuTree).toHaveBeenCalledTimes(1)
    expect(result).toMatchObject({
      path: '/system/menu',
      replace: true,
    })

    const resolvedAfterLoad = router.resolve('/system/menu')
    expect(resolvedAfterLoad.name).not.toBe('NotFound')
    expect(resolvedAfterLoad.meta.title).toBe('菜单管理')
  })
})
