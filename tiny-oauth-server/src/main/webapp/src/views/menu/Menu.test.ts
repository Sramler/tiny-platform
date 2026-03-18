import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  menuList: vi.fn(),
  getMenusByParentId: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  authUser: { value: null as { access_token?: string | null } | null },
}))

vi.mock('@/api/menu', () => ({
  menuList: apiMocks.menuList,
  menuTree: vi.fn(),
  menuTreeAll: vi.fn(),
  createMenu: vi.fn(),
  updateMenu: vi.fn(),
  deleteMenu: vi.fn(),
  batchDeleteMenus: vi.fn(),
  getMenusByParentId: apiMocks.getMenusByParentId,
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: authMocks.authUser,
  }),
}))

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

import Menu from '@/views/menu/Menu.vue'

function createToken(authorities: string[]) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities })).toString('base64url')
  return `${header}.${payload}.signature`
}

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('Menu.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.menuList.mockResolvedValue([
      { id: 1, name: 'sys', title: '系统', type: 0, parentId: null, enabled: true },
    ])
    apiMocks.getMenusByParentId.mockResolvedValue([])
    authMocks.authUser.value = {
      access_token: createToken(['system:menu:list']),
    }
  })

  it('should display menu list title and load data on mount', async () => {
    const wrapper = mount(Menu, {
      global: {
        stubs: {
          'a-table': defineComponent({
            props: ['dataSource'],
            template: '<div class="menu-table-stub">table rows: {{ (dataSource || []).length }}</div>',
          }),
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-tooltip': PassThrough,
          'a-popover': PassThrough,
          'a-checkbox': PassThrough,
          'a-tag': PassThrough,
          'a-modal': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          'a-drawer': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          VueDraggable: PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          EditOutlined: PassThrough,
          DeleteOutlined: PassThrough,
          SettingOutlined: PassThrough,
          HolderOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('菜单列表')
    expect(apiMocks.menuList).toHaveBeenCalled()
  })

  it('should not request menu list without menu management authority', async () => {
    authMocks.authUser.value = {
      access_token: createToken(['ROLE_USER']),
    }

    const wrapper = mount(Menu, {
      global: {
        stubs: {
          'a-table': defineComponent({
            props: ['dataSource'],
            template: '<div class="menu-table-stub">table rows: {{ (dataSource || []).length }}</div>',
          }),
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': PassThrough,
          'a-tooltip': PassThrough,
          'a-popover': PassThrough,
          'a-checkbox': PassThrough,
          'a-tag': PassThrough,
          'a-modal': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          'a-drawer': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          VueDraggable: PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          EditOutlined: PassThrough,
          DeleteOutlined: PassThrough,
          SettingOutlined: PassThrough,
          HolderOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(apiMocks.menuList).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('菜单管理需要额外授权')
  })
})
