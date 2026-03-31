import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const roleApiMocks = vi.hoisted(() => ({
  getAllRoles: vi.fn(),
}))

const userApiMocks = vi.hoisted(() => ({
  getUserById: vi.fn(),
  getUserRoles: vi.fn(),
}))

const orgApiMocks = vi.hoisted(() => ({
  getOrgList: vi.fn(),
  listUserUnits: vi.fn(),
}))

vi.mock('@/api/role', () => ({
  getAllRoles: roleApiMocks.getAllRoles,
}))

vi.mock('@/api/user', () => ({
  getUserById: userApiMocks.getUserById,
  getUserRoles: userApiMocks.getUserRoles,
}))

vi.mock('@/api/org', () => ({
  getOrgList: orgApiMocks.getOrgList,
  listUserUnits: orgApiMocks.listUserUnits,
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

const FormStub = defineComponent({
  emits: ['finish'],
  template: '<form @submit.prevent="$emit(\'finish\')"><slot /></form>',
})

const InputStub = defineComponent({
  props: {
    value: {
      type: String,
      default: '',
    },
  },
  emits: ['update:value'],
  template: '<input :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
})

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
})

const SwitchStub = defineComponent({
  props: {
    checked: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['update:checked'],
  template: '<input type="checkbox" :checked="checked" @change="$emit(\'update:checked\', $event.target.checked)" />',
})

const SelectStub = defineComponent({
  props: {
    value: {
      type: [Array, Number, String],
      default: undefined,
    },
    options: {
      type: Array,
      default: () => [],
    },
  },
  emits: ['update:value'],
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
    userApiMocks.getUserById.mockResolvedValue({
      id: '12',
      username: 'alice',
      nickname: 'Alice',
      email: 'alice@example.com',
      phone: '13800000000',
      enabled: true,
      accountNonExpired: true,
      accountNonLocked: true,
      credentialsNonExpired: true,
    })
    userApiMocks.getUserRoles.mockResolvedValue([1])
    orgApiMocks.getOrgList.mockResolvedValue([
      {
        id: 10,
        tenantId: 1,
        parentId: null,
        unitType: 'DEPT',
        code: 'ops',
        name: '平台运营部',
        sortOrder: 0,
        status: 'ACTIVE',
        createdAt: '2026-03-01T10:00:00',
        createdBy: 1,
        updatedAt: '2026-03-01T10:00:00',
      },
    ])
    orgApiMocks.listUserUnits.mockResolvedValue([
      {
        id: 1,
        tenantId: 1,
        userId: 12,
        unitId: 10,
        unitCode: 'ops',
        unitName: '平台运营部',
        unitType: 'DEPT',
        isPrimary: true,
        status: 'ACTIVE',
        joinedAt: '2026-03-01T10:00:00',
        leftAt: null,
        createdAt: '2026-03-01T10:00:00',
        updatedAt: '2026-03-01T10:00:00',
      },
    ])
  })

  function mountForm(props: Record<string, unknown>) {
    return mount(UserForm, {
      props,
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-form': FormStub,
          'a-form-item': PassThrough,
          'a-input': InputStub,
          'a-switch': SwitchStub,
          'a-button': ButtonStub,
          'a-select': SelectStub,
          RoleTransfer: PassThrough,
        },
      },
    })
  }

  it('loads roles, user detail and user units in edit mode', async () => {
    const wrapper = mountForm({
      mode: 'edit',
      userData: { id: '12', username: 'alice', nickname: 'Alice' },
    })

    await flushPromises()
    await flushPromises()

    expect(roleApiMocks.getAllRoles).toHaveBeenCalledTimes(1)
    expect(userApiMocks.getUserRoles).toHaveBeenCalledWith(12)
    expect(userApiMocks.getUserById).toHaveBeenCalledWith('12')
    expect(orgApiMocks.getOrgList).toHaveBeenCalledTimes(1)
    expect(orgApiMocks.listUserUnits).toHaveBeenCalledWith(12)

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.emitted('submit')).toBeTruthy()
    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      id: '12',
      username: 'alice',
      nickname: 'Alice',
      email: 'alice@example.com',
      phone: '13800000000',
      unitIds: [10],
      primaryUnitId: 10,
      roleIds: [1],
    })
  })

  it('submits contact fields and org assignments in create mode', async () => {
    const wrapper = mountForm({
      mode: 'create',
      userData: {
        username: 'new-user',
        nickname: 'New User',
        email: 'new@example.com',
        phone: '13800000001',
        unitIds: [10],
        primaryUnitId: 10,
      },
    })

    await flushPromises()

    expect(orgApiMocks.getOrgList).toHaveBeenCalledTimes(1)
    expect(userApiMocks.getUserById).not.toHaveBeenCalled()
    expect(userApiMocks.getUserRoles).not.toHaveBeenCalled()
    expect(orgApiMocks.listUserUnits).not.toHaveBeenCalled()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.emitted('submit')?.[0]?.[0]).toMatchObject({
      username: 'new-user',
      nickname: 'New User',
      email: 'new@example.com',
      phone: '13800000001',
      unitIds: [10],
      primaryUnitId: 10,
      roleIds: [],
    })
  })
})
