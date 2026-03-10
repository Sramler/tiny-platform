import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  getDag: vi.fn(),
  updateDag: vi.fn(),
  triggerDag: vi.fn(),
  stopDag: vi.fn(),
  retryDag: vi.fn(),
  pauseDag: vi.fn(),
  resumeDag: vi.fn(),
  listDagVersions: vi.fn(),
  createDagVersion: vi.fn(),
  updateDagVersion: vi.fn(),
  getDagNodes: vi.fn(),
  createDagNode: vi.fn(),
  updateDagNode: vi.fn(),
  deleteDagNode: vi.fn(),
  getUpstreamNodes: vi.fn(),
  getDownstreamNodes: vi.fn(),
  getDagEdges: vi.fn(),
  createDagEdge: vi.fn(),
  deleteDagEdge: vi.fn(),
  taskList: vi.fn(),
}))

const routerMocks = vi.hoisted(() => ({
  back: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
}))

const routeState = vi.hoisted(() => ({
  query: { id: '10' } as Record<string, string>,
}))

const uiMocks = vi.hoisted(() => ({
  messageError: vi.fn(),
  messageSuccess: vi.fn(),
  messageInfo: vi.fn(),
  messageWarning: vi.fn(),
}))

vi.mock('@/api/scheduling', () => ({
  getDag: apiMocks.getDag,
  updateDag: apiMocks.updateDag,
  triggerDag: apiMocks.triggerDag,
  stopDag: apiMocks.stopDag,
  retryDag: apiMocks.retryDag,
  pauseDag: apiMocks.pauseDag,
  resumeDag: apiMocks.resumeDag,
  listDagVersions: apiMocks.listDagVersions,
  createDagVersion: apiMocks.createDagVersion,
  updateDagVersion: apiMocks.updateDagVersion,
  getDagNodes: apiMocks.getDagNodes,
  createDagNode: apiMocks.createDagNode,
  updateDagNode: apiMocks.updateDagNode,
  deleteDagNode: apiMocks.deleteDagNode,
  getUpstreamNodes: apiMocks.getUpstreamNodes,
  getDownstreamNodes: apiMocks.getDownstreamNodes,
  getDagEdges: apiMocks.getDagEdges,
  createDagEdge: apiMocks.createDagEdge,
  deleteDagEdge: apiMocks.deleteDagEdge,
  taskList: apiMocks.taskList,
}))

vi.mock('vue-router', () => ({
  useRouter: () => routerMocks,
  useRoute: () => routeState,
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: uiMocks.messageError,
    success: uiMocks.messageSuccess,
    info: uiMocks.messageInfo,
    warning: uiMocks.messageWarning,
  },
}))

import DagDetail from '@/views/scheduling/DagDetail.vue'

const PassThrough = defineComponent({
  template: '<div><slot /><slot name="extra" /><slot name="content" /><slot name="icon" /></div>',
})

const InputStub = defineComponent({
  props: {
    value: [String, Number],
    placeholder: String,
  },
  emits: ['update:value'],
  template:
    '<input :value="value" :placeholder="placeholder" @input="$emit(\'update:value\', $event.target.value)" />',
})

