import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  userList: vi.fn(),
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

function createToken(authorities: string[]) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities })).toString('base64url')
  return `${header}.${payload}.signature`
}

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('user.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.userList.mockResolvedValue({
      records: [{ id: 1, username: 'alice', nickname: 'Alice', enabled: true }],
      total: 1,
    })
    authMocks.authUser.value = {
      access_token: createToken(['system:user:list']),
    }
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
})
