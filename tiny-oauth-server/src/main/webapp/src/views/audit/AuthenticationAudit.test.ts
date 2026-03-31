import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  listAuthenticationAuditLogs: vi.fn(),
  getAuthenticationAuditSummary: vi.fn(),
  exportAuthenticationAuditLogs: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  authUser: { value: null as { access_token?: string | null } | null },
}))

const uiMocks = vi.hoisted(() => ({
  messageError: vi.fn(),
  messageSuccess: vi.fn(),
}))

vi.mock('@/api/audit', () => ({
  listAuthenticationAuditLogs: apiMocks.listAuthenticationAuditLogs,
  getAuthenticationAuditSummary: apiMocks.getAuthenticationAuditSummary,
  exportAuthenticationAuditLogs: apiMocks.exportAuthenticationAuditLogs,
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: authMocks.authUser,
  }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: uiMocks.messageError,
    success: uiMocks.messageSuccess,
  },
}))

import AuthenticationAudit from '@/views/audit/AuthenticationAudit.vue'

const PassThrough = defineComponent({
  template: '<div><slot /><slot name="content" /><slot name="icon" /></div>',
})

const InputStub = defineComponent({
  props: { value: [String, Number, Boolean], placeholder: String },
  emits: ['update:value'],
  template:
    '<input :value="value" :placeholder="placeholder" @input="$emit(\'update:value\', $event.target.value)" />',
})

const ButtonStub = defineComponent({
  props: {
    disabled: Boolean,
    loading: Boolean,
  },
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\', $event)"><slot /><slot name="icon" /></button>',
})

const TableStub = defineComponent({
  props: {
    columns: { type: Array, default: () => [] },
    dataSource: { type: Array, default: () => [] },
  },
  setup(props, { slots }) {
    return () =>
      h(
        'div',
        {},
        (props.dataSource as Record<string, unknown>[]).map((record) =>
          h(
            'div',
            { class: 'audit-row', 'data-id': String(record.id ?? '') },
            (props.columns as { key?: string; dataIndex?: string }[]).map((column) =>
              h(
                'div',
                { class: `col-${column.key ?? column.dataIndex ?? 'unknown'}` },
                slots.bodyCell?.({ column, record }) ?? String(record[column.dataIndex ?? column.key ?? ''] ?? ''),
              ),
            ),
          ),
        ),
      )
  },
})

async function flushPromises() {
  for (let index = 0; index < 6; index += 1) {
    await Promise.resolve()
  }
  await nextTick()
}

function createToken(authorities: string[], activeScopeType = 'TENANT') {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities, activeScopeType })).toString('base64url')
  return `${header}.${payload}.signature`
}

function mountView() {
  return mount(AuthenticationAudit, {
    global: {
      stubs: {
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-input': InputStub,
        'a-select': InputStub,
        'a-range-picker': PassThrough,
        'a-button': ButtonStub,
        'a-tooltip': PassThrough,
        'a-table': TableStub,
        'a-pagination': PassThrough,
        'a-tag': PassThrough,
        ReloadOutlined: defineComponent({ template: '<span>↻</span>' }),
      },
    },
  })
}

describe('AuthenticationAudit.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:authentication-audit'),
      revokeObjectURL: vi.fn(),
    })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    authMocks.authUser.value = {
      access_token: createToken(['system:audit:authentication:view', 'system:audit:authentication:export'], 'PLATFORM'),
    }
    apiMocks.listAuthenticationAuditLogs.mockResolvedValue({
      content: [
        {
          id: 1,
          tenantId: 9,
          userId: 7,
          username: 'alice',
          eventType: 'LOGIN',
          success: true,
          authenticationProvider: 'LOCAL',
          authenticationFactor: 'PASSWORD',
          ipAddress: '127.0.0.1',
          userAgent: 'JUnit',
          sessionId: 'session-1',
          tokenId: 'token-1',
          tenantResolutionCode: 'resolved',
          tenantResolutionSource: 'token',
          createdAt: '2026-03-19T10:00:00',
        },
      ],
      totalElements: 1,
    })
    apiMocks.getAuthenticationAuditSummary.mockResolvedValue({
      totalCount: 10,
      successCount: 8,
      failureCount: 2,
      loginSuccessCount: 5,
      loginFailureCount: 1,
      eventTypeCounts: [{ eventType: 'LOGIN', count: 6 }],
    })
    apiMocks.exportAuthenticationAuditLogs.mockResolvedValue(new Blob(['csv']))
  })

  it('should not load data without permission', async () => {
    authMocks.authUser.value = {
      access_token: createToken(['ROLE_USER']),
    }

    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.listAuthenticationAuditLogs).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('认证审计需要额外授权')
  })

  it('should load authentication audit data for authorized user', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.listAuthenticationAuditLogs).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 10,
      }),
    )
    expect(apiMocks.getAuthenticationAuditSummary).toHaveBeenCalledWith({})
    expect(wrapper.text()).toContain('LOGIN')
    expect(wrapper.findAll('.audit-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('日志总数')
    expect(wrapper.text()).toContain('10')
  })

  it('should export authentication audit logs', async () => {
    const wrapper = mountView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    const tenantIdInput = inputs.find((input) => input.attributes('placeholder') === '请输入租户ID')
    const reasonInput = inputs.find((input) => input.attributes('placeholder') === '用于高敏感治理导出')
    const ticketInput = inputs.find((input) => input.attributes('placeholder') === '可选，用于审计追踪')
    await tenantIdInput?.setValue('9')
    await reasonInput?.setValue('incident-check')
    await ticketInput?.setValue('TICKET-8')

    const buttons = wrapper.findAll('button')
    const exportButton = buttons.find((button) => button.text().includes('导出 CSV'))
    expect(exportButton).toBeTruthy()
    await exportButton!.trigger('click')
    await flushPromises()

    expect(apiMocks.exportAuthenticationAuditLogs).toHaveBeenCalledWith({
      tenantId: '9',
      reason: 'incident-check',
      ticketId: 'TICKET-8',
    })
  })
})
