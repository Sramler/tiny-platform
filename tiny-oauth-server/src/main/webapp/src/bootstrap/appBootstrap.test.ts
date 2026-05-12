import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  restoreAuthState: vi.fn(),
  checkSecurityState: vi.fn(),
  loadAndRegisterPermission: vi.fn(),
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    login: mocks.login,
  }),
}))

vi.mock('@/auth/authBootstrap', () => ({
  restoreAuthState: mocks.restoreAuthState,
}))

vi.mock('@/security/securityBootstrap', () => ({
  checkSecurityState: mocks.checkSecurityState,
}))

vi.mock('@/permission/permissionBootstrap', () => ({
  loadAndRegisterPermission: mocks.loadAndRegisterPermission,
}))

describe('runAppBootstrap', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns backend-only /login redirects to the auth server instead of Vue /login', async () => {
    const assignSpy = vi.fn()
    vi.stubGlobal('location', {
      ...window.location,
      assign: assignSpy,
      href: window.location.href,
      pathname: window.location.pathname,
      search: window.location.search,
      origin: window.location.origin,
    })

    const { runAppBootstrap } = await import('./appBootstrap')
    const router = {
      replace: vi.fn(),
      currentRoute: {
        value: {
          fullPath: '/bootstrap?redirect=/login',
        },
      },
    }

    const result = await runAppBootstrap(router as any, '/login')

    expect(result.status).toBe('redirecting')
    expect(assignSpy).toHaveBeenCalledWith(`${import.meta.env.VITE_API_BASE_URL}/login`)
    expect(router.replace).not.toHaveBeenCalled()
    expect(mocks.restoreAuthState).not.toHaveBeenCalled()
    expect(mocks.checkSecurityState).not.toHaveBeenCalled()
    expect(mocks.loadAndRegisterPermission).not.toHaveBeenCalled()
    expect(mocks.login).not.toHaveBeenCalled()
  })

  it('shows bootstrap auth service unavailable when tenant authorize redirect cannot reach oidc metadata', async () => {
    mocks.restoreAuthState.mockResolvedValue({
      status: 'tenant_authorize_required',
      message: '需要跳转认证中心完成登录',
    })
    mocks.login.mockRejectedValue(new TypeError('Failed to fetch'))

    const { runAppBootstrap } = await import('./appBootstrap')
    const { useBootState } = await import('./bootState')
    const router = {
      replace: vi.fn(),
      currentRoute: {
        value: {
          fullPath: '/bootstrap?redirect=/',
        },
      },
    }

    const result = await runAppBootstrap(router as any, '/')
    const boot = useBootState()

    expect(result.status).toBe('auth_error')
    expect(result.message).toBe('认证服务暂不可用')
    expect(boot.status).toBe('error')
    expect(boot.error?.code).toBe('auth_service_unavailable')
    expect(boot.error?.message).toBe('认证服务暂不可用')
    expect(boot.error?.detail).toContain('请确认后端认证服务已启动后重试')
    expect(router.replace).not.toHaveBeenCalled()
    expect(mocks.checkSecurityState).not.toHaveBeenCalled()
    expect(mocks.loadAndRegisterPermission).not.toHaveBeenCalled()
  })
})
