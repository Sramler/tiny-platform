import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  roleList: vi.fn(),
  getAllRoles: vi.fn(),
}))

vi.mock('@/api/role', () => ({
  roleList: apiMocks.roleList,
  getRoleById: vi.fn(),
  createRole: vi.fn(),
  updateRole: vi.fn(),
  deleteRole: vi.fn(),
  getAllRoles: apiMocks.getAllRoles,
  getRoleUsers: vi.fn(),
  updateRoleUsers: vi.fn(),
  getRoleResources: vi.fn(),
  updateRoleResources: vi.fn(),
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

import Role from '@/views/role/role.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('role.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.roleList.mockResolvedValue({
      content: [
        { id: 1, name: 'Admin', code: 'ROLE_ADMIN', description: '', builtin: true, enabled: true, createdAt: '2026-01-01', updatedAt: '2026-01-01' },
      ],
      totalElements: 1,
    })
    apiMocks.getAllRoles.mockResolvedValue([])
  })

  it('should display role list title and load data on mount', async () => {
    const wrapper = mount(Role, {
      global: {
        stubs: {
          'a-table': defineComponent({
            props: ['dataSource'],
            template: '<div class="role-table-stub">table rows: {{ (dataSource || []).length }}</div>',
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
          'a-transfer': PassThrough,
          VueDraggable: PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          EditOutlined: PassThrough,
          DeleteOutlined: PassThrough,
          SettingOutlined: PassThrough,
          HolderOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('角色列表')
    expect(apiMocks.roleList).toHaveBeenCalled()
  })
})
