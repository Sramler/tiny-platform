import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, reactive } from 'vue'
import PlatformUserGovernanceTab from '@/views/platform/users/components/PlatformUserGovernanceTab.vue'
import PlatformTenantStewardshipTab from '@/views/platform/users/components/PlatformTenantStewardshipTab.vue'
import PlatformUsers from '@/views/platform/users/PlatformUsers.vue'

const platformUserMocks = vi.hoisted(() => ({
  listPlatformUsers: vi.fn(),
  getPlatformUserDetail: vi.fn(),
  getPlatformUserRoles: vi.fn(),
  replacePlatformUserRoles: vi.fn(),
  createPlatformUser: vi.fn(),
  updatePlatformUserStatus: vi.fn(),
}))

const platformRoleMocks = vi.hoisted(() => ({
  listPlatformRoleOptions: vi.fn(),
}))

const platformRoleApprovalMocks = vi.hoisted(() => ({
  listPlatformRoleAssignmentRequests: vi.fn(),
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
  getPlatformUserRoles: platformUserMocks.getPlatformUserRoles,
  replacePlatformUserRoles: platformUserMocks.replacePlatformUserRoles,
  createPlatformUser: platformUserMocks.createPlatformUser,
  updatePlatformUserStatus: platformUserMocks.updatePlatformUserStatus,
}))

vi.mock('@/api/platform-role', () => ({
  listPlatformRoleOptions: platformRoleMocks.listPlatformRoleOptions,
}))

vi.mock('@/api/platform-role-approval', () => ({
  listPlatformRoleAssignmentRequests: platformRoleApprovalMocks.listPlatformRoleAssignmentRequests,
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
    if (
      token === 'platform-token'
      || token === 'platform-tenant-token'
      || token === 'platform-no-perm'
      || token === 'platform-readonly-token'
    ) {
      return { activeScopeType: 'PLATFORM' }
    }
    return { activeScopeType: 'TENANT' }
  },
  extractAuthoritiesFromJwt: (token?: string) => {
    if (token === 'platform-token') {
      return [
        'platform:user:list',
        'platform:user:create',
        'platform:user:disable',
        'platform:role:approval:list',
      ]
    }
    if (token === 'platform-readonly-token') {
      return ['platform:user:list']
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
  props: {
    activeKey: { type: String, default: '' },
  },
  emits: ['change'],
  template: '<div class="tabs-stub"><slot /></div>',
})

const TabPaneStub = defineComponent({
  props: {
    tab: { type: String, default: '' },
    disabled: { type: Boolean, default: false },
  },
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
                    : [],
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
        VueDraggable: PassThrough,
      },
    },
  })
}

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

function getPlatformGovernanceTab(wrapper: ReturnType<typeof mountPlatformUsers>) {
  return wrapper.findComponent(PlatformUserGovernanceTab)
}

function getTenantStewardshipTab(wrapper: ReturnType<typeof mountPlatformUsers>) {
  return wrapper.findComponent(PlatformTenantStewardshipTab)
}

