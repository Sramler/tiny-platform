import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  dagList: vi.fn(),
  getDagRuns: vi.fn(),
  getDagRun: vi.fn(),
  getDagRunNodes: vi.fn(),
  getDagNodes: vi.fn(),
  getDagRunNode: vi.fn(),
  getTaskInstanceLog: vi.fn(),
  getDagStats: vi.fn(),
  stopDagRun: vi.fn(),
  retryDagRun: vi.fn(),
  triggerDagRunNode: vi.fn(),
  retryDagRunNode: vi.fn(),
  pauseDagRunNode: vi.fn(),
  resumeDagRunNode: vi.fn(),
}))

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  authUser: { value: null as { access_token?: string | null } | null },
}))

const routeState = vi.hoisted(() => ({
  path: '/scheduling/dag/history',
  query: { dagId: '10' } as Record<string, string>,
}))

const uiMocks = vi.hoisted(() => ({
  messageError: vi.fn(),
  messageSuccess: vi.fn(),
  messageInfo: vi.fn(),
  messageWarning: vi.fn(),
}))

vi.mock('@/api/scheduling', () => ({
  dagList: apiMocks.dagList,
  getDagRuns: apiMocks.getDagRuns,
  getDagRun: apiMocks.getDagRun,
  getDagRunNodes: apiMocks.getDagRunNodes,
  getDagNodes: apiMocks.getDagNodes,
  getDagRunNode: apiMocks.getDagRunNode,
  getTaskInstanceLog: apiMocks.getTaskInstanceLog,
  getDagStats: apiMocks.getDagStats,
  stopDagRun: apiMocks.stopDagRun,
  retryDagRun: apiMocks.retryDagRun,
  triggerDagRunNode: apiMocks.triggerDagRunNode,
  retryDagRunNode: apiMocks.retryDagRunNode,
  pauseDagRunNode: apiMocks.pauseDagRunNode,
  resumeDagRunNode: apiMocks.resumeDagRunNode,
}))

vi.mock('vue-router', () => ({
  useRouter: () => routerMocks,
  useRoute: () => routeState,
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: authMocks.authUser,
  }),
}))

vi.mock('@/utils/debounce', () => ({
  throttle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: uiMocks.messageError,
    success: uiMocks.messageSuccess,
    info: uiMocks.messageInfo,
    warning: uiMocks.messageWarning,
  },
}))

import { ACTIVE_SCOPE_CHANGED_EVENT } from '@/utils/activeScopeEvents'
import DagHistory from '@/views/scheduling/DagHistory.vue'

