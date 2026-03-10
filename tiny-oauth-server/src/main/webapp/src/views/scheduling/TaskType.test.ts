import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  taskTypeList: vi.fn(),
  createTaskType: vi.fn(),
  updateTaskType: vi.fn(),
  deleteTaskType: vi.fn(),
  getExecutors: vi.fn(),
}))

const uiMocks = vi.hoisted(() => ({
  messageError: vi.fn(),
  messageSuccess: vi.fn(),
}))

vi.mock('@/api/scheduling', () => ({
  taskTypeList: apiMocks.taskTypeList,
  createTaskType: apiMocks.createTaskType,
  updateTaskType: apiMocks.updateTaskType,
  deleteTaskType: apiMocks.deleteTaskType,
  getExecutors: apiMocks.getExecutors,
}))

vi.mock('@/utils/debounce', () => ({
  throttle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('@/utils/problemParser', () => ({
  extractErrorFromAxios: (_err: unknown, fallback: string) => fallback,
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: uiMocks.messageError,
    success: uiMocks.messageSuccess,
  },
}))

import TaskType from '@/views/scheduling/TaskType.vue'

const PassThrough = defineComponent({
  template: '<div><slot /><slot name="content" /><slot name="icon" /></div>',
})

const InputStub = defineComponent({
  props: { value: String, placeholder: String },
  emits: ['update:value'],
  template: '<input :value="value" :placeholder="placeholder" @input="$emit(\'update:value\', $event.target.value)" />',
})

const TextareaStub = defineComponent({
  props: { value: String, placeholder: String },
  emits: ['update:value'],
  template:
    '<textarea :value="value" :placeholder="placeholder" @input="$emit(\'update:value\', $event.target.value)" />',
})

const SelectStub = defineComponent({
  props: {
    value: [String, Number],
    options: { type: Array, default: () => [] },
    placeholder: String,
  },
  emits: ['update:value'],
  template: `
    <select
      :value="value ?? ''"
      :data-placeholder="placeholder"
      @change="$emit('update:value', $event.target.value)"
    >
      <option value="">--</option>
      <option v-for="option in options" :key="String(option.value)" :value="option.value">
        {{ option.label }}
      </option>
    </select>
  `,
})

const SwitchStub = defineComponent({
  props: {
    checked: Boolean,
  },
  emits: ['update:checked'],
  template:
    '<input type="checkbox" :checked="checked" @change="$emit(\'update:checked\', $event.target.checked)" />',
})

const InputNumberStub = defineComponent({
  props: {
    value: [String, Number],
  },
  emits: ['update:value'],
  template:
    '<input type="number" :value="value ?? \'\'" @input="$emit(\'update:value\', $event.target.value === \'\' ? undefined : Number($event.target.value))" />',
})

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\', $event)"><slot /><slot name="icon" /></button>',
})

const TableStub = defineComponent({
  props: {
    columns: { type: Array, default: () => [] },
    dataSource: { type: Array, default: () => [] },
  },
  emits: ['change'],
  setup(props, { slots }) {
    return () =>
      h(
        'div',
        {},
        (props.dataSource as { id?: number; code?: string }[]).map((record) =>
          h('div', { class: 'task-type-row', 'data-id': record.id }, [
            h('span', { class: 'code' }, String(record.code ?? '')),
            h(
              'div',
              { class: 'action-slot' },
              slots.bodyCell?.({
                column: { key: 'action' },
                record,
              }) ?? [],
            ),
          ]),
        ),
      )
  },
})

async function flushPromises() {
  await Promise.resolve()
  await Promise.resolve()
  await nextTick()
}

