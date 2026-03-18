import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(() => Promise.resolve()),
  go: vi.fn(),
  route: {
    path: '/exception/404',
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

import Page404 from '@/views/exception/404.vue'

describe('404.vue', () => {
  beforeEach(() => {
    routerMocks.push.mockClear()
    routerMocks.go.mockClear()
    routerMocks.route.path = '/exception/404'
    routerMocks.route.query = {}
  })

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
          'a-button': ButtonStub,
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

  it('should preserve activeTenantId when returning home', async () => {
    routerMocks.route.query = { activeTenantId: '6' }

    const wrapper = mount(Page404, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-divider': PassThrough,
          'a-descriptions': PassThrough,
          'a-descriptions-item': PassThrough,
          'a-typography-text': PassThrough,
          'a-tooltip': PassThrough,
          'a-button': ButtonStub,
          FileSearchOutlined: PassThrough,
          HomeOutlined: PassThrough,
          ArrowLeftOutlined: PassThrough,
          InfoCircleOutlined: PassThrough,
        },
      },
    })

    await wrapper.findAll('button')[0]?.trigger('click')

    expect(routerMocks.push).toHaveBeenCalledWith({
      path: '/',
      query: { activeTenantId: '6' },
    })
  })
})
