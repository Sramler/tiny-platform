import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import Dashboard from '@/views/Dashboard.vue'

describe('Dashboard.vue', () => {
  it('should render', () => {
    const wrapper = mount(Dashboard)
    expect(wrapper.text()).toContain('工作台页面')
  })
})

