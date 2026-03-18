import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const instanceMocks = vi.hoisted(() => ({
  getProcessInstances: vi.fn(),
}))

const tenantMocks = vi.hoisted(() => ({
  getTenants: vi.fn(),
}))

const tenantContextMocks = vi.hoisted(() => ({
  getActiveTenantId: vi.fn(),
}))

const routerMocks = vi.hoisted(() => ({
  routeQuery: {} as Record<string, unknown>,
  routerReplace: vi.fn(),
  routerPush: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: routerMocks.routeQuery,
  }),
  useRouter: () => ({
    replace: routerMocks.routerReplace,
    push: routerMocks.routerPush,
  }),
}))

vi.mock('@/api/process', () => ({
  instanceApi: {
    getProcessInstances: instanceMocks.getProcessInstances,
    suspendInstance: vi.fn(),
    activateInstance: vi.fn(),
    deleteInstance: vi.fn(),
    getTasks: vi.fn(),
  },
  tenantApi: { getTenants: tenantMocks.getTenants },
  historyApi: { getHistoricInstances: vi.fn(), getHistoricTasks: vi.fn() },
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('@/utils/tenant', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/tenant')>()
  return {
    ...actual,
    getActiveTenantId: tenantContextMocks.getActiveTenantId,
  }
})

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})

import Instance from '@/views/process/Instance.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('process Instance.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.keys(routerMocks.routeQuery).forEach((key) => {
      delete routerMocks.routeQuery[key]
    })
    instanceMocks.getProcessInstances.mockResolvedValue([])
    tenantMocks.getTenants.mockResolvedValue([])
    tenantContextMocks.getActiveTenantId.mockReturnValue('9')
  })

  it('should load data on mount and render title', async () => {
    const wrapper = mount(Instance, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': ButtonStub,
          'a-tooltip': PassThrough,
          'a-popover': PassThrough,
          'a-checkbox': PassThrough,
          'a-table': defineComponent({ props: ['dataSource'], template: '<div class=\"table\" />' }),
          'a-tag': PassThrough,
          'a-typography-text': PassThrough,
          'a-modal': PassThrough,
          'a-drawer': PassThrough,
          VueDraggable: PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          DeleteOutlined: PassThrough,
          CloseOutlined: PassThrough,
          PauseCircleOutlined: PassThrough,
          PlayCircleOutlined: PassThrough,
          PoweroffOutlined: PassThrough,
          SettingOutlined: PassThrough,
          HolderOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('流程实例列表')
    expect(instanceMocks.getProcessInstances).toHaveBeenCalledWith('9', 'active')
    expect(tenantMocks.getTenants).toHaveBeenCalled()
    expect(routerMocks.routerReplace).toHaveBeenCalledWith({
      query: { activeTenantId: '9' },
    })
  })

  it('should preserve activeTenantId when navigating to modeling', async () => {
    routerMocks.routeQuery.activeTenantId = '11'

    const wrapper = mount(Instance, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': ButtonStub,
          'a-tooltip': PassThrough,
          'a-popover': PassThrough,
          'a-checkbox': PassThrough,
          'a-table': defineComponent({ props: ['dataSource'], template: '<div class="table" />' }),
          'a-tag': PassThrough,
          'a-typography-text': PassThrough,
          'a-modal': PassThrough,
          'a-drawer': PassThrough,
          VueDraggable: PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          DeleteOutlined: PassThrough,
          CloseOutlined: PassThrough,
          PauseCircleOutlined: PassThrough,
          PlayCircleOutlined: PassThrough,
          PoweroffOutlined: PassThrough,
          SettingOutlined: PassThrough,
          HolderOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    const createButton = wrapper.findAll('button').find((button) => button.text().includes('新建流程'))
    expect(createButton).toBeDefined()
    await createButton!.trigger('click')

    expect(routerMocks.routerPush).toHaveBeenCalledWith({
      path: '/modeling',
      query: { activeTenantId: '11' },
    })
  })
})
