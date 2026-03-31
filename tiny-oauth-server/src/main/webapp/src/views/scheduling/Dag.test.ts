import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const dagListMock = vi.hoisted(() => vi.fn())

vi.mock('@/api/scheduling', () => ({
  dagList: dagListMock,
  createDag: vi.fn(),
  updateDag: vi.fn(),
  deleteDag: vi.fn(),
  triggerDag: vi.fn(),
  stopDag: vi.fn(),
  retryDag: vi.fn(),
}))

vi.mock('@/utils/debounce', () => ({
  throttle: (fn: (...args: unknown[]) => unknown) => fn,
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ query: {} }),
    useRouter: () => ({ push: vi.fn() }),
  }
})

vi.mock('@/auth/auth', () => ({
  useAuth: () => ({
    user: { value: { access_token: 'x.y.z' } },
  }),
}))

vi.mock('@/utils/jwt', () => ({
  extractAuthoritiesFromJwt: () => ['scheduling:console:config', 'scheduling:run:control'],
}))

const PassThrough = defineComponent({
  template: '<div><slot /></div>',
})

import Dag from '@/views/scheduling/Dag.vue'
import { ACTIVE_SCOPE_CHANGED_EVENT } from '@/utils/activeScopeEvents'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
}

describe('Dag.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    dagListMock.mockResolvedValue({ records: [], total: 0 })
  })

  it('should reload DAG list when active scope changes', async () => {
    mount(Dag, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-button': PassThrough,
          'a-tooltip': PassThrough,
          'a-table': defineComponent({ template: '<div class="dag-table-stub" />' }),
          'a-modal': defineComponent({ props: ['open'], template: '<div v-if="open"><slot /></div>' }),
          'a-textarea': PassThrough,
          'a-input-group': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-switch': PassThrough,
          'a-popconfirm': defineComponent({ template: '<div><slot /></div>' }),
          'a-space': PassThrough,
          'a-tag': PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          CronDesigner: PassThrough,
        },
      },
    })
    await flushPromises()
    const afterMount = dagListMock.mock.calls.length
    expect(afterMount).toBeGreaterThan(0)

    window.dispatchEvent(new CustomEvent(ACTIVE_SCOPE_CHANGED_EVENT))
    await flushPromises()

    expect(dagListMock.mock.calls.length).toBeGreaterThan(afterMount)
  })
})