const PassThrough = defineComponent({
  template: '<div><slot /><slot name="content" /><slot name="icon" /></div>',
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

function mountView() {
  return mount(DagHistory, {
    global: {
      stubs: {
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-select': PassThrough,
        'a-select-option': PassThrough,
        'a-input': PassThrough,
        'a-range-picker': PassThrough,
        'a-alert': defineComponent({
          props: {
            message: { type: String, default: '' },
            description: { type: String, default: '' },
          },
          template: '<div class="a-alert-stub">{{ message }}{{ description }}</div>',
        }),
        'a-button': ButtonStub,
        'a-tooltip': TooltipStub,
        'a-card': PassThrough,
        'a-row': PassThrough,
        'a-col': PassThrough,
        'a-statistic': PassThrough,
        'a-table': TableStub,
        'a-popconfirm': PopconfirmStub,
        'a-modal': defineComponent({
          props: ['open'],
          template: '<div v-if="open" class="modal"><slot /></div>',
        }),
        'a-space': PassThrough,
        'a-tag': PassThrough,
        'a-descriptions': PassThrough,
        'a-descriptions-item': PassThrough,
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

function createToken(authorities: string[]) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities })).toString('base64url')
  return `${header}.${payload}.signature`
}

describe('DagHistory.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authMocks.authUser.value = {
      access_token: createToken(['scheduling:run:control']),
    }
    routeState.query = { dagId: '10', activeTenantId: '9' }
    apiMocks.dagList.mockResolvedValue({
      records: [{ id: 10, code: 'etl', name: 'ETL' }],
      total: 1,
    })
    apiMocks.getDagRuns.mockResolvedValue({
      records: [
        { id: 101, runNo: 'run-101', dagVersionId: 5, status: 'RUNNING', triggerType: 'MANUAL', triggeredBy: 'alice' },
        { id: 102, runNo: 'run-102', dagVersionId: 5, status: 'SUCCESS', triggerType: 'MANUAL', triggeredBy: 'alice' },
        { id: 103, runNo: 'run-103', dagVersionId: 5, status: 'FAILED', triggerType: 'RETRY', triggeredBy: 'alice' },
      ],
      total: 3,
    })
    apiMocks.getDagStats.mockResolvedValue({
      total: 3,
      success: 1,
      failed: 1,
      avgDurationMs: 1000,
      p95DurationMs: 1500,
      p99DurationMs: 1800,
    })
    apiMocks.stopDagRun.mockResolvedValue(undefined)
    apiMocks.retryDagRun.mockResolvedValue(undefined)
    apiMocks.getDagRunNodes.mockResolvedValue([
      { id: 201, dagVersionId: 5, nodeCode: 'extract', status: 'PENDING', taskId: 1, attemptNo: 1 },
      { id: 202, dagVersionId: 5, nodeCode: 'transform', status: 'PAUSED', taskId: 2, attemptNo: 1 },
      { id: 203, dagVersionId: 5, nodeCode: 'load', status: 'FAILED', taskId: 3, attemptNo: 1 },
    ])
    apiMocks.getDagNodes.mockResolvedValue([
      { id: 11, nodeCode: 'extract' },
      { id: 12, nodeCode: 'transform' },
      { id: 13, nodeCode: 'load' },
    ])
    apiMocks.triggerDagRunNode.mockResolvedValue(undefined)
    apiMocks.pauseDagRunNode.mockResolvedValue(undefined)
    apiMocks.resumeDagRunNode.mockResolvedValue(undefined)
    apiMocks.retryDagRunNode.mockResolvedValue(undefined)
    apiMocks.getDagRun.mockResolvedValue({ id: 101 })
    apiMocks.getDagRunNode.mockResolvedValue({ id: 201 })
    apiMocks.getTaskInstanceLog.mockResolvedValue('ok')
  })

  it('should surface operational-view notice that run history does not follow active scope', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('不按顶部「活动组织/部门」收缩')
    expect(wrapper.text()).toContain('DAG 管理列表会随活动范围变化')
  })

  it('should not refetch runs when active scope changes (contract: no active-scope listener)', async () => {
    const wrapper = mountView()
    await flushPromises()
    const callCount = apiMocks.getDagRuns.mock.calls.length
    expect(callCount).toBeGreaterThan(0)
    window.dispatchEvent(new CustomEvent(ACTIVE_SCOPE_CHANGED_EVENT, { detail: {} }))
    await flushPromises()
    expect(apiMocks.getDagRuns.mock.calls.length).toBe(callCount)
  })

  it('should load DAG runs from route and execute run-level stop/retry flow', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.getDagRuns).toHaveBeenCalledWith(
      10,
      expect.objectContaining({
        current: 1,
        pageSize: 10,
      }),
    )
    expect(apiMocks.getDagStats).toHaveBeenCalledWith(10)
    expect(findRow(wrapper, 101).text()).toContain('可停止')
    expect(findRow(wrapper, 103).text()).toContain('可重试')

    await findRow(wrapper, 101).findAll('.popconfirm')[0]?.trigger('click')
    await flushPromises()
    await findRow(wrapper, 103).findAll('.popconfirm')[1]?.trigger('click')
    await flushPromises()

    expect(apiMocks.stopDagRun).toHaveBeenCalledWith(10, 101)
    expect(apiMocks.retryDagRun).toHaveBeenCalledWith(10, 103)
  })

  it('should disable ineligible run actions with explicit reasons', async () => {
    const wrapper = mountView()
    await flushPromises()

    const successRow = findRow(wrapper, 102)
    const stopButton = findButtonByText(successRow, '停止本次')
    const retryButton = findButtonByText(successRow, '重试本次')

    expect(stopButton?.attributes('disabled')).toBeDefined()
    expect(retryButton?.attributes('disabled')).toBeDefined()
    expect(successRow.text()).toContain('仅 RUNNING 的运行实例支持停止')
    expect(successRow.text()).toContain('仅失败或部分失败的运行实例支持重试')
  })

  it('should preserve activeTenantId when resetting filters', async () => {
    const wrapper = mountView()
    await flushPromises()

    await findButtonByText(wrapper, '重置')?.trigger('click')
    await flushPromises()

    expect(routerMocks.replace).toHaveBeenCalledWith({
      path: '/scheduling/dag/history',
      query: { activeTenantId: '9' },
    })
  })

  it('should map dag nodes and execute run-level node actions', async () => {
    const wrapper = mountView()
    await flushPromises()

    await findButtonByText(findRow(wrapper, 101), '节点记录')?.trigger('click')
    await flushPromises()

    expect(apiMocks.getDagRunNodes).toHaveBeenCalledWith(10, 101)
    expect(apiMocks.getDagNodes).toHaveBeenCalledWith(10, 5)

    await findButtonByText(findRow(wrapper, 201), '触发本节点')?.trigger('click')
    await flushPromises()
    await findButtonByText(findRow(wrapper, 201), '暂停本节点')?.trigger('click')
    await flushPromises()
    await findButtonByText(findRow(wrapper, 202), '恢复本节点')?.trigger('click')
    await flushPromises()
    await findButtonByText(findRow(wrapper, 203), '重试本节点')?.trigger('click')
    await flushPromises()

    expect(apiMocks.triggerDagRunNode).toHaveBeenCalledWith(10, 101, 11)
    expect(apiMocks.pauseDagRunNode).toHaveBeenCalledWith(10, 101, 11)
    expect(apiMocks.resumeDagRunNode).toHaveBeenCalledWith(10, 101, 12)
    expect(apiMocks.retryDagRunNode).toHaveBeenCalledWith(10, 101, 13)
  })
})
