import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'

const platformUserMocks = vi.hoisted(() => ({
  listPlatformUsers: vi.fn(),
  getPlatformUserDetail: vi.fn(),
  createPlatformUser: vi.fn(),
  updatePlatformUserStatus: vi.fn(),
}))

const platformTenantUserMocks = vi.hoisted(() => ({
  listPlatformTenantUsers: vi.fn(),
  getPlatformTenantUserDetail: vi.fn(),
}))

const tenantMocks = vi.hoisted(() => ({
  tenantList: vi.fn(),
  getTenantById: vi.fn(),
}))

const uiMocks = vi.hoisted(() => ({
  messageError: vi.fn(),
  messageSuccess: vi.fn(),
  messageWarning: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  token: 'platform-token',
  refreshTokenAfterActiveScopeSwitch: vi.fn(),
  fetchWithAuth: vi.fn(),
}))

const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
  replace: vi.fn(),
}))

const routeState = reactive({
  query: {} as Record<string, unknown>,
})

vi.mock('@/api/platform-user', () => ({
  listPlatformUsers: platformUserMocks.listPlatformUsers,
  getPlatformUserDetail: platformUserMocks.getPlatformUserDetail,
  createPlatformUser: platformUserMocks.createPlatformUser,
  updatePlatformUserStatus: platformUserMocks.updatePlatformUserStatus,
}))

vi.mock('@/api/platform-tenant-user', () => ({
  listPlatformTenantUsers: platformTenantUserMocks.listPlatformTenantUsers,
  getPlatformTenantUserDetail: platformTenantUserMocks.getPlatformTenantUserDetail,
}))

vi.mock('@/api/tenant', () => ({
  tenantList: tenantMocks.tenantList,
  getTenantById: tenantMocks.getTenantById,
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: uiMocks.messageError,
    success: uiMocks.messageSuccess,
    warning: uiMocks.messageWarning,
  },
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: { value: { access_token: authMocks.token } },
    fetchWithAuth: authMocks.fetchWithAuth,
  }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerMocks.push, replace: routerMocks.replace }),
  useRoute: () => routeState,
}))

vi.mock('@/utils/jwt', () => ({
  decodeJwtPayload: (token?: string) => {
    if (token === 'platform-token' || token === 'platform-tenant-token' || token === 'platform-no-perm') {
      return { activeScopeType: 'PLATFORM' }
    }
    return { activeScopeType: 'TENANT' }
  },
  extractAuthoritiesFromJwt: (token?: string) => {
    if (token === 'platform-token') {
      return ['platform:user:list', 'platform:user:create', 'platform:user:disable']
    }
    if (token === 'platform-tenant-token') {
      return [
        'platform:user:list',
        'platform:user:create',
        'platform:user:disable',
        'system:tenant:list',
        'system:tenant:view',
        'system:user:list',
      ]
    }
    return []
  },
}))

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

const CardStub = defineComponent({
  props: { title: { type: String, default: '' } },
  template: '<div><div class="card-title">{{ title }}</div><slot /></div>',
})

const TabsStub = defineComponent({
  template: '<div><slot /></div>',
})

const TabPaneStub = defineComponent({
  props: { tab: { type: String, default: '' } },
  template: '<div><div class="tab-label">{{ tab }}</div><slot /></div>',
})

const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})

const TableStub = defineComponent({
  props: {
    columns: { type: Array, default: () => [] },
    dataSource: { type: Array, default: () => [] },
    rowClassName: { type: [Function, String], default: '' },
  },
  setup(props, { slots }) {
    return () => {
      const columns = Array.isArray(props.columns) ? props.columns : []
      const dataSource = Array.isArray(props.dataSource) ? props.dataSource : []

      if (columns.length > 0) {
        return h(
          'div',
          { class: 'table-stub' },
          dataSource.map((record: any, index: number) =>
            h(
              'div',
              {
                class:
                  typeof props.rowClassName === 'function'
                    ? props.rowClassName(record, index)
                    : props.rowClassName,
              },
              columns.map((column: any) =>
                h(
                  'div',
                  { class: `table-stub-cell-${String(column.dataIndex || column.key || 'unknown')}` },
                  slots.bodyCell
                    ? slots.bodyCell({
                        column,
                        record,
                        index,
                      })
                    : null,
                ),
              ),
            ),
          ),
        )
      }

      return h('div', { class: 'table-stub-default' }, slots.default ? slots.default() : [])
    }
  },
})

const TableColumnStub = defineComponent({
  template: '<div />',
})

const ModalStub = defineComponent({
  props: ['open', 'title', 'confirmLoading', 'footer'],
  emits: ['update:open', 'ok'],
  template: '<div v-if="open"><slot /></div>',
})

const DrawerStub = defineComponent({
  props: ['open', 'title', 'width'],
  emits: ['update:open', 'close'],
  template:
    '<div v-if="open"><button class="drawer-close" @click="$emit(\'update:open\', false); $emit(\'close\')">close</button><slot /><slot name="footer" /></div>',
})

