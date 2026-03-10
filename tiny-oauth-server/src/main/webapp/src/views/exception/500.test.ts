import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), go: vi.fn() }),
  useRoute: () => ({ path: '/exception/500', query: {} }),
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import Page500 from '@/views/exception/500.vue'

describe('500.vue', () => {
  it('should render 500 page', () => {
    const wrapper = mount(Page500, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-divider': PassThrough,
          'a-descriptions': PassThrough,
          'a-descriptions-item': PassThrough,
          'a-typography-text': PassThrough,
          'a-tooltip': PassThrough,
          'a-button': PassThrough,
          ThunderboltOutlined: PassThrough,
          HomeOutlined: PassThrough,
          ArrowLeftOutlined: PassThrough,
          InfoCircleOutlined: PassThrough,
        },
      },
    })
    expect(wrapper.text()).toContain('500')
    expect(wrapper.text()).toContain('服务器错误')
  })
})

