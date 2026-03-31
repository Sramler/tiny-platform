import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const listTasksMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/export', () => ({
  exportApi: {
    listTasks: listTasksMock,
  },
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ query: {} }),
  }
})

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

import ExportTask from '@/views/export/ExportTask.vue'
import { ACTIVE_SCOPE_CHANGED_EVENT } from '@/utils/activeScopeEvents'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('ExportTask.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listTasksMock.mockResolvedValue([])
  })

  it('should reload tasks when active scope changes', async () => {
    mount(ExportTask, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-tooltip': PassThrough,
          'a-popover': PassThrough,
          'a-checkbox': PassThrough,
          'a-table': defineComponent({
            template: '<div class="export-table-stub" />',
          }),
          'a-progress': PassThrough,
          'a-drawer': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          VueDraggable: PassThrough,
          ExportTaskExamples: PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          PoweroffOutlined: PassThrough,
          SettingOutlined: PassThrough,
          HolderOutlined: PassThrough,
          EyeOutlined: PassThrough,
          DownloadOutlined: PassThrough,
        },
      },
    })
    await flushPromises()
    const afterMount = listTasksMock.mock.calls.length
    expect(afterMount).toBeGreaterThan(0)

    window.dispatchEvent(new CustomEvent(ACTIVE_SCOPE_CHANGED_EVENT))
    await flushPromises()

    expect(listTasksMock.mock.calls.length).toBeGreaterThan(afterMount)
  })
})
