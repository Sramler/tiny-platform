import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  dagList: vi.fn(),
  createDag: vi.fn(),
  updateDag: vi.fn(),
  deleteDag: vi.fn(),
  triggerDag: vi.fn(),
  stopDag: vi.fn(),
  retryDag: vi.fn(),
}))

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
}))

const uiMocks = vi.hoisted(() => ({
  messageError: vi.fn(),
  messageSuccess: vi.fn(),
  messageWarning: vi.fn(),
}))

vi.mock('@/api/scheduling', () => ({
  dagList: apiMocks.dagList,
  createDag: apiMocks.createDag,
  updateDag: apiMocks.updateDag,
  deleteDag: apiMocks.deleteDag,
  triggerDag: apiMocks.triggerDag,
  stopDag: apiMocks.stopDag,
  retryDag: apiMocks.retryDag,
}))

vi.mock('vue-router', () => ({
  useRouter: () => routerMocks,
}))

vi.mock('@/utils/debounce', () => ({
  throttle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: uiMocks.messageError,
    success: uiMocks.messageSuccess,
    warning: uiMocks.messageWarning,
  },
}))

import Dag from '@/views/scheduling/Dag.vue'

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

const SelectOptionStub = defineComponent({
  props: {
    value: [String, Number],
  },
  template: '<option :value="value"><slot /></option>',
})

