import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const deployMocks = vi.hoisted(() => ({
  getDeployments: vi.fn(),
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
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: routerMocks.routeQuery,
  }),
  useRouter: () => ({
    replace: routerMocks.routerReplace,
  }),
}))

vi.mock('@/api/process', () => ({
  deploymentApi: { getDeployments: deployMocks.getDeployments, deleteDeployment: vi.fn(), deployProcess: vi.fn(), deployProcessWithInfo: vi.fn() },
  tenantApi: { getTenants: tenantMocks.getTenants },
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
    Object.keys(routerMocks.routeQuery).forEach((key) => {
      delete routerMocks.routeQuery[key]
    })
    deployMocks.getDeployments.mockResolvedValue([])
    tenantMocks.getTenants.mockResolvedValue([])
    tenantContextMocks.getActiveTenantId.mockReturnValue('9')
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
    expect(deployMocks.getDeployments).toHaveBeenCalledWith('9')
    expect(tenantMocks.getTenants).toHaveBeenCalled()
    expect(routerMocks.routerReplace).toHaveBeenCalledWith({
      query: { activeTenantId: '9' },
    })
  })

})
