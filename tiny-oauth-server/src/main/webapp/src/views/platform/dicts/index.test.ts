import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  getPlatformDictTypeList: vi.fn(),
  getPlatformVisibleDictTypes: vi.fn(),
  getPlatformDictOverrides: vi.fn(),
  getPlatformDictOverrideDetails: vi.fn(),
}))

const routeState = vi.hoisted(() => ({
  path: '/platform/dicts',
  query: {
    activeTenantId: '9',
    targetTenantId: '12',
  } as Record<string, unknown>,
}))

const routerMocks = vi.hoisted(() => ({
  replace: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => routerMocks,
}))

vi.mock('@/api/dict', () => ({
  getPlatformDictTypeList: apiMocks.getPlatformDictTypeList,
  getPlatformVisibleDictTypes: apiMocks.getPlatformVisibleDictTypes,
  getPlatformDictOverrides: apiMocks.getPlatformDictOverrides,
  getPlatformDictOverrideDetails: apiMocks.getPlatformDictOverrideDetails,
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: vi.fn(),
    warning: vi.fn(),
  },
}))

import PlatformDictPage from '@/views/platform/dicts/index.vue'

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

const TabsStub = defineComponent({
  props: ['activeKey'],
  emits: ['change'],
  template:
    '<div class="tabs-stub" :data-active-key="activeKey"><button class="tab-change" @click="$emit(\'change\', \'item\')">切换字典项</button><slot /></div>',
})

const dictItemSetDictTypeIdMock = vi.fn()
const DictTypeStub = defineComponent({
  emits: ['view-items'],
  template: '<button class="view-items" @click="$emit(\'view-items\', 10)">查看字典项</button>',
})
const DictItemStub = defineComponent({
  methods: {
    setDictTypeId(dictTypeId: number) {
      dictItemSetDictTypeIdMock(dictTypeId)
    },
  },
  template: '<div>DictItemStub</div>',
})

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('platform/dicts/index.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.path = '/platform/dicts'
    routeState.query = {
      activeTenantId: '9',
      targetTenantId: '12',
    }
    apiMocks.getPlatformDictTypeList.mockResolvedValue({
      content: [{ id: 10, dictCode: 'ENABLE_STATUS', dictName: '启用状态' }],
      totalElements: 1,
      totalPages: 1,
      pageNumber: 0,
      pageSize: 10,
    })
    apiMocks.getPlatformVisibleDictTypes.mockResolvedValue([
      { id: 10, dictCode: 'ENABLE_STATUS', dictName: '启用状态' },
    ])
    apiMocks.getPlatformDictOverrides.mockResolvedValue([
      {
        tenantId: 7,
        tenantCode: 't-7',
        tenantName: '租户7',
        baselineCount: 2,
        overriddenCount: 1,
        inheritedCount: 1,
        orphanOverlayCount: 0,
      },
    ])
    apiMocks.getPlatformDictOverrideDetails.mockResolvedValue([
      {
        value: 'ENABLED',
        status: 'OVERRIDDEN',
        baselineLabel: '启用',
        overlayLabel: '可用',
        effectiveLabel: '可用',
        labelChanged: true,
      },
    ])
  })

  it('loads type options on mount and can load override summary/detail', async () => {
    const wrapper = mount(PlatformDictPage, {
      global: {
        stubs: {
          DictType: PassThrough,
          DictItem: PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': defineComponent({
            emits: ['click'],
            template: '<button @click="$emit(\'click\', $event)"><slot /></button>',
          }),
          'a-alert': PassThrough,
          'a-table': PassThrough,
          'a-divider': PassThrough,
          'a-tag': PassThrough,
        },
      },
    })
    await flushPromises()

    expect(apiMocks.getPlatformVisibleDictTypes).toHaveBeenCalledTimes(1)

    await (wrapper.vm as any).loadOverrideSummary()
    await flushPromises()
    expect(apiMocks.getPlatformDictOverrides).toHaveBeenCalledWith(10)

    await (wrapper.vm as any).selectTenant({
      tenantId: 7,
      tenantCode: 't-7',
      tenantName: '租户7',
    })
    await flushPromises()
    expect(apiMocks.getPlatformDictOverrideDetails).toHaveBeenCalledWith(10, 7)
  })

  it('resolves active tab from child route on direct open', async () => {
    routeState.path = '/platform/dicts/overrides'
    routeState.query = {}

    const wrapper = mount(PlatformDictPage, {
      global: {
        stubs: {
          DictType: PassThrough,
          DictItem: PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-alert': PassThrough,
          'a-table': PassThrough,
          'a-divider': PassThrough,
          'a-tag': PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.find('.tabs-stub').attributes('data-active-key')).toBe('overrides')
  })

  it('writes tab changes to child route and strips stale tenant query state', async () => {
    const wrapper = mount(PlatformDictPage, {
      global: {
        stubs: {
          DictType: PassThrough,
          DictItem: PassThrough,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-alert': PassThrough,
          'a-table': PassThrough,
          'a-divider': PassThrough,
          'a-tag': PassThrough,
        },
      },
    })

    await wrapper.find('.tab-change').trigger('click')

    expect(routerMocks.replace).toHaveBeenCalledWith({
      path: '/platform/dicts/item',
      query: {},
    })
  })

  it('switches to item tab before applying the selected dict type', async () => {
    const wrapper = mount(PlatformDictPage, {
      global: {
        stubs: {
          DictType: DictTypeStub,
          DictItem: DictItemStub,
          'a-tabs': TabsStub,
          'a-tab-pane': PassThrough,
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-alert': PassThrough,
          'a-table': PassThrough,
          'a-divider': PassThrough,
          'a-tag': PassThrough,
        },
      },
    })

    await wrapper.find('.view-items').trigger('click')
    await flushPromises()

    expect(routerMocks.replace).toHaveBeenCalledWith({
      path: '/platform/dicts/item',
      query: {},
    })
    expect(dictItemSetDictTypeIdMock).toHaveBeenCalledWith(10)
  })
})
