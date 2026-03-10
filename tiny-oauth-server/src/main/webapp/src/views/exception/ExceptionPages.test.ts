import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { describe, expect, it, vi } from 'vitest'

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
  go: vi.fn(),
  route: {
    path: '/exception/404',
    query: {},
  },
}))

vi.mock('vue-router', () => ({
  useRouter: () => routerMocks,
  useRoute: () => routerMocks.route,
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

import E400 from '@/views/exception/400.vue'
import E403 from '@/views/exception/403.vue'
import E404 from '@/views/exception/404.vue'
import E500 from '@/views/exception/500.vue'

describe('exception pages', () => {
  it('should render 400', () => {
    const wrapper = mount(E400, {
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

  it('should render 403', () => {
    const wrapper = mount(E403, {
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

  it('should render 404', () => {
    const wrapper = mount(E404, {
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

  it('should render 500', () => {
    const wrapper = mount(E500, {
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

