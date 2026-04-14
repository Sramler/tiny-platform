import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  userList: vi.fn(),
  getRuntimeUiActions: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  authUser: { value: null as { access_token?: string | null } | null },
  isAuthenticated: { value: true },
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

vi.mock('@/api/resource', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/resource')>()
  return {
    ...actual,
    getRuntimeUiActions: apiMocks.getRuntimeUiActions,
  }
})

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: authMocks.authUser,
    isAuthenticated: authMocks.isAuthenticated,
    login: vi.fn(),
    logout: vi.fn(),
    getAccessToken: vi.fn(),
    fetchWithAuth: vi.fn(),
  }),
  initPromise: Promise.resolve(),
}))

vi.mock('@/utils/logger', () => {
  const logger = {
    debug: vi.fn(),
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    log: vi.fn(),
    group: vi.fn(),
    groupEnd: vi.fn(),
    table: vi.fn(),
  }
  return {
    logger,
    persistentLogger: logger,
    default: logger,
  }
})

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

import User from '@/views/user/user.vue'
import { ACTIVE_SCOPE_CHANGED_EVENT } from '@/utils/activeScopeEvents'

function createToken(authorities: string[], activeScopeType: 'PLATFORM' | 'TENANT' = 'TENANT') {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities, activeScopeType })).toString('base64url')
  return `${header}.${payload}.signature`
}

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

function readExposed<T>(value: T | { value: T }) {
  return value && typeof value === 'object' && 'value' in value ? value.value : value
}

describe('user.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.userList.mockResolvedValue({
      records: [{ id: 1, username: 'alice', nickname: 'Alice', enabled: true }],
      total: 1,
    })
    apiMocks.getRuntimeUiActions.mockResolvedValue([])
    authMocks.authUser.value = {
      access_token: createToken(['system:user:list']),
    }
    window.history.replaceState({}, '', '/system/user')
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
          UserForm: PassThrough,
          RoleTransfer: PassThrough,
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

  it('should reload user list when active scope changes', async () => {
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
          UserForm: PassThrough,
          RoleTransfer: PassThrough,
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
    const n = apiMocks.userList.mock.calls.length
    window.dispatchEvent(new CustomEvent(ACTIVE_SCOPE_CHANGED_EVENT))
    await flushPromises()
    expect(apiMocks.userList.mock.calls.length).toBeGreaterThan(n)
  })

  it('should not request user list without user management authority', async () => {
    authMocks.authUser.value = {
      access_token: createToken(['ROLE_USER']),
    }

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

    expect(apiMocks.userList).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('用户管理需要额外授权')
  })

  it('should not request user list under platform scope', async () => {
    authMocks.authUser.value = {
      access_token: createToken(['system:user:list'], 'PLATFORM'),
    }

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

    expect(apiMocks.userList).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('当前页面仅支持租户侧作用域')
  })

  it('should hide write buttons when runtime ui actions are missing (fail-closed)', async () => {
    apiMocks.getRuntimeUiActions.mockResolvedValue([])
    authMocks.authUser.value = {
      access_token: createToken([
        'system:user:list',
        'system:user:create',
        'system:user:edit',
        'system:user:delete',
        'system:user:batch-delete',
        'system:user:batch-enable',
        'system:user:batch-disable',
        'system:user:enable',
        'system:user:disable',
        'system:user:role:assign',
      ]),
    }

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

    expect(apiMocks.getRuntimeUiActions).toHaveBeenCalledWith('/system/user')
    expect(wrapper.text()).not.toContain('新建')
    expect(wrapper.text()).not.toContain('批量删除')
    expect(wrapper.text()).not.toContain('批量启用')
    expect(wrapper.text()).not.toContain('批量禁用')
  })


  it('should keep edit flow closed when only role assignment runtime action is granted', async () => {
    apiMocks.getRuntimeUiActions.mockResolvedValue([
      { id: 191, name: 'user:role:assign', title: '用户角色分配', type: 2, permission: 'system:user:role:assign', carrierKind: 'ui_action' },
    ])

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
          UserForm: PassThrough,
          RoleTransfer: PassThrough,
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

    const state = (wrapper.vm as any).$?.setupState
    expect(readExposed(state.canEditUserManagement)).toBe(false)
    expect(readExposed(state.canAssignUserRoles)).toBe(true)

    state.handleEdit({ id: 1, username: 'alice' })
    await flushPromises()

    expect(readExposed(state.drawerVisible)).toBe(false)
  })

  it('should keep role assignment flow closed when only edit runtime action is granted', async () => {
    apiMocks.getRuntimeUiActions.mockResolvedValue([
      { id: 184, name: 'user:edit', title: '用户编辑', type: 2, permission: 'system:user:edit', carrierKind: 'ui_action' },
    ])

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
          UserForm: PassThrough,
          RoleTransfer: PassThrough,
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

    const state = (wrapper.vm as any).$?.setupState
    expect(readExposed(state.canEditUserManagement)).toBe(true)
    expect(readExposed(state.canAssignUserRoles)).toBe(false)

    await state.openBatchRoleTransfer()
    await flushPromises()

    expect(readExposed(state.showBatchRoleTransfer)).toBe(false)
  })
})
