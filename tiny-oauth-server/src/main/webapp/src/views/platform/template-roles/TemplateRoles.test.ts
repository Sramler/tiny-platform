import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'

const roleMocks = vi.hoisted(() => ({
  roleList: vi.fn(),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  updateRolePermissions: vi.fn(),
}))

const tenantMocks = vi.hoisted(() => ({
  tenantList: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  token: 'platform-token',
}))

vi.mock('@/api/role', () => ({
  roleList: roleMocks.roleList,
  createRole: roleMocks.createRole,
  updateRole: roleMocks.updateRole,
  deleteRole: roleMocks.deleteRole,
  updateRolePermissions: roleMocks.updateRolePermissions,
}))

vi.mock('@/api/tenant', () => ({
  tenantList: tenantMocks.tenantList,
}))

vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
  },
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

import TemplateRoles from '@/views/platform/template-roles/TemplateRoles.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('TemplateRoles.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authMocks.token = 'platform-token'
    roleMocks.roleList.mockResolvedValue({
      content: [{ id: 1, name: '平台管理员模板', code: 'ROLE_PLATFORM_ADMIN', enabled: true }],
    })
    tenantMocks.tenantList.mockResolvedValue({
      content: [{ id: 1, code: 'tenant-a' }],
    })
  })

  it('loads template roles and renders governance hints', async () => {
    const wrapper = mount(TemplateRoles, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-table': PassThrough,
          'a-table-column': TableColumnStub,
          'a-button': PassThrough,
          'a-space': PassThrough,
          'a-tag': PassThrough,
          'a-modal': PassThrough,
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-switch': PassThrough,
          ResourceTransfer: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(roleMocks.roleList).toHaveBeenCalled()
    expect(tenantMocks.tenantList).toHaveBeenCalled()
    expect(wrapper.text()).toContain('新建模板角色')
    expect(wrapper.text()).toContain('当前已加载租户样本数')
    expect(wrapper.text()).not.toContain('潜在派生租户')
    expect(wrapper.text()).toContain('模板角色不能直接分配用户')
  })

  it('persists permission binding payload on submit', async () => {
    const wrapper = mount(TemplateRoles, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-table': PassThrough,
          'a-table-column': TableColumnStub,
          'a-button': PassThrough,
          'a-space': PassThrough,
          'a-tag': PassThrough,
          'a-modal': PassThrough,
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-switch': PassThrough,
          ResourceTransfer: PassThrough,
        },
      },
    })
    await flushPromises()
    await (wrapper.vm.$ as any).setupState.onPermissionSaved({ permissionIds: [11, 12] })

    expect(roleMocks.updateRolePermissions).toHaveBeenCalledWith(1, { permissionIds: [11, 12] })
  })

  it('does not load platform data under tenant scope', async () => {
    authMocks.token = 'tenant-token'
    const wrapper = mount(TemplateRoles, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-table': PassThrough,
          'a-table-column': TableColumnStub,
          'a-button': PassThrough,
          'a-space': PassThrough,
          'a-tag': PassThrough,
          'a-modal': PassThrough,
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-switch': PassThrough,
          ResourceTransfer: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(roleMocks.roleList).not.toHaveBeenCalled()
    expect(tenantMocks.tenantList).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('当前会话不是 PLATFORM 作用域')
  })
})
