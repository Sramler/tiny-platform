import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  tenantList: vi.fn(),
  createTenant: vi.fn(),
  updateTenant: vi.fn(),
  deleteTenant: vi.fn(),
  initializePlatformTemplate: vi.fn(),
  freezeTenant: vi.fn(),
  unfreezeTenant: vi.fn(),
  decommissionTenant: vi.fn(),
  getRuntimeUiActions: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  authUser: { value: null as { access_token?: string | null } | null },
  tenantCode: 'default',
}))

vi.mock('@/api/tenant', () => ({
  tenantList: apiMocks.tenantList,
  getTenantById: vi.fn(),
  createTenant: apiMocks.createTenant,
  updateTenant: apiMocks.updateTenant,
  deleteTenant: apiMocks.deleteTenant,
  initializePlatformTemplate: apiMocks.initializePlatformTemplate,
  freezeTenant: apiMocks.freezeTenant,
  unfreezeTenant: apiMocks.unfreezeTenant,
  decommissionTenant: apiMocks.decommissionTenant,
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: authMocks.authUser,
    isAuthenticated: { value: true },
    login: vi.fn(),
    logout: vi.fn(),
    getAccessToken: vi.fn(),
    fetchWithAuth: vi.fn(),
  }),
}))

vi.mock('@/utils/tenant', () => ({
  getTenantCode: () => authMocks.tenantCode,
}))

vi.mock('@/api/resource', () => ({
  getRuntimeUiActions: apiMocks.getRuntimeUiActions,
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

function createToken(authorities: string[]) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities })).toString('base64url')
  return `${header}.${payload}.signature`
}

import Tenant from '@/views/tenant/Tenant.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('Tenant.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.tenantList.mockResolvedValue({ content: [], totalElements: 0 })
    apiMocks.getRuntimeUiActions.mockResolvedValue([])
    authMocks.authUser.value = {
      access_token: createToken(['system:tenant:list', 'system:tenant:view']),
    }
    authMocks.tenantCode = 'default'
    window.history.replaceState({}, '', '/system/tenant')
  })

  it('should render title and call tenantList on mount', async () => {
    const wrapper = mount(Tenant, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-password': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-tooltip': PassThrough,
          'a-table': defineComponent({ props: ['dataSource'], template: '<div class="table" />' }),
          'a-tag': PassThrough,
          'a-pagination': PassThrough,
          'a-drawer': PassThrough,
          'a-input-number': PassThrough,
          'a-switch': PassThrough,
          'a-textarea': PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          EditOutlined: PassThrough,
          DeleteOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('租户列表')
    expect(apiMocks.tenantList).toHaveBeenCalled()
  })

  it('should not request tenant list for non-platform tenant users', async () => {
    authMocks.authUser.value = {
      access_token: createToken(['system:tenant:list']),
    }
    authMocks.tenantCode = 'tenant-a'

    const wrapper = mount(Tenant, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-password': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-tooltip': PassThrough,
          'a-table': defineComponent({ props: ['dataSource'], template: '<div class="table" />' }),
          'a-tag': PassThrough,
          'a-pagination': PassThrough,
          'a-drawer': PassThrough,
          'a-input-number': PassThrough,
          'a-switch': PassThrough,
          'a-textarea': PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          EditOutlined: PassThrough,
          DeleteOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(apiMocks.tenantList).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('租户管理仅对平台管理员开放')
  })

  it('should hide write buttons when runtime ui actions are missing (fail-closed)', async () => {
    apiMocks.getRuntimeUiActions.mockResolvedValue([])
    authMocks.authUser.value = {
      access_token: createToken([
        'system:tenant:list',
        'system:tenant:view',
        'system:tenant:create',
        'system:tenant:edit',
        'system:tenant:delete',
        'system:tenant:template:initialize',
        'system:tenant:freeze',
        'system:tenant:unfreeze',
        'system:tenant:decommission',
      ]),
    }
    authMocks.tenantCode = 'default'

    const wrapper = mount(Tenant, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-password': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-tooltip': PassThrough,
          'a-table': defineComponent({ props: ['dataSource'], template: '<div class="table" />' }),
          'a-tag': PassThrough,
          'a-pagination': PassThrough,
          'a-drawer': PassThrough,
          'a-input-number': PassThrough,
          'a-switch': PassThrough,
          'a-textarea': PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          EditOutlined: PassThrough,
          DeleteOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(apiMocks.getRuntimeUiActions).toHaveBeenCalledWith('/system/tenant')
    expect(wrapper.text()).not.toContain('新建租户')
    expect(wrapper.text()).not.toContain('批量删除')
  })
})