const TextareaStub = defineComponent({
  props: {
    value: [String, Number],
    placeholder: String,
  },
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
    options: { type: Array, default: () => [] },
  },
  emits: ['update:value', 'change'],
  methods: {
    normalizeValue(raw: string) {
      if (raw === '') return undefined
      if (/^-?\\d+$/.test(raw)) return Number(raw)
      return raw
    },
  },
  template: `
    <select
      :value="value ?? ''"
      :data-placeholder="placeholder"
      @change="(event) => {
        const nextValue = normalizeValue(event.target.value)
        $emit('update:value', nextValue)
        $emit('change', nextValue)
      }"
    >
      <option value="">--</option>
      <option
        v-for="option in options"
        :key="String(option.value)"
        :value="option.value"
      >
        {{ option.label }}
      </option>
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

const InputNumberStub = defineComponent({
  props: {
    value: [Number, String],
  },
  emits: ['update:value'],
  template: `
    <input
      type="number"
      :value="value ?? ''"
      @input="$emit('update:value', $event.target.value === '' ? undefined : Number($event.target.value))"
    />
  `,
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
  for (let index = 0; index < 6; index += 1) {
    await Promise.resolve()
  }
  await nextTick()
}

function buildDagResponse(overrides: Record<string, unknown> = {}) {
  return {
    id: 10,
    code: 'sales-report',
    name: 'Sales Report',
    description: 'Daily sales report',
    enabled: true,
    currentVersionId: 5,
    hasRunningRun: true,
    hasRetryableRun: true,
    cronEnabled: true,
    cronExpression: '0 0 2 * * ?',
    cronTimezone: 'Asia/Shanghai',
    createdAt: '2026-03-10 10:00:00',
    ...overrides,
  }
}

function mountView() {
  return mount(DagDetail, {
    global: {
      stubs: {
        'a-page-header': PassThrough,
        'a-space': PassThrough,
        'a-tooltip': TooltipStub,
        'a-button': ButtonStub,
        'a-popconfirm': PopconfirmStub,
        'a-tabs': PassThrough,
        'a-tab-pane': PassThrough,
        'a-card': PassThrough,
        'a-descriptions': PassThrough,
        'a-descriptions-item': PassThrough,
        'a-tag': PassThrough,
        'a-table': TableStub,
        'a-select': SelectStub,
        'a-select-option': SelectOptionStub,
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-input': InputStub,
        'a-input-group': PassThrough,
        'a-textarea': TextareaStub,
        'a-input-number': InputNumberStub,
        'a-switch': SwitchStub,
        'a-modal': defineComponent({
          props: ['open', 'title'],
          emits: ['ok', 'cancel'],
          template:
            '<div v-if="open" class="modal" :data-title="title"><slot /><button class="modal-ok" @click="$emit(\'ok\')">确定</button><button class="modal-cancel" @click="$emit(\'cancel\')">取消</button></div>',
        }),
        CronDesigner: defineComponent({ template: '<div class="cron-designer" />' }),
        PlusOutlined: defineComponent({ template: '<span>+</span>' }),
      },
    },
  })
}

function findButtonByText(wrapper: ReturnType<typeof mountView>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

describe('DagDetail.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.query = { id: '10' }
    apiMocks.getDag.mockResolvedValue(buildDagResponse())
    apiMocks.listDagVersions.mockResolvedValue([
      { id: 5, versionNo: 1, status: 'ACTIVE', createdAt: '2026-03-10 10:00:00' },
    ])
    apiMocks.getDagNodes.mockResolvedValue([{ id: 11, nodeCode: 'extract', name: 'Extract', taskId: 2 }])
    apiMocks.getDagEdges.mockResolvedValue([{ id: 21, fromNodeCode: 'extract', toNodeCode: 'merge' }])
    apiMocks.taskList.mockResolvedValue({
      records: [{ id: 2, code: 'extract-task', name: 'Extract Task' }],
    })
    apiMocks.triggerDag.mockResolvedValue(undefined)
    apiMocks.stopDag.mockResolvedValue(undefined)
    apiMocks.retryDag.mockResolvedValue(undefined)
  })

  it('should load dag detail and execute dag-level actions', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.getDag).toHaveBeenCalledWith(10)
    expect(apiMocks.listDagVersions).toHaveBeenCalledWith(10)
    expect(apiMocks.getDagNodes).toHaveBeenCalledWith(10, 5)
    expect(apiMocks.getDagEdges).toHaveBeenCalledWith(10, 5)
    expect(apiMocks.taskList).toHaveBeenCalledWith({ current: 1, pageSize: 1000 })

    await findButtonByText(wrapper, '运行历史')?.trigger('click')
    await findButtonByText(wrapper, '触发执行')?.trigger('click')
    await flushPromises()
    await findButtonByText(wrapper, '停止 DAG')?.trigger('click')
    await flushPromises()
    await findButtonByText(wrapper, '重试最近失败运行')?.trigger('click')
    await flushPromises()

    expect(routerMocks.push).toHaveBeenCalledWith({
      path: '/scheduling/dag/history',
      query: { dagId: '10' },
    })
    expect(apiMocks.triggerDag).toHaveBeenCalledWith(10)
    expect(apiMocks.stopDag).toHaveBeenCalledWith(10)
    expect(apiMocks.retryDag).toHaveBeenCalledWith(10)
  })

  it('should disable dag-level actions with explicit reasons when dag is not runnable', async () => {
    apiMocks.getDag.mockResolvedValue(
      buildDagResponse({
        code: 'draft-only',
        name: 'Draft Only',
        currentVersionId: null,
        hasRunningRun: false,
        hasRetryableRun: false,
        cronEnabled: false,
      }),
    )

    const wrapper = mountView()
    await flushPromises()

    const triggerButton = findButtonByText(wrapper, '触发执行')
    const stopButton = findButtonByText(wrapper, '停止 DAG')
    const retryButton = findButtonByText(wrapper, '重试最近失败运行')

    expect(triggerButton?.attributes('disabled')).toBeDefined()
    expect(stopButton?.attributes('disabled')).toBeDefined()
    expect(retryButton?.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('请先创建并激活版本')
    expect(wrapper.text()).toContain('当前没有运行中的 Run')
    expect(wrapper.text()).toContain('当前没有可重试的失败运行')
  })

  it('should edit dag and handle pause-resume flow', async () => {
    apiMocks.getDag
      .mockResolvedValueOnce(buildDagResponse())
      .mockResolvedValueOnce(buildDagResponse({ enabled: false, hasRunningRun: false }))
      .mockResolvedValueOnce(buildDagResponse({ enabled: true, hasRunningRun: false }))
      .mockResolvedValueOnce(
        buildDagResponse({
          enabled: true,
          hasRunningRun: false,
          code: 'sales-report-v2',
          name: 'Sales Report V2',
          cronEnabled: false,
          cronTimezone: 'UTC',
        }),
      )
    apiMocks.updateDag.mockResolvedValue(undefined)
    apiMocks.pauseDag.mockResolvedValue(undefined)
    apiMocks.resumeDag.mockResolvedValue(undefined)

    const wrapper = mountView()
    await flushPromises()

    await findButtonByText(wrapper, '暂停 DAG')?.trigger('click')
    await flushPromises()
    expect(apiMocks.pauseDag).toHaveBeenCalledWith(10)

    await findButtonByText(wrapper, '恢复 DAG')?.trigger('click')
    await flushPromises()
    expect(apiMocks.resumeDag).toHaveBeenCalledWith(10)

    await findButtonByText(wrapper, '编辑')?.trigger('click')
    await flushPromises()

    const codeInput = wrapper.find('input[placeholder="请输入编码"]')
    const nameInputs = wrapper.findAll('input[placeholder="请输入名称"]')
    const dagNameInput = nameInputs[0]!
    const timezoneSelect = wrapper.find('select[data-placeholder="可选，默认使用系统时区"]')
    const switches = wrapper.findAll('input[type="checkbox"]')

    await codeInput.setValue('sales-report-v2')
    await dagNameInput.setValue('Sales Report V2')
    await timezoneSelect.setValue('UTC')
    await switches[0]?.setValue(false)
    await switches[1]?.setValue(false)
    await wrapper.find('.modal[data-title="编辑 DAG"] .modal-ok').trigger('click')
    await flushPromises()

    expect(apiMocks.updateDag).toHaveBeenCalledWith(
      10,
      expect.objectContaining({
        id: 10,
        code: 'sales-report-v2',
        name: 'Sales Report V2',
        cronTimezone: 'UTC',
        cronEnabled: false,
        enabled: false,
      }),
    )
  })

  it('should manage versions by creating activating and switching nodes view', async () => {
    apiMocks.listDagVersions.mockResolvedValue([
      { id: 5, versionNo: 1, status: 'ACTIVE', definition: '{"v":1}', createdAt: '2026-03-10 10:00:00' },
      { id: 6, versionNo: 2, status: 'DRAFT', definition: '{"v":2}', createdAt: '2026-03-10 10:05:00' },
    ])
    apiMocks.createDagVersion.mockResolvedValue({ id: 6 })
    apiMocks.updateDagVersion.mockResolvedValue(undefined)

    const wrapper = mountView()
    await flushPromises()

    await findButtonByText(wrapper, '创建新版本')?.trigger('click')
    await flushPromises()
    const versionStatusSelect = wrapper.find('.modal[data-title="创建版本"] select')
    await versionStatusSelect.setValue('ACTIVE')
    await wrapper.find('.modal[data-title="创建版本"] textarea[placeholder="请输入JSON格式的定义"]').setValue('{"mode":"parallel"}')
    await wrapper.find('.modal[data-title="创建版本"] .modal-ok').trigger('click')
    await flushPromises()

    expect(apiMocks.createDagVersion).toHaveBeenCalledWith(
      10,
      expect.objectContaining({ status: 'ACTIVE', definition: '{"mode":"parallel"}' }),
    )

    await findButtonByText(wrapper, '激活')?.trigger('click')
    await flushPromises()
    expect(apiMocks.updateDagVersion).toHaveBeenCalledWith(10, 6, { status: 'ACTIVE' })

    apiMocks.getDagNodes.mockClear()
    apiMocks.getDagEdges.mockClear()
    const switchButtons = wrapper.findAll('button').filter((button) => button.text().includes('切换到此版本'))
    await switchButtons[1]?.trigger('click')
    await flushPromises()

    expect(apiMocks.getDagNodes).toHaveBeenCalledWith(10, 6)
    expect(apiMocks.getDagEdges).toHaveBeenCalledWith(10, 6)
  })

  it('should validate create node and edge flows with upstream downstream and delete actions', async () => {
    apiMocks.listDagVersions.mockResolvedValue([
      { id: 5, versionNo: 1, status: 'ACTIVE', createdAt: '2026-03-10 10:00:00' },
    ])
    apiMocks.getDagNodes.mockResolvedValue([
      { id: 11, nodeCode: 'extract', name: 'Extract', taskId: 2 },
      { id: 12, nodeCode: 'merge', name: 'Merge', taskId: 3 },
    ])
    apiMocks.getDagEdges.mockResolvedValue([{ id: 21, fromNodeCode: 'extract', toNodeCode: 'merge' }])
    apiMocks.taskList.mockResolvedValue({
      records: [
        { id: 2, code: 'extract-task', name: 'Extract Task' },
        { id: 3, code: 'merge-task', name: 'Merge Task' },
      ],
    })
    apiMocks.createDagNode.mockResolvedValue({ id: 13 })
    apiMocks.updateDagNode.mockResolvedValue(undefined)
    apiMocks.deleteDagNode.mockResolvedValue(undefined)
    apiMocks.getUpstreamNodes.mockResolvedValue([{ id: 11, nodeCode: 'extract' }])
    apiMocks.getDownstreamNodes.mockResolvedValue([{ id: 12, nodeCode: 'merge' }])
    apiMocks.createDagEdge.mockResolvedValue({ id: 22 })
    apiMocks.deleteDagEdge.mockResolvedValue(undefined)

    const wrapper = mountView()
    await flushPromises()

    await findButtonByText(wrapper, '添加节点')?.trigger('click')
    await flushPromises()
    await wrapper.find('.modal[data-title="添加节点"] .modal-ok').trigger('click')
    await flushPromises()
    expect(uiMocks.messageError).toHaveBeenCalledWith('请输入节点编码')

    await wrapper.find('.modal[data-title="添加节点"] input[placeholder="请输入节点编码（唯一）"]').setValue('join')
    await wrapper.find('.modal[data-title="添加节点"] select[data-placeholder="请选择任务"]').setValue('3')
    await wrapper.find('.modal[data-title="添加节点"] .modal-ok').trigger('click')
    await flushPromises()

    expect(apiMocks.createDagNode).toHaveBeenCalledWith(
      10,
      5,
      expect.objectContaining({ nodeCode: 'join', taskId: '3' }),
    )

    const editButtons = wrapper.findAll('button').filter((button) => button.text() === '编辑')
    await editButtons[editButtons.length - 1]?.trigger('click')
    await flushPromises()
    const nodeNameInput = wrapper.find('.modal[data-title="编辑节点"] input[placeholder="请输入节点名称"]')
    await nodeNameInput.setValue('Extract Updated')
    await wrapper.find('.modal[data-title="编辑节点"] .modal-ok').trigger('click')
    await flushPromises()

    expect(apiMocks.updateDagNode).toHaveBeenCalledWith(
      10,
      5,
      12,
      expect.objectContaining({ name: 'Extract Updated' }),
    )

    await findButtonByText(wrapper, '上游')?.trigger('click')
    await flushPromises()
    await findButtonByText(wrapper, '下游')?.trigger('click')
    await flushPromises()
    expect(uiMocks.messageInfo).toHaveBeenCalledWith('上游节点: extract')
    expect(uiMocks.messageInfo).toHaveBeenCalledWith('下游节点: merge')

    await findButtonByText(wrapper, '删除')?.trigger('click')
    await flushPromises()
    expect(apiMocks.deleteDagNode).toHaveBeenCalledWith(10, 5, 11)

    await findButtonByText(wrapper, '添加依赖')?.trigger('click')
    await flushPromises()
    const edgeModal = '.modal[data-title="添加依赖"]'
    const edgeSelects = wrapper.findAll(`${edgeModal} select`)
    await edgeSelects[0]?.setValue('extract')
    await edgeSelects[1]?.setValue('extract')
    await wrapper.find(`${edgeModal} .modal-ok`).trigger('click')
    await flushPromises()
    expect(uiMocks.messageError).toHaveBeenCalledWith('节点不能依赖自身')

    await edgeSelects[1]?.setValue('merge')
    await wrapper.find(`${edgeModal} textarea[placeholder="请输入JSON格式的条件"]`).setValue('{"type":"all"}')
    await wrapper.find(`${edgeModal} .modal-ok`).trigger('click')
    await flushPromises()
    expect(apiMocks.createDagEdge).toHaveBeenCalledWith(
      10,
      5,
      expect.objectContaining({
        fromNodeCode: 'extract',
        toNodeCode: 'merge',
        condition: '{"type":"all"}',
      }),
    )

    apiMocks.deleteDagEdge.mockClear()
    const deleteButtons = wrapper.findAll('button').filter((button) => button.text().includes('删除'))
    await deleteButtons[deleteButtons.length - 1]?.trigger('click')
    await flushPromises()
    expect(apiMocks.deleteDagEdge).toHaveBeenCalledWith(10, 5, 21)
  })

  it('should disable add node when no version exists and warn when adding edge without nodes', async () => {
    apiMocks.listDagVersions.mockResolvedValue([])

    const wrapper = mountView()
    await flushPromises()

    const addNodeButton = wrapper.findAll('button').find((button) => button.text().includes('添加节点'))
    const addEdgeButton = wrapper.findAll('button').find((button) => button.text().includes('添加依赖'))
    expect(addNodeButton?.attributes('disabled')).toBeDefined()
    expect(addEdgeButton?.attributes('disabled')).toBeDefined()

    apiMocks.listDagVersions.mockResolvedValue([
      { id: 5, versionNo: 1, status: 'ACTIVE', createdAt: '2026-03-10 10:00:00' },
    ])
    apiMocks.getDagNodes.mockResolvedValue([])
    const withVersionWrapper = mountView()
    await flushPromises()

    await findButtonByText(withVersionWrapper, '添加依赖')?.trigger('click')
    await flushPromises()
    expect(uiMocks.messageWarning).toHaveBeenCalledWith('请先添加节点')
  })

  it('should redirect back to dag list when route has no dag id', async () => {
    routeState.query = {}

    mountView()
    await flushPromises()

    expect(routerMocks.replace).toHaveBeenCalledWith('/scheduling/dag')
    expect(uiMocks.messageWarning).toHaveBeenCalled()
    expect(apiMocks.getDag).not.toHaveBeenCalled()
  })
})
