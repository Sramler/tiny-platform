import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), go: vi.fn() }),
  useRoute: () => ({ path: '/exception/404', query: {} }),
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import Page404 from '@/views/exception/404.vue'

describe('404.vue', () => {
  it('should render 404 page', () => {
    const wrapper = mount(Page404, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-divider': PassThrough,
          'a-descriptions': PassThrough,
          'a-descriptions-item': PassThrough,
          'a-typography-text': PassThrough,
          'a-tooltip': PassThrough,
          'a-button': PassThrough,
          FileSearchOutlined: PassThrough,
          HomeOutlined: PassThrough,
          ArrowLeftOutlined: PassThrough,
          InfoCircleOutlined: PassThrough,
        },
      },
    })
    expect(wrapper.text()).toContain('404')
    expect(wrapper.text()).toContain('页面未找到')
  })
})