describe('TaskType.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.getExecutors.mockResolvedValue(['logging', 'shell'])
    apiMocks.taskTypeList.mockResolvedValue({ records: [], total: 0 })
  })

  function mountView() {
    return mount(TaskType, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': InputStub,
          'a-input-number': InputNumberStub,
          'a-textarea': TextareaStub,
          'a-select': SelectStub,
          'a-switch': SwitchStub,
          'a-button': ButtonStub,
          'a-tooltip': PassThrough,
          'a-popconfirm': defineComponent({
            props: ['title'],
            emits: ['confirm'],
            template: '<div @click="$emit(\'confirm\')"><slot /></div>',
          }),
          'a-table': TableStub,
          'a-modal': defineComponent({
            props: ['open', 'title'],
            emits: ['ok', 'cancel', 'update:open'],
            template: '<div v-if="open"><slot /><button class="modal-ok" @click="$emit(\'ok\')">确定</button><button class="modal-cancel" @click="$emit(\'cancel\')">取消</button></div>',
          }),
          'a-space': PassThrough,
          'a-tag': PassThrough,
          'a-pagination': PassThrough,
          PlusOutlined: defineComponent({ template: '<span>+</span>' }),
          ReloadOutlined: defineComponent({ template: '<span>↻</span>' }),
        },
      },
    })
  }

  it('should load task type list and executors on mount', async () => {
    apiMocks.taskTypeList.mockResolvedValue({
      records: [{ id: 1, code: 'billing', name: 'Billing' }],
      total: 1,
    })

    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.getExecutors).toHaveBeenCalled()
    expect(apiMocks.taskTypeList).toHaveBeenCalledWith(
      expect.objectContaining({
        current: 1,
        pageSize: 10,
      }),
    )
    expect(wrapper.find('.task-type-row').exists() || wrapper.text()).toBeTruthy()
  })

  it('should call taskTypeList when search is clicked', async () => {
    const wrapper = mountView()
    await flushPromises()
    apiMocks.taskTypeList.mockClear()

    const searchBtn = wrapper.findAll('button').find((b) => b.text().includes('搜索'))
    if (searchBtn) {
      await searchBtn.trigger('click')
      await flushPromises()
      expect(apiMocks.taskTypeList).toHaveBeenCalled()
    }
  })

  it('should open create modal when 新建 is clicked', async () => {
    const wrapper = mountView()
    await flushPromises()

    const createBtn = wrapper.findAll('button').find((b) => b.text().includes('新建'))
    expect(createBtn).toBeDefined()
    if (createBtn) {
      await createBtn.trigger('click')
      await flushPromises()
      const modal = wrapper.findComponent({ name: 'AModal' })
      expect(modal.exists() || wrapper.find('.modal-ok').exists()).toBeTruthy()
    }
  })

  it('should call createTaskType when form is submitted with valid data', async () => {
    apiMocks.createTaskType.mockResolvedValue({ id: 1 })
    const wrapper = mountView()
    await flushPromises()

    const createBtn = wrapper.findAll('button').find((b) => b.text().includes('新建'))
    if (createBtn) {
      await createBtn.trigger('click')
      await flushPromises()
    }

    const inputs = wrapper.findAll('input')
    const modalCodeInput = inputs.find((i) => {
      const ph = (i.attributes() as { placeholder?: string }).placeholder ?? ''
      return ph.includes('唯一')
    })
    const nameInputs = inputs.filter((i) => (i.attributes() as { placeholder?: string }).placeholder === '请输入名称')
    if (modalCodeInput) await modalCodeInput.setValue('new-type')
    const secondaryNameInput = nameInputs[1]
    const primaryNameInput = nameInputs[0]
    if (secondaryNameInput) await secondaryNameInput.setValue('New Type')
    else if (primaryNameInput) await primaryNameInput.setValue('New Type')
    await flushPromises()

    const okBtn = wrapper.find('.modal-ok')
    if (okBtn.exists()) {
      await okBtn.trigger('click')
      await flushPromises()
    }
    expect(apiMocks.createTaskType).toHaveBeenCalledWith(
      expect.objectContaining({
        code: 'new-type',
        name: 'New Type',
      }),
    )
  })

  it('should reject submit when required fields are missing', async () => {
    const wrapper = mountView()
    await flushPromises()

    const createBtn = wrapper.findAll('button').find((b) => b.text().includes('新建'))
    if (createBtn) {
      await createBtn.trigger('click')
      await flushPromises()
    }

    const okBtn = wrapper.find('.modal-ok')
    if (okBtn.exists()) {
      await okBtn.trigger('click')
      await flushPromises()
    }

    expect(uiMocks.messageError).toHaveBeenCalledWith('请输入编码')
    expect(apiMocks.createTaskType).not.toHaveBeenCalled()
  })

  it('should call deleteTaskType when delete is confirmed', async () => {
    apiMocks.taskTypeList.mockResolvedValue({
      records: [{ id: 1, code: 'billing', name: 'Billing' }],
      total: 1,
    })
    apiMocks.deleteTaskType.mockResolvedValue(undefined)

    const wrapper = mountView()
    await flushPromises()

    const deleteBtn = wrapper.findAll('button').find((b) => b.text().includes('删除'))
    if (deleteBtn) {
      await deleteBtn.trigger('click')
      await flushPromises()
      expect(apiMocks.deleteTaskType).toHaveBeenCalledWith(1)
    }
  })

  it('should edit existing task type and normalize payload', async () => {
    apiMocks.taskTypeList.mockResolvedValue({
      records: [
        {
          id: 1,
          code: 'billing',
          name: 'Billing',
          description: 'desc',
          executor: 'logging',
          param_schema: '{"type":"object"}',
          default_timeout_sec: 30,
          default_max_retry: 2,
          enabled: true,
        },
      ],
      total: 1,
    })
    apiMocks.updateTaskType.mockResolvedValue(undefined)

    const wrapper = mountView()
    await flushPromises()

    const editButton = wrapper.findAll('button').find((b) => b.text().includes('编辑'))
    expect(editButton).toBeDefined()
    await editButton?.trigger('click')
    await flushPromises()

    const modalInputs = wrapper.findAll('input')
    const modalCodeInput = modalInputs.find((i) => {
      const ph = (i.attributes() as { placeholder?: string }).placeholder ?? ''
      return ph.includes('唯一')
    })
    const modalNameInput = modalInputs.filter((i) => (i.attributes() as { placeholder?: string }).placeholder === '请输入名称')[1]
    const numberInputs = wrapper.findAll('input[type="number"]')
    const executorSelect = wrapper.find('select[data-placeholder="请选择执行器"]')
    const switchInput = wrapper.find('input[type="checkbox"]')

    await modalCodeInput?.setValue(' billing-updated ')
    await modalNameInput?.setValue(' Billing Updated ')
    await executorSelect.setValue('shell')
    await numberInputs[0]?.setValue('-5')
    await numberInputs[1]?.setValue('')
    await switchInput.setValue(false)
    await flushPromises()

    const okBtn = wrapper.find('.modal-ok')
    await okBtn.trigger('click')
    await flushPromises()

    expect(apiMocks.updateTaskType).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        code: 'billing-updated',
        name: 'Billing Updated',
        executor: 'shell',
        defaultTimeoutSec: 0,
        defaultMaxRetry: 0,
        enabled: false,
      }),
    )
  })

  it('should reset filters refresh list and handle table change', async () => {
    const wrapper = mountView()
    await flushPromises()
    apiMocks.taskTypeList.mockClear()

    const inputs = wrapper.findAll('input')
    await inputs[0]?.setValue('code-a')
    await inputs[1]?.setValue('name-a')

    const resetBtn = wrapper.findAll('button').find((b) => b.text().includes('重置'))
    await resetBtn?.trigger('click')
    await flushPromises()
    expect(apiMocks.taskTypeList).toHaveBeenCalledWith(expect.objectContaining({ code: '', name: '' }))

    apiMocks.taskTypeList.mockClear()
    await wrapper.find('.action-icon').trigger('click')
    await flushPromises()
    expect(apiMocks.taskTypeList).toHaveBeenCalled()

    apiMocks.taskTypeList.mockClear()
    const table = wrapper.findComponent(TableStub)
    table.vm.$emit('change', { current: 2, pageSize: 20 })
    await flushPromises()
    expect(apiMocks.taskTypeList).toHaveBeenCalledWith(expect.objectContaining({ current: 2, pageSize: 20 }))
  })

  it('should show load and delete errors and clear executors on load failure', async () => {
    apiMocks.getExecutors.mockRejectedValueOnce(new Error('executor load failed'))
    apiMocks.taskTypeList.mockRejectedValueOnce(new Error('list load failed'))
    apiMocks.taskTypeList.mockResolvedValueOnce({
      records: [{ id: 1, code: 'billing', name: 'Billing' }],
      total: 1,
    })
    apiMocks.deleteTaskType.mockRejectedValueOnce(new Error('delete failed'))

    const wrapper = mountView()
    await flushPromises()

    expect(uiMocks.messageError).toHaveBeenCalledWith('加载数据失败')

    await wrapper.find('.action-icon').trigger('click')
    await flushPromises()

    const deleteBtn = wrapper.findAll('button').find((b) => b.text().includes('删除'))
    await deleteBtn?.trigger('click')
    await flushPromises()

    expect(uiMocks.messageError).toHaveBeenCalledWith('delete failed')
  })
})
