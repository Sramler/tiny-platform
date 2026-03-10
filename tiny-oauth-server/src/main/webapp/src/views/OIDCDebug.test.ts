import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('ant-design-vue', () => ({
  message: { success: vi.fn(), error: vi.fn() },
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({ user: { value: null }, isAuthenticated: { value: false } }),
}))

vi.mock('@/auth/oidc', () => ({
  userManager: { getUser: vi.fn(), removeUser: vi.fn() },
}))

vi.mock('@/utils/auth-utils', () => ({
  clearOidcCache: vi.fn().mockResolvedValue(true),
  isInOidcFlow: vi.fn().mockReturnValue(false),
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import OIDCDebug from '@/views/OIDCDebug.vue'

describe('OIDCDebug.vue', () => {
  it('should render debug page title', () => {
    const wrapper = mount(OIDCDebug, {
      global: {
        stubs: {
          StatusSection: PassThrough,
          TokenInfoSection: PassThrough,
          UrlParamsSection: PassThrough,
          StorageSection: PassThrough,
          EnvironmentSection: PassThrough,
          ActionSection: PassThrough,
          LogSection: PassThrough,
        },
      },
    })

    expect(wrapper.text()).toContain('OIDC 调试工具')
  })
})

