import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  menuTreeAll: vi.fn(),
}))

vi.mock('@/api/menu', () => ({
  menuTreeAll: apiMocks.menuTreeAll,
}))

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

import MenuForm from '@/views/menu/MenuForm.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('MenuForm.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.menuTreeAll.mockResolvedValue([])
  })

  it('should load menu tree on mount', async () => {
    const wrapper = mount(MenuForm, {
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
          'a-tree-select': PassThrough,
          'a-button': defineComponent({
            emits: ['click'],
            template: `<button @click="$emit('click')"><slot /></button>`,
          }),
          'a-modal': PassThrough,
          IconSelect: PassThrough,
          Icon: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(apiMocks.menuTreeAll).toHaveBeenCalled()
    expect(wrapper.text()).toContain('基本信息')
  })
})

