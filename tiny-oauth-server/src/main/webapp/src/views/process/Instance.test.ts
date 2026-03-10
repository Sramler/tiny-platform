import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const instanceMocks = vi.hoisted(() => ({
  getProcessInstances: vi.fn(),
}))

const tenantMocks = vi.hoisted(() => ({
  getTenants: vi.fn(),
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

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

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
    instanceMocks.getProcessInstances.mockResolvedValue([])
    tenantMocks.getTenants.mockResolvedValue([])
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
          'a-button': PassThrough,
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
    expect(instanceMocks.getProcessInstances).toHaveBeenCalled()
    expect(tenantMocks.getTenants).toHaveBeenCalled()
  })
})

