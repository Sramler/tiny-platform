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

vi.mock('@/permission/menuTreeLoader', () => ({
  loadVerifiedMenuTree: async () => ({
    menus: await routerMocks.menuTree(),
    source: 'network',
  }),
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

async function markRuntimeReady() {
  const boot = await import('@/bootstrap/bootState')
  const menuState = await import('./menuState')
  boot.beginBootRun('/')
  boot.markBootReady()
  menuState.updateMenuRouteState({
    loading: false,
    loaded: true,
    error: null,
    menus: [],
    lastLoadedAt: Date.now(),
  })
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

  it('redirects protected business routes to bootstrap before heavy auth work', async () => {
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

    expect(routerMocks.login).not.toHaveBeenCalled()
    expect(routerMocks.trySilentLoginFromPlatformSession).not.toHaveBeenCalled()
    expect(result).toEqual({
      path: '/bootstrap',
      query: {
        redirect: '/system/menu',
      },
      replace: true,
    })
  })

  it('allows bootstrap, callback and security routes without bootstrap recursion', async () => {
    const { authGuard } = await loadRouterModule()

    for (const path of ['/bootstrap', '/callback', '/self/security/totp-bind']) {
      const result = await authGuard(
        {
          path,
          fullPath: path,
          meta: {},
          query: {},
        } as any,
        {} as any,
        undefined as any,
      )
      expect(result).toBe(true)
    }

    expect(routerMocks.login).not.toHaveBeenCalled()
    expect(routerMocks.trySilentLoginFromPlatformSession).not.toHaveBeenCalled()
  })

  it('keeps silent renew outside Vue Router', async () => {
    const { default: router } = await loadRouterModule()

    expect(router.getRoutes().some((route) => route.path === '/oidc/silent-callback')).toBe(false)
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
      query: {
        redirect: '/',
      },
      replace: true,
    })
    expect(routerMocks.completePostLogoutRedirect).toHaveBeenCalledTimes(1)
    expect(routerMocks.login).not.toHaveBeenCalled()
    expect(routerMocks.trySilentLoginFromPlatformSession).not.toHaveBeenCalled()
  })

  it('redirects platform runtime module entry to platform console in platform mode', async () => {
    const { platformRuntimeBridgeGuard } = await loadRouterModule()
    await markRuntimeReady()

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

  it('loads dynamic menu routes through permission bootstrap instead of router guard', async () => {
    const module = await loadRouterModule()
    const router = module.default
    const { loadAndRegisterPermission } = await import('@/permission/permissionBootstrap')

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

    const result = await loadAndRegisterPermission(router)

    expect(routerMocks.menuTree).toHaveBeenCalledTimes(1)
    expect(result.status).toBe('ready')

    const resolvedAfterLoad = router.resolve('/system/menu')
    expect(resolvedAfterLoad.name).not.toBe('NotFound')
    expect(resolvedAfterLoad.meta.title).toBe('菜单管理')
  })

  it('keeps permission bootstrap fail-closed when menu tree is empty', async () => {
    const module = await loadRouterModule()
    const { loadAndRegisterPermission } = await import('@/permission/permissionBootstrap')
    const menuState = await import('./menuState')

    routerMocks.menuTree.mockResolvedValue([])

    const result = await loadAndRegisterPermission(module.default)

    expect(result.status).toBe('empty_menu')
    expect(menuState.useMenuRouteState().loaded).toBe(false)
    expect(menuState.useMenuRouteState().error).toContain('菜单数据为空')
  })

  it('keeps permission bootstrap fail-closed when menu URL is external', async () => {
    const module = await loadRouterModule()
    const { loadAndRegisterPermission } = await import('@/permission/permissionBootstrap')
    const menuState = await import('./menuState')

    routerMocks.menuTree.mockResolvedValue([
      {
        id: 201,
        name: 'external-menu',
        title: '外部菜单',
        url: 'https://evil.example/system/menu',
        component: '/views/menu/Menu.vue',
        enabled: true,
        hidden: false,
        children: [],
      },
    ])

    const result = await loadAndRegisterPermission(module.default)

    expect(result.status).toBe('route_register_error')
    expect(result.diagnostics?.invalidUrls).toEqual([
      {
        menu: 'external-menu',
        url: 'https://evil.example/system/menu',
      },
    ])
    expect(menuState.useMenuRouteState().loaded).toBe(false)
    expect(menuState.useMenuRouteState().error).toContain('菜单路由配置存在无效路径')
  })

  it('keeps permission bootstrap fail-closed when menu component is missing', async () => {
    const module = await loadRouterModule()
    const router = module.default
    const { loadAndRegisterPermission } = await import('@/permission/permissionBootstrap')
    const menuState = await import('./menuState')

    routerMocks.menuTree.mockResolvedValue([
      {
        id: 202,
        name: 'missing-component',
        title: '缺失组件',
        url: '/broken/component',
        component: '/views/missing/Nope.vue',
        enabled: true,
        hidden: false,
        children: [],
      },
    ])

    const result = await loadAndRegisterPermission(router)

    expect(result.status).toBe('route_register_error')
    expect(result.diagnostics?.missingComponents).toEqual([
      {
        menu: 'missing-component',
        component: '/views/missing/Nope.vue',
      },
    ])
    expect(router.resolve('/broken/component').name).toBe('NotFound')
    expect(menuState.useMenuRouteState().loaded).toBe(false)
    expect(menuState.useMenuRouteState().error).toContain('菜单路由配置存在无效路径')
  })

  it('keeps permission bootstrap fail-closed when menu paths are duplicated', async () => {
    const module = await loadRouterModule()
    const router = module.default
    const { loadAndRegisterPermission } = await import('@/permission/permissionBootstrap')
    const menuState = await import('./menuState')

    routerMocks.menuTree.mockResolvedValue([
      {
        id: 203,
        name: 'system-menu-a',
        title: '菜单管理 A',
        url: '/system/menu',
        component: '/views/menu/Menu.vue',
        enabled: true,
        hidden: false,
        children: [],
      },
      {
        id: 204,
        name: 'system-menu-b',
        title: '菜单管理 B',
        url: '/system/menu',
        component: '/views/menu/Menu.vue',
        enabled: true,
        hidden: false,
        children: [],
      },
    ])

    const result = await loadAndRegisterPermission(router)

    expect(result.status).toBe('route_register_error')
    expect(result.diagnostics?.duplicatePaths).toEqual(['/system/menu'])
    expect(router.resolve('/system/menu').name).toBe('NotFound')
    expect(menuState.useMenuRouteState().loaded).toBe(false)
    expect(menuState.useMenuRouteState().error).toContain('菜单路由配置存在无效路径')
  })
})
