import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const roleApiMocks = vi.hoisted(() => ({
  getAllRoles: vi.fn(),
}))

const userApiMocks = vi.hoisted(() => ({
  getUserRoles: vi.fn(),
}))

vi.mock('@/api/role', () => ({
  getAllRoles: roleApiMocks.getAllRoles,
}))

vi.mock('@/api/user', () => ({
  getUserRoles: userApiMocks.getUserRoles,
  updateUserRoles: vi.fn(),
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('ant-design-vue', () => ({
  message: { success: vi.fn(), error: vi.fn() },
}))

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

import UserForm from '@/views/user/UserForm.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('UserForm.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    roleApiMocks.getAllRoles.mockResolvedValue([{ id: 1, name: 'Admin', code: 'ROLE_ADMIN' }])
    userApiMocks.getUserRoles.mockResolvedValue([1])
  })

  it('should load roles in edit mode when user id exists', async () => {
    mount(UserForm, {
      props: {
        mode: 'edit',
        userData: { id: '12', username: 'alice', nickname: 'Alice' },
      },
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-switch': PassThrough,
          'a-button': PassThrough,
          RoleTransfer: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(roleApiMocks.getAllRoles).toHaveBeenCalledTimes(1)
    expect(userApiMocks.getUserRoles).toHaveBeenCalledWith(12)
  })
})