function mountPlatformUsers() {
  return mount(PlatformUsers, {
    global: {
      stubs: {
        'a-card': CardStub,
        'a-tabs': TabsStub,
        'a-tab-pane': TabPaneStub,
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-space': PassThrough,
        'a-input': PassThrough,
        'a-input-number': PassThrough,
        'a-select': PassThrough,
        'a-select-option': PassThrough,
        'a-button': ButtonStub,
        'a-table': TableStub,
        'a-table-column': TableColumnStub,
        'a-tag': PassThrough,
        'a-tooltip': PassThrough,
        'a-dropdown': PassThrough,
        'a-menu': PassThrough,
        'a-menu-item': PassThrough,
        'a-popover': PassThrough,
        'a-checkbox': PassThrough,
        'a-pagination': PassThrough,
        'a-modal': ModalStub,
        'a-drawer': DrawerStub,
        'a-descriptions': PassThrough,
        'a-descriptions-item': PassThrough,
        'a-spin': PassThrough,
        'a-empty': PassThrough,
      },
    },
  })
}

import PlatformUsers from '@/views/platform/users/PlatformUsers.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

function createFetchResponse(ok: boolean, status: number, payload: Record<string, unknown> = {}) {
  return {
    ok,
    status,
    json: vi.fn().mockResolvedValue(payload),
  }
}

