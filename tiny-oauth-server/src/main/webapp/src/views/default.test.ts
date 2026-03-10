import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRoute: () => ({ meta: { menuInfo: { title: 'T', path: '/p', icon: 'i' } } }),
}))

import DefaultView from '@/views/default.vue'

describe('default.vue', () => {
  it('should render menuInfo from route meta', () => {
    const wrapper = mount(DefaultView, {
      global: {
        stubs: {
          // no antd components here
        },
      },
    })
    expect(wrapper.text()).toContain('T')
    expect(wrapper.text()).toContain('/p')
  })
})

