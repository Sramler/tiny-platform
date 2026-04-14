import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'

const permissionMocks = vi.hoisted(() => ({
  getPermissionList: vi.fn(),
  getPermissionById: vi.fn(),
  updatePermissionEnabled: vi.fn(),
}))

const roleMocks = vi.hoisted(() => ({
  getAllRoles: vi.fn(),
  getRolePermissions: vi.fn(),
  updateRolePermissions: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  token: 'platform-token',
}))

vi.mock('@/api/permission', () => ({
  getPermissionList: permissionMocks.getPermissionList,
  getPermissionById: permissionMocks.getPermissionById,
  updatePermissionEnabled: permissionMocks.updatePermissionEnabled,
}))

vi.mock('@/api/role', () => ({
  getAllRoles: roleMocks.getAllRoles,
  getRolePermissions: roleMocks.getRolePermissions,
  updateRolePermissions: roleMocks.updateRolePermissions,
}))

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

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const TableColumnStub = defineComponent({ template: '<div />' })
const SwitchStub = defineComponent({
  props: ['checked'],
  emits: ['change'],
  template: '<button class="toggle" @click="$emit(\'change\', !checked)"><slot /></button>',
})

import PermissionControl from '@/views/platform/permissions/PermissionControl.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('PermissionControl.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authMocks.token = 'platform-token'
    permissionMocks.getPermissionList.mockResolvedValue([
      { id: 1, permissionCode: 'system:role:list', permissionName: '角色列表', moduleCode: 'system', enabled: true, boundRoleCount: 1 },
    ])
    permissionMocks.getPermissionById.mockResolvedValue({
      id: 1,
      permissionCode: 'system:role:list',
      permissionName: '角色列表',
      enabled: true,
      boundRoles: [{ roleId: 1, roleCode: 'ROLE_ADMIN', roleName: '管理员' }],
    })
    permissionMocks.updatePermissionEnabled.mockResolvedValue(undefined)
    roleMocks.getAllRoles.mockResolvedValue([{ id: 1, code: 'ROLE_ADMIN', name: '管理员' }])
    roleMocks.getRolePermissions.mockResolvedValue([1])
    roleMocks.updateRolePermissions.mockResolvedValue(undefined)
  })

  it('loads grouped permissions and supports role permission save', async () => {
    const wrapper = mount(PermissionControl, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-table': PassThrough,
          'a-table-column': TableColumnStub,
          'a-switch': SwitchStub,
          'a-button': PassThrough,
          'a-modal': PassThrough,
          'a-list': PassThrough,
          'a-list-item': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-checkbox-group': PassThrough,
          'a-row': PassThrough,
          'a-col': PassThrough,
          'a-checkbox': PassThrough,
        },
      },
    })
    await flushPromises()

    expect(permissionMocks.getPermissionList).toHaveBeenCalled()
    expect(roleMocks.getAllRoles).toHaveBeenCalled()
    expect(roleMocks.getRolePermissions).toHaveBeenCalled()
    expect(wrapper.text()).toContain('保存角色权限')
  })

  it('does not load platform data under tenant scope', async () => {
    authMocks.token = 'tenant-token'
    const wrapper = mount(PermissionControl, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-table': PassThrough,
          'a-table-column': TableColumnStub,
          'a-switch': SwitchStub,
          'a-button': PassThrough,
          'a-modal': PassThrough,
          'a-list': PassThrough,
          'a-list-item': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-checkbox-group': PassThrough,
          'a-row': PassThrough,
          'a-col': PassThrough,
          'a-checkbox': PassThrough,
        },
      },
    })
    await flushPromises()

    expect(permissionMocks.getPermissionList).not.toHaveBeenCalled()
    expect(roleMocks.getAllRoles).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('当前会话不是 PLATFORM 作用域')
  })
})
