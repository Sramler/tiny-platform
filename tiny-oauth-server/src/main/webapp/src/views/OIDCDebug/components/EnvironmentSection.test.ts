import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import EnvironmentSection from '@/views/OIDCDebug/components/EnvironmentSection.vue'

describe('EnvironmentSection.vue', () => {
  it('should render environment info', () => {
    const wrapper = mount(EnvironmentSection, {
      props: {
        userClaims: { sub: 'u1' },
        environmentInfo: {
          origin: 'http://localhost',
          href: 'http://localhost/path',
          userAgent: 'ua',
          online: true,
          cookieSize: 10,
        },
      },
    })

    expect(wrapper.text()).toContain('环境信息')
    expect(wrapper.text()).toContain('http://localhost')
    expect(wrapper.text()).toContain('在线')
  })
})

