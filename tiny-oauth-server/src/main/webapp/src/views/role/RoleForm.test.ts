import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const roleApiMocks = vi.hoisted(() => ({
  getRoleUsers: vi.fn(),
}))

const userApiMocks = vi.hoisted(() => ({
  userList: vi.fn(),
}))

vi.mock('@/api/role', () => ({
  getRoleUsers: roleApiMocks.getRoleUsers,
  updateRoleUsers: vi.fn(),
}))

vi.mock('@/api/user', () => ({
  userList: userApiMocks.userList,
}))

vi.mock('ant-design-vue', () => ({
  message: { error: vi.fn(), warning: vi.fn() },
}))

const FormStub = defineComponent({
  setup(_, { slots, expose }) {
    expose({ validate: () => Promise.resolve() })
    return () => slots.default?.()
  },
})

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

import RoleForm from '@/views/role/RoleForm.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('RoleForm.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    userApiMocks.userList.mockResolvedValue({ records: [{ id: 1, username: 'alice' }], total: 1 })
    roleApiMocks.getRoleUsers.mockResolvedValue([1])
  })

  it('should load users when opening user transfer', async () => {
    const wrapper = mount(RoleForm, {
      props: {
        mode: 'edit',
        roleData: { id: '9', name: 'Admin', code: 'ROLE_ADMIN' },
      },
      global: {
        stubs: {
          'a-form': FormStub,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-switch': PassThrough,
          'a-button': defineComponent({
            emits: ['click'],
            template: `<button @click="$emit('click')"><slot /></button>`,
          }),
          UserTransfer: PassThrough,
        },
      },
    })
    await flushPromises()

    await wrapper.findAll('button').find((b) => b.text().includes('配置用户'))!.trigger('click')
    await flushPromises()

    expect(userApiMocks.userList).toHaveBeenCalled()
    expect(roleApiMocks.getRoleUsers).toHaveBeenCalledWith(9)
  })
})

