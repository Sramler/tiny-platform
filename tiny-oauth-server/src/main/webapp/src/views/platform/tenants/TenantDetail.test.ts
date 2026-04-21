import { mount } from '@vue/test-utils'
import { reactive, defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const tenantApiMocks = vi.hoisted(() => ({
  getTenantById: vi.fn(),
  getTenantPermissionSummary: vi.fn(),
  diffTenantPlatformTemplate: vi.fn(),
}))

const routeState = reactive({ params: { id: '1' }, query: {} as Record<string, unknown> })
const routerMocks = vi.hoisted(() => ({
  push: vi.fn(),
}))

vi.mock('@/api/tenant', () => ({
  getTenantById: tenantApiMocks.getTenantById,
  getTenantPermissionSummary: tenantApiMocks.getTenantPermissionSummary,
  diffTenantPlatformTemplate: tenantApiMocks.diffTenantPlatformTemplate,
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ push: routerMocks.push }),
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})

import TenantDetail from '@/views/platform/tenants/TenantDetail.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('TenantDetail.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    routeState.params.id = '1'
    routeState.query = {}
    tenantApiMocks.getTenantById.mockResolvedValue({ id: 1, code: 't1', name: 'Tenant 1', lifecycleStatus: 'ACTIVE', enabled: true })
    tenantApiMocks.getTenantPermissionSummary.mockResolvedValue({
      tenantId: 1,
      totalRoles: 1,
      enabledRoles: 1,
      totalPermissions: 2,
      assignedPermissions: 2,
      totalCarriers: 3,
      boundCarriers: 3,
      menuCarriers: 1,
      uiActionCarriers: 1,
      apiEndpointCarriers: 1,
    })
    tenantApiMocks.diffTenantPlatformTemplate.mockResolvedValue({
      tenantId: 1,
      summary: { totalPlatformEntries: 3, totalTenantEntries: 3, missingInTenant: 0, extraInTenant: 0, changed: 0 },
      diffs: [],
    })
  })

  it('should reload when route tenant id changes', async () => {
    mount(TenantDetail, {
      global: {
        stubs: {
          'a-button': ButtonStub,
          'a-spin': PassThrough,
          'a-tag': PassThrough,
          'a-table': PassThrough,
        },
      },
    })
    await flushPromises()
    expect(tenantApiMocks.getTenantById).toHaveBeenCalledWith('1')

    routeState.params.id = '2'
    tenantApiMocks.getTenantById.mockResolvedValueOnce({ id: 2, code: 't2', name: 'Tenant 2', lifecycleStatus: 'ACTIVE', enabled: true })
    tenantApiMocks.getTenantPermissionSummary.mockResolvedValueOnce({
      tenantId: 2,
      totalRoles: 2,
      enabledRoles: 2,
      totalPermissions: 3,
      assignedPermissions: 3,
      totalCarriers: 4,
      boundCarriers: 4,
      menuCarriers: 2,
      uiActionCarriers: 1,
      apiEndpointCarriers: 1,
    })
    tenantApiMocks.diffTenantPlatformTemplate.mockResolvedValueOnce({
      tenantId: 2,
      summary: { totalPlatformEntries: 3, totalTenantEntries: 3, missingInTenant: 0, extraInTenant: 0, changed: 0 },
      diffs: [],
    })
    await flushPromises()

    expect(tenantApiMocks.getTenantById).toHaveBeenCalledWith('2')
    expect(tenantApiMocks.getTenantById).toHaveBeenCalledTimes(2)
  })

  it('should go back to platform users stewardship when source query is present', async () => {
    routeState.query = {
      from: '/platform/users?tab=tenantStewardship&tenantId=9',
    }

    const wrapper = mount(TenantDetail, {
      global: {
        stubs: {
          'a-button': ButtonStub,
          'a-spin': PassThrough,
          'a-tag': PassThrough,
          'a-table': PassThrough,
        },
      },
    })
    await flushPromises()

    const backButton = wrapper.findAll('button').find((button) => button.text().includes('返回租户用户代管'))
    expect(backButton).toBeDefined()

    await backButton!.trigger('click')

    expect(routerMocks.push).toHaveBeenCalledWith('/platform/users?tab=tenantStewardship&tenantId=9')
  })

  it('should focus permission summary section when section query is permission-summary', async () => {
    routeState.query = { section: 'permission-summary' }
    const wrapper = mount(TenantDetail, {
      global: {
        stubs: {
          'a-button': ButtonStub,
          'a-spin': PassThrough,
          'a-tag': PassThrough,
          'a-table': PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.find('[data-test="section-permission-summary"]').classes()).toContain('section-focused')
    expect(wrapper.find('[data-test="section-overview"]').classes()).not.toContain('section-focused')
  })

  it('should focus template diff section when section query changes', async () => {
    routeState.query = { section: 'overview' }
    const wrapper = mount(TenantDetail, {
      global: {
        stubs: {
          'a-button': ButtonStub,
          'a-spin': PassThrough,
          'a-tag': PassThrough,
          'a-table': PassThrough,
        },
      },
    })
    await flushPromises()
    expect(wrapper.find('[data-test="section-overview"]').classes()).toContain('section-focused')

    routeState.query = { section: 'template-diff' }
    await flushPromises()

    expect(wrapper.find('[data-test="section-template-diff"]').classes()).toContain('section-focused')
  })

  it('should fallback to overview when section query is invalid', async () => {
    routeState.query = { section: 'unknown-section' }
    const wrapper = mount(TenantDetail, {
      global: {
        stubs: {
          'a-button': ButtonStub,
          'a-spin': PassThrough,
          'a-tag': PassThrough,
          'a-table': PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.find('[data-test="section-overview"]').classes()).toContain('section-focused')
    expect(wrapper.find('[data-test="section-permission-summary"]').classes()).not.toContain('section-focused')
    expect(wrapper.find('[data-test="section-template-diff"]').classes()).not.toContain('section-focused')
  })

  it('should reset focused section to normalized query when tenant changes', async () => {
    routeState.query = {}
    const wrapper = mount(TenantDetail, {
      global: {
        stubs: {
          'a-button': ButtonStub,
          'a-spin': PassThrough,
          'a-tag': PassThrough,
          'a-table': PassThrough,
        },
      },
    })
    await flushPromises()

    const permissionSummaryButton = wrapper.findAll('button').find((button) => button.text().includes('权限摘要'))
    expect(permissionSummaryButton).toBeDefined()
    await permissionSummaryButton!.trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-test="section-permission-summary"]').classes()).toContain('section-focused')

    routeState.params.id = '2'
    tenantApiMocks.getTenantById.mockResolvedValueOnce({ id: 2, code: 't2', name: 'Tenant 2', lifecycleStatus: 'ACTIVE', enabled: true })
    tenantApiMocks.getTenantPermissionSummary.mockResolvedValueOnce({
      tenantId: 2,
      totalRoles: 2,
      enabledRoles: 2,
      totalPermissions: 3,
      assignedPermissions: 3,
      totalCarriers: 4,
      boundCarriers: 4,
      menuCarriers: 2,
      uiActionCarriers: 1,
      apiEndpointCarriers: 1,
    })
    tenantApiMocks.diffTenantPlatformTemplate.mockResolvedValueOnce({
      tenantId: 2,
      summary: { totalPlatformEntries: 3, totalTenantEntries: 3, missingInTenant: 0, extraInTenant: 0, changed: 0 },
      diffs: [],
    })
    await flushPromises()

    expect(wrapper.find('[data-test="section-overview"]').classes()).toContain('section-focused')
    expect(wrapper.find('[data-test="section-permission-summary"]').classes()).not.toContain('section-focused')
  })
})
