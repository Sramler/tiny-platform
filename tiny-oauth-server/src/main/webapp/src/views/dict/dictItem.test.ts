import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  getDictItemList: vi.fn(),
  getVisibleDictTypes: vi.fn(),
  getPlatformVisibleDictTypes: vi.fn(),
  getPlatformDictItemList: vi.fn(),
  createPlatformDictItem: vi.fn(),
  updatePlatformDictItem: vi.fn(),
  deletePlatformDictItem: vi.fn(),
}))

const tenantMocks = vi.hoisted(() => ({
  getActiveTenantId: vi.fn(),
}))

const uiMocks = vi.hoisted(() => ({
  messageError: vi.fn(),
}))

vi.mock('@/api/dict', () => ({
  getDictItemList: apiMocks.getDictItemList,
  getVisibleDictTypes: apiMocks.getVisibleDictTypes,
  createDictItem: vi.fn(),
  updateDictItem: vi.fn(),
  deleteDictItem: vi.fn(),
  getPlatformVisibleDictTypes: apiMocks.getPlatformVisibleDictTypes,
  getPlatformDictItemList: apiMocks.getPlatformDictItemList,
  createPlatformDictItem: apiMocks.createPlatformDictItem,
  updatePlatformDictItem: apiMocks.updatePlatformDictItem,
  deletePlatformDictItem: apiMocks.deletePlatformDictItem,
}))

vi.mock('@/utils/tenant', () => ({
  getActiveTenantId: tenantMocks.getActiveTenantId,
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('ant-design-vue', () => ({
  message: { error: uiMocks.messageError },
  Modal: { confirm: vi.fn() },
}))

import { ACTIVE_SCOPE_CHANGED_EVENT } from '@/utils/activeScopeEvents'
import DictItem from '@/views/dict/dictItem.vue'

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

const TableStub = defineComponent({
  props: {
    columns: { type: Array, default: () => [] },
    dataSource: { type: Array, default: () => [] },
  },
  setup(props) {
    return () =>
      h(
        'div',
        { class: 'dict-item-table' },
        (props.dataSource as { id?: number; value?: string; label?: string }[]).map((r) =>
          h('div', { class: 'dict-item-row', 'data-value': r.value }, [
            h('span', { class: 'item-value' }, String(r.value ?? '')),
            h('span', { class: 'item-label' }, String(r.label ?? '')),
          ]),
        ),
      )
  },
})

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('dictItem.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    tenantMocks.getActiveTenantId.mockReturnValue('7')
    apiMocks.getVisibleDictTypes.mockResolvedValue([
      { id: 10, dictCode: 'STATUS', dictName: '状态' },
    ])
    apiMocks.getDictItemList.mockResolvedValue({
      content: [
        { id: 1, dictTypeId: 10, value: '1', label: '启用' },
        { id: 2, dictTypeId: 10, value: '0', label: '禁用' },
      ],
      totalElements: 2,
      totalPages: 1,
      pageNumber: 0,
      pageSize: 10,
    })
  })

  function mountView() {
    return mount(DictItem, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': defineComponent({
            emits: ['click'],
            template: '<button @click="$emit(\'click\', $event)"><slot /></button>',
          }),
          'a-tooltip': PassThrough,
          'a-tag': PassThrough,
          'a-drawer': defineComponent({
            props: ['open'],
            template: '<div v-if="open"><slot /></div>',
          }),
          'a-alert': PassThrough,
          'a-pagination': PassThrough,
          'a-textarea': PassThrough,
          'a-input-number': PassThrough,
          'a-switch': PassThrough,
          'a-table': TableStub,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          EditOutlined: PassThrough,
          DeleteOutlined: PassThrough,
          EyeOutlined: PassThrough,
        },
      },
    })
  }

  it('should load dict items and types on mount and display in table', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.getDictItemList).toHaveBeenCalledTimes(1)
    expect(apiMocks.getVisibleDictTypes).toHaveBeenCalledTimes(1)
    const rows = wrapper.findAll('.dict-item-row')
    expect(rows).toHaveLength(2)
    expect(wrapper.text()).toContain('1')
    expect(wrapper.text()).toContain('0')
    expect(wrapper.text()).toContain('启用')
    expect(wrapper.text()).toContain('禁用')
  })

  it('should show error when tenant is not selected', async () => {
    tenantMocks.getActiveTenantId.mockReturnValue(null)

    mountView()
    await flushPromises()

    expect(uiMocks.messageError).toHaveBeenCalledWith('请先确定当前活动租户')
  })

  it('should show error message when load fails', async () => {
    apiMocks.getDictItemList.mockRejectedValue(new Error('load failed'))

    mountView()
    await flushPromises()

    expect(uiMocks.messageError).toHaveBeenCalledWith('加载字典项数据失败: load failed')
  })

  it('should display table title', () => {
    const wrapper = mountView()
    expect(wrapper.text()).toContain('字典项管理')
  })

  it('should refetch visible types and items when active scope changes with tenant', async () => {
    const wrapper = mountView()
    await flushPromises()
    const itemCalls = apiMocks.getDictItemList.mock.calls.length
    const typeCalls = apiMocks.getVisibleDictTypes.mock.calls.length
    expect(itemCalls).toBeGreaterThanOrEqual(1)
    window.dispatchEvent(new CustomEvent(ACTIVE_SCOPE_CHANGED_EVENT))
    await flushPromises()
    expect(apiMocks.getDictItemList.mock.calls.length).toBeGreaterThan(itemCalls)
    expect(apiMocks.getVisibleDictTypes.mock.calls.length).toBeGreaterThan(typeCalls)
    wrapper.unmount()
  })

  it('should not refetch on active scope change without active tenant', async () => {
    tenantMocks.getActiveTenantId.mockReturnValue(null)
    const wrapper = mountView()
    await flushPromises()
    const itemCalls = apiMocks.getDictItemList.mock.calls.length
    window.dispatchEvent(new CustomEvent(ACTIVE_SCOPE_CHANGED_EVENT))
    await flushPromises()
    expect(apiMocks.getDictItemList.mock.calls.length).toBe(itemCalls)
    wrapper.unmount()
  })
})
