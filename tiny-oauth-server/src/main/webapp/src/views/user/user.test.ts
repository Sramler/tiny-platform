import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  userList: vi.fn(),
}))

vi.mock('@/api/user', () => ({
  userList: apiMocks.userList,
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUser: vi.fn(),
  batchDeleteUsers: vi.fn(),
  batchEnableUsers: vi.fn(),
  batchDisableUsers: vi.fn(),
  getUserRoles: vi.fn(),
  updateUserRoles: vi.fn(),
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

import User from '@/views/user/user.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('user.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.userList.mockResolvedValue({
      content: [{ id: 1, username: 'alice', nickname: 'Alice', enabled: true }],
      totalElements: 1,
    })
  })

  it('should display user list title and load data on mount', async () => {
    const wrapper = mount(User, {
      global: {
        stubs: {
          'a-table': defineComponent({
            props: ['dataSource'],
            template: '<div class="user-table-stub">rows: {{ (dataSource || []).length }}</div>',
          }),
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-button': PassThrough,
          'a-tooltip': PassThrough,
          'a-tag': PassThrough,
          'a-modal': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          'a-drawer': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          'a-popover': PassThrough,
          'a-checkbox': PassThrough,
          'a-pagination': PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          EditOutlined: PassThrough,
          DeleteOutlined: PassThrough,
          SettingOutlined: PassThrough,
          HolderOutlined: PassThrough,
          CloseOutlined: PassThrough,
          CheckCircleOutlined: PassThrough,
          StopOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('用户列表')
    expect(apiMocks.userList).toHaveBeenCalled()
  })
})

