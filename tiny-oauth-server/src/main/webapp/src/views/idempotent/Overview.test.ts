import { mount } from '@vue/test-utils'
import { computed, defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  getIdempotentMetrics: vi.fn(),
  getIdempotentTopKeys: vi.fn(),
  getIdempotentMqMetrics: vi.fn(),
  tenantList: vi.fn(),
  routerPush: vi.fn(),
  routerReplace: vi.fn(),
  messageError: vi.fn(),
  messageWarning: vi.fn(),
  consoleWarn: vi.fn(),
  routeQuery: {} as Record<string, unknown>,
  authUser: { value: null as { access_token?: string | null } | null },
  isPlatformScope: { value: true },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mocks.routerPush,
    replace: mocks.routerReplace,
  }),
  useRoute: () => ({
    query: mocks.routeQuery,
  }),
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: mocks.authUser,
  }),
}))

vi.mock('@/composables/usePlatformScope', () => ({
  usePlatformScope: () => ({
    isPlatformScope: computed(() => mocks.isPlatformScope.value),
  }),
}))

vi.mock('@/api/idempotent', () => ({
  getIdempotentMetrics: mocks.getIdempotentMetrics,
  getIdempotentTopKeys: mocks.getIdempotentTopKeys,
  getIdempotentMqMetrics: mocks.getIdempotentMqMetrics,
}))

vi.mock('@/api/tenant', () => ({
  tenantList: mocks.tenantList,
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: mocks.messageError,
    warning: mocks.messageWarning,
  },
}))

import Overview from '@/views/idempotent/Overview.vue'

vi.spyOn(console, 'warn').mockImplementation(mocks.consoleWarn)

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

const SelectStub = defineComponent({
  props: {
    value: {
      type: [String, Number],
      default: 'all',
    },
    options: {
      type: Array,
      default: () => [],
    },
  },
  emits: ['change'],
  methods: {
    onChange(event: Event) {
      const value = (event.target as HTMLSelectElement).value
      const option = (this.options as Array<{ value: string | number }>).find(
        (item) => String(item.value) === value,
      )
      this.$emit('change', option?.value ?? value)
    },
  },
  template: `
    <select data-testid="idempotent-tenant-filter" :value="String(value)" @change="onChange">
      <option v-for="option in options" :key="String(option.value)" :value="String(option.value)">
        {{ option.label }}
      </option>
    </select>
  `,
})

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

function createToken(authorities: string[], activeScopeType: 'PLATFORM' | 'TENANT' = 'PLATFORM') {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities, activeScopeType })).toString('base64url')
  return `${header}.${payload}.signature`
}

function mountView() {
  return mount(Overview, {
    global: {
      stubs: {
        'a-space': PassThrough,
        'a-button': ButtonStub,
        'a-row': PassThrough,
        'a-col': PassThrough,
        'a-card': PassThrough,
        'a-statistic': StatisticStub,
        'a-tag': PassThrough,
        'a-progress': PassThrough,
        'a-alert': PassThrough,
        'a-table': PassThrough,
        'a-select': SelectStub,
        ArrowLeftOutlined: PassThrough,
        ReloadOutlined: PassThrough,
      },
    },
  })
}

