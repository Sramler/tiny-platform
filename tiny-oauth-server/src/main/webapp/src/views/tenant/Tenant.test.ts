import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  tenantList: vi.fn(),
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
}))

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
  path: '/system/tenant',
}))

const antMocks = vi.hoisted(() => ({
  message: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  },
  modalConfirm: vi.fn(),
}))

const wizardMocks = vi.hoisted(() => ({
  mountSequence: 0,
}))

vi.mock('ant-design-vue', () => ({
  message: antMocks.message,
  Modal: {
    confirm: antMocks.modalConfirm,
  },
}))

vi.mock('@/api/tenant', () => ({
  tenantList: apiMocks.tenantList,
  getTenantById: vi.fn(),
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

vi.mock('@/api/resource', () => ({
  getRuntimeUiActions: apiMocks.getRuntimeUiActions,
}))

vi.mock('@/views/tenant/TenantCreateWizard.vue', () => ({
  default: defineComponent({
    emits: ['cancel', 'completed'],
    setup() {
      const instanceId = ++wizardMocks.mountSequence
      return { instanceId }
    },
    template: `
      <div data-test="tenant-create-wizard">
        <span data-test="wizard-instance">{{ instanceId }}</span>
        <button
          data-test="wizard-completed"
          @click="$emit('completed')"
        >
          wizard-completed
        </button>
        <button data-test="wizard-cancel" @click="$emit('cancel')">wizard-cancel</button>
      </div>
    `,
  }),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: routerMocks.path }),
  useRouter: () => ({ push: routerMocks.push }),
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})
const DrawerStub = defineComponent({
  props: {
    open: {
      type: Boolean,
      default: false,
    },
    closable: {
      type: Boolean,
      default: true,
    },
    maskClosable: {
      type: Boolean,
      default: true,
    },
    keyboard: {
      type: Boolean,
      default: true,
    },
  },
  template: '<div v-if="open"><slot /></div>',
})

function createToken(authorities: string[], activeScopeType: 'PLATFORM' | 'TENANT' = 'PLATFORM') {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities, activeScopeType })).toString('base64url')
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
  function mountTenant() {
    return mount(Tenant, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-password': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': ButtonStub,
          'a-tooltip': PassThrough,
          'a-table': defineComponent({ props: ['dataSource'], template: '<div class="table" />' }),
          'a-tag': PassThrough,
          'a-pagination': PassThrough,
          'a-drawer': DrawerStub,
          'a-input-number': PassThrough,
          'a-switch': PassThrough,
          'a-textarea': PassThrough,
          'a-steps': PassThrough,
          'a-step': PassThrough,
          'a-alert': PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          EditOutlined: PassThrough,
          DeleteOutlined: PassThrough,
        },
      },
    })
  }

  async function openCreateWizard(wrapper: ReturnType<typeof mountTenant>) {
    const createButton = wrapper.findAll('button').find((node) => node.text().includes('新建'))
    expect(createButton).toBeTruthy()
    await createButton!.trigger('click')
    await flushPromises()
  }

  beforeEach(() => {
    vi.clearAllMocks()
    wizardMocks.mountSequence = 0
    routerMocks.path = '/system/tenant'
    apiMocks.tenantList.mockResolvedValue({ content: [], totalElements: 0 })
    apiMocks.getRuntimeUiActions.mockResolvedValue([])
    authMocks.authUser.value = {
      access_token: createToken(['system:tenant:list', 'system:tenant:view'], 'PLATFORM'),
    }
    window.history.replaceState({}, '', '/system/tenant')
  })

  it('should render title and call tenantList on mount', async () => {
    const wrapper = mountTenant()
    await flushPromises()

    expect(wrapper.text()).toContain('租户列表')
    expect(apiMocks.tenantList).toHaveBeenCalled()
  })

  it('should not request tenant list for non-platform tenant users', async () => {
    authMocks.authUser.value = {
      access_token: createToken(['system:tenant:list'], 'TENANT'),
    }

    const wrapper = mountTenant()
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
      ], 'PLATFORM'),
    }

    const wrapper = mountTenant()
    await flushPromises()

    expect(apiMocks.getRuntimeUiActions).toHaveBeenCalledWith('/system/tenant')
    expect(wrapper.text()).not.toContain('新建租户')
    expect(wrapper.text()).not.toContain('批量删除')
  })

  it('should render create button when runtime ui actions include tenant create', async () => {
    apiMocks.getRuntimeUiActions.mockResolvedValue([
      { permission: 'system:tenant:create' },
    ])

    const wrapper = mountTenant()
    await flushPromises()

    expect(apiMocks.getRuntimeUiActions).toHaveBeenCalledWith('/system/tenant')
    expect(wrapper.text()).toContain('新建')
  })

  it('should resolve runtime actions on platform route with system tenant menu path alias', async () => {
    routerMocks.path = '/platform/tenants'
    window.history.replaceState({}, '', '/platform/tenants')

    const wrapper = mountTenant()
    await flushPromises()

    expect(apiMocks.getRuntimeUiActions).toHaveBeenCalledWith('/system/tenant')
  })

  it('should open tenant create wizard when clicking create', async () => {
    apiMocks.getRuntimeUiActions.mockResolvedValue([
      { permission: 'system:tenant:create' },
    ])
    const wrapper = mountTenant()
    await flushPromises()

    await openCreateWizard(wrapper)

    expect(wrapper.find('[data-test="tenant-create-wizard"]').exists()).toBe(true)
  })

  it('should disable drawer built-in close controls for create wizard', async () => {
    apiMocks.getRuntimeUiActions.mockResolvedValue([
      { permission: 'system:tenant:create' },
    ])
    const wrapper = mountTenant()
    await flushPromises()

    await openCreateWizard(wrapper)

    const createDrawer = wrapper.findAllComponents(DrawerStub).at(0)
    expect(createDrawer?.props('closable')).toBe(false)
    expect(createDrawer?.props('maskClosable')).toBe(false)
    expect(createDrawer?.props('keyboard')).toBe(false)
  })

  it('should refresh list after wizard completion and close drawer', async () => {
    apiMocks.getRuntimeUiActions.mockResolvedValue([
      { permission: 'system:tenant:create' },
    ])
    const wrapper = mountTenant()
    await flushPromises()

    await openCreateWizard(wrapper)
    expect(wrapper.find('[data-test="tenant-create-wizard"]').exists()).toBe(true)
    await wrapper.find('[data-test="wizard-completed"]').trigger('click')
    await flushPromises()

    expect(apiMocks.tenantList).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-test="tenant-create-wizard"]').exists()).toBe(false)
  })

  it('should remount wizard with a fresh instance after close and reopen', async () => {
    apiMocks.getRuntimeUiActions.mockResolvedValue([
      { permission: 'system:tenant:create' },
    ])
    const wrapper = mountTenant()
    await flushPromises()

    await openCreateWizard(wrapper)
    const firstInstanceId = wrapper.find('[data-test="wizard-instance"]').text()

    await wrapper.find('[data-test="wizard-cancel"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-test="tenant-create-wizard"]').exists()).toBe(false)

    await openCreateWizard(wrapper)
    const secondInstanceId = wrapper.find('[data-test="wizard-instance"]').text()

    expect(secondInstanceId).not.toBe(firstInstanceId)
  })

  it('should keep edit flow in TenantForm without opening wizard', async () => {
    apiMocks.getRuntimeUiActions.mockResolvedValue([
      { permission: 'system:tenant:edit' },
    ])
    apiMocks.tenantList.mockResolvedValue({
      content: [{ id: 1, code: 't1', name: 'Tenant 1', lifecycleStatus: 'ACTIVE' }],
      totalElements: 1,
    })

    const wrapper = mountTenant()
    await flushPromises()

    const vm = wrapper.vm as unknown as { throttledEdit: (record: unknown) => void }
    vm.throttledEdit({ id: 1, code: 't1', name: 'Tenant 1', lifecycleStatus: 'ACTIVE' })
    await flushPromises()

    expect(wrapper.find('[data-test="tenant-create-wizard"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('取消')
    expect(wrapper.text()).toContain('保存')
  })
})
