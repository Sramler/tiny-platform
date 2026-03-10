import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  demoExportUsageList: vi.fn(),
}))

vi.mock('@/api/demoExportUsage', () => ({
  demoExportUsageList: apiMocks.demoExportUsageList,
  getDemoExportUsage: vi.fn(),
  createDemoExportUsage: vi.fn(),
  updateDemoExportUsage: vi.fn(),
  deleteDemoExportUsage: vi.fn(),
  generateDemoExportUsage: vi.fn(),
  clearDemoExportUsage: vi.fn(),
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('@/utils/tenant', async (importOriginal) => {
  const actual = await importOriginal<any>()
  return {
    ...actual,
    getTenantId: () => '1',
    getTenantCode: () => 't1',
  }
})

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import TestData from '@/views/export/testData.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('export testData.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.demoExportUsageList.mockResolvedValue({ records: [], total: 0 })
  })

  it('should render title and call list on mount', async () => {
    const wrapper = mount(TestData, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-tooltip': PassThrough,
          'a-switch': PassThrough,
          'a-dropdown': PassThrough,
          'a-menu': PassThrough,
          'a-menu-item': PassThrough,
          'a-table': defineComponent({ props: ['dataSource'], template: '<div class="table" />' }),
          'a-drawer': PassThrough,
          'a-modal': PassThrough,
          'a-checkbox': PassThrough,
          'a-popover': PassThrough,
          VueDraggable: PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          PoweroffOutlined: PassThrough,
          CopyOutlined: PassThrough,
          ColumnHeightOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('导出测试数据')
    expect(apiMocks.demoExportUsageList).toHaveBeenCalled()
  })
})

