import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  menuList: vi.fn(),
  getMenusByParentId: vi.fn(),
  getRuntimeUiActions: vi.fn(),
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
    isAuthenticated: { value: true },
    login: vi.fn(),
    logout: vi.fn(),
    getAccessToken: vi.fn(),
    fetchWithAuth: vi.fn(),
  }),
  initPromise: Promise.resolve(),
}))

vi.mock('@/api/resource', () => ({
  getRuntimeUiActions: apiMocks.getRuntimeUiActions,
}))

vi.mock('@/utils/logger', () => {
  const logger = {
    debug: vi.fn(),
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    log: vi.fn(),
    group: vi.fn(),
    groupEnd: vi.fn(),
    table: vi.fn(),
  }
  return {
    logger,
    persistentLogger: logger,
    default: logger,
  }
})

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

const TableStub = defineComponent({
  name: 'TableStub',
  props: [
    'dataSource',
    'scroll',
    'indentSize',
    'childrenColumnName',
    'expandIconColumnIndex',
    'expandIcon',
    'rowExpandable',
    'expandedRowKeys',
    'onExpand',
    'onExpandedRowsChange',
  ],
  template: `
    <div class="menu-table-stub ant-table">
      <div class="ant-table-header"></div>
      <table>
        <thead class="ant-table-thead">
          <tr><th>标题</th></tr>
        </thead>
        <tbody class="ant-table-tbody">
          <tr v-for="record in (dataSource || [])" :key="record.id">
            <td>{{ record.title || record.name || record.id }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="!(dataSource || []).length" class="ant-table-placeholder"></div>
    </div>
  `,
})

const globalStubs = {
  'a-table': TableStub,
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
  MinusOutlined: PassThrough,
  ReloadOutlined: PassThrough,
  EditOutlined: PassThrough,
  DeleteOutlined: PassThrough,
  SettingOutlined: PassThrough,
  HolderOutlined: PassThrough,
}

function firstItem<T>(items: T[] | undefined, label = 'item'): T {
  if (!items || items.length === 0) {
    throw new Error(`Expected at least one ${label}`)
  }
  return items[0]!
}

import { ACTIVE_SCOPE_CHANGED_EVENT } from '@/utils/activeScopeEvents'
import Menu from '@/views/menu/Menu.vue'

function createToken(authorities: string[]) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities })).toString('base64url')
  return `${header}.${payload}.signature`
}

async function flushPromises() {
  for (let index = 0; index < 6; index += 1) {
    await Promise.resolve()
    await nextTick()
  }
}

