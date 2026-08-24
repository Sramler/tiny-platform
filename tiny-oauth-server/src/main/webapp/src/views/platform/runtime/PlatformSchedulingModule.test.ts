import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routeState = vi.hoisted(() => ({
  path: '/platform/scheduling/dag',
  query: {
    activeTenantId: '9',
    targetTenantId: '12',
  } as Record<string, unknown>,
}))

const routerMocks = vi.hoisted(() => ({
  replace: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  authorities: ['scheduling:entry:view'] as string[],
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

vi.mock('@/views/scheduling/Dag.vue', () => ({
  default: defineComponent({ template: '<div>SchedulingDagStub</div>' }),
}))
vi.mock('@/views/scheduling/Task.vue', () => ({
  default: defineComponent({ template: '<div>SchedulingTaskStub</div>' }),
}))
vi.mock('@/views/scheduling/TaskType.vue', () => ({
  default: defineComponent({ template: '<div>SchedulingTaskTypeStub</div>' }),
}))
vi.mock('@/views/scheduling/DagHistory.vue', () => ({
  default: defineComponent({ template: '<div>SchedulingDagHistoryStub</div>' }),
}))
vi.mock('@/views/scheduling/Audit.vue', () => ({
  default: defineComponent({ template: '<div>SchedulingAuditStub</div>' }),
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const TabsStub = defineComponent({
  props: ['activeKey'],
  emits: ['change'],
  template: '<div><button class="tab-change" @click="$emit(\'change\', \'taskType\')">切换任务类型</button><slot /></div>',
})

import PlatformSchedulingModule from '@/views/platform/runtime/PlatformSchedulingModule.vue'

describe('PlatformSchedulingModule.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.path = '/platform/scheduling/dag'
    routeState.query = {
      activeTenantId: '9',
      targetTenantId: '12',
    }
    authMocks.authorities = ['scheduling:entry:view']
    authMocks.isPlatformScope = true
  })

  it('should render platform scheduling business module without the explanatory header', () => {
    const wrapper = mount(PlatformSchedulingModule, {
      global: {
        stubs: {
          'a-alert': PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
        },
      },
    })

    expect(wrapper.text()).not.toContain('Platform Scheduling')
    expect(wrapper.text()).not.toContain('后端数据边界为')
    expect(wrapper.text()).not.toContain('平台运行态说明')
    expect(wrapper.text()).toContain('SchedulingDagStub')
    expect(wrapper.text()).not.toContain('SchedulingTaskTypeStub')
    expect(wrapper.text()).not.toContain('目标租户')
  })

  it('should render tab content from child route on direct open', () => {
    routeState.path = '/platform/scheduling/task-type'
    routeState.query = {}

    const wrapper = mount(PlatformSchedulingModule, {
      global: {
        stubs: {
          'a-alert': PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
        },
      },
    })

    expect(wrapper.text()).toContain('SchedulingTaskTypeStub')
    expect(wrapper.text()).not.toContain('SchedulingDagStub')
  })

  it('should guard missing scheduling permission before loading scheduling module', () => {
    authMocks.authorities = []

    const wrapper = mount(PlatformSchedulingModule, {
      global: {
        stubs: {
          'a-alert': PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
        },
      },
    })

    expect(wrapper.text()).toContain('当前会话缺少平台调度管理权限')
    expect(wrapper.text()).not.toContain('SchedulingDagStub')
  })

  it('should strip tenant query when switching platform scheduling tab', async () => {
    const wrapper = mount(PlatformSchedulingModule, {
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
      path: '/platform/scheduling/task-type',
      query: {},
    })
  })
})
