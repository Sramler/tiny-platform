import { beforeEach, describe, expect, it, vi } from 'vitest'

const requestState = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    get: requestState.get,
  },
}))

describe('exportApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should request export task list', async () => {
    requestState.get.mockResolvedValue([{ taskId: 'task-1' }])
    const { exportApi } = await import('@/api/export')

    const result = await exportApi.listTasks()

    expect(requestState.get).toHaveBeenCalledWith('/export/task')
    expect(result).toEqual([{ taskId: 'task-1' }])
  })

  it('should request single export task detail', async () => {
    requestState.get.mockResolvedValue({ taskId: 'task-2' })
    const { exportApi } = await import('@/api/export')

    const result = await exportApi.getTask('task-2')

    expect(requestState.get).toHaveBeenCalledWith('/export/task/task-2')
    expect(result).toEqual({ taskId: 'task-2' })
  })
})
