import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it } from 'vitest'

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import DictManagement from '@/views/dict/DictManagement.vue'

describe('DictManagement.vue', () => {
  it('should render DictIndex', () => {
    const wrapper = mount(DictManagement, {
      global: {
        stubs: {
          DictIndex: PassThrough,
        },
      },
    })

    expect(wrapper.exists()).toBe(true)
  })
})

