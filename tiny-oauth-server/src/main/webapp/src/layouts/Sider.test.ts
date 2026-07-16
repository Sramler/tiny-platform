import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  routerPush: vi.fn(),
  route: { path: '/' },
  messageWarning: vi.fn(),
  messageError: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mocks.routerPush,
  }),
  useRoute: () => mocks.route,
}))

vi.mock('@/api/menu', () => ({
  menuTree: vi.fn(),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    warning: mocks.messageWarning,
    error: mocks.messageError,
  },
}))

import Sider from '@/layouts/Sider.vue'
import { updateMenuRouteState } from '@/router/menuState'

describe('Sider.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    mocks.route.path = '/'
    updateMenuRouteState({ menus: [], loaded: false, loading: false, error: null })
  })

  it('should fallback to first visible child when directory redirect points to a missing menu', async () => {
    updateMenuRouteState({
      loaded: true,
      menus: [
      {
        id: 1,
        title: '系统管理',
        name: 'system',
        url: '/system',
        redirect: '/system/role',
        children: [
          {
            id: 2,
            title: '用户管理',
            name: 'user',
            url: '/system/user',
          },
        ],
      },
      ],
    })

    const wrapper = mount(Sider, {
      global: {
        stubs: {
          Icon: {
            template: '<i />',
          },
        },
      },
    })

    await flushPromises()
    await wrapper.get('.menu-item').trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith('/system/user')
  })

  it('should keep using directory redirect when it still points to a visible descendant', async () => {
    updateMenuRouteState({
      loaded: true,
      menus: [
      {
        id: 1,
        title: '系统管理',
        name: 'system',
        url: '/system',
        redirect: '/system/role',
        children: [
          {
            id: 2,
            title: '角色管理',
            name: 'role',
            url: '/system/role',
          },
          {
            id: 3,
            title: '用户管理',
            name: 'user',
            url: '/system/user',
          },
        ],
      },
      ],
    })

    const wrapper = mount(Sider, {
      global: {
        stubs: {
          Icon: {
            template: '<i />',
          },
        },
      },
    })

    await flushPromises()
    await wrapper.get('.menu-item').trigger('click')

    expect(mocks.routerPush).toHaveBeenCalledWith('/system/role')
  })
})
