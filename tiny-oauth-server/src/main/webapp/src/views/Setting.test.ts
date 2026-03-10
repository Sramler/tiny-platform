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
  }
})

const CardStub = defineComponent({
  props: { title: { type: String, default: '' } },
  template: `<div><div class="card-title">{{ title }}</div><slot /></div>`,
})

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

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
    userApiMocks.getCurrentUser.mockResolvedValue({ id: 1, username: 'alice', nickname: 'Alice' })
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
          'a-button': PassThrough,
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
})