describe('PlatformUsers.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authMocks.token = 'platform-token'
    authMocks.refreshTokenAfterActiveScopeSwitch.mockResolvedValue({ ok: true })
    authMocks.fetchWithAuth.mockResolvedValue(createFetchResponse(true, 200, {}))
    routerMocks.push.mockReset()
    routerMocks.replace.mockReset()
    routeState.query = {}
    platformUserMocks.listPlatformUsers.mockResolvedValue({
      records: [{ userId: 1001, username: 'platform_admin', displayName: 'Platform Admin', platformStatus: 'ACTIVE' }],
      total: 1,
    })
    platformUserMocks.getPlatformUserDetail.mockResolvedValue({
      userId: 1001,
      username: 'platform_admin',
      displayName: 'Platform Admin',
      platformStatus: 'ACTIVE',
    })
    platformUserMocks.createPlatformUser.mockResolvedValue(undefined)
    platformUserMocks.updatePlatformUserStatus.mockResolvedValue(undefined)
    platformTenantUserMocks.listPlatformTenantUsers.mockResolvedValue({
      records: [{ id: 3001, username: 'tenant.alice', nickname: 'Alice', enabled: true }],
      total: 1,
    })
    platformTenantUserMocks.getPlatformTenantUserDetail.mockResolvedValue({
      id: 3001,
      username: 'tenant.alice',
      nickname: 'Alice',
      enabled: true,
      accountNonExpired: true,
      accountNonLocked: true,
      credentialsNonExpired: true,
    })
    tenantMocks.tenantList.mockResolvedValue({
      content: [{ id: 9, code: 'tenant-9', name: 'Tenant 9', enabled: true, lifecycleStatus: 'ACTIVE' }],
      totalElements: 1,
    })
    tenantMocks.getTenantById.mockResolvedValue({
      id: 9,
      code: 'tenant-9',
      name: 'Tenant 9',
      enabled: true,
      lifecycleStatus: 'ACTIVE',
    })
  })

  it('loads platform users under platform scope with read authority', async () => {
    const wrapper = mountPlatformUsers()
    await flushPromises()

    expect(platformUserMocks.listPlatformUsers).toHaveBeenCalledWith({
      current: 1,
      pageSize: 10,
      keyword: undefined,
      enabled: undefined,
      status: undefined,
    })
    expect(tenantMocks.tenantList).not.toHaveBeenCalled()
    expect(wrapper.find('.content-container').exists()).toBe(true)
    expect(wrapper.find('.content-card').exists()).toBe(true)
    expect(wrapper.find('.form-container').exists()).toBe(true)
    expect(wrapper.find('.toolbar-container').exists()).toBe(true)
    expect(wrapper.find('.table-container').exists()).toBe(true)
    expect(wrapper.find('.pagination-container').exists()).toBe(true)
    expect(wrapper.findAll('.action-icon').length).toBeGreaterThan(0)
    expect(wrapper.text()).toContain('platform_user_profile')
    expect(wrapper.text()).toContain('创建平台用户档案')
    expect(wrapper.text()).toContain('租户用户代管')
  })

  it('loads tenant stewardship entry points when tenant read authority is present', async () => {
    authMocks.token = 'platform-tenant-token'

    const wrapper = mountPlatformUsers()
    await flushPromises()

    expect(platformUserMocks.listPlatformUsers).toHaveBeenCalled()
    expect(tenantMocks.tenantList).toHaveBeenCalledWith({
      code: undefined,
      name: undefined,
      page: 0,
      size: 5,
    })
    expect(wrapper.text()).toContain('进入租户用户管理')
    expect(wrapper.text()).toContain('前往平台租户治理')
  })

  it('opens create drawer with grouped platform profile form sections', async () => {
    const wrapper = mountPlatformUsers()
    await flushPromises()

    const createButton = wrapper.findAll('button').find((button) => button.text().includes('创建平台用户档案'))
    expect(createButton).toBeDefined()

    await createButton!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('关联基础用户')
    expect(wrapper.text()).toContain('平台档案设置')
  })

  it('does not load platform users under tenant scope', async () => {
    authMocks.token = 'tenant-token'

    const wrapper = mountPlatformUsers()
    await flushPromises()

    expect(platformUserMocks.listPlatformUsers).not.toHaveBeenCalled()
    expect(tenantMocks.tenantList).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('当前页面只支持 PLATFORM 作用域')
  })

  it('does not load platform users without read authority', async () => {
    authMocks.token = 'platform-no-perm'

    const wrapper = mountPlatformUsers()
    await flushPromises()

    expect(platformUserMocks.listPlatformUsers).not.toHaveBeenCalled()
    expect(tenantMocks.tenantList).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('缺少')
    expect(wrapper.text()).toContain('/platform/users')
  })

  it('navigates to platform tenant governance from the stewardship entry area', async () => {
    authMocks.token = 'platform-tenant-token'

    const wrapper = mountPlatformUsers()
    await flushPromises()

    const tenantButton = wrapper.findAll('button').find((button) => button.text().includes('前往平台租户治理'))
    expect(tenantButton).toBeDefined()

    await tenantButton!.trigger('click')

    expect(routerMocks.push).toHaveBeenCalledWith('/platform/tenants')
  })

  it('checks tenant detail readability before navigating to tenant detail', async () => {
    authMocks.token = 'platform-tenant-token'
    authMocks.fetchWithAuth.mockResolvedValue(createFetchResponse(true, 200, {}))

    const wrapper = mountPlatformUsers()
    await flushPromises()

    const detailButton = wrapper.findAll('button').find((button) => button.text().includes('平台详情'))
    expect(detailButton).toBeDefined()

    await detailButton!.trigger('click')
    await flushPromises()

    expect(authMocks.fetchWithAuth).toHaveBeenCalledWith(
      expect.stringContaining('/sys/tenants/9'),
      expect.objectContaining({
        method: 'GET',
      }),
    )
    expect(routerMocks.push).toHaveBeenCalledWith({
      path: '/platform/tenants/9',
      query: {
        from: '/platform/users?tab=tenantStewardship&tenantId=9',
      },
    })
  })

  it('locks stewardship tenant and loads platform bridge user list instead of switching scope', async () => {
    authMocks.token = 'platform-tenant-token'

    const wrapper = mountPlatformUsers()
    await flushPromises()

    const manageButton = wrapper.findAll('button').find((button) => button.text().includes('进入租户用户管理'))
    expect(manageButton).toBeDefined()

    await manageButton!.trigger('click')
    await flushPromises()

    expect(platformTenantUserMocks.listPlatformTenantUsers).toHaveBeenCalledWith({
      tenantId: 9,
      current: 1,
      pageSize: 10,
      username: undefined,
      nickname: undefined,
    })
    expect(wrapper.text()).toContain('已锁定代管租户')
    expect(wrapper.text()).toContain('当前代管目标')
    expect(wrapper.find('.tenant-entry-row--active').exists()).toBe(true)
    expect(routerMocks.replace).toHaveBeenCalledWith({
      path: '/platform/users',
      query: {
        tab: 'tenantStewardship',
        tenantId: '9',
      },
    })
    expect(authMocks.fetchWithAuth).not.toHaveBeenCalledWith(
      expect.stringContaining('/sys/users/current/active-scope'),
      expect.anything(),
    )
  })

  it('restores tenant stewardship context from route query when returning from tenant detail', async () => {
    authMocks.token = 'platform-tenant-token'
    routeState.query = {
      tab: 'tenantStewardship',
      tenantId: '9',
    }

    const wrapper = mountPlatformUsers()
    await flushPromises()
    await flushPromises()

    expect(platformTenantUserMocks.listPlatformTenantUsers).toHaveBeenCalledWith({
      tenantId: 9,
      current: 1,
      pageSize: 10,
      username: undefined,
      nickname: undefined,
    })
    expect(wrapper.text()).toContain('已锁定代管租户')
    expect(wrapper.find('.tenant-entry-row--active').exists()).toBe(true)
  })

  it('closes tenant stewardship drawer through common drawer close interaction', async () => {
    authMocks.token = 'platform-tenant-token'

    const wrapper = mountPlatformUsers()
    await flushPromises()

    const manageButton = wrapper.findAll('button').find((button) => button.text().includes('进入租户用户管理'))
    expect(manageButton).toBeDefined()

    await manageButton!.trigger('click')
    await flushPromises()

    const closeButton = wrapper.find('.drawer-close')
    expect(closeButton.exists()).toBe(true)

    await closeButton.trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('已锁定代管租户')
    expect(wrapper.find('.tenant-entry-row--active').exists()).toBe(false)
    expect(routerMocks.replace).toHaveBeenLastCalledWith({
      path: '/platform/users',
      query: {
        tab: 'tenantStewardship',
      },
    })
  })
})
