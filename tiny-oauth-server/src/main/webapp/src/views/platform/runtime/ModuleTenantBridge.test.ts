import { mount } from '@vue/test-utils'
import { computed, defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const bridgeMocks = vi.hoisted(() => ({
  routePath: '/platform/process',
  routeQuery: {
    target: '/process/modeling',
  } as Record<string, unknown>,
  isPlatformScope: true,
  authorities: ['workflow:console:view'],
  routerReplace: vi.fn(),
  tenantList: vi.fn(),
  getTenantById: vi.fn(),
  switchActiveScope: vi.fn(),
  getCurrentUser: vi.fn(),
  refreshTokenAfterActiveScopeSwitch: vi.fn(),
  notifyActiveScopeChanged: vi.fn(),
  setLoginMode: vi.fn(),
  setTenantCode: vi.fn(),
  setActiveTenantId: vi.fn(),
  getDeployments: vi.fn(),
  getProcessDefinitions: vi.fn(),
  getProcessInstances: vi.fn(),
  getHistoricInstances: vi.fn(),
  getEngineInfo: vi.fn(),
  healthCheck: vi.fn(),
  getExecutors: vi.fn(),
  getQuartzClusterStatus: vi.fn(),
  message: {
    warning: vi.fn(),
    error: vi.fn(),
    success: vi.fn(),
  },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    path: bridgeMocks.routePath,
    query: bridgeMocks.routeQuery,
  }),
  useRouter: () => ({
    replace: bridgeMocks.routerReplace,
  }),
}))

vi.mock('@/composables/usePlatformScope', () => ({
  usePlatformScope: () => ({
    isPlatformScope: computed(() => bridgeMocks.isPlatformScope),
  }),
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: {
      value: {
        access_token: 'token',
      },
    },
  }),
  refreshTokenAfterActiveScopeSwitch: bridgeMocks.refreshTokenAfterActiveScopeSwitch,
}))

vi.mock('@/utils/jwt', () => ({
  extractAuthoritiesFromJwt: () => bridgeMocks.authorities,
}))

vi.mock('@/api/tenant', () => ({
  tenantList: bridgeMocks.tenantList,
  getTenantById: bridgeMocks.getTenantById,
}))

vi.mock('@/api/user', () => ({
  switchActiveScope: bridgeMocks.switchActiveScope,
  getCurrentUser: bridgeMocks.getCurrentUser,
}))

vi.mock('@/api/process', () => ({
  deploymentApi: {
    getDeployments: bridgeMocks.getDeployments,
  },
  historyApi: {
    getHistoricInstances: bridgeMocks.getHistoricInstances,
  },
  instanceApi: {
    getProcessInstances: bridgeMocks.getProcessInstances,
  },
  maintenanceApi: {
    getEngineInfo: bridgeMocks.getEngineInfo,
    healthCheck: bridgeMocks.healthCheck,
  },
  processApi: {
    getProcessDefinitions: bridgeMocks.getProcessDefinitions,
  },
}))

vi.mock('@/api/scheduling', () => ({
  getExecutors: bridgeMocks.getExecutors,
  getQuartzClusterStatus: bridgeMocks.getQuartzClusterStatus,
}))

vi.mock('@/utils/activeScopeEvents', () => ({
  notifyActiveScopeChanged: bridgeMocks.notifyActiveScopeChanged,
}))

vi.mock('@/utils/redirect', () => ({
  sanitizeInternalRedirect: (value: string) => (value.startsWith('/') ? value : '/'),
}))

vi.mock('@/utils/tenant', () => ({
  setLoginMode: bridgeMocks.setLoginMode,
  setTenantCode: bridgeMocks.setTenantCode,
  setActiveTenantId: bridgeMocks.setActiveTenantId,
  withActiveTenantQuery: (
    query: Record<string, unknown>,
    activeTenantId?: string | number | null,
  ) => (activeTenantId ? { ...query, activeTenantId: String(activeTenantId) } : { ...query }),
}))

vi.mock('ant-design-vue', () => ({
  message: bridgeMocks.message,
}))

import ModuleTenantBridge from '@/views/platform/runtime/ModuleTenantBridge.vue'

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

function mountConsole() {
  return mount(ModuleTenantBridge, {
    global: {
      stubs: {
        'a-card': PassThrough,
        'a-alert': PassThrough,
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-input': PassThrough,
        'a-select': PassThrough,
        'a-select-option': PassThrough,
        'a-space': PassThrough,
        'a-button': defineComponent({
          emits: ['click'],
          template: '<button @click="$emit(\'click\', $event)"><slot /></button>',
        }),
      },
    },
  })
}

