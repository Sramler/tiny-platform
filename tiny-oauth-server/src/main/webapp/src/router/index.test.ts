import { beforeEach, describe, expect, it, vi } from 'vitest'

type RedirectFn = (route: unknown) => unknown

const routerMocks = vi.hoisted(() => ({
  isAuthenticated: false,
  tenantCode: 'default' as string | null,
  loginMode: 'TENANT' as 'TENANT' | 'PLATFORM',
  activeTenantId: null as string | null,
  syncTenantContextFromAccessToken: vi.fn(),
  login: vi.fn<(...args: unknown[]) => Promise<void>>(),
  trySilentLoginFromPlatformSession: vi.fn<() => Promise<boolean>>(),
  consumePostLogoutRedirectMarker: vi.fn<() => boolean>(),
  completePostLogoutRedirect: vi.fn<() => Promise<boolean>>(),
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
    user: { value: { access_token: 'test-token' } },
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
  consumePostLogoutRedirectMarker: routerMocks.consumePostLogoutRedirectMarker,
  completePostLogoutRedirect: routerMocks.completePostLogoutRedirect,
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
  getLoginMode: () => routerMocks.loginMode,
  getActiveTenantId: () => routerMocks.activeTenantId,
  syncTenantContextFromAccessToken: routerMocks.syncTenantContextFromAccessToken,
}))

async function loadRouterModule() {
  vi.resetModules()
  return import('./index')
}

describe('router guards', () => {
  beforeEach(() => {
    routerMocks.isAuthenticated = false
    routerMocks.tenantCode = 'default'
    routerMocks.loginMode = 'TENANT'
    routerMocks.activeTenantId = null
    routerMocks.syncTenantContextFromAccessToken.mockReset()
    routerMocks.login.mockReset().mockResolvedValue(undefined)
    routerMocks.trySilentLoginFromPlatformSession.mockReset().mockResolvedValue(false)
    routerMocks.consumePostLogoutRedirectMarker.mockReset().mockReturnValue(false)
    routerMocks.completePostLogoutRedirect.mockReset().mockResolvedValue(false)
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

  it('redirects to login without auto-authorize after post logout landing', async () => {
    const { authGuard } = await loadRouterModule()
    routerMocks.completePostLogoutRedirect.mockResolvedValue(true)

    const result = await authGuard(
      {
        path: '/',
        fullPath: '/',
        meta: {},
        query: {},
      } as any,
      {} as any,
      undefined as any,
    )

    expect(result).toEqual({
      path: '/login',
      replace: true,
    })
    expect(routerMocks.completePostLogoutRedirect).toHaveBeenCalledTimes(1)
    expect(routerMocks.login).not.toHaveBeenCalled()
    expect(routerMocks.trySilentLoginFromPlatformSession).not.toHaveBeenCalled()
  })

  it('redirects platform runtime module entry to platform console in platform mode', async () => {
    const { platformRuntimeBridgeGuard } = await loadRouterModule()

    routerMocks.isAuthenticated = true
    routerMocks.loginMode = 'PLATFORM'
    routerMocks.activeTenantId = '31'

    const result = await platformRuntimeBridgeGuard(
      {
        path: '/scheduling/dag',
        fullPath: '/scheduling/dag?view=all',
        meta: {},
        query: {
          view: 'all',
        },
      } as any,
      {} as any,
      undefined as any,
    )

    expect(result).toMatchObject({
      path: '/platform/scheduling/dag',
      replace: true,
      query: {
        view: 'all',
      },
    })
    expect(routerMocks.syncTenantContextFromAccessToken).toHaveBeenCalledWith('test-token')
  })

  it('resolves platform process tab child routes and redirects base path to the default child route', async () => {
    const { default: router } = await loadRouterModule()

    const childRoute = router.resolve('/platform/process/modeling')
    expect(childRoute.name).toBe('PlatformProcessModeling')

    const baseRoute = router.resolve('/platform/process?activeTenantId=9')
    expect(baseRoute.redirectedFrom).toBeUndefined()
    const redirect = baseRoute.matched[baseRoute.matched.length - 1]?.redirect
    expect(typeof redirect).toBe('function')
    expect((redirect as RedirectFn)(baseRoute)).toEqual({
      path: '/platform/process/definition',
      query: {},
    })
  })

  it('resolves platform dict tab child routes and redirects base path to the default child route', async () => {
    const { default: router } = await loadRouterModule()

    const childRoute = router.resolve('/platform/dicts/overrides')
    expect(childRoute.name).toBe('PlatformDictOverrides')

    const baseRoute = router.resolve('/platform/dicts?activeTenantId=9')
    const redirect = baseRoute.matched[baseRoute.matched.length - 1]?.redirect
    expect(typeof redirect).toBe('function')
    expect((redirect as RedirectFn)(baseRoute)).toEqual({
      path: '/platform/dicts/type',
      query: {},
    })
  })

  it('resolves platform scheduling tab child routes and redirects base path to the default child route', async () => {
    const { default: router } = await loadRouterModule()

    const childRoute = router.resolve('/platform/scheduling/task-type')
    expect(childRoute.name).toBe('PlatformSchedulingTaskType')

    const baseRoute = router.resolve('/platform/scheduling?activeTenantId=9')
    const redirect = baseRoute.matched[baseRoute.matched.length - 1]?.redirect
    expect(typeof redirect).toBe('function')
    expect((redirect as RedirectFn)(baseRoute)).toEqual({
      path: '/platform/scheduling/dag',
      query: {},
    })
  })

  it('resolves platform user governance child routes and redirects base path to the default child route', async () => {
    const { default: router } = await loadRouterModule()

    const childRoute = router.resolve('/platform/users/tenant-stewardship')
    expect(childRoute.name).toBe('PlatformTenantStewardship')

    const baseRoute = router.resolve('/platform/users?tenantId=9')
    const redirect = baseRoute.matched[baseRoute.matched.length - 1]?.redirect
    expect(typeof redirect).toBe('function')
    expect((redirect as RedirectFn)(baseRoute)).toEqual({
      path: '/platform/users/governance',
      query: {},
    })
  })

  it('resolves platform audit child routes and redirects base path to the default child route', async () => {
    const { default: router } = await loadRouterModule()

    const childRoute = router.resolve('/platform/audit/authorization')
    expect(childRoute.name).toBe('PlatformAuthorizationAudit')

    const baseRoute = router.resolve('/platform/audit?activeTenantId=9')
    const redirect = baseRoute.matched[baseRoute.matched.length - 1]?.redirect
    expect(typeof redirect).toBe('function')
    expect((redirect as RedirectFn)(baseRoute)).toEqual({
      path: '/platform/audit/authentication',
      query: {},
    })
  })

  it('resolves platform role constraint child routes and redirects base path to the default child route', async () => {
    const { default: router } = await loadRouterModule()

    const childRoute = router.resolve('/platform/role-constraints/violations')
    expect(childRoute.name).toBe('PlatformRoleConstraintViolations')

    const baseRoute = router.resolve('/platform/role-constraints?activeTenantId=9')
    const redirect = baseRoute.matched[baseRoute.matched.length - 1]?.redirect
    expect(typeof redirect).toBe('function')
    expect((redirect as RedirectFn)(baseRoute)).toEqual({
      path: '/platform/role-constraints/hierarchy',
      query: {},
    })
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

    const unresolvedTarget = router.resolve('/system/menu') as any
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
