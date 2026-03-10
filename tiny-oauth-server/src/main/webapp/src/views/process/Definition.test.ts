import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const processMocks = vi.hoisted(() => ({
  getProcessDefinitions: vi.fn(),
}))

const tenantMocks = vi.hoisted(() => ({
  getTenants: vi.fn(),
}))

vi.mock('@/api/process', () => ({
  processApi: { getProcessDefinitions: processMocks.getProcessDefinitions },
  deploymentApi: { deleteDeployment: vi.fn(), getDeployments: vi.fn(), deployProcess: vi.fn(), deployProcessWithInfo: vi.fn() },
  tenantApi: { getTenants: tenantMocks.getTenants },
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import Definition from '@/views/process/Definition.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('process Definition.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    processMocks.getProcessDefinitions.mockResolvedValue([])
    tenantMocks.getTenants.mockResolvedValue([])
  })

  it('should load data on mount and render title', async () => {
    const wrapper = mount(Definition, {
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
          'a-badge': PassThrough,
          VueDraggable: PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          DeleteOutlined: PassThrough,
          CloseOutlined: PassThrough,
          PoweroffOutlined: PassThrough,
          SettingOutlined: PassThrough,
          HolderOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('流程定义列表')
    expect(processMocks.getProcessDefinitions).toHaveBeenCalled()
    expect(tenantMocks.getTenants).toHaveBeenCalled()
  })
})

