import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  taskList: vi.fn(),
  createTask: vi.fn(),
  updateTask: vi.fn(),
  deleteTask: vi.fn(),
  getTask: vi.fn(),
  taskTypeList: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  authUser: { value: null as { access_token?: string | null } | null },
}))

const uiMocks = vi.hoisted(() => ({
  messageError: vi.fn(),
  messageSuccess: vi.fn(),
  messageWarning: vi.fn(),
}))

vi.mock('@/api/scheduling', () => ({
  taskList: apiMocks.taskList,
  createTask: apiMocks.createTask,
  updateTask: apiMocks.updateTask,
  deleteTask: apiMocks.deleteTask,
  getTask: apiMocks.getTask,
  taskTypeList: apiMocks.taskTypeList,
}))

vi.mock('@/utils/debounce', () => ({
  throttle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: authMocks.authUser,
  }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: uiMocks.messageError,
    success: uiMocks.messageSuccess,
    warning: uiMocks.messageWarning,
  },
}))

import Task from '@/views/scheduling/Task.vue'

const PassThrough = defineComponent({
  template: '<div><slot /><slot name="content" /><slot name="icon" /></div>',
})

const InputStub = defineComponent({
  props: { value: [String, Number], placeholder: String },
  emits: ['update:value'],
  template:
    '<input :value="value" :placeholder="placeholder" @input="$emit(\'update:value\', $event.target.value)" />',
})

const SelectStub = defineComponent({
  props: { value: [String, Number], placeholder: String },
  emits: ['update:value', 'change'],
  template:
    '<input :value="value" :placeholder="placeholder" @input="$emit(\'update:value\', Number($event.target.value) || $event.target.value); $emit(\'change\', Number($event.target.value) || $event.target.value)" />',
})

const ButtonStub = defineComponent({
  props: {
    disabled: Boolean,
    loading: Boolean,
  },
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\', $event)"><slot /><slot name="icon" /></button>',
})

const TableStub = defineComponent({
  props: {
    columns: { type: Array, default: () => [] },
    dataSource: { type: Array, default: () => [] },
  },
  setup(props, { slots }) {
    return () =>
      h(
        'div',
        {},
        (props.dataSource as Record<string, unknown>[]).map((record) =>
          h(
            'div',
            { class: 'task-row', 'data-id': String(record.id ?? '') },
            (props.columns as { key?: string; dataIndex?: string }[]).map((column) =>
              h(
                'div',
                { class: `col-${column.key ?? column.dataIndex ?? 'unknown'}` },
                slots.bodyCell?.({ column, record }) ?? String(record[column.dataIndex ?? column.key ?? ''] ?? ''),
              ),
            ),
          ),
        ),
      )
  },
})

const FormStub = defineComponent({
  setup(_, { slots, expose }) {
    expose({
      validate: vi.fn().mockResolvedValue(undefined),
    })
    return () => h('div', slots.default?.())
  },
})

async function flushPromises() {
  for (let index = 0; index < 6; index += 1) {
    await Promise.resolve()
  }
  await nextTick()
}

function mountView() {
  return mount(Task, {
    global: {
      stubs: {
        'a-form': FormStub,
        'a-form-item': PassThrough,
        'a-input': InputStub,
        'a-input-number': InputStub,
        'a-textarea': InputStub,
        'a-select': SelectStub,
        'a-select-option': PassThrough,
        'a-switch': PassThrough,
        'a-button': ButtonStub,
        'a-tooltip': PassThrough,
        'a-popconfirm': defineComponent({
          props: ['title'],
          emits: ['confirm'],
          template: '<div @click="$emit(\'confirm\')"><slot /></div>',
        }),
        'a-table': TableStub,
        'a-modal': defineComponent({
          props: ['open', 'title', 'footer'],
          emits: ['ok', 'cancel', 'update:open'],
          template:
            '<div v-if="open"><slot /><button class="modal-ok" @click="$emit(\'ok\')">确定</button><button class="modal-cancel" @click="$emit(\'cancel\')">取消</button></div>',
        }),
        'a-space': PassThrough,
        'a-tag': PassThrough,
        'a-descriptions': PassThrough,
        'a-descriptions-item': PassThrough,
        PlusOutlined: defineComponent({ template: '<span>+</span>' }),
        ReloadOutlined: defineComponent({ template: '<span>↻</span>' }),
      },
    },
  })
}

function findRow(wrapper: ReturnType<typeof mountView>, id: number) {
  return wrapper.get(`.task-row[data-id="${id}"]`)
}

function findButtonByText(wrapper: ReturnType<typeof mountView> | ReturnType<typeof findRow>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function createToken(authorities: string[]) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities })).toString('base64url')
  return `${header}.${payload}.signature`
}

