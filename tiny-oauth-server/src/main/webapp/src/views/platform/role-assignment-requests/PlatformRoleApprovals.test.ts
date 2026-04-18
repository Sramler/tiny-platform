import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, nextTick, reactive } from 'vue'

const approvalMocks = vi.hoisted(() => ({
  list: vi.fn(),
  listRoleOptions: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  token: 't1',
}))

vi.mock('@/api/platform-role-approval', () => ({
  listPlatformRoleAssignmentRequests: approvalMocks.list,
  submitPlatformRoleAssignmentRequest: vi.fn(),
  approvePlatformRoleAssignmentRequest: vi.fn(),
  rejectPlatformRoleAssignmentRequest: vi.fn(),
  cancelPlatformRoleAssignmentRequest: vi.fn(),
}))

vi.mock('@/api/platform-role', () => ({
  listPlatformRoleOptions: approvalMocks.listRoleOptions,
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({ user: { value: { access_token: authMocks.token } } }),
}))

vi.mock('@/utils/jwt', () => ({
  extractAuthoritiesFromJwt: (t?: string) => {
    if (t === 'no-approval') {
      return ['platform:user:list']
    }
    if (t === 'submit-only') {
      return ['platform:role:approval:submit']
    }
    return ['platform:role:approval:list']
  },
  extractUserIdFromJwt: () => 99,
}))

vi.mock('@/composables/usePlatformScope', () => ({
  usePlatformScope: () => ({ isPlatformScope: computed(() => true) }),
}))

vi.mock('vue-router', () => ({
  useRoute: () => reactive({ query: {} }),
}))

vi.mock('ant-design-vue', () => {
  const message = { error: vi.fn(), success: vi.fn(), warning: vi.fn() }
  return { message }
})

describe('PlatformRoleApprovals.vue', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    authMocks.token = 't1'
    approvalMocks.list.mockResolvedValue({ records: [], total: 0 })
    approvalMocks.listRoleOptions.mockResolvedValue([])
  })

  it('shows permission guard without approval authorities', async () => {
    authMocks.token = 'no-approval'
    const comp = (await import('./PlatformRoleApprovals.vue')).default
    const wrapper = mount(comp)
    await nextTick()
    expect(wrapper.text()).toContain('缺少平台赋权审批权限')
    expect(approvalMocks.list).not.toHaveBeenCalled()
    expect(approvalMocks.listRoleOptions).not.toHaveBeenCalled()
  })

  it('loads table when approval list permission present', async () => {
    const comp = (await import('./PlatformRoleApprovals.vue')).default
    mount(comp)
    await nextTick()
    await nextTick()
    expect(approvalMocks.list).toHaveBeenCalled()
    expect(approvalMocks.listRoleOptions).not.toHaveBeenCalled()
  })

  it('loads role options lazily when submit-only user opens submit modal', async () => {
    authMocks.token = 'submit-only'
    const comp = (await import('./PlatformRoleApprovals.vue')).default
    const wrapper = mount(comp)
    await nextTick()
    await nextTick()

    expect(approvalMocks.list).toHaveBeenCalled()
    expect(approvalMocks.listRoleOptions).not.toHaveBeenCalled()

    await (wrapper.vm as any).openSubmitModal()
    await nextTick()
    await nextTick()

    expect(approvalMocks.listRoleOptions).toHaveBeenCalledWith({ limit: 500 })
  })
})
