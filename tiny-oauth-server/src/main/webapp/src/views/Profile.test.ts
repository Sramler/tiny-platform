import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const userApiMocks = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
}))

vi.mock('@/api/process', () => ({
  userApi: {
    getCurrentUser: userApiMocks.getCurrentUser,
  },
}))

vi.mock('@/api/user', () => ({
  updateUser: vi.fn(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({ push: vi.fn() }),
    useRoute: () => ({ path: '/profile', query: {}, meta: {} }),
  }
})

vi.mock('ant-design-vue', () => ({
  message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

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
    userApiMocks.getCurrentUser.mockResolvedValue({ username: 'alice', nickname: 'Alice', enabled: true })
  })

  it('should render profile shell', async () => {
    const wrapper = mount(Profile, {
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
          'a-button': PassThrough,
          'a-upload': PassThrough,
          'a-space': PassThrough,
          'a-spin': PassThrough,
          'a-alert': PassThrough,
          'a-list': PassThrough,
          'a-list-item': PassThrough,
          'a-list-item-meta': PassThrough,
          'a-empty': PassThrough,
          'a-table': PassThrough,
          'a-tooltip': PassThrough,
          UserOutlined: PassThrough,
          CheckCircleOutlined: PassThrough,
          ClockCircleOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.exists()).toBe(true)
    expect(userApiMocks.getCurrentUser).toHaveBeenCalled()
  })
})