describe('Menu.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.menuList.mockResolvedValue([
      { id: 1, name: 'sys', title: '系统', type: 0, parentId: null, enabled: true },
    ])
    apiMocks.getMenusByParentId.mockResolvedValue([])
    apiMocks.getRuntimeUiActions.mockResolvedValue([])
    authMocks.authUser.value = {
      access_token: createToken(['system:menu:list']),
    }
    window.history.replaceState({}, '', '/system/menu')
  })

  it('should display menu list title and load data on mount', async () => {
    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('菜单列表')
    expect(apiMocks.menuList).toHaveBeenCalled()
    const firstCallParams = apiMocks.menuList.mock.calls[0]?.[0] as Record<string, unknown>
    expect(firstCallParams).toBeTruthy()
    expect(firstCallParams).not.toHaveProperty('permission')
  })

  it('should refetch menu list when active scope changes', async () => {
    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()
    const callsAfterMount = apiMocks.menuList.mock.calls.length
    expect(callsAfterMount).toBeGreaterThanOrEqual(1)
    window.dispatchEvent(new CustomEvent(ACTIVE_SCOPE_CHANGED_EVENT))
    await flushPromises()
    expect(apiMocks.menuList.mock.calls.length).toBeGreaterThan(callsAfterMount)
    wrapper.unmount()
  })

  it('should not request menu list without menu management authority', async () => {
    authMocks.authUser.value = {
      access_token: createToken(['ROLE_USER']),
    }

    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    expect(apiMocks.menuList).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('菜单管理需要额外授权')
  })

  it('should hide write buttons when runtime ui actions are missing (fail-closed)', async () => {
    apiMocks.getRuntimeUiActions.mockResolvedValue([])
    authMocks.authUser.value = {
      access_token: createToken([
        'system:menu:list',
        'system:menu:create',
        'system:menu:edit',
        'system:menu:delete',
        'system:menu:batch-delete',
      ]),
    }

    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    expect(apiMocks.getRuntimeUiActions).toHaveBeenCalledWith('/system/menu')
    expect(wrapper.text()).not.toContain('新建菜单')
    expect(wrapper.text()).not.toContain('批量删除')
    expect(wrapper.text()).not.toContain('编辑')
    expect(wrapper.text()).not.toContain('删除')
  })

  it('should not force vertical table scroll for small datasets', async () => {
    apiMocks.menuList.mockResolvedValue(
      Array.from({ length: 5 }, (_, index) => ({
        id: index + 1,
        name: `menu-${index + 1}`,
        title: `菜单${index + 1}`,
        type: 1,
        parentId: null,
        enabled: true,
      })),
    )

    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    expect(wrapper.findComponent(TableStub).props('scroll')).toEqual({ x: 'max-content' })
  })

  it('should configure the table as a tree hierarchy', async () => {
    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    const table = wrapper.findComponent(TableStub)
    expect(table.props('childrenColumnName')).toBe('children')
    expect(table.props('indentSize')).toBe(24)
    expect(table.props('expandIconColumnIndex')).toBe(4)
    expect(typeof table.props('expandIcon')).toBe('function')
    expect(typeof table.props('rowExpandable')).toBe('function')
  })

  it('should not preload direct child menus for top-level directories on mount', async () => {
    apiMocks.menuList.mockResolvedValue([
      { id: 1, name: 'sys', title: '系统管理', type: 0, parentId: null, enabled: true, leaf: false, children: [] },
    ])
    apiMocks.getMenusByParentId.mockResolvedValue([
      { id: 2, name: 'sys-user', title: '用户管理', type: 1, parentId: 1, enabled: true, leaf: true },
    ])

    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    const table = wrapper.findComponent(TableStub)
    const data = table.props('dataSource') as Array<Record<string, any>>
    const firstNode = firstItem(data, 'menu row')
    expect(apiMocks.getMenusByParentId).not.toHaveBeenCalled()
    expect(data).toHaveLength(1)
    expect(firstNode.children).toEqual([])
  })

  it('should lazy load direct children when a top-level directory is expanded', async () => {
    apiMocks.menuList.mockResolvedValue([
      { id: 1, name: 'sys', title: '系统管理', type: 0, parentId: null, enabled: true, leaf: false, children: [] },
    ])
    apiMocks.getMenusByParentId.mockResolvedValue([
      { id: 2, name: 'sys-user', title: '用户管理', type: 1, parentId: 1, enabled: true, leaf: true },
    ])

    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    const table = wrapper.findComponent(TableStub)
    const beforeExpandData = table.props('dataSource') as Array<Record<string, any>>
    const onExpand = table.props('onExpand') as (expanded: boolean, record: Record<string, any>) => void
    expect(apiMocks.getMenusByParentId).not.toHaveBeenCalled()

    onExpand(true, firstItem(beforeExpandData, 'menu row'))
    await flushPromises()

    const afterExpandData = wrapper.findComponent(TableStub).props('dataSource') as Array<Record<string, any>>
    const firstNode = firstItem(afterExpandData, 'expanded menu row')
    const firstChild = firstItem(firstNode.children as Array<Record<string, any>>, 'child menu row')
    expect(apiMocks.getMenusByParentId).toHaveBeenCalledWith(1)
    expect(firstNode.children).toHaveLength(1)
    expect(firstChild.title).toBe('用户管理')
  })

  it('should keep a lazily loaded node expanded when expanded row keys are cleared before children mount', async () => {
    apiMocks.menuList.mockResolvedValue([
      { id: 1, name: 'sys', title: '系统管理', type: 0, parentId: null, enabled: true, leaf: false, children: [] },
    ])
    apiMocks.getMenusByParentId.mockResolvedValue([
      { id: 2, name: 'sys-user', title: '用户管理', type: 1, parentId: 1, enabled: true, leaf: true },
    ])

    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    const table = wrapper.findComponent(TableStub)
    const beforeExpandData = table.props('dataSource') as Array<Record<string, any>>
    const onExpand = table.props('onExpand') as (expanded: boolean, record: Record<string, any>) => void
    const onExpandedRowsChange = table.props('onExpandedRowsChange') as (expandedKeys: Array<string | number>) => void

    onExpand(true, firstItem(beforeExpandData, 'menu row'))
    onExpandedRowsChange([])
    await flushPromises()

    const afterExpandData = wrapper.findComponent(TableStub).props('dataSource') as Array<Record<string, any>>
    const afterExpandedRowKeys = wrapper.findComponent(TableStub).props('expandedRowKeys') as string[]
    const firstNode = firstItem(afterExpandData, 'expanded menu row')

    expect(apiMocks.getMenusByParentId).toHaveBeenCalledWith(1)
    expect(firstNode.children).toHaveLength(1)
    expect(afterExpandedRowKeys).toEqual(['1'])
  })

  it('should lazy load direct children even when non-leaf rows are returned with empty children arrays', async () => {
    apiMocks.menuList.mockResolvedValue([
      { id: 2, name: 'system', title: '系统管理', type: 0, parentId: null, enabled: true, leaf: false, children: [] },
    ])
    apiMocks.getMenusByParentId.mockResolvedValue([
      { id: 21, name: 'role', title: '角色管理', type: 1, parentId: 2, enabled: true, leaf: true },
    ])

    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    const table = wrapper.findComponent(TableStub)
    const beforeExpandData = table.props('dataSource') as Array<Record<string, any>>
    const onExpand = table.props('onExpand') as (expanded: boolean, record: Record<string, any>) => void

    onExpand(true, firstItem(beforeExpandData, 'menu row'))
    await flushPromises()

    const afterExpandData = wrapper.findComponent(TableStub).props('dataSource') as Array<Record<string, any>>
    const firstNode = firstItem(afterExpandData, 'expanded menu row')
    const firstChild = firstItem(firstNode.children as Array<Record<string, any>>, 'child menu row')
    expect(apiMocks.getMenusByParentId).toHaveBeenCalledWith(2)
    expect(firstNode.children).toHaveLength(1)
    expect(firstChild.title).toBe('角色管理')
  })

  it('should attach lazy-loaded children when menu ids are returned as strings', async () => {
    apiMocks.menuList.mockResolvedValue([
      { id: '2', name: 'system', title: '系统管理', type: 0, parentId: null, enabled: true, leaf: false, children: [] },
    ])
    apiMocks.getMenusByParentId.mockResolvedValue([
      { id: '21', name: 'role', title: '角色管理', type: 1, parentId: '2', enabled: true, leaf: true },
    ])

    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    const table = wrapper.findComponent(TableStub)
    const beforeExpandData = table.props('dataSource') as Array<Record<string, any>>
    const onExpand = table.props('onExpand') as (expanded: boolean, record: Record<string, any>) => void

    onExpand(true, firstItem(beforeExpandData, 'menu row'))
    await flushPromises()

    const afterExpandData = wrapper.findComponent(TableStub).props('dataSource') as Array<Record<string, any>>
    const firstNode = firstItem(afterExpandData, 'expanded menu row')
    const firstChild = firstItem(firstNode.children as Array<Record<string, any>>, 'child menu row')
    expect(apiMocks.getMenusByParentId).toHaveBeenCalledWith(2)
    expect(firstNode.children).toHaveLength(1)
    expect(firstChild.id).toBe('21')
    expect(firstChild.parentId).toBe('2')
  })

  it('should normalize non-leaf rows without real children into leaf rows after lazy loading', async () => {
    apiMocks.menuList.mockResolvedValue([
      { id: 1, name: 'workbench', title: '工作台', type: 0, parentId: null, enabled: true, leaf: false },
    ])
    apiMocks.getMenusByParentId.mockResolvedValue([])

    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    const table = wrapper.findComponent(TableStub)
    const beforeExpandData = table.props('dataSource') as Array<Record<string, any>>
    const onExpand = table.props('onExpand') as (expanded: boolean, record: Record<string, any>) => void

    onExpand(true, firstItem(beforeExpandData, 'menu row'))
    await flushPromises()

    const afterPreloadData = wrapper.findComponent(TableStub).props('dataSource') as Array<Record<string, any>>
    const afterExpandedRowKeys = wrapper.findComponent(TableStub).props('expandedRowKeys') as string[]
    const firstNode = firstItem(afterPreloadData, 'expanded menu row')
    expect(apiMocks.getMenusByParentId).toHaveBeenCalledWith(1)
    expect(firstNode.leaf).toBe(true)
    expect(firstNode.children).toBeUndefined()
    expect(afterExpandedRowKeys).toEqual([])
  })

  it('should keep expanded medium datasets using native container height', async () => {
    apiMocks.menuList.mockResolvedValue(
      Array.from({ length: 7 }, (_, index) => ({
        id: index + 1,
        name: `menu-${index + 1}`,
        title: `菜单${index + 1}`,
        type: 1,
        parentId: null,
        enabled: true,
      })),
    )

    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    expect(wrapper.findComponent(TableStub).props('scroll')).toEqual({ x: 'max-content' })
  })

  it('should keep larger datasets free of internal y scroll', async () => {
    apiMocks.menuList.mockResolvedValue(
      Array.from({ length: 10 }, (_, index) => ({
        id: index + 1,
        name: `menu-${index + 1}`,
        title: `菜单${index + 1}`,
        type: 1,
        parentId: null,
        enabled: true,
      })),
    )

    const wrapper = mount(Menu, {
      global: {
        stubs: globalStubs,
      },
    })
    await flushPromises()

    expect(wrapper.findComponent(TableStub).props('scroll')).toEqual({ x: 'max-content' })
  })
})
