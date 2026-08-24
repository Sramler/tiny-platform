import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routeState = vi.hoisted(() => ({
  path: '/platform/process',
  query: {
    activeTenantId: '9',
    targetTenantId: '12',
  } as Record<string, unknown>,
}))

const routerMocks = vi.hoisted(() => ({
  replace: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  authorities: ['workflow:console:view'] as string[],
  isPlatformScope: true,
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => routerMocks,
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: { get value() { return { activeScopeType: 'PLATFORM', permissions: authMocks.authorities } } },
  }),
}))

vi.mock('@/composables/usePlatformScope', () => ({
  usePlatformScope: () => ({
    isPlatformScope: {
      __v_isRef: true,
      get value() {
        return authMocks.isPlatformScope
      },
    },
  }),
}))

vi.mock('@/views/process/Modeling.vue', () => ({
  default: defineComponent({ template: '<div>ProcessModelingStub</div>' }),
}))
vi.mock('@/views/process/Deployment.vue', () => ({
  default: defineComponent({ template: '<div>ProcessDeploymentStub</div>' }),
}))
vi.mock('@/views/process/Definition.vue', () => ({
  default: defineComponent({ template: '<div>ProcessDefinitionStub</div>' }),
}))
vi.mock('@/views/process/Instance.vue', () => ({
  default: defineComponent({ template: '<div>ProcessInstanceStub</div>' }),
}))
vi.mock('@/views/process/task.vue', () => ({
  default: defineComponent({ template: '<div>ProcessTaskStub</div>' }),
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const TabsStub = defineComponent({
  props: ['activeKey'],
  emits: ['change'],
  template: '<div><button class="tab-change" @click="$emit(\'change\', \'instance\')">切换实例</button><slot /></div>',
})

import PlatformProcessModule from '@/views/platform/runtime/PlatformProcessModule.vue'

describe('PlatformProcessModule.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.path = '/platform/process'
    routeState.query = {
      activeTenantId: '9',
      targetTenantId: '12',
    }
    authMocks.authorities = ['workflow:console:view']
    authMocks.isPlatformScope = true
  })

  it('should render platform workflow business module without the explanatory header', () => {
    const wrapper = mount(PlatformProcessModule, {
      global: {
        stubs: {
          'a-alert': PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
        },
      },
    })

    expect(wrapper.text()).not.toContain('Platform Workflow')
    expect(wrapper.text()).not.toContain('平台侧流程管理是 PLATFORM 作用域自己的工作流能力')
    expect(wrapper.text()).not.toContain('平台运行态说明')
    expect(wrapper.text()).toContain('ProcessDefinitionStub')
    expect(wrapper.text()).not.toContain('ProcessInstanceStub')
    expect(wrapper.text()).not.toContain('目标租户')
  })

  it('should render tab content from child route on direct open', () => {
    routeState.path = '/platform/process/instance'
    routeState.query = {}

    const wrapper = mount(PlatformProcessModule, {
      global: {
        stubs: {
          'a-alert': PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
        },
      },
    })

    expect(wrapper.text()).toContain('ProcessInstanceStub')
    expect(wrapper.text()).not.toContain('ProcessDefinitionStub')
  })

  it('should guard non-platform scope before loading workflow module', () => {
    authMocks.isPlatformScope = false

    const wrapper = mount(PlatformProcessModule, {
      global: {
        stubs: {
          'a-alert': PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
        },
      },
    })

    expect(wrapper.text()).toContain('当前页面只支持 PLATFORM 作用域')
    expect(wrapper.text()).not.toContain('ProcessDefinitionStub')
  })

  it('should strip tenant query when switching platform workflow tab', async () => {
    const wrapper = mount(PlatformProcessModule, {
      global: {
        stubs: {
          'a-alert': PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
        },
      },
    })

    await wrapper.find('.tab-change').trigger('click')

    expect(routerMocks.replace).toHaveBeenCalledWith({
      path: '/platform/process/instance',
      query: {},
    })
  })
})
