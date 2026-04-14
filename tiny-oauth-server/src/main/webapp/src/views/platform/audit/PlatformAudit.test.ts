import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const authMocks = vi.hoisted(() => ({
  token: 'platform-token',
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: { value: { access_token: authMocks.token } },
  }),
}))

vi.mock('@/utils/jwt', () => ({
  decodeJwtPayload: (token?: string) => {
    if (token === 'platform-token') {
      return { activeScopeType: 'PLATFORM' }
    }
    return { activeScopeType: 'TENANT' }
  },
}))

vi.mock('@/views/audit/AuthenticationAudit.vue', () => ({
  default: defineComponent({ template: '<div>AuthenticationAuditStub</div>' }),
}))

vi.mock('@/views/audit/AuthorizationAudit.vue', () => ({
  default: defineComponent({ template: '<div>AuthorizationAuditStub</div>' }),
}))

import PlatformAudit from '@/views/platform/audit/PlatformAudit.vue'

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

describe('PlatformAudit.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authMocks.token = 'platform-token'
  })

  it('renders audit tabs in platform scope', () => {
    const wrapper = mount(PlatformAudit, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-tabs': PassThrough,
          'a-tab-pane': PassThrough,
          AuthenticationAudit: PassThrough,
          AuthorizationAudit: PassThrough,
        },
      },
    })
    expect(wrapper.text()).toContain('平台审计治理')
    expect(wrapper.text()).toContain('登录审计')
  })

  it('shows scope guard and hides audit content in tenant scope', () => {
    authMocks.token = 'tenant-token'
    const wrapper = mount(PlatformAudit, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-tabs': PassThrough,
          'a-tab-pane': PassThrough,
          AuthenticationAudit: PassThrough,
          AuthorizationAudit: PassThrough,
        },
      },
    })
    expect(wrapper.text()).toContain('当前会话不是 PLATFORM 作用域')
    expect(wrapper.text()).not.toContain('平台审计治理')
  })
})
