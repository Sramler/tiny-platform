import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  getResourceTree: vi.fn(),
}))

vi.mock('@/api/resource', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/resource')>()
  return {
    ...actual,
    getResourceTree: apiMocks.getResourceTree,
  }
})

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

import Resource from '@/views/resource/resource.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('resource.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.getResourceTree.mockResolvedValue([
      { id: 1, name: 'res1', title: 'Resource 1', type: 0, children: [] },
    ])
  })

  it('should display resource title and load tree on mount', async () => {
    const wrapper = mount(Resource, {
      global: {
        stubs: {
          'a-table': defineComponent({
            props: ['dataSource'],
            template: '<div class="resource-table-stub">table rows: {{ (dataSource || []).length }}</div>',
          }),
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-tooltip': PassThrough,
          'a-popover': PassThrough,
          'a-modal': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          'a-drawer': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          VueDraggable: PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          EditOutlined: PassThrough,
          DeleteOutlined: PassThrough,
          SettingOutlined: PassThrough,
          HolderOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('资源管理')
    expect(apiMocks.getResourceTree).toHaveBeenCalled()
  })
})
