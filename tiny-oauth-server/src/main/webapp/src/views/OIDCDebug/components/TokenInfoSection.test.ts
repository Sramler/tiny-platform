import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import TokenInfoSection from '@/views/OIDCDebug/components/TokenInfoSection.vue'

describe('TokenInfoSection.vue', () => {
  it('should render token labels', () => {
    const wrapper = mount(TokenInfoSection, {
      props: {
        accessToken: 'a.b.c',
        idToken: 'a.b.c',
        refreshToken: '',
        scopes: 'openid',
        sessionInfo: { tokenType: 'Bearer', sessionState: 's1' },
        decodedAccessToken: { sub: 'u1' },
        decodedIdToken: { sub: 'u1' },
      },
    })

    expect(wrapper.text()).toContain('Token 信息')
    expect(wrapper.text()).toContain('Access Token')
    expect(wrapper.text()).toContain('ID Token')
    expect(wrapper.text()).toContain('Scopes')
  })
})

