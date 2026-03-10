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

vi.mock('ant-design-vue', () => ({
  message: { error: vi.fn(), warning: vi.fn() },
}))

const FormStub = defineComponent({
  setup(_, { slots, expose }) {
    expose({ validate: () => Promise.resolve() })
    return () => slots.default?.()
  },
})

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

import ResourceForm from '@/views/resource/ResourceForm.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('ResourceForm.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.getResourceTree.mockResolvedValue([])
  })

  it('should load resource tree on mount', async () => {
    const wrapper = mount(ResourceForm, {
      props: {
        mode: 'create',
      },
      global: {
        stubs: {
          'a-form': FormStub,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-input-number': PassThrough,
          'a-switch': PassThrough,
          'a-divider': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-tree-select': PassThrough,
          'a-button': defineComponent({
            emits: ['click'],
            template: `<button @click="$emit('click')"><slot /></button>`,
          }),
        },
      },
    })
    await flushPromises()

    expect(apiMocks.getResourceTree).toHaveBeenCalled()
    expect(wrapper.text()).toContain('基本信息')
  })
})

