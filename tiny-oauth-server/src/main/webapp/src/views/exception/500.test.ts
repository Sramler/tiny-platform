import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(() => Promise.resolve()),
  go: vi.fn(),
  route: {
    path: '/exception/500',
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

import Page500 from '@/views/exception/500.vue'

describe('500.vue', () => {
  beforeEach(() => {
    routerMocks.push.mockClear()
    routerMocks.go.mockClear()
    routerMocks.route.path = '/exception/500'
    routerMocks.route.query = {}
  })

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
          'a-button': ButtonStub,
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

  it('should preserve activeTenantId when returning home', async () => {
    routerMocks.route.query = { activeTenantId: '12' }

    const wrapper = mount(Page500, {
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

    await wrapper.find('button').trigger('click')

    expect(routerMocks.push).toHaveBeenCalledWith({
      path: '/',
      query: { activeTenantId: '12' },
    })
  })
})
