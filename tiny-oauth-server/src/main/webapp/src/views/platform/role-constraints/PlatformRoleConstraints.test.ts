import { mount } from '@vue/test-utils'
import { computed, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const platformConstraintMocks = vi.hoisted(() => ({
  listPlatformHierarchies: vi.fn(),
  listPlatformMutexes: vi.fn(),
  listPlatformPrerequisites: vi.fn(),
  listPlatformCardinalities: vi.fn(),
  listPlatformViolations: vi.fn(),
}))

const platformRoleMocks = vi.hoisted(() => ({
  listPlatformRoleOptions: vi.fn(),
}))

const scopeMocks = vi.hoisted(() => ({
  isPlatformScope: { value: false },
}))

const authMocks = vi.hoisted(() => ({
  token: 't-tenant',
}))

const antDesignMocks = vi.hoisted(() => ({
  message: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  Modal: { confirm: vi.fn() },
}))

vi.mock('@/api/platformRoleConstraint', () => ({
  listPlatformHierarchies: platformConstraintMocks.listPlatformHierarchies,
  listPlatformMutexes: platformConstraintMocks.listPlatformMutexes,
  listPlatformPrerequisites: platformConstraintMocks.listPlatformPrerequisites,
  listPlatformCardinalities: platformConstraintMocks.listPlatformCardinalities,
  listPlatformViolations: platformConstraintMocks.listPlatformViolations,
  createPlatformHierarchy: vi.fn(),
  deletePlatformHierarchy: vi.fn(),
  createPlatformMutex: vi.fn(),
  deletePlatformMutex: vi.fn(),
  createPlatformPrerequisite: vi.fn(),
  deletePlatformPrerequisite: vi.fn(),
  createPlatformCardinality: vi.fn(),
  deletePlatformCardinality: vi.fn(),
}))

vi.mock('@/api/platform-role', () => ({
  listPlatformRoleOptions: platformRoleMocks.listPlatformRoleOptions,
}))

vi.mock('@/composables/usePlatformScope', () => ({
  usePlatformScope: () => ({
    isPlatformScope: computed(() => scopeMocks.isPlatformScope.value),
  }),
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: { value: { access_token: authMocks.token } },
  }),
}))

vi.mock('@/utils/jwt', () => ({
  extractAuthoritiesFromJwt: (token?: string) => {
    if (token === 't-platform-view') {
      return ['system:role:constraint:view', 'system:role:constraint:violation:view']
    }
    if (token === 't-platform-edit') {
      return ['system:role:constraint:edit']
    }
    if (token === 't-platform-violation') {
      return ['system:role:constraint:violation:view']
    }
    return []
  },
}))

vi.mock('ant-design-vue', () => ({
  message: antDesignMocks.message,
  Modal: antDesignMocks.Modal,
}))

import PlatformRoleConstraints from './PlatformRoleConstraints.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

function createStubs() {
  return {
    'a-card': { template: '<div><slot /></div>' },
    'a-tabs': { template: '<div><slot /></div>' },
    'a-tab-pane': { template: '<div><slot /></div>' },
    'a-table': { template: '<div />' },
    'a-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
    'a-tooltip': { template: '<span><slot /></span>' },
    'a-modal': { template: '<div />' },
    'a-form': { template: '<form><slot /></form>' },
    'a-form-item': { template: '<div><slot /></div>' },
    'a-input': { template: '<input />' },
    'a-select': { template: '<select />' },
    'a-select-option': { template: '<option />' },
    'a-input-number': { template: '<input />' },
    'a-pagination': { template: '<div />' },
    'a-tag': { template: '<span><slot /></span>' },
  }
}

describe('PlatformRoleConstraints.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    scopeMocks.isPlatformScope.value = false
    authMocks.token = 't-tenant'
    platformConstraintMocks.listPlatformHierarchies.mockResolvedValue([])
    platformConstraintMocks.listPlatformViolations.mockResolvedValue({ content: [], totalElements: 0 })
    platformRoleMocks.listPlatformRoleOptions.mockResolvedValue([])
  })

  it('does not call platform role-constraint APIs when not in platform scope', async () => {
    authMocks.token = 't-platform-view'
    const wrapper = mount(PlatformRoleConstraints, {
      global: {
        stubs: createStubs(),
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('PLATFORM 作用域')
    expect(platformConstraintMocks.listPlatformHierarchies).not.toHaveBeenCalled()
    expect(platformRoleMocks.listPlatformRoleOptions).not.toHaveBeenCalled()
  })

  it('loads hierarchy and role options when platform scope and view permission', async () => {
    scopeMocks.isPlatformScope.value = true
    authMocks.token = 't-platform-view'

    mount(PlatformRoleConstraints, {
      global: {
        stubs: createStubs(),
      },
    })
    await flushPromises()
    await flushPromises()

    expect(platformRoleMocks.listPlatformRoleOptions).toHaveBeenCalledWith({ limit: 500 })
    expect(platformConstraintMocks.listPlatformHierarchies).toHaveBeenCalled()
  })

  it('allows edit-only users to load role catalog without forcing read gate', async () => {
    scopeMocks.isPlatformScope.value = true
    authMocks.token = 't-platform-edit'

    const wrapper = mount(PlatformRoleConstraints, {
      global: {
        stubs: createStubs(),
      },
    })
    await flushPromises()
    await flushPromises()

    expect(wrapper.text()).not.toContain('平台 RBAC3 需要额外授权')
    expect(platformRoleMocks.listPlatformRoleOptions).toHaveBeenCalledWith({ limit: 500 })
    expect(platformConstraintMocks.listPlatformHierarchies).not.toHaveBeenCalled()
  })

  it('allows violation-only readers to open the page and load violations', async () => {
    scopeMocks.isPlatformScope.value = true
    authMocks.token = 't-platform-violation'

    const wrapper = mount(PlatformRoleConstraints, {
      global: {
        stubs: createStubs(),
      },
    })
    await flushPromises()
    await flushPromises()

    expect(wrapper.text()).not.toContain('平台 RBAC3 需要额外授权')
    expect(platformConstraintMocks.listPlatformViolations).toHaveBeenCalled()
    expect(platformConstraintMocks.listPlatformHierarchies).not.toHaveBeenCalled()
    expect(platformRoleMocks.listPlatformRoleOptions).not.toHaveBeenCalled()
  })
})
