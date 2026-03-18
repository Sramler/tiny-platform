import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  getDictTypeList: vi.fn(),
}))

const tenantMocks = vi.hoisted(() => ({
  getActiveTenantId: vi.fn(),
}))

const uiMocks = vi.hoisted(() => ({
  messageError: vi.fn(),
}))

vi.mock('@/api/dict', () => ({
  getDictTypeList: apiMocks.getDictTypeList,
  createDictType: vi.fn(),
  updateDictType: vi.fn(),
  deleteDictType: vi.fn(),
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

import DictType from '@/views/dict/dictType.vue'

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
        { class: 'dict-type-table' },
        (props.dataSource as { id?: number; dictCode?: string; dictName?: string }[]).map((r) =>
          h('div', { class: 'dict-type-row', 'data-dict-code': r.dictCode }, [
            h('span', { class: 'dict-code' }, String(r.dictCode ?? '')),
            h('span', { class: 'dict-name' }, String(r.dictName ?? '')),
          ]),
        ),
      )
  },
})

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('dictType.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    tenantMocks.getActiveTenantId.mockReturnValue('7')
    apiMocks.getDictTypeList.mockResolvedValue({
      content: [
        { id: 1, dictCode: 'STATUS', dictName: '状态', enabled: true },
        { id: 2, dictCode: 'COLOR', dictName: '颜色', enabled: true },
      ],
      totalElements: 2,
      totalPages: 1,
      pageNumber: 0,
      pageSize: 10,
    })
  })

  function mountView() {
    return mount(DictType, {
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

  it('should load dict types on mount and display in table', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.getDictTypeList).toHaveBeenCalledTimes(1)
    const rows = wrapper.findAll('.dict-type-row')
    expect(rows).toHaveLength(2)
    expect(wrapper.text()).toContain('STATUS')
    expect(wrapper.text()).toContain('COLOR')
    expect(wrapper.text()).toContain('状态')
    expect(wrapper.text()).toContain('颜色')
  })

  it('should show error message when load fails', async () => {
    apiMocks.getDictTypeList.mockRejectedValue(new Error('load failed'))

    mountView()
    await flushPromises()

    expect(uiMocks.messageError).toHaveBeenCalledWith('加载字典类型数据失败: load failed')
  })

  it('should display table title', () => {
    const wrapper = mountView()
    expect(wrapper.text()).toContain('字典类型管理')
  })
})
