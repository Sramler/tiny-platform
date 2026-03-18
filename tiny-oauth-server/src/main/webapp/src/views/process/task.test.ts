import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const taskMocks = vi.hoisted(() => ({
  getTasks: vi.fn(),
}))

const userMocks = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
}))

const tenantMocks = vi.hoisted(() => ({
  getActiveTenantId: vi.fn(),
}))

const routerMocks = vi.hoisted(() => ({
  routeQuery: {} as Record<string, unknown>,
  routerReplace: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: routerMocks.routeQuery,
  }),
  useRouter: () => ({
    replace: routerMocks.routerReplace,
  }),
}))

vi.mock('@/api/process', () => ({
  taskApi: { getTasks: taskMocks.getTasks, claimTask: vi.fn(), completeTask: vi.fn() },
}))

vi.mock('@/api/user', () => ({
  getCurrentUser: userMocks.getCurrentUser,
}))

vi.mock('@/utils/tenant', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/utils/tenant')>()
  return {
    ...actual,
    getActiveTenantId: tenantMocks.getActiveTenantId,
  }
})

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })
const ButtonStub = defineComponent({
  emits: ['click'],
  template: '<button @click="$emit(\'click\')"><slot /></button>',
})

import TaskView from '@/views/process/task.vue'

async function flushPromises() {
  await Promise.resolve()
  await nextTick()
  await Promise.resolve()
  await nextTick()
}

describe('process task.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.keys(routerMocks.routeQuery).forEach((key) => {
      delete routerMocks.routeQuery[key]
    })
    tenantMocks.getActiveTenantId.mockReturnValue('9')
    userMocks.getCurrentUser.mockResolvedValue({ username: 'alice', activeTenantId: 9 })
    taskMocks.getTasks.mockResolvedValue([])
    vi.spyOn(window, 'open').mockImplementation(() => null)
    vi.useFakeTimers()
  })

  it('should fetch current user and tasks on mount', async () => {
    const wrapper = mount(TaskView, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': ButtonStub,
          'a-tooltip': PassThrough,
          'a-table': defineComponent({ props: ['dataSource'], template: '<div class=\"table\" />' }),
          'a-tag': PassThrough,
          'a-modal': PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          CloseOutlined: PassThrough,
          UserAddOutlined: PassThrough,
          CheckCircleOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('任务列表')
    expect(userMocks.getCurrentUser).toHaveBeenCalled()
    expect(taskMocks.getTasks).toHaveBeenCalledWith('*')
  })

  it('should honor activeTenantId route query on first load', async () => {
    routerMocks.routeQuery.activeTenantId = '11'

    mount(TaskView, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': ButtonStub,
          'a-tooltip': PassThrough,
          'a-table': defineComponent({ props: ['dataSource'], template: '<div class=\"table\" />' }),
          'a-tag': PassThrough,
          'a-modal': PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          CloseOutlined: PassThrough,
          UserAddOutlined: PassThrough,
          CheckCircleOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    expect(taskMocks.getTasks).toHaveBeenCalledWith('*')
    expect(routerMocks.routerReplace).not.toHaveBeenCalled()
  })

  it('should preserve activeTenantId when opening modeling page', async () => {
    routerMocks.routeQuery.activeTenantId = '11'

    const wrapper = mount(TaskView, {
      global: {
        stubs: {
          'a-form': PassThrough,
          'a-form-item': PassThrough,
          'a-input': PassThrough,
          'a-select': PassThrough,
          'a-select-option': PassThrough,
          'a-button': ButtonStub,
          'a-tooltip': PassThrough,
          'a-table': defineComponent({ props: ['dataSource'], template: '<div class="table" />' }),
          'a-tag': PassThrough,
          'a-modal': PassThrough,
          PlusOutlined: PassThrough,
          ReloadOutlined: PassThrough,
          CloseOutlined: PassThrough,
          UserAddOutlined: PassThrough,
          CheckCircleOutlined: PassThrough,
        },
      },
    })
    await flushPromises()

    const createButton = wrapper.findAll('button').find((button) => button.text().includes('新建流程'))
    expect(createButton).toBeDefined()
    await createButton!.trigger('click')

    expect(window.open).toHaveBeenCalledWith('/process/modeling?activeTenantId=11', '_blank')
  })
})
