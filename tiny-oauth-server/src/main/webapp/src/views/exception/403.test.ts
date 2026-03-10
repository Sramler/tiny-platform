import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), go: vi.fn() }),
  useRoute: () => ({ path: '/exception/403', query: {} }),
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import Page403 from '@/views/exception/403.vue'

describe('403.vue', () => {
  it('should render 403 page', () => {
    const wrapper = mount(Page403, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-divider': PassThrough,
          'a-descriptions': PassThrough,
          'a-descriptions-item': PassThrough,
          'a-typography-text': PassThrough,
          'a-tooltip': PassThrough,
          'a-button': PassThrough,
          StopOutlined: PassThrough,
          HomeOutlined: PassThrough,
          ArrowLeftOutlined: PassThrough,
          InfoCircleOutlined: PassThrough,
        },
      },
    })
    expect(wrapper.text()).toContain('403')
    expect(wrapper.text()).toContain('访问被拒绝')
  })
})

