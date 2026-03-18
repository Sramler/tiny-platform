import { mount } from '@vue/test-utils'
import { ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  routerReplace: vi.fn(),
  signinRedirectCallback: vi.fn(),
  removeUser: vi.fn(),
  getActiveTenantId: vi.fn(),
  withActiveTenantQuery: vi.fn((query, activeTenantId) => (activeTenantId ? { ...query, activeTenantId: String(activeTenantId) } : { ...query })),
  persistentLogger: {
    debug: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  },
  syncTenantContextFromClaims: vi.fn(),
  syncTenantContextFromAccessToken: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    replace: mocks.routerReplace,
  }),
}))

vi.mock('@/auth/oidc.ts', () => ({
  userManager: {
    signinRedirectCallback: mocks.signinRedirectCallback,
    removeUser: mocks.removeUser,
  },
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    isAuthenticated: ref(false),
  }),
}))

vi.mock('@/utils/logger', () => ({
  persistentLogger: mocks.persistentLogger,
}))

vi.mock('@/utils/tenant', () => ({
  getActiveTenantId: mocks.getActiveTenantId,
  syncTenantContextFromClaims: mocks.syncTenantContextFromClaims,
  syncTenantContextFromAccessToken: mocks.syncTenantContextFromAccessToken,
  withActiveTenantQuery: mocks.withActiveTenantQuery,
}))

import OidcCallback from '@/views/OidcCallback.vue'

async function flushPromises() {
  for (let index = 0; index < 5; index += 1) {
    await Promise.resolve()
  }
}

describe('OidcCallback.vue', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.routerReplace.mockReset()
    mocks.signinRedirectCallback.mockReset()
    mocks.removeUser.mockReset()
    mocks.persistentLogger.debug.mockReset()
    mocks.persistentLogger.error.mockReset()
    mocks.persistentLogger.info.mockReset()
    mocks.getActiveTenantId.mockReset()
    mocks.syncTenantContextFromClaims.mockReset()
    mocks.syncTenantContextFromAccessToken.mockReset()
    mocks.withActiveTenantQuery.mockClear()
    mocks.removeUser.mockResolvedValue(undefined)
    mocks.getActiveTenantId.mockReturnValue(null)
    window.history.replaceState({}, '', '/callback')
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should sanitize return url and redirect after successful callback', async () => {
    window.history.replaceState({}, '', '/callback?code=abc&state=xyz')
    mocks.getActiveTenantId.mockReturnValue('1')
    mocks.signinRedirectCallback.mockResolvedValue({
      state: { returnUrl: 'https://evil.com/callback' },
      profile: { activeTenantId: 1, iss: 'http://localhost/issuer/tiny' },
      access_token: 'access-token',
      refresh_token: 'refresh-token',
      scope: 'openid profile',
      expires_at: 123456,
    })

    mount(OidcCallback)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(100)
    await flushPromises()

    expect(mocks.signinRedirectCallback).toHaveBeenCalledTimes(1)
    expect(mocks.syncTenantContextFromClaims).toHaveBeenCalledTimes(1)
    expect(mocks.syncTenantContextFromAccessToken).toHaveBeenCalledWith('access-token')
    expect(mocks.routerReplace).toHaveBeenCalledWith({
      path: '/',
      query: { activeTenantId: '1' },
    })
  })

  it('should show state mismatch error and redirect to login', async () => {
    window.history.replaceState({}, '', '/callback?code=abc&state=xyz')
    mocks.signinRedirectCallback.mockRejectedValue(new Error('No matching state found in storage'))

    const wrapper = mount(OidcCallback)
    await flushPromises()

    expect(wrapper.text()).toContain('登录状态已失效，请重新登录')
    expect(mocks.removeUser).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()

    expect(mocks.routerReplace).toHaveBeenCalledWith('/login')
  })

  it('should render authorization error and redirect to login', async () => {
    window.history.replaceState(
      {},
      '',
      '/callback?error=access_denied&error_description=tenant%20required',
    )

    const wrapper = mount(OidcCallback)
    await flushPromises()

    expect(wrapper.text()).toContain('tenant required')

    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()

    expect(mocks.routerReplace).toHaveBeenCalledWith('/login')
  })

  it('should redirect home when current page is not an oidc callback', async () => {
    mocks.getActiveTenantId.mockReturnValue('9')

    mount(OidcCallback)
    await flushPromises()

    expect(mocks.routerReplace).toHaveBeenCalledWith({
      path: '/',
      query: { activeTenantId: '9' },
    })
  })
})
