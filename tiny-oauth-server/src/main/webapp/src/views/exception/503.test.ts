import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(() => Promise.resolve()),
  go: vi.fn(),
  route: {
    path: '/exception/503',
    query: {} as Record<string, unknown>,
  },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerMocks.push, go: routerMocks.go }),
  useRoute: () => routerMocks.route,
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})

import Page503 from '@/views/exception/503.vue'

describe('503.vue', () => {
  beforeEach(() => {
    routerMocks.push.mockClear()
    routerMocks.go.mockClear()
    routerMocks.route.path = '/exception/503'
    routerMocks.route.query = {}
  })

  it('should render 503 page', () => {
    const wrapper = mount(Page503, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-divider': PassThrough,
          'a-descriptions': PassThrough,
          'a-descriptions-item': PassThrough,
          'a-typography-text': PassThrough,
          'a-tooltip': PassThrough,
          'a-button': ButtonStub,
          ThunderboltOutlined: PassThrough,
          HomeOutlined: PassThrough,
          ArrowLeftOutlined: PassThrough,
          InfoCircleOutlined: PassThrough,
        },
      },
    })
    expect(wrapper.text()).toContain('503')
    expect(wrapper.text()).toContain('服务不可用')
  })

  it('should show backend unavailable message from query', () => {
    routerMocks.route.query = {
      message: '当前运行环境未启用流程引擎，流程管理接口不可用',
      path: '/process/definitions',
    }

    const wrapper = mount(Page503, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-divider': PassThrough,
          'a-descriptions': PassThrough,
          'a-descriptions-item': PassThrough,
          'a-typography-text': PassThrough,
          'a-tooltip': PassThrough,
          'a-button': ButtonStub,
          ThunderboltOutlined: PassThrough,
          HomeOutlined: PassThrough,
          ArrowLeftOutlined: PassThrough,
          InfoCircleOutlined: PassThrough,
        },
      },
    })

    expect(wrapper.text()).toContain('当前运行环境未启用流程引擎，流程管理接口不可用')
    expect(wrapper.text()).toContain('/process/definitions')
  })

  it('should sanitize unsafe from query before returning', async () => {
    routerMocks.route.query = { from: 'https://evil.example/phish' }

    const wrapper = mount(Page503, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-divider': PassThrough,
          'a-descriptions': PassThrough,
          'a-descriptions-item': PassThrough,
          'a-typography-text': PassThrough,
          'a-tooltip': PassThrough,
          'a-button': ButtonStub,
          ThunderboltOutlined: PassThrough,
          HomeOutlined: PassThrough,
          ArrowLeftOutlined: PassThrough,
          InfoCircleOutlined: PassThrough,
        },
      },
    })

    await wrapper.findAll('button')[1]?.trigger('click')

    expect(routerMocks.push).toHaveBeenCalledWith('/')
    expect(routerMocks.push).not.toHaveBeenCalledWith('https://evil.example/phish')
  })
})
