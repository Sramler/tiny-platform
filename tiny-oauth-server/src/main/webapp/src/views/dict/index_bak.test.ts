import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it } from 'vitest'

import IndexBak from '@/views/dict/index_bak.vue'

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

describe('dict index_bak.vue', () => {
  it('should render', () => {
    const wrapper = mount(IndexBak, {
      global: {
        stubs: {
          'a-tabs': TabsStub,
          'a-tab-pane': TabPaneStub,
          DictType: PassThrough,
          DictItem: PassThrough,
        },
      },
    })
    expect(wrapper.exists()).toBe(true)
  })
})

