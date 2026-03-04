import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  routerPush: vi.fn(),
  removeUser: vi.fn(),
  user: { value: { id: 1 } as { id: number } | null },
}))

const routeState: { path: string; query: Record<string, unknown> } = {
  path: '/exception/401',
  query: {},
}

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mocks.routerPush,
  }),
  useRoute: () => routeState,
}))

vi.mock('@/auth/oidc', () => ({
  userManager: {
    removeUser: mocks.removeUser,
  },
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: mocks.user,
  }),
}))

import Error401 from '@/views/exception/401.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

async function waitFor(assertion: () => void) {
  let lastError: unknown
  for (let index = 0; index < 10; index += 1) {
    try {
      assertion()
      return
    } catch (error) {
      lastError = error
      await flushPromises()
    }
  }
  throw lastError
}

describe('401.vue', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mocks.routerPush.mockReset()
    mocks.removeUser.mockReset()
    mocks.removeUser.mockResolvedValue(undefined)
    mocks.user.value = { id: 1 }
    routeState.path = '/exception/401'
    routeState.query = {
      path: '/api/secure',
      message: 'expired',
      traceId: 'trace-401',
    }
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should cleanup local auth state and auto redirect to login after countdown', async () => {
    const wrapper = mount(Error401, {
      global: {
        stubs: {
          'a-card': { template: '<div><slot /></div>' },
          'a-alert': { props: ['message'], template: '<div>{{ message }}</div>' },
          'a-divider': { template: '<div><slot /></div>' },
          'a-descriptions': { template: '<div><slot /></div>' },
          'a-descriptions-item': { template: '<div><slot /></div>' },
          'a-typography-text': { template: '<span><slot /></span>' },
          'a-tooltip': { template: '<span><slot /></span>' },
          'a-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
          LockOutlined: { template: '<i />' },
          LoginOutlined: { template: '<i />' },
          HomeOutlined: { template: '<i />' },
          InfoCircleOutlined: { template: '<i />' },
        },
      },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('expired')
    expect(wrapper.text()).toContain('trace-401')
    await waitFor(() => {
      expect(mocks.removeUser).toHaveBeenCalledTimes(1)
    })

    await vi.advanceTimersByTimeAsync(5000)
    await flushPromises()

    expect(mocks.routerPush).toHaveBeenCalledWith('/login')
  })

  it('should navigate home when clicking home button', async () => {
    const wrapper = mount(Error401, {
      global: {
        stubs: {
          'a-card': { template: '<div><slot /></div>' },
          'a-alert': { props: ['message'], template: '<div>{{ message }}</div>' },
          'a-divider': { template: '<div><slot /></div>' },
          'a-descriptions': { template: '<div><slot /></div>' },
          'a-descriptions-item': { template: '<div><slot /></div>' },
          'a-typography-text': { template: '<span><slot /></span>' },
          'a-tooltip': { template: '<span><slot /></span>' },
          'a-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
          LockOutlined: { template: '<i />' },
          LoginOutlined: { template: '<i />' },
          HomeOutlined: { template: '<i />' },
          InfoCircleOutlined: { template: '<i />' },
        },
      },
    })

    await flushPromises()
    const buttons = wrapper.findAll('button')
    await buttons[1]!.trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith('/')
  })
})
