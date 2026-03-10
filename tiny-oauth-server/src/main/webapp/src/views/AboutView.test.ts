import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AboutView from '@/views/AboutView.vue'

describe('AboutView.vue', () => {
  it('should render', () => {
    const wrapper = mount(AboutView)
    expect(wrapper.text()).toContain('分析页')
  })
})

