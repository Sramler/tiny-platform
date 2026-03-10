import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import StorageSection from '@/views/OIDCDebug/components/StorageSection.vue'

describe('StorageSection.vue', () => {
  it('should render storage counts', () => {
    const wrapper = mount(StorageSection, {
      props: { localStorageCount: 3, sessionStorageCount: 2, oidcKeys: ['oidc.user'] },
    })

    expect(wrapper.text()).toContain('本地存储')
    expect(wrapper.text()).toContain('3')
    expect(wrapper.text()).toContain('2')
    expect(wrapper.text()).toContain('oidc.user')
  })
})