describe('platform/runtime/ModuleTenantBridge.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    bridgeMocks.routePath = '/platform/process'
    bridgeMocks.routeQuery = {
      target: '/process/modeling',
    }
    bridgeMocks.isPlatformScope = true
    bridgeMocks.authorities = ['workflow:console:view']
    bridgeMocks.tenantList.mockResolvedValue({
      content: [
        {
          id: 9,
          code: 'bench-1m',
          name: 'Bench 1M',
          enabled: true,
          lifecycleStatus: 'ACTIVE',
        },
      ],
    })
    bridgeMocks.getTenantById.mockResolvedValue({
      id: 9,
      code: 'bench-1m',
      name: 'Bench 1M',
      enabled: true,
      lifecycleStatus: 'ACTIVE',
    })
    bridgeMocks.switchActiveScope.mockResolvedValue({
      success: true,
      tokenRefreshRequired: true,
      activeTenantId: 9,
      activeScopeType: 'TENANT',
    })
    bridgeMocks.refreshTokenAfterActiveScopeSwitch.mockResolvedValue({ ok: true })
    bridgeMocks.getCurrentUser.mockResolvedValue({
      id: '1001',
      username: 'e2e_admin',
      activeTenantId: 9,
    })
    bridgeMocks.routerReplace.mockResolvedValue(undefined)
    bridgeMocks.getDeployments.mockResolvedValue([
      { id: 'dep-1', name: 'Deploy A', recordTenantId: '9' },
    ])
    bridgeMocks.getProcessDefinitions.mockResolvedValue([
      {
        id: 'def-1',
        key: 'leave',
        name: 'Leave Flow',
        version: 1,
        deploymentId: 'dep-1',
        deploymentTime: '2026-04-21',
        recordTenantId: '9',
      },
    ])
    bridgeMocks.getProcessInstances.mockResolvedValue([
      {
        id: 'inst-1',
        processDefinitionId: 'def-1',
        processDefinitionName: 'Leave Flow',
        startTime: '2026-04-21',
        recordTenantId: '9',
      },
    ])
    bridgeMocks.getHistoricInstances.mockResolvedValue([{ id: 'hist-1' }])
    bridgeMocks.getEngineInfo.mockResolvedValue({ name: 'Camunda', version: '7.x' })
    bridgeMocks.healthCheck.mockResolvedValue({ status: 'UP', message: 'ok' })
    bridgeMocks.getExecutors.mockResolvedValue(['shell'])
    bridgeMocks.getQuartzClusterStatus.mockResolvedValue({
      status: 'RUNNING',
      clusterMode: '单机模式',
    })
  })

  it('loads workflow platform overview data before any tenant reentry', async () => {
    const wrapper = mountConsole()
    await flushPromises()

    expect(bridgeMocks.getDeployments).toHaveBeenCalledWith(undefined)
    expect(bridgeMocks.getProcessDefinitions).toHaveBeenCalledWith(undefined)
    expect(bridgeMocks.getProcessInstances).toHaveBeenCalledWith(undefined)
    expect(bridgeMocks.getHistoricInstances).toHaveBeenCalledWith(undefined)
    expect(bridgeMocks.getEngineInfo).toHaveBeenCalledTimes(1)
    expect(bridgeMocks.healthCheck).toHaveBeenCalledTimes(1)
    expect(bridgeMocks.switchActiveScope).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('平台工作流控制台')
    expect(wrapper.text()).toContain('Deploy A')
    expect(wrapper.text()).toContain('Leave Flow')
  })

  it('loads scheduling platform overview data in platform scope', async () => {
    bridgeMocks.routePath = '/platform/scheduling'
    bridgeMocks.routeQuery = {
      target: '/scheduling/dag/history',
    }
    bridgeMocks.authorities = ['scheduling:cluster:view']

    const wrapper = mountConsole()
    await flushPromises()

    expect(bridgeMocks.getQuartzClusterStatus).toHaveBeenCalledTimes(1)
    expect(bridgeMocks.getExecutors).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('平台调度控制台')
    expect(wrapper.text()).toContain('Quartz 集群状态')
    expect(wrapper.text()).toContain('shell')
  })

  it('switches to tenant scope only after explicit entry from the platform console', async () => {
    bridgeMocks.routeQuery = {
      target: '/process/modeling?tab=design',
    }

    const wrapper = mountConsole()
    await flushPromises()
    ;(wrapper.vm as any).selectedTenantId = 9
    await (wrapper.vm as any).enterTenantModule()
    await flushPromises()

    expect(bridgeMocks.switchActiveScope).toHaveBeenCalledWith({
      scopeType: 'TENANT',
      scopeId: 9,
    })
    expect(bridgeMocks.refreshTokenAfterActiveScopeSwitch).toHaveBeenCalledTimes(1)
    expect(bridgeMocks.setLoginMode).toHaveBeenCalledWith('TENANT')
    expect(bridgeMocks.setTenantCode).toHaveBeenCalledWith('bench-1m')
    expect(bridgeMocks.setActiveTenantId).toHaveBeenCalledWith(9)
    expect(bridgeMocks.routerReplace).toHaveBeenCalledWith({
      path: '/process/modeling',
      query: {
        tab: 'design',
        activeTenantId: '9',
      },
    })
    expect(bridgeMocks.notifyActiveScopeChanged).toHaveBeenCalledTimes(1)
    expect(bridgeMocks.message.success).toHaveBeenCalledTimes(1)
  })
})
