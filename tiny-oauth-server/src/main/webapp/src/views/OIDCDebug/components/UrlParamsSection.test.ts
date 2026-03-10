import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import UrlParamsSection from '@/views/OIDCDebug/components/UrlParamsSection.vue'

describe('UrlParamsSection.vue', () => {
  it('should show empty message when no params', () => {
    const wrapper = mount(UrlParamsSection, { props: { params: {} } })
    expect(wrapper.text()).toContain('当前 URL 不包含查询参数')
  })

  it('should list params when provided', () => {
    const wrapper = mount(UrlParamsSection, { props: { params: { code: 'abc' } } })
    expect(wrapper.text()).toContain('code')
    expect(wrapper.text()).toContain('abc')
  })
})

