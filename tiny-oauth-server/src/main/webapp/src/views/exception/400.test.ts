import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), go: vi.fn() }),
  useRoute: () => ({ path: '/exception/400', query: {} }),
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import Page400 from '@/views/exception/400.vue'

describe('400.vue', () => {
  it('should render 400 page', () => {
    const wrapper = mount(Page400, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-divider': PassThrough,
          'a-descriptions': PassThrough,
          'a-descriptions-item': PassThrough,
          'a-typography-text': PassThrough,
          'a-tooltip': PassThrough,
          'a-button': PassThrough,
          CloseCircleOutlined: PassThrough,
          HomeOutlined: PassThrough,
          ArrowLeftOutlined: PassThrough,
          InfoCircleOutlined: PassThrough,
        },
      },
    })
    expect(wrapper.text()).toContain('400')
    expect(wrapper.text()).toContain('请求错误')
  })
})

