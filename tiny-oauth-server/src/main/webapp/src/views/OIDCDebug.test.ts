import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
  route: {
    query: {} as Record<string, unknown>,
  },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerMocks.push }),
  useRoute: () => routerMocks.route,
}))

vi.mock('ant-design-vue', () => ({
  message: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({ user: { value: null }, isAuthenticated: { value: false } }),
}))

vi.mock('@/auth/oidc', () => ({
  userManager: { getUser: vi.fn(), removeUser: vi.fn().mockResolvedValue(undefined) },
}))

vi.mock('@/utils/auth-utils', () => ({
  clearOidcCache: vi.fn().mockResolvedValue(true),
  isInOidcFlow: vi.fn().mockReturnValue(false),
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const ActionSectionStub = defineComponent({
  emits: ['refresh', 'clear-cache', 'force-logout', 'go-login', 'go-home'],
  template: `
    <div>
      <button data-testid="go-home" @click="$emit('go-home')">go-home</button>
      <button data-testid="go-login" @click="$emit('go-login')">go-login</button>
      <button data-testid="force-logout" @click="$emit('force-logout')">force-logout</button>
    </div>
  `,
})

import OIDCDebug from '@/views/OIDCDebug.vue'

describe('OIDCDebug.vue', () => {
  beforeEach(() => {
    routerMocks.push.mockReset()
    routerMocks.route.query = {}
  })

  it('should render debug page title', () => {
    const wrapper = mount(OIDCDebug, {
      global: {
        stubs: {
          StatusSection: PassThrough,
          TokenInfoSection: PassThrough,
          UrlParamsSection: PassThrough,
          StorageSection: PassThrough,
          EnvironmentSection: PassThrough,
          ActionSection: ActionSectionStub,
          LogSection: PassThrough,
        },
      },
    })

    expect(wrapper.text()).toContain('OIDC 调试工具')
  })

  it('should preserve activeTenantId when going home', async () => {
    routerMocks.route.query = { activeTenantId: '11' }

    const wrapper = mount(OIDCDebug, {
      global: {
        stubs: {
          StatusSection: PassThrough,
          TokenInfoSection: PassThrough,
          UrlParamsSection: PassThrough,
          StorageSection: PassThrough,
          EnvironmentSection: PassThrough,
          ActionSection: ActionSectionStub,
          LogSection: PassThrough,
        },
      },
    })

    await wrapper.get('[data-testid=\"go-home\"]').trigger('click')

    expect(routerMocks.push).toHaveBeenCalledWith({
      path: '/',
      query: { activeTenantId: '11' },
    })
  })

  it('should keep login navigation free of activeTenantId query', async () => {
    routerMocks.route.query = { activeTenantId: '11' }

    const wrapper = mount(OIDCDebug, {
      global: {
        stubs: {
          StatusSection: PassThrough,
          TokenInfoSection: PassThrough,
          UrlParamsSection: PassThrough,
          StorageSection: PassThrough,
          EnvironmentSection: PassThrough,
          ActionSection: ActionSectionStub,
          LogSection: PassThrough,
        },
      },
    })

    await wrapper.get('[data-testid="go-login"]').trigger('click')
    expect(routerMocks.push).toHaveBeenCalledWith('/login')

    routerMocks.push.mockClear()

    await wrapper.get('[data-testid="force-logout"]').trigger('click')
    await Promise.resolve()
    expect(routerMocks.push).toHaveBeenCalledWith('/login')
  })
})