describe('Task.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authMocks.authUser.value = {
      access_token: createToken(['scheduling:console:config']),
    }
    apiMocks.taskTypeList.mockResolvedValue({
      records: [{ id: 2, name: '统计任务', paramSchema: '' }],
      total: 1,
    })
    apiMocks.taskList.mockResolvedValue({
      records: [
        {
          id: 1,
          code: 'daily-stat',
          name: 'Daily Stat',
          typeId: 2,
          timeoutSec: 60,
          maxRetry: 1,
          concurrencyPolicy: 'PARALLEL',
          enabled: true,
        },
      ],
      total: 1,
    })
    apiMocks.getTask.mockResolvedValue({
      id: 1,
      code: 'daily-stat',
      name: 'Daily Stat',
      typeId: 2,
      params: '{"day":"2026-03-10"}',
      retryPolicy: '{"delaySec":60}',
      enabled: true,
    })
    apiMocks.createTask.mockResolvedValue({ id: 2 })
    apiMocks.updateTask.mockResolvedValue({ id: 1 })
    apiMocks.deleteTask.mockResolvedValue(undefined)
  })

  it('should load task list and task types on mount', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.taskTypeList).toHaveBeenCalledWith({ current: 1, pageSize: 1000 })
    expect(apiMocks.taskList).toHaveBeenCalledWith(
      expect.objectContaining({
        current: 1,
        pageSize: 10,
      }),
    )
    expect(findRow(wrapper, 1).text()).toContain('统计任务')
    expect(findRow(wrapper, 1).text()).toContain('启用')
  })

  it('should update task with trimmed payload', async () => {
    const wrapper = mountView()
    await flushPromises()

    await findButtonByText(findRow(wrapper, 1), '编辑')?.trigger('click')
    await flushPromises()

    const inputs = wrapper.findAll('input')
    const codeInput = inputs.find((input) => input.attributes('placeholder')?.includes('编码（可选）'))
    const nameInput = inputs.find((input) => input.attributes('placeholder') === '请输入名称')

    await codeInput?.setValue('  updated-task  ')
    await nameInput?.setValue('  Updated Task  ')
    await flushPromises()

    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(apiMocks.updateTask).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        code: 'updated-task',
        typeId: 2,
      }),
    )
  })

  it('should open detail and call delete for row actions', async () => {
    const wrapper = mountView()
    await flushPromises()

    await findButtonByText(findRow(wrapper, 1), '查看')?.trigger('click')
    await flushPromises()
    await findButtonByText(findRow(wrapper, 1), '删除')?.trigger('click')
    await flushPromises()

    expect(apiMocks.getTask).toHaveBeenCalledWith(1)
    expect(apiMocks.deleteTask).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('"day": "2026-03-10"')
  })

  it('should validate task type selection and create task successfully', async () => {
    const wrapper = mountView()
    await flushPromises()

    await findButtonByText(wrapper, '新建')?.trigger('click')
    await flushPromises()

    const inputs = wrapper.findAll('input')
    const nameInput = inputs.filter((input) => input.attributes('placeholder') === '请输入名称')[1]
    await nameInput?.setValue('  New Task  ')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(uiMocks.messageError).toHaveBeenCalledWith('请选择任务类型')
    expect(apiMocks.createTask).not.toHaveBeenCalled()

    const typeInputs = wrapper.findAll('input').filter((input) => input.attributes('placeholder') === '请选择任务类型')
    await typeInputs[typeInputs.length - 1]?.setValue('2')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(apiMocks.createTask).toHaveBeenCalledWith(
      expect.objectContaining({
        typeId: 2,
        name: 'New Task',
        code: '',
        concurrencyPolicy: 'PARALLEL',
      }),
    )
  })

  it('should reset filters refresh data and change table page', async () => {
    const wrapper = mountView()
    await flushPromises()
    apiMocks.taskList.mockClear()

    const inputs = wrapper.findAll('input')
    await inputs[0]?.setValue('code-a')
    await inputs[1]?.setValue('name-a')
    const queryTypeInput = wrapper.findAll('input').find((input) => input.attributes('placeholder') === '请选择任务类型')
    await queryTypeInput?.setValue('2')

    await findButtonByText(wrapper, '重置')?.trigger('click')
    await flushPromises()
    expect(apiMocks.taskList).toHaveBeenCalledWith(expect.objectContaining({ code: '', name: '', typeId: undefined }))

    apiMocks.taskList.mockClear()
    await wrapper.find('.action-icon').trigger('click')
    await flushPromises()
    expect(apiMocks.taskList).toHaveBeenCalled()

    apiMocks.taskList.mockClear()
    const table = wrapper.findComponent(TableStub)
    table.vm.$emit('change', { current: 2, pageSize: 20 })
    await flushPromises()
    expect(apiMocks.taskList).toHaveBeenCalledWith(expect.objectContaining({ current: 2, pageSize: 20 }))
  })

  it('should report view and delete errors from row actions', async () => {
    apiMocks.getTask.mockRejectedValueOnce(new Error('view failed'))
    apiMocks.deleteTask.mockRejectedValueOnce(new Error('delete failed'))

    const wrapper = mountView()
    await flushPromises()

    await findButtonByText(findRow(wrapper, 1), '查看')?.trigger('click')
    await flushPromises()
    await findButtonByText(findRow(wrapper, 1), '删除')?.trigger('click')
    await flushPromises()

    expect(uiMocks.messageError).toHaveBeenCalledWith('view failed')
    expect(uiMocks.messageError).toHaveBeenCalledWith('delete failed')
  })
})
