import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const deployMocks = vi.hoisted(() => ({
  getDeployments: vi.fn(),
}))

const tenantMocks = vi.hoisted(() => ({
  getTenants: vi.fn(),
}))

vi.mock('@/api/process', () => ({
  deploymentApi: { getDeployments: deployMocks.getDeployments, deleteDeployment: vi.fn(), deployProcess: vi.fn(), deployProcessWithInfo: vi.fn() },
  tenantApi: { getTenants: tenantMocks.getTenants },
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import Deployment from '@/views/process/Deployment.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('process Deployment.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    deployMocks.getDeployments.mockResolvedValue([])
    tenantMocks.getTenants.mockResolvedValue([])
  })

  it('should load data on mount and render title', async () => {
    const wrapper = mount(Deployment, {
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
          'a-upload': PassThrough,
          VueDraggable: PassThrough,
          UploadOutlined: PassThrough,
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

    expect(wrapper.text()).toContain('部署记录列表')
    expect(deployMocks.getDeployments).toHaveBeenCalled()
    expect(tenantMocks.getTenants).toHaveBeenCalled()
  })
})

