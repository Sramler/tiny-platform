import { mount } from '@vue/test-utils'
import { defineComponent, reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const authMocks = vi.hoisted(() => ({
  token: 'platform-token',
}))

const routerMocks = vi.hoisted(() => ({
  replace: vi.fn(),
}))

const routeState = reactive({
  path: '/platform/audit/authentication',
  query: {} as Record<string, unknown>,
})

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

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => routerMocks,
}))

vi.mock('@/views/audit/AuthenticationAudit.vue', () => ({
  default: defineComponent({ template: '<div>AuthenticationAuditStub</div>' }),
}))

vi.mock('@/views/audit/AuthorizationAudit.vue', () => ({
  default: defineComponent({ template: '<div>AuthorizationAuditStub</div>' }),
}))

import PlatformAudit from '@/views/platform/audit/PlatformAudit.vue'

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const TabsStub = defineComponent({
  props: ['activeKey'],
  emits: ['change'],
  template:
    '<div class="tabs-stub" :data-active-key="activeKey"><button class="tab-change" @click="$emit(\'change\', \'authorization\')">切换授权审计</button><slot /></div>',
})

describe('PlatformAudit.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authMocks.token = 'platform-token'
    routerMocks.replace.mockReset()
    routeState.path = '/platform/audit/authentication'
    routeState.query = {}
  })

  it('renders audit tabs in platform scope', () => {
    const wrapper = mount(PlatformAudit, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
        },
      },
    })
    expect(wrapper.text()).toContain('平台审计治理')
    expect(wrapper.text()).toContain('登录审计')
    expect(wrapper.find('.tabs-stub').attributes('data-active-key')).toBe('authentication')
    expect(wrapper.text()).toContain('AuthenticationAuditStub')
    expect(wrapper.text()).not.toContain('AuthorizationAuditStub')
  })

  it('renders authorization audit tab from child route and writes tab changes to route', async () => {
    routeState.path = '/platform/audit/authorization'
    routeState.query = {
      activeTenantId: '9',
      keyword: 'admin',
    }
    const wrapper = mount(PlatformAudit, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
        },
      },
    })

    expect(wrapper.find('.tabs-stub').attributes('data-active-key')).toBe('authorization')
    expect(wrapper.text()).toContain('AuthorizationAuditStub')

    await wrapper.find('.tab-change').trigger('click')

    expect(routerMocks.replace).toHaveBeenCalledWith({
      path: '/platform/audit/authorization',
      query: {
        keyword: 'admin',
      },
    })
  })

  it('shows scope guard and hides audit content in tenant scope', () => {
    authMocks.token = 'tenant-token'
    const wrapper = mount(PlatformAudit, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
        },
      },
    })
    expect(wrapper.text()).toContain('当前会话不是 PLATFORM 作用域')
    expect(wrapper.text()).not.toContain('平台审计治理')
  })
})
