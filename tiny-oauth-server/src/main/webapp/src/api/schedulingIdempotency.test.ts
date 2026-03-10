import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  post: vi.fn(),
  put: vi.fn(),
  get: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: {
    post: mocks.post,
    put: mocks.put,
    get: mocks.get,
    delete: mocks.delete,
  },
}))

describe('scheduling API idempotency', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
  })

  it('should attach idempotency config for scheduling mutations', async () => {
    const schedulingApi = await import('@/api/scheduling')
    const dto = { code: 'daily-job', name: 'Daily Job' }
    const version = { versionName: 'v1' }
    const node = { nodeCode: 'extract' }
    const edge = { fromNodeCode: 'extract', toNodeCode: 'load' }

    await schedulingApi.createTaskType(dto)
    await schedulingApi.updateTaskType(1, dto)
    await schedulingApi.deleteTaskType(1)
    await schedulingApi.createTask(dto)
    await schedulingApi.updateTask(2, dto)
    await schedulingApi.deleteTask(2)
    await schedulingApi.createDag(dto)
    await schedulingApi.updateDag(3, dto)
    await schedulingApi.deleteDag(3)
    await schedulingApi.createDagVersion(3, version)
    await schedulingApi.updateDagVersion(3, 4, version)
    await schedulingApi.createDagNode(3, 4, node)
    await schedulingApi.updateDagNode(3, 4, 5, node)
    await schedulingApi.deleteDagNode(3, 4, 5)
    await schedulingApi.createDagEdge(3, 4, edge)
    await schedulingApi.deleteDagEdge(3, 4, 6)
    await schedulingApi.triggerDag(3)
    await schedulingApi.pauseDag(3)
    await schedulingApi.resumeDag(3)
    await schedulingApi.stopDag(3)
    await schedulingApi.stopDagRun(3, 7)
    await schedulingApi.retryDag(3)
    await schedulingApi.retryDagRun(3, 7)
    await schedulingApi.triggerNode(3, 5)
    await schedulingApi.retryNode(3, 5)
    await schedulingApi.pauseNode(3, 5)
    await schedulingApi.resumeNode(3, 5)
    await schedulingApi.triggerDagRunNode(3, 7, 5)
    await schedulingApi.retryDagRunNode(3, 7, 5)
    await schedulingApi.pauseDagRunNode(3, 7, 5)
    await schedulingApi.resumeDagRunNode(3, 7, 5)

    expect(mocks.post).toHaveBeenCalledWith('/scheduling/task-type', dto, {
      idempotency: { scope: 'scheduling-task-type:create', payload: dto },
    })
    expect(mocks.put).toHaveBeenCalledWith('/scheduling/task-type/1', dto, {
      idempotency: { scope: 'scheduling-task-type:update:1', payload: dto },
    })
    expect(mocks.delete).toHaveBeenCalledWith('/scheduling/task-type/1', {
      idempotency: { scope: 'scheduling-task-type:delete:1', payload: { id: 1 } },
    })
    expect(mocks.post).toHaveBeenCalledWith('/scheduling/dag/3/version/4/edge', edge, {
      idempotency: {
        scope: 'scheduling-dag-edge:create:3:4',
        payload: { dagId: 3, versionId: 4, data: edge },
      },
    })
    expect(mocks.delete).toHaveBeenCalledWith('/scheduling/dag/3/version/4/edge/6', {
      idempotency: {
        scope: 'scheduling-dag-edge:delete:3:4:6',
        payload: { dagId: 3, versionId: 4, edgeId: 6 },
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/scheduling/dag/3/trigger', null, {
      idempotency: {
        scope: 'scheduling-dag:trigger:3',
        payload: { dagId: 3 },
        mode: 'submit',
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/scheduling/dag/3/run/7/stop', null, {
      idempotency: {
        scope: 'scheduling-dag-run:stop:3:7',
        payload: { dagId: 3, runId: 7 },
        mode: 'submit',
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/scheduling/dag/3/run/7/retry', null, {
      idempotency: {
        scope: 'scheduling-dag-run:retry:3:7',
        payload: { dagId: 3, runId: 7 },
        mode: 'submit',
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/scheduling/dag/3/node/5/resume', null, {
      idempotency: {
        scope: 'scheduling-dag-node:resume:3:5',
        payload: { dagId: 3, nodeId: 5 },
        mode: 'submit',
      },
    })
    expect(mocks.post).toHaveBeenCalledWith('/scheduling/dag/3/run/7/node/5/resume', null, {
      idempotency: {
        scope: 'scheduling-dag-run-node:resume:3:7:5',
        payload: { dagId: 3, runId: 7, nodeId: 5 },
        mode: 'submit',
      },
    })
  })
})