describe('PlatformUsers.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authMocks.token = 'platform-token'
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
    platformUserMocks.getPlatformUserRoles.mockResolvedValue([
      { roleId: 11, code: 'PLATFORM_ADMIN', name: '平台管理员', enabled: true, builtin: true },
    ])
    platformUserMocks.replacePlatformUserRoles.mockResolvedValue([
      { roleId: 11, code: 'PLATFORM_ADMIN', name: '平台管理员', enabled: true, builtin: true },
      { roleId: 12, code: 'PLATFORM_AUDITOR', name: '平台审计员', enabled: true, builtin: false },
    ])
    platformUserMocks.createPlatformUser.mockResolvedValue(undefined)
    platformUserMocks.updatePlatformUserStatus.mockResolvedValue(undefined)
    platformRoleMocks.listPlatformRoleOptions.mockResolvedValue([
      { roleId: 11, code: 'PLATFORM_ADMIN', name: '平台管理员', enabled: true, builtin: true, approvalMode: 'NONE' },
      { roleId: 12, code: 'PLATFORM_AUDITOR', name: '平台审计员', enabled: true, builtin: false, approvalMode: 'ONE_STEP' },
    ])
    platformRoleApprovalMocks.listPlatformRoleAssignmentRequests.mockResolvedValue({ records: [], total: 0 })
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

  it('loads platform user governance tab by default under platform scope with read authority', async () => {
    const wrapper = mountPlatformUsers()
    await flushPromises()

    expect(getPlatformGovernanceTab(wrapper).exists()).toBe(true)
    expect(getTenantStewardshipTab(wrapper).exists()).toBe(false)
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
    expect(wrapper.text()).toContain('平台用户治理')
    expect(wrapper.text()).toContain('租户用户代管')
    expect(wrapper.text()).toContain('用户列表')
    expect(wrapper.text()).toContain('新建')
  })

  it('loads tenant stewardship tab when route requests it and tenant authorities are present', async () => {
    authMocks.token = 'platform-tenant-token'
    routeState.query = {
      tab: 'tenantStewardship',
    }

    const wrapper = mountPlatformUsers()
    await flushPromises()

    expect(getPlatformGovernanceTab(wrapper).exists()).toBe(false)
    expect(getTenantStewardshipTab(wrapper).exists()).toBe(true)
    expect(platformUserMocks.listPlatformUsers).not.toHaveBeenCalled()
    expect(tenantMocks.tenantList).toHaveBeenCalledWith({
      page: 0,
      size: 200,
    })
    expect((getTenantStewardshipTab(wrapper).vm as any).tenantSelectOptions).toEqual(
      expect.arrayContaining([{ value: 9, label: 'Tenant 9 (tenant-9)' }]),
    )
    expect(wrapper.text()).toContain('租户侧用户列表')
  })

  it('opens create drawer with grouped platform profile form sections', async () => {
    const wrapper = mountPlatformUsers()
    await flushPromises()

    const createButton = wrapper.findAll('button').find((button) => button.text().includes('新建'))
    expect(createButton).toBeDefined()

    await createButton!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('关联基础用户')
    expect(wrapper.text()).toContain('平台档案设置')
  })

  it('loads bound role details when opening platform user detail drawer', async () => {
    const wrapper = mountPlatformUsers()
    await flushPromises()

    await (getPlatformGovernanceTab(wrapper).vm as any).showDetail({
      userId: 1001,
      username: 'platform_admin',
      platformStatus: 'ACTIVE',
    })
    await flushPromises()

    expect(platformUserMocks.getPlatformUserDetail).toHaveBeenCalledWith(1001)
    expect(platformUserMocks.getPlatformUserRoles).toHaveBeenCalledWith(1001)
    expect(platformRoleApprovalMocks.listPlatformRoleAssignmentRequests).toHaveBeenCalledWith({
      targetUserId: 1001,
      status: 'PENDING',
      current: 1,
      pageSize: 1,
    })
    expect(wrapper.text()).toContain('平台角色绑定明细')
  })

  it('does not load approval summary without approval permissions', async () => {
    authMocks.token = 'platform-readonly-token'

    const wrapper = mountPlatformUsers()
    await flushPromises()

    await (getPlatformGovernanceTab(wrapper).vm as any).showDetail({
      userId: 1001,
      username: 'platform_admin',
      platformStatus: 'ACTIVE',
    })
    await flushPromises()

    expect(platformRoleApprovalMocks.listPlatformRoleAssignmentRequests).not.toHaveBeenCalled()
  })

  it('submits role binding updates through platform user role API', async () => {
    const wrapper = mountPlatformUsers()
    await flushPromises()

    await (getPlatformGovernanceTab(wrapper).vm as any).openRoleEditor(1001)
    await flushPromises()
    await (getPlatformGovernanceTab(wrapper).vm as any).submitRoleEditor()
    await flushPromises()

    expect(platformRoleMocks.listPlatformRoleOptions).toHaveBeenCalledWith({
      limit: 200,
    })
    expect(platformUserMocks.getPlatformUserRoles).toHaveBeenCalledWith(1001)
    expect(platformUserMocks.replacePlatformUserRoles).toHaveBeenCalledWith(1001, [11])
  })

  it('does not open role editor when platform user update authority is missing', async () => {
    authMocks.token = 'platform-readonly-token'

    const wrapper = mountPlatformUsers()
    await flushPromises()

    await (getPlatformGovernanceTab(wrapper).vm as any).openRoleEditor(1001)
    await flushPromises()

    expect(platformRoleMocks.listPlatformRoleOptions).not.toHaveBeenCalled()
    expect(platformUserMocks.replacePlatformUserRoles).not.toHaveBeenCalled()
    expect(uiMocks.messageWarning).toHaveBeenCalledWith('当前会话缺少平台用户更新权限，无法编辑平台角色')
  })

  it('does not load platform tabs under tenant scope', async () => {
    authMocks.token = 'tenant-token'

    const wrapper = mountPlatformUsers()
    await flushPromises()

    expect(getPlatformGovernanceTab(wrapper).exists()).toBe(false)
    expect(getTenantStewardshipTab(wrapper).exists()).toBe(false)
    expect(platformUserMocks.listPlatformUsers).not.toHaveBeenCalled()
    expect(tenantMocks.tenantList).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('当前页面只支持 PLATFORM 作用域')
  })

  it('does not load platform tabs without required read authorities', async () => {
    authMocks.token = 'platform-no-perm'

    const wrapper = mountPlatformUsers()
    await flushPromises()

    expect(getPlatformGovernanceTab(wrapper).exists()).toBe(false)
    expect(getTenantStewardshipTab(wrapper).exists()).toBe(false)
    expect(platformUserMocks.listPlatformUsers).not.toHaveBeenCalled()
    expect(tenantMocks.tenantList).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('当前会话缺少平台用户治理所需权限')
    expect(wrapper.text()).toContain('platform:user:list / platform:user:view')
  })

  it('checks tenant detail readability before navigating to tenant detail', async () => {
    authMocks.token = 'platform-tenant-token'
    routeState.query = {
      tab: 'tenantStewardship',
    }
    authMocks.fetchWithAuth.mockResolvedValue(createFetchResponse(true, 200, {}))

    const wrapper = mountPlatformUsers()
    await flushPromises()

    await (getTenantStewardshipTab(wrapper).vm as any).handleStewardshipTenantChange(9)
    await flushPromises()
    await (getTenantStewardshipTab(wrapper).vm as any).openTenantDetail(
      (getTenantStewardshipTab(wrapper).vm as any).selectedStewardshipTenant,
    )
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

  it('loads platform bridge user list in-page after selecting a stewardship tenant', async () => {
    authMocks.token = 'platform-tenant-token'
    routeState.query = {
      tab: 'tenantStewardship',
    }

    const wrapper = mountPlatformUsers()
    await flushPromises()

    await (getTenantStewardshipTab(wrapper).vm as any).handleStewardshipTenantChange(9)
    await flushPromises()

    expect(platformTenantUserMocks.listPlatformTenantUsers).toHaveBeenCalledWith({
      tenantId: 9,
      current: 1,
      pageSize: 10,
      username: undefined,
      nickname: undefined,
    })
    expect(wrapper.text()).toContain('清空租户')
    expect(wrapper.text()).toContain('平台详情')
    expect(wrapper.text()).toContain('租户侧用户列表')
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

  it('aligns tenant stewardship columns with tenant-side user management structure', async () => {
    authMocks.token = 'platform-tenant-token'
    routeState.query = {
      tab: 'tenantStewardship',
    }

    const wrapper = mountPlatformUsers()
    await flushPromises()

    await (getTenantStewardshipTab(wrapper).vm as any).handleStewardshipTenantChange(9)
    await flushPromises()

    const tenantColumnLabels = (getTenantStewardshipTab(wrapper).vm as any).columnOptions.map(
      (column: { label: string }) => column.label,
    )
    expect(wrapper.text()).toContain('租户侧用户列表')
    expect(tenantColumnLabels).toEqual(
      expect.arrayContaining([
        '是否启用',
        '账号未过期',
        '账号未锁定',
        '锁定状态',
        '剩余锁定时间',
        '失败登录次数',
        '最后失败时间',
        '密码未过期',
        '最后登录时间',
      ]),
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
    expect(wrapper.text()).toContain('清空租户')
    expect((getTenantStewardshipTab(wrapper).vm as any).selectedStewardshipTenant?.id).toBe(9)
  })

  it('clears stewardship tenant through toolbar action', async () => {
    authMocks.token = 'platform-tenant-token'
    routeState.query = {
      tab: 'tenantStewardship',
    }

    const wrapper = mountPlatformUsers()
    await flushPromises()

    await (getTenantStewardshipTab(wrapper).vm as any).handleStewardshipTenantChange(9)
    await flushPromises()

    const closeButton = wrapper.findAll('button').find((button) => button.text().includes('清空租户'))
    expect(closeButton).toBeDefined()

    await closeButton!.trigger('click')
    await flushPromises()

    expect((getTenantStewardshipTab(wrapper).vm as any).selectedStewardshipTenant).toBeNull()
    expect(routerMocks.replace).toHaveBeenLastCalledWith({
      path: '/platform/users',
      query: {
        tab: 'tenantStewardship',
      },
    })
  })
})
