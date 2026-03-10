import { mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const taskMocks = vi.hoisted(() => ({
  getTasks: vi.fn(),
}))

const userMocks = vi.hoisted(() => ({
  getCurrentUser: vi.fn(),
}))

vi.mock('@/api/process', () => ({
  taskApi: { getTasks: taskMocks.getTasks, claimTask: vi.fn(), completeTask: vi.fn() },
  userApi: { getCurrentUser: userMocks.getCurrentUser },
}))

vi.mock('@/utils/debounce', () => ({
  useThrottle: (fn: (...args: unknown[]) => unknown) => fn,
}))

const PassThrough = defineComponent({ template: '<div><slot /></div>' })

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
    userMocks.getCurrentUser.mockResolvedValue({ username: 'alice' })
    taskMocks.getTasks.mockResolvedValue([])
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
          'a-button': PassThrough,
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
    expect(taskMocks.getTasks).toHaveBeenCalled()
  })
})

