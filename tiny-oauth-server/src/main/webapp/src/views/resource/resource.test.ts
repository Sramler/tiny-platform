import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  getResourceTree: vi.fn(),
  getRuntimeUiActions: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  authUser: { value: null as { access_token?: string | null } | null },
  isAuthenticated: { value: true },
}))

vi.mock('@/api/resource', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/resource')>()
  return {
    ...actual,
    getResourceTree: apiMocks.getResourceTree,
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

import { ACTIVE_SCOPE_CHANGED_EVENT } from '@/utils/activeScopeEvents'
import Resource from '@/views/resource/resource.vue'

function createToken(authorities: string[]) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities })).toString('base64url')
  return `${header}.${payload}.signature`
}

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('resource.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.getResourceTree.mockResolvedValue([
      { id: 1, name: 'res1', title: 'Resource 1', type: 0, carrierKind: 'menu', children: [] },
    ])
    apiMocks.getRuntimeUiActions.mockResolvedValue([
      { id: 11, name: 'resource:create', title: '资源新增', type: 2, permission: 'system:resource:create', carrierKind: 'ui_action' },
      { id: 12, name: 'resource:edit', title: '资源编辑', type: 2, permission: 'system:resource:edit', carrierKind: 'ui_action' },
      { id: 13, name: 'resource:delete', title: '资源删除', type: 2, permission: 'system:resource:delete', carrierKind: 'ui_action' },
      { id: 14, name: 'resource:batch-delete', title: '资源批量删除', type: 2, permission: 'system:resource:batch-delete', carrierKind: 'ui_action' },
    ])
    authMocks.authUser.value = {
      access_token: createToken(['system:resource:list']),
    }
  })

  it('should display resource title and load tree on mount', async () => {
    const wrapper = mount(Resource, {
      global: {
        stubs: {
          'a-table': defineComponent({
            props: ['dataSource'],
            template: '<div class="resource-table-stub">table rows: {{ (dataSource || []).length }}</div>',
          }),
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-checkbox': PassThrough,
          'a-tag': PassThrough,
          'a-tooltip': PassThrough,
          'a-popover': PassThrough,
          'a-modal': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          'a-drawer': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
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

    expect(wrapper.text()).toContain('资源管理')
    expect(wrapper.text()).toContain('menu')
    expect(wrapper.text()).toContain('ui_action')
    expect(wrapper.text()).toContain('api_endpoint')
    expect(apiMocks.getResourceTree).toHaveBeenCalled()
    expect(apiMocks.getRuntimeUiActions).toHaveBeenCalledWith('/system/resource')
  })

  it('should refetch resource tree when active scope changes', async () => {
    const wrapper = mount(Resource, {
      global: {
        stubs: {
          'a-table': defineComponent({
            props: ['dataSource'],
            template: '<div class="resource-table-stub">table rows: {{ (dataSource || []).length }}</div>',
          }),
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-checkbox': PassThrough,
          'a-tag': PassThrough,
          'a-tooltip': PassThrough,
          'a-popover': PassThrough,
          'a-modal': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          'a-drawer': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
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
    const callsAfterMount = apiMocks.getResourceTree.mock.calls.length
    expect(callsAfterMount).toBeGreaterThanOrEqual(1)
    window.dispatchEvent(new CustomEvent(ACTIVE_SCOPE_CHANGED_EVENT))
    await flushPromises()
    expect(apiMocks.getResourceTree.mock.calls.length).toBeGreaterThan(callsAfterMount)
    wrapper.unmount()
  })

  it('should hide action buttons when runtime ui actions deny them', async () => {
    apiMocks.getRuntimeUiActions.mockResolvedValue([])
    authMocks.authUser.value = {
      access_token: createToken([
        'system:resource:list',
        'system:resource:create',
        'system:resource:edit',
        'system:resource:delete',
        'system:resource:batch-delete',
      ]),
    }

    const wrapper = mount(Resource, {
      global: {
        stubs: {
          'a-table': defineComponent({
            props: ['dataSource'],
            template: '<div class="resource-table-stub">table rows: {{ (dataSource || []).length }}</div>',
          }),
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-checkbox': PassThrough,
          'a-tag': PassThrough,
          'a-tooltip': PassThrough,
          'a-popover': PassThrough,
          'a-modal': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          'a-drawer': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
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

    expect(wrapper.text()).not.toContain('新建资源')
    expect(wrapper.text()).not.toContain('批量删除')
  })

  it('should not request resource tree without resource management authority', async () => {
    authMocks.authUser.value = {
      access_token: createToken(['ROLE_USER']),
    }

    const wrapper = mount(Resource, {
      global: {
        stubs: {
          'a-table': defineComponent({
            props: ['dataSource'],
            template: '<div class="resource-table-stub">table rows: {{ (dataSource || []).length }}</div>',
          }),
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-checkbox': PassThrough,
          'a-tag': PassThrough,
          'a-tooltip': PassThrough,
          'a-popover': PassThrough,
          'a-modal': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          'a-drawer': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
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

    expect(apiMocks.getResourceTree).not.toHaveBeenCalled()
    expect(apiMocks.getRuntimeUiActions).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('资源管理需要额外授权')
  })
})
