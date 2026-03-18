import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  auditList: vi.fn(),
}))

const authMocks = vi.hoisted(() => ({
  authUser: { value: null as { access_token?: string | null } | null },
}))

const uiMocks = vi.hoisted(() => ({
  messageError: vi.fn(),
  messageWarning: vi.fn(),
}))

vi.mock('@/api/scheduling', () => ({
  auditList: apiMocks.auditList,
}))

vi.mock('@/utils/debounce', () => ({
  throttle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: authMocks.authUser,
  }),
}))

vi.mock('ant-design-vue', () => ({
  message: {
    error: uiMocks.messageError,
    warning: uiMocks.messageWarning,
  },
}))

import Audit from '@/views/scheduling/Audit.vue'

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

function mountView() {
  return mount(Audit, {
    global: {
      stubs: {
        'a-form': PassThrough,
        'a-form-item': PassThrough,
        'a-select': InputStub,
        'a-select-option': PassThrough,
        'a-button': ButtonStub,
        'a-tooltip': PassThrough,
        'a-table': TableStub,
        'a-modal': defineComponent({
          props: ['open', 'footer'],
          template: '<div v-if="open"><slot /></div>',
        }),
        'a-tag': PassThrough,
        'a-descriptions': PassThrough,
        'a-descriptions-item': PassThrough,
        ReloadOutlined: defineComponent({ template: '<span>↻</span>' }),
      },
    },
  })
}

function findRow(wrapper: ReturnType<typeof mountView>, id: number) {
  return wrapper.get(`.audit-row[data-id="${id}"]`)
}

function findButtonByText(wrapper: ReturnType<typeof mountView> | ReturnType<typeof findRow>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

function createToken(authorities: string[]) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(JSON.stringify({ authorities })).toString('base64url')
  return `${header}.${payload}.signature`
}

describe('Audit.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authMocks.authUser.value = {
      access_token: createToken(['scheduling:audit:view']),
    }
    apiMocks.auditList.mockResolvedValue({
      records: [
        {
          id: 1,
          recordTenantId: 88,
          objectType: 'dag',
          objectId: '10',
          action: 'TRIGGER',
          performedBy: 'alice',
          createdAt: '2026-03-10 10:00:00',
          detail: '{"runId":77}',
        },
      ],
      total: 1,
    })
  })

  it('should load audit list and support search/reset/refresh', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(apiMocks.auditList).toHaveBeenCalledWith(
      expect.objectContaining({
        current: 1,
        pageSize: 10,
      }),
    )

    await findButtonByText(wrapper, '搜索')?.trigger('click')
    await flushPromises()
    await findButtonByText(wrapper, '重置')?.trigger('click')
    await flushPromises()

    expect(apiMocks.auditList).toHaveBeenCalledTimes(3)
  })

  it('should open detail modal for selected audit record', async () => {
    const wrapper = mountView()
    await flushPromises()

    await findButtonByText(findRow(wrapper, 1), '查看详情')?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('alice')
    expect(wrapper.text()).toContain('"runId": 77')
  })
})
