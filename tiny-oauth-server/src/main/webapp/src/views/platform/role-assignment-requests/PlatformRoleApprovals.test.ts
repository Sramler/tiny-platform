import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed, defineComponent, nextTick, reactive } from 'vue'
import PlatformRoleApprovals from './PlatformRoleApprovals.vue'

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
  useAuth: () => ({
    user: {
      get value() {
        const permissions = authMocks.token === 'no-approval'
          ? ['platform:user:list']
          : authMocks.token === 'submit-only'
            ? ['platform:role:approval:submit']
            : ['platform:role:approval:list']
        return { userId: 99, activeScopeType: 'PLATFORM', permissions }
      },
    },
  }),
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

const PassThrough = defineComponent({
  template: '<div><slot /><slot name="icon" /><slot name="content" /><slot name="overlay" /></div>',
})

const TableStub = defineComponent({
  props: ['columns', 'dataSource', 'loading', 'pagination', 'rowKey'],
  template: '<div class="table-stub"></div>',
})

const ModalStub = defineComponent({
  props: ['open', 'title', 'confirmLoading'],
  emits: ['ok', 'cancel', 'update:open'],
  template: '<div v-if="open" class="modal-stub"><slot /></div>',
})

function mountApprovals(component: any) {
  return mount(component, {
    global: {
      stubs: {
        'a-card': PassThrough,
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-input-number': PassThrough,
        'a-select': PassThrough,
        'a-select-option': PassThrough,
        'a-textarea': PassThrough,
        'a-button': PassThrough,
        'a-tooltip': PassThrough,
        'a-space': PassThrough,
        'a-table': TableStub,
        'a-modal': ModalStub,
        PlusOutlined: PassThrough,
        ReloadOutlined: PassThrough,
      },
    },
  })
}

describe('PlatformRoleApprovals.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authMocks.token = 't1'
    approvalMocks.list.mockResolvedValue({ records: [], total: 0 })
    approvalMocks.listRoleOptions.mockResolvedValue([])
  })

  it('shows permission guard without approval authorities', async () => {
    authMocks.token = 'no-approval'
    const wrapper = mountApprovals(PlatformRoleApprovals)
    await nextTick()
    expect(wrapper.text()).toContain('缺少平台赋权审批权限')
    expect(approvalMocks.list).not.toHaveBeenCalled()
    expect(approvalMocks.listRoleOptions).not.toHaveBeenCalled()
  })

  it('loads table when approval list permission present', async () => {
    mountApprovals(PlatformRoleApprovals)
    await nextTick()
    await nextTick()
    expect(approvalMocks.list).toHaveBeenCalled()
    expect(approvalMocks.listRoleOptions).not.toHaveBeenCalled()
  })

  it('loads role options lazily when submit-only user opens submit modal', async () => {
    authMocks.token = 'submit-only'
    const wrapper = mountApprovals(PlatformRoleApprovals)
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