describe('idempotent Overview.vue', () => {
  beforeEach(() => {
    mocks.getIdempotentMetrics.mockReset()
    mocks.getIdempotentTopKeys.mockReset()
    mocks.getIdempotentMqMetrics.mockReset()
    mocks.tenantList.mockReset()
    mocks.routerPush.mockReset()
    mocks.routerReplace.mockReset()
    mocks.messageError.mockReset()
    mocks.messageWarning.mockReset()
    mocks.consoleWarn.mockReset()
    mocks.authUser.value = {
      access_token: createToken(['idempotent:ops:view']),
    }
    mocks.isPlatformScope.value = true
    Object.keys(mocks.routeQuery).forEach((key) => {
      delete mocks.routeQuery[key]
    })

    mocks.routerReplace.mockImplementation(
      async ({ query }: { query?: Record<string, unknown> }) => {
        Object.keys(mocks.routeQuery).forEach((key) => {
          delete mocks.routeQuery[key]
        })
        Object.entries(query ?? {}).forEach(([key, value]) => {
          if (value !== undefined) {
            mocks.routeQuery[key] = value
          }
        })
      },
    )

    mocks.getIdempotentMetrics.mockResolvedValue({
      windowMinutes: 60,
      windowStartEpochMillis: 1741431600000,
      windowEndEpochMillis: 1741435200000,
      passCount: 18,
      hitCount: 3,
      successCount: 15,
      failureCount: 0,
      storeErrorCount: 0,
      validationRejectCount: 1,
      rejectCount: 4,
      totalCheckCount: 22,
      conflictRate: 0.17,
      storageErrorRate: 0,
    })
    mocks.getIdempotentTopKeys.mockResolvedValue([{ key: 'POST /sys/users', count: 6 }])
    mocks.getIdempotentMqMetrics.mockResolvedValue({
      windowMinutes: 60,
      windowStartEpochMillis: 1741431600000,
      windowEndEpochMillis: 1741435200000,
      successCount: 11,
      failureCount: 1,
      duplicateRate: 0.09,
    })
    mocks.tenantList.mockResolvedValue({
      content: [
        { id: 1, code: 'default', name: '平台租户' },
        { id: 7, code: 'demo', name: '演示租户' },
      ],
      totalElements: 2,
    })
  })

  it('should load platform view first and pass activeTenantId after filter changes', async () => {
    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(mocks.tenantList).toHaveBeenCalledWith({ page: 0, size: 200 })
    expect(mocks.getIdempotentMetrics).toHaveBeenCalledWith(undefined)
    expect(mocks.getIdempotentTopKeys).toHaveBeenCalledWith(10, undefined)
    expect(mocks.getIdempotentMqMetrics).toHaveBeenCalledWith(undefined)
    expect(wrapper.text()).toContain('平台汇总')

    await wrapper.get('[data-testid="idempotent-tenant-filter"]').setValue('7')
    await flushPromises()
    await flushPromises()

    expect(mocks.routerReplace).toHaveBeenCalledWith({ query: { activeTenantId: '7' } })
    expect(mocks.getIdempotentMetrics).toHaveBeenLastCalledWith(7)
    expect(mocks.getIdempotentTopKeys).toHaveBeenLastCalledWith(10, 7)
    expect(mocks.getIdempotentMqMetrics).toHaveBeenLastCalledWith(7)
    expect(wrapper.text()).toContain('演示租户 (demo)')
  })

  it('should honor activeTenantId from route query on first load', async () => {
    mocks.routeQuery.activeTenantId = '7'

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(mocks.getIdempotentMetrics).toHaveBeenCalledWith(7)
    expect(mocks.getIdempotentTopKeys).toHaveBeenCalledWith(10, 7)
    expect(mocks.getIdempotentMqMetrics).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('演示租户 (demo)')
  })

  it('should keep overview available when tenant list loading fails', async () => {
    mocks.tenantList.mockRejectedValue(new Error('tenant api down'))

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(mocks.getIdempotentMetrics).toHaveBeenCalledTimes(1)
    expect(mocks.getIdempotentTopKeys).toHaveBeenCalledTimes(1)
    expect(mocks.getIdempotentMqMetrics).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('租户列表加载失败')
    expect(mocks.messageError).not.toHaveBeenCalled()
  })

  it('should preserve activeTenantId when returning home', async () => {
    mocks.routeQuery.activeTenantId = '7'

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    await wrapper.findAll('button')[0]?.trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith({
      path: '/',
      query: { activeTenantId: '7' },
    })
  })

  it('should block tenant scoped users before any metrics requests are sent', async () => {
    mocks.isPlatformScope.value = false
    mocks.authUser.value = {
      access_token: createToken(['idempotent:ops:view'], 'TENANT'),
    }

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(mocks.tenantList).not.toHaveBeenCalled()
    expect(mocks.getIdempotentMetrics).not.toHaveBeenCalled()
    expect(mocks.getIdempotentTopKeys).not.toHaveBeenCalled()
    expect(mocks.getIdempotentMqMetrics).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('平台作用域限制')
  })

  it('should block users without idempotent ops permission before requests are sent', async () => {
    mocks.authUser.value = {
      access_token: createToken([]),
    }

    const wrapper = mountView()
    await flushPromises()
    await flushPromises()

    expect(mocks.tenantList).not.toHaveBeenCalled()
    expect(mocks.getIdempotentMetrics).not.toHaveBeenCalled()
    expect(mocks.getIdempotentTopKeys).not.toHaveBeenCalled()
    expect(mocks.getIdempotentMqMetrics).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('缺少 `idempotent:ops:view` 权限')
  })
})
