import { mount } from '@vue/test-utils'
import { defineComponent, nextTick, h } from 'vue'
import { describe, expect, it } from 'vitest'

const TabsStub = defineComponent({
  setup(_, { slots }) {
    return () => h('div', { class: 'tabs' }, slots.default?.())
  },
})

const TabPaneStub = defineComponent({
  props: { tab: { type: String, default: '' } },
  setup(props, { slots }) {
    return () => h('div', { class: 'tab-pane' }, [h('div', props.tab), slots.default?.()])
  },
})

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import DictIndex from '@/views/dict/index.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('dict index.vue', () => {
  it('should render tabs', async () => {
    const wrapper = mount(DictIndex, {
      global: {
        stubs: {
          'a-tabs': TabsStub,
          'a-tab-pane': TabPaneStub,
          DictType: PassThrough,
          DictItem: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('字典类型')
    expect(wrapper.text()).toContain('字典项')
  })
})

