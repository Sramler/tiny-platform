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
  getSecuritySessions: vi.fn(),
  revokeSecuritySession: vi.fn(),
  revokeOtherSecuritySessions: vi.fn(),
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
  getSecuritySessions: securityApiMocks.getSecuritySessions,
  revokeSecuritySession: securityApiMocks.revokeSecuritySession,
  revokeOtherSecuritySessions: securityApiMocks.revokeOtherSecuritySessions,
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

function mountSetting() {
  return mount(Setting, {
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
        'a-empty': PassThrough,
        'a-tag': PassThrough,
        UserOutlined: PassThrough,
      },
    },
  })
}

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
    securityApiMocks.getSecuritySessions.mockResolvedValue({ activeTenantId: 9, currentSessionId: 'sid-1', content: [] })
  })

  it('should render setting page title', async () => {
    const wrapper = mountSetting()
    await flushPromises()
    expect(wrapper.text()).toContain('个人设置')
    expect(userApiMocks.getCurrentUser).toHaveBeenCalled()
  })

  it('should preserve activeTenantId when navigating to totp bind', async () => {
    routerMocks.routeQuery.activeTenantId = '11'

    const wrapper = mountSetting()
    await flushPromises()

    const bindButton = wrapper.findAll('button').find((button) => button.text().includes('绑定两步验证'))
    expect(bindButton).toBeDefined()
    await bindButton!.trigger('click')

    expect(routerMocks.routerPush).toHaveBeenCalledWith({
      path: '/self/security/totp-bind',
      query: { activeTenantId: '11' },
    })
  })

  it('should load active sessions and allow revoking other sessions', async () => {
    securityApiMocks.getSecuritySessions.mockResolvedValue({
      activeTenantId: 9,
      currentSessionId: 'sid-current',
      content: [
        { sessionId: 'sid-current', current: true, userAgent: 'Chrome Current' },
        { sessionId: 'sid-other', current: false, userAgent: 'Safari Other' },
      ],
    })
    securityApiMocks.revokeOtherSecuritySessions.mockResolvedValue({ success: true, revokedCount: 1, message: '其他会话已强制下线' })

    const wrapper = mountSetting()
    await flushPromises()

    expect(securityApiMocks.getSecuritySessions).toHaveBeenCalled()
    expect(wrapper.text()).toContain('活跃会话')
    expect(wrapper.text()).toContain('Safari Other')

    const revokeOthersButton = wrapper.findAll('button').find((button) => button.text().includes('下线其他会话'))
    expect(revokeOthersButton).toBeDefined()
    await revokeOthersButton!.trigger('click')

    expect(securityApiMocks.revokeOtherSecuritySessions).toHaveBeenCalled()
  })

  it('should show activated totp state only when totpActivated is true', async () => {
    securityApiMocks.getSecurityStatus.mockResolvedValue({
      activeTenantId: 9,
      totpBound: true,
      totpActivated: true,
      disableMfa: false,
      forceMfa: false,
    })

    const wrapper = mountSetting()
    await flushPromises()

    expect(wrapper.text()).toContain('解绑两步验证')
    expect(wrapper.text()).not.toContain('继续绑定两步验证')
  })

  it('should show pending activation state when totp is bound but not activated', async () => {
    securityApiMocks.getSecurityStatus.mockResolvedValue({
      activeTenantId: 9,
      totpBound: true,
      totpActivated: false,
      disableMfa: false,
      forceMfa: false,
    })

    const wrapper = mountSetting()
    await flushPromises()

    expect(wrapper.text()).toContain('继续绑定两步验证')
    expect(wrapper.text()).not.toContain('解绑两步验证')
  })
})