const SelectStub = defineComponent({
  props: {
    value: [String, Number],
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
      <slot />
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
        { class: 'table-root' },
        (props.dataSource as Record<string, unknown>[]).map((record) =>
          h(
            'div',
            { class: 'table-row', 'data-id': String(record.id ?? '') },
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

const PopconfirmStub = defineComponent({
  props: {
    disabled: Boolean,
  },
  emits: ['confirm'],
  template: '<div class="popconfirm" @click="!disabled && $emit(\'confirm\')"><slot /></div>',
})

const TooltipStub = defineComponent({
  props: {
    title: String,
  },
  template: '<div class="tooltip"><span v-if="title" class="tooltip-title">{{ title }}</span><slot /></div>',
})

async function flushPromises() {
  for (let index = 0; index < 5; index += 1) {
    await Promise.resolve()
  }
  await nextTick()
}

function mountView() {
  return mount(Dag, {
    global: {
      stubs: {
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-input': InputStub,
        'a-input-group': PassThrough,
        'a-textarea': TextareaStub,
        'a-select': SelectStub,
        'a-select-option': SelectOptionStub,
        'a-switch': SwitchStub,
        'a-button': ButtonStub,
        'a-tooltip': TooltipStub,
        'a-popconfirm': PopconfirmStub,
        'a-table': TableStub,
        'a-modal': defineComponent({
          props: ['open', 'title'],
          emits: ['ok', 'cancel'],
          template:
            '<div v-if="open" class="modal" :data-title="title"><slot /><button class="modal-ok" @click="$emit(\'ok\')">确定</button><button class="modal-cancel" @click="$emit(\'cancel\')">取消</button></div>',
        }),
        'a-space': PassThrough,
        'a-tag': PassThrough,
        CronDesigner: defineComponent({ template: '<div class="cron-designer" />' }),
        PlusOutlined: defineComponent({ template: '<span>+</span>' }),
        ReloadOutlined: defineComponent({ template: '<span>↻</span>' }),
      },
    },
  })
}

function findRow(wrapper: ReturnType<typeof mountView>, id: number) {
  return wrapper.get(`.table-row[data-id="${id}"]`)
}

function findButtonByText(wrapper: ReturnType<typeof mountView> | ReturnType<typeof findRow>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

describe('Dag.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.createDag.mockResolvedValue({ id: 999 })
    apiMocks.updateDag.mockResolvedValue({ id: 999 })
    apiMocks.deleteDag.mockResolvedValue(undefined)
    apiMocks.triggerDag.mockResolvedValue({ id: 1, status: 'RUNNING' })
    apiMocks.stopDag.mockResolvedValue(undefined)
    apiMocks.retryDag.mockResolvedValue(undefined)
    apiMocks.dagList.mockResolvedValue({
      records: [
        { id: 1, code: 'draft-only', name: 'Draft Only', enabled: true, currentVersionId: null, hasRunningRun: false, hasRetryableRun: false },
        { id: 2, code: 'running-dag', name: 'Running DAG', enabled: true, currentVersionId: 12, hasRunningRun: true, hasRetryableRun: false },
        { id: 3, code: 'failed-dag', name: 'Failed DAG', enabled: true, currentVersionId: 13, hasRunningRun: false, hasRetryableRun: true },
      ],
      total: 3,
    })
  })

  it('should load DAG list and render version/runtime state markers', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.dagList).toHaveBeenCalledWith(
      expect.objectContaining({
        current: 1,
        pageSize: 10,
      }),
    )
    expect(findRow(wrapper, 2).text()).toContain('12')
    expect(findRow(wrapper, 2).text()).toContain('运行中')
    expect(findRow(wrapper, 3).text()).toContain('可重试')
  })

  it('should call DAG trigger/stop/retry APIs only for eligible dags', async () => {
    const wrapper = mountView()
    await flushPromises()

    const runningRow = findRow(wrapper, 2)
    const retryableRow = findRow(wrapper, 3)

    await findButtonByText(runningRow, '触发')?.trigger('click')
    await flushPromises()
    await findButtonByText(runningRow, '停止 DAG')?.trigger('click')
    await flushPromises()
    await findButtonByText(retryableRow, '重试最近失败运行')?.trigger('click')
    await flushPromises()

    expect(apiMocks.triggerDag).toHaveBeenCalledWith(2)
    expect(apiMocks.stopDag).toHaveBeenCalledWith(2)
    expect(apiMocks.retryDag).toHaveBeenCalledWith(3)
  })

  it('should disable ineligible DAG actions with explicit reasons', async () => {
    const wrapper = mountView()
    await flushPromises()

    const draftOnlyRow = findRow(wrapper, 1)
    const triggerButton = findButtonByText(draftOnlyRow, '触发')
    const stopButton = findButtonByText(draftOnlyRow, '停止 DAG')
    const retryButton = findButtonByText(draftOnlyRow, '重试最近失败运行')

    expect(triggerButton?.attributes('disabled')).toBeDefined()
    expect(stopButton?.attributes('disabled')).toBeDefined()
    expect(retryButton?.attributes('disabled')).toBeDefined()
    expect(draftOnlyRow.text()).toContain('请先创建并激活版本')
    expect(draftOnlyRow.text()).toContain('当前没有运行中的 Run')
    expect(draftOnlyRow.text()).toContain('当前没有可重试的失败运行')
  })

  it('should navigate to detail and history pages from row actions', async () => {
    const wrapper = mountView()
    await flushPromises()

    const runningRow = findRow(wrapper, 2)
    await findButtonByText(runningRow, '详情')?.trigger('click')
    await findButtonByText(runningRow, '历史')?.trigger('click')

    expect(routerMocks.push).toHaveBeenNthCalledWith(1, {
      path: '/scheduling/dag/detail',
      query: { id: 2 },
    })
    expect(routerMocks.push).toHaveBeenNthCalledWith(2, {
      path: '/scheduling/dag/history',
      query: { dagId: 2 },
    })
  })

  it('should validate create and edit dag payloads', async () => {
    const wrapper = mountView()
    await flushPromises()

    await findButtonByText(wrapper, '新建')?.trigger('click')
    await flushPromises()
    await wrapper.find('.modal[data-title="新建 DAG"] .modal-ok').trigger('click')
    await flushPromises()
    expect(uiMocks.messageError).toHaveBeenCalledWith('请输入名称')

    const createCodeInput = wrapper.find('.modal[data-title="新建 DAG"] input[placeholder="请输入编码（可选）"]')
    const createNameInput = wrapper.find('.modal[data-title="新建 DAG"] input[placeholder="请输入名称"]')
    const createTimezoneSelect = wrapper.find('.modal[data-title="新建 DAG"] select[data-placeholder="可选，默认使用系统时区"]')
    const createSwitches = wrapper.findAll('.modal[data-title="新建 DAG"] input[type="checkbox"]')
    await createCodeInput.setValue('  daily-dag  ')
    await createNameInput.setValue('  Daily DAG  ')
    await createTimezoneSelect.setValue('UTC')
    await createSwitches[0]?.setValue(false)
    await createSwitches[1]?.setValue(false)
    await wrapper.find('.modal[data-title="新建 DAG"] .modal-ok').trigger('click')
    await flushPromises()

    expect(apiMocks.createDag).toHaveBeenCalledWith(
      expect.objectContaining({
        code: 'daily-dag',
        name: 'Daily DAG',
        cronTimezone: 'UTC',
        cronEnabled: false,
        enabled: false,
      }),
    )

    const runningRow = findRow(wrapper, 2)
    await findButtonByText(runningRow, '编辑')?.trigger('click')
    await flushPromises()
    const editCodeInput = wrapper.find('.modal[data-title="编辑 DAG"] input[placeholder="请输入编码（可选）"]')
    const editNameInput = wrapper.find('.modal[data-title="编辑 DAG"] input[placeholder="请输入名称"]')
    await editCodeInput.setValue('running-dag-v2')
    await editNameInput.setValue('Running DAG V2')
    await wrapper.find('.modal[data-title="编辑 DAG"] .modal-ok').trigger('click')
    await flushPromises()

    expect(apiMocks.updateDag).toHaveBeenCalledWith(
      2,
      expect.objectContaining({
        code: 'running-dag-v2',
        name: 'Running DAG V2',
      }),
    )
  })

  it('should reset refresh paginate and delete dag rows', async () => {
    const wrapper = mountView()
    await flushPromises()
    apiMocks.dagList.mockClear()

    const inputs = wrapper.findAll('input')
    await inputs[0]?.setValue('code-a')
    await inputs[1]?.setValue('name-a')

    await findButtonByText(wrapper, '重置')?.trigger('click')
    await flushPromises()
    expect(apiMocks.dagList).toHaveBeenCalledWith(expect.objectContaining({ code: '', name: '' }))

    apiMocks.dagList.mockClear()
    await wrapper.find('.action-icon').trigger('click')
    await flushPromises()
    expect(apiMocks.dagList).toHaveBeenCalled()

    apiMocks.dagList.mockClear()
    wrapper.findComponent(TableStub).vm.$emit('change', { current: 2, pageSize: 20 })
    await flushPromises()
    expect(apiMocks.dagList).toHaveBeenCalledWith(expect.objectContaining({ current: 2, pageSize: 20 }))

    await findButtonByText(findRow(wrapper, 2), '删除')?.trigger('click')
    await flushPromises()
    expect(apiMocks.deleteDag).toHaveBeenCalledWith(2)
  })

  it('should surface load and row action errors', async () => {
    apiMocks.dagList.mockRejectedValueOnce(new Error('list failed'))
    apiMocks.triggerDag.mockRejectedValueOnce(new Error('trigger failed'))
    apiMocks.stopDag.mockRejectedValueOnce(new Error('stop failed'))
    apiMocks.retryDag.mockRejectedValueOnce(new Error('retry failed'))
    apiMocks.deleteDag.mockRejectedValueOnce(new Error('delete failed'))

    const wrapper = mountView()
    await flushPromises()

    expect(uiMocks.messageError).toHaveBeenCalledWith('list failed')

    apiMocks.dagList.mockResolvedValue({
      records: [
        { id: 2, code: 'running-dag', name: 'Running DAG', enabled: true, currentVersionId: 12, hasRunningRun: true, hasRetryableRun: true },
      ],
      total: 1,
    })
    await wrapper.find('.action-icon').trigger('click')
    await flushPromises()

    const row = findRow(wrapper, 2)
    await findButtonByText(row, '触发')?.trigger('click')
    await flushPromises()
    await findButtonByText(row, '停止 DAG')?.trigger('click')
    await flushPromises()
    await findButtonByText(row, '重试最近失败运行')?.trigger('click')
    await flushPromises()
    await findButtonByText(row, '删除')?.trigger('click')
    await flushPromises()

    expect(uiMocks.messageError).toHaveBeenCalledWith('trigger failed')
    expect(uiMocks.messageError).toHaveBeenCalledWith('stop failed')
    expect(uiMocks.messageError).toHaveBeenCalledWith('retry failed')
    expect(uiMocks.messageError).toHaveBeenCalledWith('delete failed')
  })
})
