import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  listAuditLogs: vi.fn(),
  getAuthorizationAuditSummary: vi.fn(),
  exportAuthorizationAuditLogs: vi.fn(),
  purgeAuditLogs: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  authUser: { value: null as { access_token?: string | null } | null },
}))

vi.mock('@/api/audit', () => ({
  listAuditLogs: apiMocks.listAuditLogs,
  getAuthorizationAuditSummary: apiMocks.getAuthorizationAuditSummary,
  exportAuthorizationAuditLogs: apiMocks.exportAuthorizationAuditLogs,
  purgeAuditLogs: apiMocks.purgeAuditLogs,
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: authMocks.authUser,
  }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
  },
  Modal: {
    confirm: vi.fn(),
  },
}))

import AuthorizationAudit from '@/views/audit/AuthorizationAudit.vue'

const PassThrough = defineComponent({
  template: '<div><slot /><slot name="content" /><slot name="icon" /></div>',
})

const InputStub = defineComponent({
  props: { value: [String, Number], placeholder: String },
  emits: ['update:value'],
  template:
    '<input :value="value" :placeholder="placeholder" @input="$emit(\'update:value\', $event.target.value)" />',
})

const ButtonStub = defineComponent({
  props: { disabled: Boolean, loading: Boolean },
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

const RangePickerStub = defineComponent({
  emits: ['update:value'],
  setup(_, { emit }) {
    return () =>
      h(
        'button',
        {
          class: 'range-picker',
          onClick: () =>
            emit('update:value', [
              { toISOString: () => '2026-03-01T00:00:00.000Z' },
              { toISOString: () => '2026-03-02T23:59:59.000Z' },
            ]),
        },
        'range',
      )
  },
})

async function flushPromises() {
  for (let index = 0; index < 6; index += 1) {
    await Promise.resolve()
  }
  await nextTick()
}

function createToken(payload: Record<string, unknown>) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const encodedPayload = Buffer.from(JSON.stringify(payload)).toString('base64url')
  return `${header}.${encodedPayload}.signature`
}

function mountView() {
  return mount(AuthorizationAudit, {
    global: {
      stubs: {
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-input': InputStub,
        'a-select': InputStub,
        'a-range-picker': RangePickerStub,
        'a-button': ButtonStub,
        'a-tooltip': PassThrough,
        'a-table': TableStub,
        'a-pagination': PassThrough,
        'a-tag': PassThrough,
        'a-modal': PassThrough,
        'a-input-number': InputStub,
        ReloadOutlined: defineComponent({ template: '<span>↻</span>' }),
        DeleteOutlined: defineComponent({ template: '<span>✖</span>' }),
      },
    },
  })
}

describe('AuthorizationAudit.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:authorization-audit'),
      revokeObjectURL: vi.fn(),
    })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    authMocks.authUser.value = {
      access_token: createToken({
        permissions: ['system:audit:auth:view', 'system:audit:auth:export'],
        activeScopeType: 'PLATFORM',
      }),
    }
    apiMocks.listAuditLogs.mockResolvedValue({
      content: [
        {
          id: 1,
          tenantId: 9,
          eventType: 'ROLE_ASSIGNMENT_GRANT',
          actorUserId: 3,
          targetUserId: 7,
          scopeType: 'TENANT',
          scopeId: '9',
          roleId: 11,
          module: 'iam',
          resourcePermission: 'system:user:assign-role',
          eventDetail: '{"userId":7}',
          result: 'SUCCESS',
          resultReason: null,
          ipAddress: '127.0.0.1',
          createdAt: '2026-03-19T10:00:00',
        },
      ],
      totalElements: 1,
    })
    apiMocks.getAuthorizationAuditSummary.mockResolvedValue({
      totalCount: 8,
      successCount: 6,
      deniedCount: 2,
      eventTypeCounts: [{ eventType: 'ROLE_ASSIGNMENT_GRANT', count: 5 }],
    })
    apiMocks.exportAuthorizationAuditLogs.mockResolvedValue(new Blob(['csv']))
  })

  it('should not load data without permission', async () => {
    authMocks.authUser.value = {
      access_token: createToken({
        authorities: ['ROLE_USER'],
      }),
    }

    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.listAuditLogs).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('授权审计日志需要额外授权')
  })

  it('should load audit data and submit search filters', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.listAuditLogs).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 10,
      }),
    )
    expect(apiMocks.getAuthorizationAuditSummary).toHaveBeenCalledWith({})
    expect(wrapper.text()).toContain('ROLE_ASSIGNMENT_GRANT')
    expect(wrapper.text()).toContain('日志总数')
    expect(wrapper.text()).toContain('8')

    const inputs = wrapper.findAll('input')
    const tenantIdInput = inputs.find((input) => input.attributes('placeholder') === '请输入租户ID')
    const targetUserInput = inputs.find((input) => input.attributes('placeholder') === '请输入用户ID')
    const allSelectInputs = inputs.filter((input) => input.attributes('placeholder') === '全部')
    const eventTypeInput = allSelectInputs[0]
    const carrierTypeInput = allSelectInputs[2]
    const decisionInput = allSelectInputs[3]
    const requirementGroupInput = inputs.find((input) => input.attributes('placeholder') === '如 0')
    await tenantIdInput?.setValue('9')
    await eventTypeInput?.setValue('ROLE_ASSIGNMENT_GRANT')
    await targetUserInput?.setValue('7')
    await carrierTypeInput?.setValue('api_endpoint')
    await requirementGroupInput?.setValue('0')
    await decisionInput?.setValue('DENY')
    await wrapper.find('.range-picker').trigger('click')
    const searchButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('搜索'))
    expect(searchButton).toBeTruthy()
    await searchButton!.trigger('click')
    await flushPromises()

    expect(apiMocks.listAuditLogs).toHaveBeenLastCalledWith(
      expect.objectContaining({
        page: 0,
        size: 10,
        tenantId: '9',
        eventType: 'ROLE_ASSIGNMENT_GRANT',
        targetUserId: '7',
        carrierType: 'api_endpoint',
        requirementGroup: 0,
        decision: 'DENY',
        startTime: '2026-03-01T00:00:00.000Z',
        endTime: '2026-03-02T23:59:59.000Z',
      }),
    )

    expect(apiMocks.getAuthorizationAuditSummary).toHaveBeenLastCalledWith(
      expect.objectContaining({
        tenantId: '9',
        eventType: 'ROLE_ASSIGNMENT_GRANT',
        targetUserId: '7',
        carrierType: 'api_endpoint',
        requirementGroup: 0,
        decision: 'DENY',
        startTime: '2026-03-01T00:00:00.000Z',
        endTime: '2026-03-02T23:59:59.000Z',
      }),
    )
  })

  it('should export audit logs with current filters', async () => {
    const wrapper = mountView()
    await flushPromises()

    const inputs = wrapper.findAll('input')
    const tenantIdInput = inputs.find((input) => input.attributes('placeholder') === '请输入租户ID')
    const reasonInput = inputs.find((input) => input.attributes('placeholder') === '用于高敏感治理导出')
    const ticketInput = inputs.find((input) => input.attributes('placeholder') === '可选，用于审计追踪')
    const allSelectInputs = inputs.filter((input) => input.attributes('placeholder') === '全部')
    const carrierTypeInput = allSelectInputs[2]
    const decisionInput = allSelectInputs[3]
    const requirementGroupInput = inputs.find((input) => input.attributes('placeholder') === '如 0')
    await tenantIdInput?.setValue('9')
    await reasonInput?.setValue('incident-check')
    await ticketInput?.setValue('TICKET-9')
    await carrierTypeInput?.setValue('ui_action')
    await requirementGroupInput?.setValue('1')
    await decisionInput?.setValue('ALLOW')

    const buttons = wrapper.findAll('button')
    const exportButton = buttons.find((button) => button.text().includes('导出 CSV'))
    expect(exportButton).toBeTruthy()
    await exportButton!.trigger('click')
    await flushPromises()

    expect(apiMocks.exportAuthorizationAuditLogs).toHaveBeenCalledWith({
      tenantId: '9',
      carrierType: 'ui_action',
      requirementGroup: 1,
      decision: 'ALLOW',
      reason: 'incident-check',
      ticketId: 'TICKET-9',
    })
  })
})
