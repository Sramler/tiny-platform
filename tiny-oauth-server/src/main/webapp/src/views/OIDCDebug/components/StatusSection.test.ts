import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import StatusSection from '@/views/OIDCDebug/components/StatusSection.vue'

describe('StatusSection.vue', () => {
  it('should render authentication status', () => {
    const wrapper = mount(StatusSection, {
      props: { isAuthenticated: true, userInfo: 'alice', tokenExpiry: 'soon' },
    })

    expect(wrapper.text()).toContain('认证状态')
    expect(wrapper.text()).toContain('已认证')
    expect(wrapper.text()).toContain('alice')
  })
})

