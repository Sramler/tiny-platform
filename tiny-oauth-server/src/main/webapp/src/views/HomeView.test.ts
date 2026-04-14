import { mount } from '@vue/test-utils'
import { computed, defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  getIdempotentMetrics: vi.fn(),
  getIdempotentTopKeys: vi.fn(),
  routerPush: vi.fn(),
  messageError: vi.fn(),
  authUser: { value: null as { access_token?: string | null } | null },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mocks.routerPush,
  }),
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: mocks.authUser,
    isAuthenticated: computed(() => !!mocks.authUser.value),
  }),
}))

vi.mock('@/api/idempotent', () => ({
  getIdempotentMetrics: mocks.getIdempotentMetrics,
  getIdempotentTopKeys: mocks.getIdempotentTopKeys,
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: mocks.messageError,
  },
}))

import HomeView from '@/views/HomeView.vue'

const PassThrough = defineComponent({
  template: '<div><slot /><slot name="title" /><slot name="icon" /></div>',
})

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\', $event)"><slot /><slot name="icon" /></button>',
})

const StatisticStub = defineComponent({
  props: ['title', 'value'],
  template: '<div>{{ title }} {{ value }}</div>',
})

function createToken(authorities: string[]) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities })).toString('base64url')
  return `${header}.${payload}.signature`
}

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

function mountView() {
  return mount(HomeView, {
    global: {
      stubs: {
        'a-space': PassThrough,
        'a-button': ButtonStub,
        'a-row': PassThrough,
        'a-col': PassThrough,
        'a-card': PassThrough,
        'a-statistic': StatisticStub,
        'a-tag': PassThrough,
        'a-list': PassThrough,
        'a-list-item': PassThrough,
        ReloadOutlined: PassThrough,
      },
    },
  })
}

describe('HomeView.vue', () => {
  beforeEach(() => {
    mocks.getIdempotentMetrics.mockReset()
    mocks.getIdempotentTopKeys.mockReset()
    mocks.routerPush.mockReset()
    mocks.messageError.mockReset()
    mocks.authUser.value = null
    window.localStorage.clear()
    mocks.getIdempotentMetrics.mockResolvedValue({
      windowMinutes: 60,
      windowStartEpochMillis: 1741431600000,
      windowEndEpochMillis: 1741435200000,
      passCount: 12,
      hitCount: 2,
      successCount: 10,
      failureCount: 0,
      storeErrorCount: 0,
      validationRejectCount: 0,
      rejectCount: 2,
      totalCheckCount: 14,
      conflictRate: 0.14,
      storageErrorRate: 0,
    })
    mocks.getIdempotentTopKeys.mockResolvedValue([{ key: 'POST /sys/users', count: 4 }])
  })

  it('should load overview and show governance entry for platform metrics operators', async () => {
    mocks.authUser.value = {
      access_token: createToken(['idempotent:ops:view']),
    }

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(mocks.getIdempotentMetrics).toHaveBeenCalledTimes(1)
    expect(mocks.getIdempotentTopKeys).toHaveBeenCalledWith(5)
    expect(wrapper.text()).toContain('进入治理页')

    await wrapper.find('button').trigger('click')
    expect(mocks.routerPush).toHaveBeenCalledWith('/ops/idempotent')
  })

  it('should preserve current tenant scope when opening governance page', async () => {
    mocks.authUser.value = {
      access_token: createToken(['idempotent:ops:view']),
    }
    window.localStorage.setItem('app_active_tenant_id', '7')

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    await wrapper.find('button').trigger('click')
    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: '/ops/idempotent',
      query: { activeTenantId: '7' },
    })
  })

  it('should not fetch metrics or show governance entry without authority', async () => {
    mocks.authUser.value = {
      access_token: createToken([]),
    }

    const wrapper = mountView()
    await flushPromises()

    expect(mocks.getIdempotentMetrics).not.toHaveBeenCalled()
    expect(mocks.getIdempotentTopKeys).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('幂等治理指标仅对具备平台级幂等治理权限的用户开放')
    expect(wrapper.text()).not.toContain('进入治理页')
  })
})
