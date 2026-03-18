import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('ant-design-vue', () => ({
  message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  Modal: { confirm: vi.fn() },
}))

const userApiMocks = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
}))

const securityApiMocks = vi.hoisted(() => ({
  getSecurityStatus: vi.fn(),
}))

const routerMocks = vi.hoisted(() => ({
  routeQuery: {} as Record<string, unknown>,
  routerPush: vi.fn(),
}))

const tenantContextMocks = vi.hoisted(() => ({
  getActiveTenantId: vi.fn(),
}))

vi.mock('@/api/security', () => ({
  getSecurityStatus: securityApiMocks.getSecurityStatus,
}))

vi.mock('@/api/user', () => ({
  getCurrentUser: userApiMocks.getCurrentUser,
  updateUser: vi.fn(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({ push: routerMocks.routerPush }),
    useRoute: () => ({ path: '/profile/setting', query: routerMocks.routeQuery, meta: {} }),
  }
})

vi.mock('@/utils/tenant', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/tenant')>()
  return {
    ...actual,
    getActiveTenantId: tenantContextMocks.getActiveTenantId,
  }
})

const CardStub = defineComponent({
  props: { title: { type: String, default: '' } },
  template: `<div><div class="card-title">{{ title }}</div><slot /></div>`,
})

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})

import Setting from '@/views/Setting.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('Setting.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.keys(routerMocks.routeQuery).forEach((key) => {
      delete routerMocks.routeQuery[key]
    })
    tenantContextMocks.getActiveTenantId.mockReturnValue('9')
    userApiMocks.getCurrentUser.mockResolvedValue({ id: 1, username: 'alice', nickname: 'Alice' })
    securityApiMocks.getSecurityStatus.mockResolvedValue({ activeTenantId: 9, disableMfa: false, forceMfa: false })
  })

  it('should render setting page title', async () => {
    const wrapper = mount(Setting, {
      global: {
        stubs: {
          'a-card': CardStub,
          'a-tabs': PassThrough,
          'a-tab-pane': PassThrough,
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-password': PassThrough,
          'a-button': ButtonStub,
          'a-upload': PassThrough,
          'a-avatar': PassThrough,
          'a-space': PassThrough,
          'a-spin': PassThrough,
          'a-alert': PassThrough,
          'a-modal': PassThrough,
          UserOutlined: PassThrough,
        },
      },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('个人设置')
    expect(userApiMocks.getCurrentUser).toHaveBeenCalled()
  })

  it('should preserve activeTenantId when navigating to totp bind', async () => {
    routerMocks.routeQuery.activeTenantId = '11'

    const wrapper = mount(Setting, {
      global: {
        stubs: {
          'a-card': CardStub,
          'a-tabs': PassThrough,
          'a-tab-pane': PassThrough,
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-password': PassThrough,
          'a-button': ButtonStub,
          'a-upload': PassThrough,
          'a-avatar': PassThrough,
          'a-space': PassThrough,
          'a-spin': PassThrough,
          'a-alert': PassThrough,
          'a-modal': PassThrough,
          UserOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    const bindButton = wrapper.findAll('button').find((button) => button.text().includes('绑定两步验证'))
    expect(bindButton).toBeDefined()
    await bindButton!.trigger('click')

    expect(routerMocks.routerPush).toHaveBeenCalledWith({
      path: '/self/security/totp-bind',
      query: { activeTenantId: '11' },
    })
  })
})
