import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(() => Promise.resolve()),
  go: vi.fn(),
  route: {
    path: '/exception/400',
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

import Page400 from '@/views/exception/400.vue'

describe('400.vue', () => {
  beforeEach(() => {
    routerMocks.push.mockClear()
    routerMocks.go.mockClear()
    routerMocks.route.path = '/exception/400'
    routerMocks.route.query = {}
  })

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
          'a-button': ButtonStub,
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

  it('should preserve activeTenantId when returning scheduling dag list', async () => {
    routerMocks.route.query = {
      path: '/scheduling/dag/detail',
      activeTenantId: '9',
    }

    const wrapper = mount(Page400, {
      global: {
        stubs: {
          'a-card': PassThrough,
          'a-divider': PassThrough,
          'a-descriptions': PassThrough,
          'a-descriptions-item': PassThrough,
          'a-typography-text': PassThrough,
          'a-tooltip': PassThrough,
          'a-button': ButtonStub,
          CloseCircleOutlined: PassThrough,
          HomeOutlined: PassThrough,
          ArrowLeftOutlined: PassThrough,
          InfoCircleOutlined: PassThrough,
        },
      },
    })

    await wrapper.findAll('button')[1]?.trigger('click')

    expect(routerMocks.push).toHaveBeenCalledWith({
      path: '/scheduling/dag',
      query: { activeTenantId: '9' },
    })
  })
})
