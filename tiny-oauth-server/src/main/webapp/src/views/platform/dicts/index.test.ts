import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  getPlatformDictTypeList: vi.fn(),
  getPlatformDictOverrides: vi.fn(),
  getPlatformDictOverrideDetails: vi.fn(),
}))

vi.mock('@/api/dict', () => ({
  getPlatformDictTypeList: apiMocks.getPlatformDictTypeList,
  getPlatformDictOverrides: apiMocks.getPlatformDictOverrides,
  getPlatformDictOverrideDetails: apiMocks.getPlatformDictOverrideDetails,
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: vi.fn(),
  },
}))

import PlatformDictPage from '@/views/platform/dicts/index.vue'

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('platform/dicts/index.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.getPlatformDictTypeList.mockResolvedValue({
      content: [{ id: 10, dictCode: 'ENABLE_STATUS', dictName: '启用状态' }],
      totalElements: 1,
      totalPages: 1,
      pageNumber: 0,
      pageSize: 10,
    })
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
          'a-tabs': PassThrough,
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

    expect(apiMocks.getPlatformDictTypeList).toHaveBeenCalledTimes(1)

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
})
