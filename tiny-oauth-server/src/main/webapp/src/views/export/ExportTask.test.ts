import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routeState: { query: Record<string, unknown> } = {
  query: {},
}

const apiMocks = vi.hoisted(() => ({
  listTasks: vi.fn(),
}))

const uiMocks = vi.hoisted(() => ({
  messageError: vi.fn(),
  messageWarning: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
}))

vi.mock('@/api/export', () => ({
  exportApi: {
    listTasks: apiMocks.listTasks,
  },
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: any[]) => any) => fn,
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: uiMocks.messageError,
    warning: uiMocks.messageWarning,
  },
}))

vi.mock('./ExportTaskExamples.vue', () => ({
  default: defineComponent({
    name: 'ExportTaskExamplesStub',
    template: '<div>examples</div>',
  }),
}))

import ExportTask from '@/views/export/ExportTask.vue'

const PassThrough = defineComponent({
  template: '<div><slot /><slot name="content" /><slot name="icon" /></div>',
})

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\', $event)"><slot /><slot name="icon" /></button>',
})

const DrawerStub = defineComponent({
  props: ['open'],
  emits: ['close', 'update:open'],
  template: '<div><slot /></div>',
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
        (props.dataSource as any[]).map((record) =>
          h('div', { class: 'task-row', 'data-task-id': record.taskId }, [
            h('span', { class: 'task-id' }, String(record.taskId)),
            h(
              'div',
              { class: 'action-slot' },
              slots.bodyCell?.({
                column: { dataIndex: 'action' },
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
  await nextTick()
}

describe('ExportTask.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.query = {}
  })

  function mountView() {
    return mount(ExportTask, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': ButtonStub,
          'a-tooltip': PassThrough,
          'a-popover': PassThrough,
          'a-checkbox': PassThrough,
          'a-tag': PassThrough,
          'a-progress': PassThrough,
          'a-pagination': PassThrough,
          'a-drawer': DrawerStub,
          'a-descriptions': PassThrough,
          'a-descriptions-item': PassThrough,
          'a-typography-paragraph': PassThrough,
          'a-table': TableStub,
          VueDraggable: PassThrough,
          ReloadOutlined: PassThrough,
          SettingOutlined: PassThrough,
          HolderOutlined: PassThrough,
          EyeOutlined: PassThrough,
          DownloadOutlined: PassThrough,
          PoweroffOutlined: PassThrough,
          PlusOutlined: PassThrough,
        },
      },
    })
  }

  it('should load tasks on mount and apply route taskId filter', async () => {
    routeState.query = { taskId: 'task-2' }
    apiMocks.listTasks.mockResolvedValue([
      { taskId: 'task-1', userId: 'u-1', username: 'alice', status: 'SUCCESS' },
      { taskId: 'task-2', userId: 'u-2', username: 'bob', status: 'RUNNING' },
    ])

    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.listTasks).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('task-2')
    expect(wrapper.text()).not.toContain('task-1')
  })

  it('should open download url for successful task and disable unfinished task download', async () => {
    const openSpy = vi.fn()
    vi.stubGlobal('open', openSpy)
    apiMocks.listTasks.mockResolvedValue([
      { taskId: 'task-1', userId: 'u-1', username: 'alice', status: 'SUCCESS' },
      { taskId: 'task-2', userId: 'u-2', username: 'bob', status: 'RUNNING' },
    ])

    const wrapper = mountView()
    await flushPromises()

    const rows = wrapper.findAll('.task-row')
    await rows[0].findAll('button')[1].trigger('click')
    expect(openSpy).toHaveBeenCalledWith(
      'http://test-api.example.com/export/task/task-1/download',
      '_blank',
    )

    const disabledDownloadButton = rows[1].findAll('button')[1]
    expect(disabledDownloadButton.attributes('disabled')).toBeDefined()
    await disabledDownloadButton.trigger('click')
    expect(openSpy).toHaveBeenCalledTimes(1)
  })

  it('should show error message when task loading fails', async () => {
    apiMocks.listTasks.mockRejectedValue(new Error('load failed'))

    mountView()
    await flushPromises()

    expect(uiMocks.messageError).toHaveBeenCalledWith('加载导出任务失败: load failed')
  })
})
