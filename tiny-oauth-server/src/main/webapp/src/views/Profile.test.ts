import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

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
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({ push: routerMocks.routerPush }),
    useRoute: () => ({ path: '/profile', query: routerMocks.routeQuery, meta: {} }),
  }
})

vi.mock('@/utils/tenant', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/tenant')>()
  return {
    ...actual,
    getActiveTenantId: tenantContextMocks.getActiveTenantId,
  }
})

vi.mock('ant-design-vue', () => ({
  message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})
const ListItemStub = defineComponent({
  template: '<div><slot /><slot name="actions" /></div>',
})
const ListItemMetaStub = defineComponent({
  template: '<div><slot name="title" /><slot name="description" /><slot /></div>',
})

function mountProfile() {
  return mount(Profile, {
    global: {
      stubs: {
        'a-row': PassThrough,
        'a-col': PassThrough,
        'a-card': PassThrough,
        'a-avatar': PassThrough,
        'a-divider': PassThrough,
        'a-tag': PassThrough,
        'a-tabs': PassThrough,
        'a-tab-pane': PassThrough,
        'a-descriptions': PassThrough,
        'a-descriptions-item': PassThrough,
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-input': PassThrough,
        'a-input-password': PassThrough,
        'a-button': ButtonStub,
        'a-upload': PassThrough,
        'a-space': PassThrough,
        'a-spin': PassThrough,
        'a-alert': PassThrough,
        'a-list': PassThrough,
        'a-list-item': ListItemStub,
        'a-list-item-meta': ListItemMetaStub,
        'a-empty': PassThrough,
        'a-table': PassThrough,
        'a-tooltip': PassThrough,
        UserOutlined: PassThrough,
        SettingOutlined: PassThrough,
        CheckCircleOutlined: PassThrough,
        ClockCircleOutlined: PassThrough,
      },
    },
  })
}

import Profile from '@/views/Profile.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('Profile.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.keys(routerMocks.routeQuery).forEach((key) => {
      delete routerMocks.routeQuery[key]
    })
    tenantContextMocks.getActiveTenantId.mockReturnValue('9')
    userApiMocks.getCurrentUser.mockResolvedValue({ username: 'alice', nickname: 'Alice', enabled: true })
    securityApiMocks.getSecurityStatus.mockResolvedValue({ activeTenantId: 9, disableMfa: false, forceMfa: false })
  })

  it('should render profile shell', async () => {
    const wrapper = mountProfile()
    await flushPromises()

    expect(wrapper.exists()).toBe(true)
    expect(userApiMocks.getCurrentUser).toHaveBeenCalled()
  })

  it('should preserve activeTenantId when navigating to setting and totp bind', async () => {
    routerMocks.routeQuery.activeTenantId = '11'

    const wrapper = mountProfile()
    await flushPromises()

    const settingButton = wrapper.findAll('button').find((button) => button.text().includes('编辑个人设置'))
    expect(settingButton).toBeDefined()
    await settingButton!.trigger('click')

    expect(routerMocks.routerPush).toHaveBeenCalledWith({
      path: '/profile/setting',
      query: { activeTenantId: '11' },
    })
  })

  it('should show activated totp copy only when totpActivated is true', async () => {
    securityApiMocks.getSecurityStatus.mockResolvedValue({
      activeTenantId: 9,
      totpBound: true,
      totpActivated: true,
      disableMfa: false,
      forceMfa: false,
    })

    const wrapper = mountProfile()
    await flushPromises()

    expect(wrapper.text()).toContain('已开启两步验证，账户安全性更高')
    expect(wrapper.text()).not.toContain('两步验证待完成激活，请继续完成绑定')
    expect(wrapper.text()).toContain('查看')
  })

  it('should show pending totp copy when totp is bound but not activated', async () => {
    securityApiMocks.getSecurityStatus.mockResolvedValue({
      activeTenantId: 9,
      totpBound: true,
      totpActivated: false,
      disableMfa: false,
      forceMfa: false,
    })

    const wrapper = mountProfile()
    await flushPromises()

    expect(wrapper.text()).toContain('两步验证待完成激活，请继续完成绑定')
    expect(wrapper.text()).toContain('继续绑定')
    expect(wrapper.text()).not.toContain('已开启两步验证，账户安全性更高')
  })
})
